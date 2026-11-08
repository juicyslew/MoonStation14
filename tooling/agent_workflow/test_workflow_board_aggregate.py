import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

from starlette.testclient import TestClient

import workflow_coordinator as wc
import workflow_store
from test_workflow_board_projection import record
from workflow_store_test_support import admission_records
from deploy_workflow import logical_db_fingerprint


class BoardAggregateHTTPTests(unittest.TestCase):
    def test_configured_activity_roots_allow_only_exact_agent_01_through_04(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            paths=[root/'MoonStation14',*(root/f'MoonStation14-agent-{i:02d}' for i in range(1,6)),
                   root/'MoonStation14-agent-04'/'..'/'MoonStation14-agent-04']
            result=wc._configured_activity_roots(paths)
            self.assertEqual({Path(p).name for p in result},{f'MoonStation14-agent-{i:02d}' for i in range(1,5)})
            self.assertEqual(len(result),4)

    def setup_coordinator(self, root):
        main=root/'MoonStation14'; agent=root/'MoonStation14-agent-01'; foreign=root/'foreign'
        main.mkdir(); agent.mkdir(); foreign.mkdir()
        store=Mock(); store.path=str(root/'state.sqlite')
        store.outbox_summary.return_value={}
        main_run={'run':{'run_id':'main-run','run_kind':'FEATURE_GATE','current_attempt':1,'sequence':1,
            'input_digest':'d','author':{'worktree':str(main)}},'attempt':{'generation':1,'phase':'DECIDED'}}
        agent_run={'run':{'run_id':'agent-run','run_kind':'FEATURE_GATE','current_attempt':2,'sequence':2,
            'input_digest':'e','author':{'worktree':str(agent)}},'attempt':{'generation':2,'phase':'DECIDED'}}
        foreign_run={'run':{'run_id':'foreign-run','run_kind':'FEATURE_GATE','current_attempt':3,'sequence':3,
            'input_digest':'f','author':{'worktree':str(foreign)}},'attempt':{'generation':3,'phase':'DECIDED'}}
        store.get_run.side_effect=lambda rid:{'main-run':main_run,'agent-run':agent_run,'foreign-run':foreign_run}.get(rid)
        store.get_terminal_result.return_value=None; store.get_run_submission.return_value=None
        store.list_tasks.return_value=[]
        binding=wc.CoordinatorBinding('main-caller',SimpleNamespace(repo=str(main),authenticated_author={'session_id':'human-owner'}))
        author=wc.AuthorWorktreeBinding('agent-caller','unused','unused',str(agent),{},'receipt','sha')
        coordinator=wc.ExplicitCoordinator(store,bindings={'main-token':binding},author_worktrees={'agent-token':author},
            artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
            owner_bindings={'owner-token':wc.OwnerBinding('human-owner-caller',str(main),'human-owner')},
            author_activity_observer=lambda:{'checked_at':1,'complete':True,'agents':[
                {'worktree':str(agent),'label':'Agent 01','activity':'working','summary':'Implementing feature','session_id':'safe-session'}], 'errors':[]})
        return coordinator,main,agent,foreign

    def test_cookie_aggregate_and_bearer_scope_and_invalid_bearer_precedence(self):
        with tempfile.TemporaryDirectory() as tmp:
            c,main,agent,_=self.setup_coordinator(Path(tmp)); client=TestClient(c.app(),base_url='http://127.0.0.1:59180',client=('127.0.0.1',12345))
            client.get('/integration-status')
            # Populate shared observer memory; the subsequent HTTP reads must not refresh it.
            c.refresh_author_activity()
            c.author_activity_observer=lambda:(_ for _ in ()).throw(AssertionError('snapshot called observer'))
            main_record=record('CHECKING'); main_record['worktree']=str(main); main_record['run']['run_id']='main-test'
            accepted_decision={'decision_id':'accept-d','run_id':'agent-test','generation':2,'input_digest':'digest',
                'decision':{'decision':'ACCEPT','run_id':'agent-test','evaluation_generation':2,'input_digest':'digest'}}
            acceptance={'acceptance_id':'accept-a','decision_id':'accept-d','run_id':'agent-test','evaluation_generation':2,'input_digest':'digest'}
            agent_record=record('DECIDED','PASS',decisions=(accepted_decision,),extra={'acceptances':[acceptance]})
            agent_record['worktree']=str(agent); agent_record['run']['run_id']='agent-test'
            def read_snapshot(_path, *, worktree):
                return {'available':True,'records':[main_record] if worktree==str(main) else [agent_record],'history':[]}
            with patch.object(workflow_store.WorkflowStore,'readonly_board_snapshot',side_effect=read_snapshot) as read:
                read.reset_mock()
                aggregate_response=client.get('/v1/board/snapshot')
                self.assertEqual(200,aggregate_response.status_code)
                aggregate=aggregate_response.json()
                self.assertEqual(read.call_count,2)
                scoped=client.get('/v1/board/snapshot',headers={'Authorization':'Bearer owner-token'}).json()
                self.assertEqual(read.call_count,3)
                self.assertEqual([call.kwargs['worktree'] for call in read.call_args_list],[str(main),str(agent),str(main)])
            self.assertEqual(aggregate['worktree'],'configured owner roots')
            self.assertEqual(len(aggregate['columns']['Building']),1)
            self.assertEqual(aggregate['columns']['Building'][0]['details']['session_id'],'safe-session')
            self.assertEqual(len(aggregate['columns']['Checking']),1)
            self.assertEqual(len(aggregate['columns']['Accepted']),1)
            self.assertEqual(scoped['worktree'],str(main))
            self.assertFalse(scoped['columns']['Building'])
            denied=client.get('/v1/board/snapshot',headers={'Authorization':'Bearer wrong'})
            self.assertEqual(403,denied.status_code)

    def test_cookie_target_resolution_and_bearer_cannot_cross_scope(self):
        with tempfile.TemporaryDirectory() as tmp:
            c,main,agent,_=self.setup_coordinator(Path(tmp)); client=TestClient(c.app(),base_url='http://127.0.0.1:59180',client=('127.0.0.1',12345))
            client.get('/integration-status')
            snap=client.get('/v1/owner/snapshot?run_id=agent-run')
            self.assertEqual(200,snap.status_code)
            self.assertEqual('agent-run',snap.json()['run']['run_id'])
            self.assertEqual(403,client.get('/v1/owner/snapshot?run_id=foreign-run').status_code)
            decision=Mock(return_value={'status':'ACCEPTED'}); c.owner_service=Mock(decide=decision)
            payload={'decision':{'run_id':'agent-run','decision_id':'decision-id','decision':'ACCEPT'},
                     'displayed_result_id':'result-id','displayed_result_digest':'digest'}
            response=client.post('/v1/owner/decision',json=payload,headers={'Origin':'http://127.0.0.1:59180'})
            self.assertEqual(200,response.status_code)
            owner=decision.call_args.kwargs['authenticated_owner']
            self.assertEqual(str(agent),owner['worktree'])
            self.assertEqual('human-owner',owner['session_id'])
            self.assertEqual(403,client.post('/v1/owner/decision',json={**payload,'decision':{**payload['decision'],'run_id':'foreign-run'}},
                headers={'Origin':'http://127.0.0.1:59180'}).status_code)
            c.run_control=Mock(); c.run_control.cancel.return_value={'state':'STOPPING'}
            cancel={'run_id':'agent-run','evaluation_generation':2,'expected_sequence':2,
                    'command_id':'cancel-agent','actor':'OWNER','reason':'CANCELLED'}
            response=client.post('/v1/cancel',json=cancel,headers={'Origin':'http://127.0.0.1:59180'})
            self.assertEqual(200,response.status_code)
            self.assertEqual(str(agent),c.run_control.cancel.call_args.args[1].worktree)
            store_task={'task_id':'review-task','run_id':'agent-run','evaluation_generation':2,'kind':'REVIEW'}
            c.store.get_task.return_value=store_task; c.store.get_review_retry.return_value=None
            c.review_retry=Mock(); c.review_retry.retry.return_value={'status':'PENDING'}
            response=client.post('/v1/recovery/retry-review',json={'task_id':'review-task','command_id':'retry-agent','expected_sequence':2},
                headers={'Origin':'http://127.0.0.1:59180'})
            self.assertEqual(202,response.status_code)
            self.assertEqual(('review-task','retry-agent','human-owner-caller'),c.review_retry.retry.call_args.args)
            bearer=TestClient(c.app(),base_url='http://127.0.0.1:59180')
            self.assertEqual(403,bearer.post('/v1/owner/decision',json={**payload,'decision':{**payload['decision'],'run_id':'agent-run'}},
                headers={'Authorization':'Bearer owner-token'}).status_code)
            self.assertEqual(1,decision.call_count)

    def test_real_store_aggregate_is_read_only_and_root_scoped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); main=root/'MoonStation14'; agent=root/'MoonStation14-agent-01'
            main.mkdir(); agent.mkdir(); db=root/'state.sqlite'; store=workflow_store.WorkflowStore(db)
            for prefix,repo,caller in (('aggregate-main',main,'main-author'),('aggregate-agent',agent,'agent-author')):
                request,run,task,event=admission_records(prefix)
                request['author']['worktree']=str(repo)
                request['source_worktree']=str(repo)
                run['author']=dict(request['author'])
                store.submit(caller=caller,request=request,run=run,first_task=task,outbox_event=event,logical_task_key='initial')
            main_binding=wc.CoordinatorBinding('main-author',SimpleNamespace(repo=str(main),authenticated_author={'session_id':'main-session'}))
            author=wc.AuthorWorktreeBinding('agent-author','unused','unused',str(agent),{},'receipt','sha')
            observer=Mock(return_value={'checked_at':1,'complete':True,'agents':[{'worktree':str(agent),'label':'Agent 01',
                'activity':'working','summary':'Working','session_id':'safe'}],'errors':[]})
            c=wc.ExplicitCoordinator(store,bindings={'main':main_binding},author_worktrees={'agent':author},
                artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                owner_bindings={'owner':wc.OwnerBinding('owner',str(main))},author_activity_observer=observer)
            c.refresh_author_activity()
            client=TestClient(c.app(),base_url='http://127.0.0.1:59181',client=('127.0.0.1',12345)); client.get('/integration-status')
            before=logical_db_fingerprint(db)
            response=client.get('/v1/board/snapshot')
            self.assertEqual(200,response.status_code)
            board=response.json()
            self.assertEqual(2,sum(len(cards) for cards in board['columns'].values()))
            self.assertTrue(any(card['run_id']==f'run_aggregate-main' for cards in board['columns'].values() for card in cards))
            self.assertTrue(any(card['run_id']==f'run_aggregate-agent' for cards in board['columns'].values() for card in cards))
            self.assertEqual(1,observer.call_count)
            self.assertEqual(before,logical_db_fingerprint(db))

    def test_aggregate_preserves_unknown_queue_for_unavailable_root(self):
        with tempfile.TemporaryDirectory() as tmp:
            coordinator,*_=self.setup_coordinator(Path(tmp))
            client=TestClient(coordinator.app(),base_url='http://127.0.0.1:59182',client=('127.0.0.1',12345))
            client.get('/integration-status')
            response=client.get('/v1/board/snapshot')
            self.assertEqual(200,response.status_code,response.text)
            self.assertFalse(response.json()['available'])
            self.assertEqual('unknown',response.json()['system']['queue'])

    def test_passed_feature_remains_visible_alongside_live_author_activity(self):
        for activity in ('working', 'idle'):
            with self.subTest(activity=activity), tempfile.TemporaryDirectory() as tmp:
                c,main,agent,_=self.setup_coordinator(Path(tmp))
                c.author_activity_observer=lambda: {'checked_at':1,'complete':True,'agents':[
                    {'worktree':str(agent),'label':'Agent 01','activity':activity,'summary':activity.title(),'session_id':'safe-session'}], 'errors':[]}
                c.refresh_author_activity()
                client=TestClient(c.app(),base_url='http://127.0.0.1:59180',client=('127.0.0.1',12345))
                client.get('/integration-status')
                passed=record('DECIDED','PASS',extra={'terminal_result':{'result_id':'result-pass'},'events':[]})
                passed['worktree']=str(agent); passed['run']['run_id']='passed-feature'
                c.store.get_run.side_effect=lambda rid: None
                c.store.get_terminal_result.return_value=None
                with patch.object(workflow_store.WorkflowStore,'readonly_board_snapshot',return_value={'available':True,'records':[passed],'history':[]}):
                    response=client.get('/v1/board/snapshot')
                self.assertEqual(200,response.status_code)
                board=response.json()
                self.assertTrue(any(card['run_id']=='passed-feature' for card in board['columns']['Your testing']))
                column='Building' if activity=='working' else 'Idle'
                self.assertTrue(any(card['details'].get('session_id')=='safe-session' for card in board['columns'][column]))

    def test_cold_start_configured_roots_and_observer_failure_remain_truthful(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); main=root/'MoonStation14'; main.mkdir()
            roots=[root/f'MoonStation14-agent-{i:02d}' for i in range(1,5)]
            observer=Mock(side_effect=RuntimeError('observer unavailable'))
            binding=wc.CoordinatorBinding('main',SimpleNamespace(repo=str(main),authenticated_author={'session_id':'owner'}))
            configured={f'agent-{i:02d}':wc.AuthorWorktreeBinding(f'agent-{i:02d}','unused','unused',str(path),{},'receipt','sha')
                        for i,path in enumerate(roots,1)}
            coordinator=wc.ExplicitCoordinator(Mock(path=str(root/'state.sqlite'),outbox_summary=Mock(return_value={}),list_tasks=Mock(return_value=[])),
                bindings={'main-token':binding},author_worktrees=configured,artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                owner_bindings={'owner-token':wc.OwnerBinding('owner',str(main),'owner')},author_activity_observer=observer)
            client=TestClient(coordinator.app(),base_url='http://127.0.0.1:59183',client=('127.0.0.1',12345)); client.get('/integration-status')
            before=client.get('/v1/board/snapshot').json()
            before_cards=before['columns']['Building']+before['columns']['Idle']
            self.assertEqual(4,len(before_cards))
            self.assertTrue(all(card['details'].get('activity_unknown') for card in before_cards))
            coordinator.refresh_author_activity()
            after=client.get('/v1/board/snapshot').json()
            after_cards=after['columns']['Building']+after['columns']['Idle']
            self.assertEqual(4,len(after_cards))
            self.assertTrue(all(card['details'].get('activity_unknown') for card in after_cards))


if __name__=='__main__':
    unittest.main()
