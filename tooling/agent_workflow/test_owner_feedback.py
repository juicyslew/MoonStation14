import tempfile
import unittest
import shutil
import subprocess
import sys
from pathlib import Path
from unittest.mock import patch

from tooling.agent_workflow import owner_feedback as f, workflow_runtime as w, workflow_observer


class FeedbackTests(unittest.TestCase):
    def test_messages_merge_admission_and_projection_dedup_cursor(self):
        calls = []
        def request(server, method, path, **kw):
            calls.append(path)
            if path.endswith('/inbox'):
                return 200, {'data': [{'id':'m1','type':'user','time':{'created':2000},'payload':{'text':'reply','metadata':{}}}]}
            if 'cursor=next' in path:
                return 200, {'data':[{'id':'m2','type':'user','text':'older','time':{'created':1000}}], 'cursor':{}}
            return 200, {'data':[{'id':'m1','type':'user','text':'reply','time':{'created':2000}}], 'cursor':{'next':'next'}}
        rows = f._messages(request, 'sid')
        self.assertEqual({x['id'] for x in rows}, {'m1','m2'})
        self.assertIn('type=user', calls[-2]); self.assertIn('cursor=next', calls[-1])
        self.assertIn('order=desc', calls[-2]); self.assertNotIn('order=', calls[-1])
        self.assertTrue(f._eligible(rows[0]))
        self.assertFalse(f._eligible({'text':'robot','metadata':{'workflow_gate_completion':True}}))
        self.assertFalse(f._eligible({'text':'  ','metadata':{}}))
        self.assertTrue(f._eligible({'text':'actual UI request','metadata':None}))

    def test_reconcile_wrong_root_and_feedback_ack(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            w.writejson(root/'candidates'/'c.json', {'id':'c','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},'gate_outcome':{'result':'ready_for_owner'},'owner_review':{'state':'awaiting_owner','origin_session_id':'sid','requested_at':1}})
            def wrong(server, method, path, **kw):
                if path == '/api/session/sid': return 200, {'data':{'location':{'directory':'Z:/wrong'}}}
                raise AssertionError(path)
            result=f.reconcile(root, request=wrong)
            self.assertEqual(len(result['errors']),1)
            self.assertEqual(w.readjson(root/'candidates'/'c.json')['owner_review']['state'],'awaiting_owner')

    def test_legacy_receipt_uses_exact_announcement_timestamp(self):
        c={'id':'c','snapshot_key':'s','gate_outcome':{'result':'ready_for_owner'},
           'gate_request':{'origin_session_id':'author'},'author_completion':{'status':'delivered','result':'ready_for_owner','origin_session_id':'author','id':'notice'}}
        row={'id':'notice','created':12000,'metadata':{'workflow_gate_completion':True,'candidate_id':'c','snapshot_key':'s','result':'ready_for_owner'}}
        self.assertEqual(f.discover_legacy_request(c,[row])['requested_at'],12)
        self.assertIsNone(f.discover_legacy_request(c,[]))

    def test_legacy_announcement_requires_all_exact_bindings(self):
        c={'id':'c','snapshot_key':'s','gate_outcome':{'result':'ready_for_owner'},'gate_request':{'origin_session_id':'author'},
           'author_completion':{'status':'delivered','result':'ready_for_owner','origin_session_id':'author','id':'notice'}}
        row={'id':'notice','created':123000,'metadata':{'workflow_gate_completion':True,'candidate_id':'other','snapshot_key':'s','result':'ready_for_owner'}}
        self.assertIsNone(f.discover_legacy_request(c,[row]))
        row['metadata']['candidate_id']='c'; row['id']='different'
        self.assertIsNone(f.discover_legacy_request(c,[row]))

    def test_api_oversize_is_explicit_error(self):
        def request(*args, **kwargs): raise ValueError('OpenCode response exceeds limit')
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            w.writejson(root/'candidates'/'c.json', {'id':'c','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},'gate_outcome':{'result':'ready_for_owner'},'owner_review':{'state':'awaiting_owner','origin_session_id':'sid','requested_at':1}})
            def lookup(server,method,path,**kw):
                return 200, {'data':{'location':{'directory':td}}}
            # Session identity succeeds, then bounded inbox fails; request is retained.
            def failing(server,method,path,**kw):
                if path.startswith('/api/session/') and path.endswith('/inbox'): return 500, {}
                return lookup(server,method,path,**kw)
            result=f.reconcile(root,request=failing)
            self.assertTrue(result['errors'])
            self.assertEqual(w.readjson(root/'owner-feedback-observer.json')['status'],'error')
            self.assertEqual(w.readjson(root/'candidates'/'c.json')['owner_review']['state'],'awaiting_owner')

    def test_acknowledgement_receives_normalized_admission_message(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            w.writejson(root/'candidates'/'c.json', {'id':'c','snapshot_key':'s','revision_sequence':1,'sprint_id':'x','worktree':{'worktree':td},'gate_outcome':{'result':'ready_for_owner'},'owner_review':{'state':'awaiting_owner','origin_session_id':'sid','requested_at':1}})
            def request(server,method,path,**kw):
                if path == '/api/session/sid': return 200, {'data':{'agent':'agent-consultant','location':{'directory':td}}}
                if path.endswith('/inbox'): return 200, {'data':[{'id':'m','type':'user','time':{'created':3000},'payload':{'text':'feedback','metadata':{}}}]}
                return 200, {'data':[{'id':'m','type':'user','text':'feedback','time':{'created':3000}}]}
            observed=[]
            def ack(root,sid,mid,created,*,workflow,evidence):
                observed.append(evidence); return {'status':'acknowledged'}
            with patch.object(f.owner_attention,'acknowledge_feedback',side_effect=ack):
                result=f.reconcile(root,request=request)
            self.assertEqual(result['acknowledged'],1)
            self.assertEqual(observed[0]['session_id'],'sid'); self.assertEqual(observed[0]['text'],'feedback')

    def test_actual_session_author_profile_not_candidate_cli_identity_controls_owner_feedback(self):
        cases = [('different-profile-same-root','agent-consultant','architecture-lead',True),
                 ('same-profile-wrong-root','architecture-lead-agent02','architecture-lead-agent02',False),
                 ('integration-lead','integration-lead','architecture-lead',False)]
        for label, actual_agent, candidate_agent, should_ack in cases:
            with self.subTest(label=label), tempfile.TemporaryDirectory() as td:
                root=Path(td); (root/'candidates').mkdir()
                candidate={'id':'c','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s',
                    'worktree':{'worktree':td},'agent':candidate_agent,
                    'gate_outcome':{'result':'ready_for_owner'},
                    'owner_review':{'state':'awaiting_owner','origin_session_id':'sid','requested_at':1}}
                w.writejson(root/'candidates'/'c.json',candidate)
                observed=[]
                def request(server,method,path,**kwargs):
                    if path == '/api/session/sid':
                        directory='C:/wrong-root' if label=='same-profile-wrong-root' else td
                        return 200,{'data':{'agent':actual_agent,'location':{'directory':directory}}}
                    if path.endswith('/inbox'):
                        return 200,{'data':[{'id':'owner-msg','type':'user','time':{'created':3000},'payload':{'text':'please change this','metadata':{}}}]}
                    if '/message?' in path:
                        return 200,{'data':[{'id':'owner-msg','type':'user','text':'please change this','time':{'created':3000}}]}
                    raise AssertionError(path)
                def ack(*args,**kwargs):
                    observed.append(kwargs['evidence']); return {'status':'acknowledged'}
                with patch.object(f.owner_attention,'acknowledge_feedback',side_effect=ack):
                    stats=f.reconcile(root,request=request)
                self.assertEqual(bool(observed),should_ack)
                if should_ack:
                    self.assertEqual(stats['acknowledged'],1)
                    self.assertEqual(observed[0]['session_id'],'sid')
                else:
                    self.assertEqual(w.readjson(root/'candidates'/'c.json')['owner_review']['state'],'awaiting_owner')

    def test_real_lifecycle_acknowledges_inbox_and_projection_timestamp_seconds(self):
        class Lock:
            def close(self): pass
        class Runtime:
            readjson=staticmethod(w.readjson); writejson=staticmethod(w.writejson)
            @staticmethod
            def sprint_lock(*args): return Lock()
        for inbox in (True, False):
            with self.subTest(inbox=inbox), tempfile.TemporaryDirectory() as td:
                root=Path(td); (root/'candidates').mkdir()
                c={'id':'c','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},
                   'gate_outcome':{'result':'ready_for_owner'},'owner_review':{'state':'awaiting_owner','candidate_id':'c','snapshot_key':'snap','origin_session_id':'sid','requested_at':1}}
                Runtime.writejson(root/'candidates'/'c.json',c)
                def request(server,method,path,**kw):
                    if path == '/api/session/sid': return 200, {'data':{'agent':'author','location':{'directory':td}}}
                    if path.endswith('/inbox'):
                        rows=[{'id':'reply','type':'user','time':{'created':3000},'payload':{'text':'owner feedback','metadata':{}}}] if inbox else []
                        return 200, {'data':rows}
                    return 200, {'data':[{'id':'reply','type':'user','text':'owner feedback','metadata':{},'time':{'created':3000}}]}
                result=f.reconcile(root,workflow=Runtime,request=request)
                saved=Runtime.readjson(root/'candidates'/'c.json')
                self.assertEqual(result['acknowledged'],1)
                self.assertEqual(saved['owner_review']['state'],'feedback_received')
                self.assertEqual(saved['gate_outcome']['result'],'ready_for_owner')

    def test_reconcile_hydrates_verified_manual_recovery_announcement(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            c={'id':'legacy','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},
               'agent':'author','gate_request':{'origin_session_id':'sid'},'gate_outcome':{'result':'ready_for_owner'}}
            w.writejson(root/'candidates'/'legacy.json',c)
            def request(server,method,path,**kw):
                if path == '/api/session/sid': return 200, {'data':{'agent':'author','location':{'directory':td}}}
                if path.endswith('/inbox'):
                    return 200, {'data':[{'id':'notice','type':'user','time':{'created':9000},'payload':{'text':'published','metadata':{'manual_recovery':True,'candidate_id':'legacy','snapshot_key':'snap','result':'ready_for_owner'}}}]}
                return 200, {'data':[]}
            registered=[]
            with patch.object(f.owner_attention,'register_request',side_effect=lambda *a,**kw:registered.append((a,kw))), patch.object(f.owner_attention,'collect',return_value={}):
                result=f.reconcile(root,request=request)
            self.assertFalse(result['errors'])
            self.assertEqual(registered[0][1]['requested_at'],9)
            self.assertEqual(registered[0][1]['origin_session_id'],'sid')
            proof=registered[0][1]['legacy_head']
            self.assertEqual(proof['announcement_message_id'],'notice')
            self.assertTrue(proof['is_current'])
            self.assertEqual(proof['requested_at'],9)

    def test_exact_completion_receipt_avoids_old_history_when_strict_registration_rejects(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            c={'id':'legacy','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},
               'agent':'author','gate_request':{'origin_session_id':'sid'},'gate_outcome':{'result':'ready_for_owner'},
               'author_completion':{'id':'receipt-id','status':'delivered','result':'ready_for_owner','origin_session_id':'sid'}}
            w.writejson(root/'candidates'/'legacy.json',c); paths=[]
            def request(server,method,path,**kw):
                paths.append(path)
                if path=='/api/session/sid': return 200,{'data':{'agent':'author','location':{'directory':td}}}
                if path.endswith('/message/receipt-id'):
                    return 200,{'data':{'id':'receipt-id','type':'user','text':'ready','time':{'created':1000},
                        'metadata':{'workflow_gate_completion':True,'candidate_id':'legacy','snapshot_key':'snap','result':'ready_for_owner'}}}
                raise AssertionError('legacy receipt must not trigger historical scan: '+path)
            with patch.object(f.owner_attention,'register_request',side_effect=ValueError('stale')), patch.object(f.owner_attention,'collect',return_value={}):
                result=f.reconcile(root,request=request)
            self.assertFalse(result['errors'])
            self.assertIn('/api/session/sid/message/receipt-id',paths)
            self.assertFalse(any('/message?' in path for path in paths))

    def test_exact_receipt_then_recent_owner_reply_acknowledges(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'candidates'; folder.mkdir()
            c={'id':'legacy','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},
               'agent':'author','gate_request':{'origin_session_id':'sid'},'gate_outcome':{'result':'ready_for_owner'},
               'author_completion':{'id':'receipt-id','status':'delivered','result':'ready_for_owner','origin_session_id':'sid'}}
            w.writejson(folder/'legacy.json',c); paths=[]; acknowledged=[]
            def request(server,method,path,**kw):
                paths.append(path)
                if path=='/api/session/sid': return 200,{'data':{'agent':'author','location':{'directory':td}}}
                if path.endswith('/message/receipt-id'):
                    return 200,{'data':{'id':'receipt-id','type':'user','text':'ready','time':{'created':100000},
                        'metadata':{'workflow_gate_completion':True,'candidate_id':'legacy','snapshot_key':'snap','result':'ready_for_owner'}}}
                if path.endswith('/inbox'):
                    return 200,{'data':[{'id':'owner-reply','type':'user','time':{'created':102000},'payload':{'text':'please revise','metadata':{}}}]}
                if '/message?' in path:
                    return 200,{'data':[{'id':'owner-reply','type':'user','text':'please revise','metadata':{},'time':{'created':102000}}]}
                raise AssertionError(path)
            def register(root,candidate,*,requested_at,origin_session_id,workflow,legacy_head):
                latest=dict(candidate); latest['owner_review']={'state':'awaiting_owner','origin_session_id':origin_session_id,
                    'requested_at':requested_at,'candidate_id':candidate['id'],'snapshot_key':candidate['snapshot_key']}
                w.writejson(folder/'legacy.json',latest)
            def ack(root,sid,mid,created,*,workflow,evidence):
                acknowledged.append(evidence); return {'status':'acknowledged'}
            with patch.object(f.owner_attention,'register_request',side_effect=register), \
                 patch.object(f.owner_attention,'acknowledge_feedback',side_effect=ack), \
                 patch.object(f.owner_attention,'collect',return_value={}):
                result=f.reconcile(root,request=request)
            self.assertFalse(result['errors']); self.assertEqual(result['acknowledged'],1)
            self.assertEqual(acknowledged[0]['id'],'owner-reply')
            self.assertIn('/api/session/sid/message/receipt-id',paths)
            self.assertTrue(any('/message?' in path for path in paths))

    def test_unknown_legacy_checks_only_one_recent_page(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            w.writejson(root/'candidates'/'legacy.json',{'id':'legacy','snapshot_key':'snap','revision_sequence':1,'sprint_id':'s',
                'worktree':{'worktree':td},'agent':'author','gate_request':{'origin_session_id':'sid'},
                'gate_outcome':{'result':'ready_for_owner'}})
            pages=[]
            def request(server,method,path,**kw):
                if path=='/api/session/sid': return 200,{'data':{'agent':'author','location':{'directory':td}}}
                if path.endswith('/inbox'): return 200,{'data':[]}
                if '/message?' in path:
                    pages.append(path)
                    old=[{'id':f'old-{i}','type':'user','text':'old unrelated','metadata':{},'time':{'created':i}} for i in range(20)]
                    return 200,{'data':old,'cursor':{'next':'opaque-next'}}
                raise AssertionError(path)
            with patch.object(f.owner_attention,'collect',return_value={}): result=f.reconcile(root,request=request)
            self.assertFalse(result['errors']); self.assertEqual(len(pages),1)
            self.assertNotIn('cursor=opaque-next',pages[0])

    def test_multiple_legacy_announcements_select_latest_fresh_candidate_only(self):
        class Lock:
            def close(self): pass
        verified=[]
        class Runtime:
            readjson=staticmethod(w.readjson); writejson=staticmethod(w.writejson)
            @staticmethod
            def sprint_lock(*args): return Lock()
            @staticmethod
            def verify_gate(candidate):
                verified.append(candidate['id'])
                if candidate.get('fresh') is not True: raise ValueError('stale snapshot')
            @staticmethod
            def verify_publication(candidate,root):
                if candidate.get('fresh') is not True: raise ValueError('publication stale')
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'candidates'; folder.mkdir()
            for ident in ('older','newer'):
                w.writejson(folder/f'{ident}.json',{'id':ident,'snapshot_key':ident,'fresh':True,'sprint_id':'s',
                    'worktree':{'worktree':td},'agent':'author','gate_request':{'origin_session_id':'sid'},
                    'gate_outcome':{'result':'ready_for_owner'}})
            rows=[]
            for ident,stamp in (('older',1000),('newer',2000)):
                rows.append({'id':'notice-'+ident,'type':'user','time':{'created':stamp},'payload':{'text':'published',
                    'metadata':{'manual_recovery':True,'candidate_id':ident,'snapshot_key':ident,'result':'ready_for_owner'}}})
            def request(server,method,path,**kw):
                if path == '/api/session/sid': return 200, {'data':{'agent':'author','location':{'directory':td}}}
                if path.endswith('/inbox'): return 200, {'data':rows}
                return 200, {'data':[]}
            result=f.reconcile(root,workflow=Runtime,request=request)
            self.assertFalse(result['errors'])
            self.assertEqual(w.readjson(folder/'newer.json')['owner_review']['state'],'awaiting_owner')
            self.assertNotIn('owner_review',w.readjson(folder/'older.json'))
            self.assertEqual(set(verified),{'newer'})

    def test_tied_legacy_announcement_timestamps_are_explicitly_unconfirmed(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'candidates'; folder.mkdir()
            rows=[]
            for ident in ('one','two'):
                w.writejson(folder/f'{ident}.json',{'id':ident,'snapshot_key':ident,'sprint_id':'s',
                    'worktree':{'worktree':td},'agent':'author','gate_request':{'origin_session_id':'sid'},
                    'gate_outcome':{'result':'ready_for_owner'}})
                rows.append({'id':'notice-'+ident,'type':'user','time':{'created':1000},'payload':{'text':'published',
                    'metadata':{'manual_recovery':True,'candidate_id':ident,'snapshot_key':ident,'result':'ready_for_owner'}}})
            def request(server,method,path,**kw):
                if path == '/api/session/sid': return 200, {'data':{'agent':'author','location':{'directory':td}}}
                if path.endswith('/inbox'): return 200, {'data':rows}
                return 200, {'data':[]}
            registered=[]
            with patch.object(f.owner_attention,'register_request',side_effect=lambda *a,**k:registered.append(a)):
                result=f.reconcile(root,request=request)
            self.assertEqual(registered,[])
            self.assertTrue(any('ambiguous' in error['error'] for error in result['errors']))

    def test_only_lifecycle_selected_head_is_considered(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'candidates'; folder.mkdir()
            w.writejson(folder/'old.json',{'id':'old','snapshot_key':'old','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},'gate_outcome':{'result':'ready_for_owner'},'gate_request':{'origin_session_id':'old-session'}})
            w.writejson(folder/'new.json',{'id':'new','snapshot_key':'new','revision_sequence':2,'sprint_id':'s','worktree':{'worktree':td},'gate_outcome':{'result':'pending'}})
            def forbidden(*a,**k): raise AssertionError('non-current request must not cause OpenCode API access')
            result=f.reconcile(root,request=forbidden)
            self.assertFalse(result['errors']); self.assertEqual(result['sessions'],0)

    def test_feedback_received_head_does_not_poll_and_observer_idles(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'candidates'; folder.mkdir()
            w.writejson(folder/'done.json',{'id':'done','snapshot_key':'s','revision_sequence':1,'sprint_id':'s','worktree':{'worktree':td},'owner_review':{'state':'feedback_received','candidate_id':'done','snapshot_key':'s','origin_session_id':'sid'},'gate_outcome':{'result':'ready_for_owner'}})
            def forbidden(*a,**k): raise AssertionError('answered request must not be polled')
            stats=f.reconcile(root,request=forbidden)
            self.assertEqual(stats['sessions'],0)
            with patch.object(workflow_observer,'watch',return_value=0) as shared:
                self.assertEqual(f.watch(root,request=forbidden),0)
                shared.assert_called_once()

    def test_unknown_legacy_without_origin_is_not_polled(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); folder=root/'candidates'; folder.mkdir()
            w.writejson(folder/'legacy.json',{'id':'legacy','snapshot_key':'s','sprint_id':'s','worktree':{'worktree':td},'gate_outcome':{'result':'ready_for_owner'}})
            def forbidden(*a,**k): raise AssertionError('unknown-origin archive must not call API')
            stats=f.reconcile(root,request=forbidden)
            self.assertEqual(stats['sessions'],0); self.assertFalse(stats['errors'])

    def test_refresh_failure_and_network_failure_remain_visible_and_prevent_idle(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            with patch.object(f.owner_attention,'collect',side_effect=ValueError('stale proof')):
                stats=f.reconcile(root,request=lambda *a,**k:(200,{'data':[]}),refresh=True)
            health=w.readjson(root/'owner-feedback-observer.json')
            self.assertEqual(health['status'],'error'); self.assertTrue(all(isinstance(x,str) for x in health['errors']))
            with patch.object(workflow_observer,'watch',return_value=0) as shared:
                f.watch(root)
                shared.assert_called_once()

    def test_stale_readiness_refresh_moves_item_to_agent_and_renders_board(self):
        class Lock:
            def close(self): pass
        class Runtime:
            readjson=staticmethod(w.readjson); writejson=staticmethod(w.writejson)
            @staticmethod
            def sprint_lock(*args): return Lock()
            @staticmethod
            def verify_gate(candidate): raise ValueError('stale gate evidence')
            @staticmethod
            def verify_publication(candidate,root): raise ValueError('stale publication')
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); (root/'candidates').mkdir()
            c={'id':'c','snapshot_key':'s','revision_sequence':1,'sprint_id':'x','worktree':{'worktree':td},
               'gate_outcome':{'result':'ready_for_owner'},'owner_review':{'state':'awaiting_owner','candidate_id':'c','snapshot_key':'s','origin_session_id':'sid','requested_at':1}}
            Runtime.writejson(root/'candidates'/'c.json',c)
            stats=f.reconcile(root,workflow=Runtime,request=lambda *a,**k:(200,{'data':[]}),refresh=True)
            self.assertEqual(stats['queue']['agent_work'],1)
            self.assertEqual(Runtime.readjson(root/'candidates'/'c.json')['owner_review']['readiness']['eligible'],False)
            self.assertTrue((root/'integration-status.html').is_file())

    def test_installed_layout_once_uses_workflow_py_without_workflow_runtime(self):
        source=Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as td:
            base=Path(td)
            for name in ('owner_feedback.py','workflow_observer.py','owner_attention.py','gate_dispatch.py','gate_progress.py','validation_evidence.py','gate_liveness.py'):
                shutil.copyfile(source/name,base/name)
            shutil.copyfile(source/'workflow_runtime.py',base/'workflow.py')
            exchange=base/'exchange'
            proc=subprocess.run([sys.executable,str(base/'owner_feedback.py'),'--exchange',str(exchange),'--once'],
                cwd=base,capture_output=True,text=True,timeout=10)
            self.assertEqual(proc.returncode,0,proc.stderr)
            self.assertTrue((exchange/'owner-feedback-observer.json').is_file())

    def test_hidden_launch_and_bounded_idle(self):
        with patch.object(workflow_observer.subprocess,'Popen',return_value='proc') as pop:
            self.assertEqual(f.ensure_started('exchange'),'proc')
            self.assertEqual(pop.call_args.kwargs['stdin'], workflow_observer.subprocess.DEVNULL)

    def test_duplicate_singleton_watch_exits_without_polling(self):
        with patch.object(workflow_observer,'_workflow',side_effect=AssertionError('duplicate lock must short-circuit')):
            with patch.object(workflow_observer, 'watch', side_effect=BlockingIOError):
                with self.assertRaises(BlockingIOError): f.watch('x')

    def test_ensure_started_cli_returns_immediately(self):
        class Proc: pid=42
        with patch.object(f,'ensure_started',return_value=Proc()):
            self.assertEqual(f.main(['--exchange','x','--ensure-started']),0)

    def test_page_guard_fails_closed_if_boundary_not_reached(self):
        calls=[]
        def request(server,method,path,**kw):
            calls.append(path)
            if path.endswith('/inbox'): return 200, {'data':[]}
            return 200, {'data':[{'id':str(len(calls)),'type':'user','text':'old','time':{'created':1}}], 'cursor':{'next':'again'}}
        with patch.object(f,'MAX_PAGES',2), self.assertRaisesRegex(ValueError,'bounded pagination'):
            f._messages(request,'sid',earliest=0.0001)


if __name__ == '__main__': unittest.main()
