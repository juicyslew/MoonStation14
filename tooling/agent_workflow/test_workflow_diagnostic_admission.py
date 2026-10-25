import tempfile
import unittest
import copy
from pathlib import Path

import workflow_store
import workflow_submission
from diagnostic_test_support import make_diagnostic_case


class DiagnosticAdmissionTests(unittest.TestCase):
    def test_bad_gametest_fixture_is_rejected_and_replay_is_stable(self):
        cases=('unmanaged','missing','duplicate','wrong-prefix','wrong-log')
        for suffix in cases:
            with self.subTest(case=suffix), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); key='bad-'+suffix; case=make_diagnostic_case(root,key=key)
                req=copy.deepcopy(case.request); check=req['diagnostic_plan']['checks'][0]
                expected=f"build/workflow-fixtures/{req['input_digest'][:12]}"
                arg='-Pms14GameTestDir='+expected
                if suffix=='unmanaged': arg='-Pms14GameTestDir=build/gametest-tags-fixture'
                if suffix=='missing': check['argv']=['./gradlew','runGameTestServer']
                else: check['argv']=['./gradlew','runGameTestServer',arg]
                if suffix=='duplicate': check['argv'].append(arg)
                if suffix=='wrong-prefix': check['argv'][-1]='-Pms14GameTestDir=build/workflow-fixtures/000000000000'
                check['kind']='gametest'
                check['log']=(expected+'/logs/latest.log' if suffix!='wrong-log' else 'build/gametest-tags-fixture/logs/latest.log')
                store=workflow_store.WorkflowStore(root/'db.sqlite')
                response=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=key,
                    request=req,context=case.context,artifact_dir=root/'artifacts')
                self.assertEqual((response['status'],response['result'],response['code']),('REJECTED','FAIL','DIAGNOSTIC_CHECK_PLAN'))
                finding=response['findings'][0]
                self.assertEqual(finding['location'],'diagnostic_plan.checks.0')
                self.assertIn(expected, finding['required_change'])
                self.assertIn('-Pms14GameTestDir='+expected, finding['required_change'])
                replay=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=key,
                    request=req,context=case.context,artifact_dir=root/'artifacts')
                self.assertEqual(replay,response)
                db=store._connect()
                try:
                    for table in ('runs','tasks','native_scopes','native_claims','native_check_records','outbox'):
                        self.assertEqual(db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0],0,table)
                finally: db.close()

    def test_public_submission_replay_keeps_feature_head(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case=make_diagnostic_case(root)
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            feature={'contract_version':1,'run_id':'run_feature','sprint_id':case.request['sprint_id'],
                'run_kind':'FEATURE_GATE','input_digest':case.request['input_digest'],'plan_digest':case.request['plan_digest'],
                'policy_digest':case.request['policy_digest'],'author':case.request['author'],'current_attempt':1,'sequence':0}
            # Seed the existing feature head through the public store API, using
            # the same validated diagnostic source/policy binding for isolation.
            req=dict(case.request,purpose='OWNER_READY',implementation_complete=True,
                criteria=[dict(case.request['criteria'][0],status='COMPLETE')],manual_steps=['manual'],
                check_packs=[{'pack_id':'package','kind':'package','selectors':[]},{'pack_id':'provenance','kind':'provenance','selectors':[]}])
            req.pop('diagnostic_plan'); req.pop('diagnostic_rationale'); req['idempotency_key']='seed-feature'
            task={'contract_version':1,'task_id':'task_feature','run_id':'run_feature','evaluation_generation':1,'kind':'CHECK','dependency_ids':[],
                'input_digest':req['input_digest'],'state':'QUEUED','assignment_generation':1,'assigned_session_id':None,'deadline_ms':9999999999999,'result':None}
            ev={'contract_version':1,'event_id':'event_feature','kind':'TASK_READY','run_id':'run_feature','evaluation_generation':1,'task_id':'task_feature','assignment_generation':1,'input_digest':req['input_digest']}
            store.submit(caller=case.caller,request=req,run=feature,first_task=task,outbox_event=ev,logical_task_key='feature:check')
            db=store._connect()
            try: before=db.execute('SELECT run_id FROM feature_heads WHERE worktree=? AND sprint=?',(req['source_worktree'],req['sprint_id'])).fetchone()[0]
            finally: db.close()

            self.assertEqual(before,'run_feature')
            response=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,
                request=case.request,context=case.context,artifact_dir=root/'artifacts')
            self.assertEqual(response['status'],'ACCEPTED')
            self.assertEqual(response['next_action'],'WAIT_FOR_RESULT')
            saved=store.get_run(response['run_id'])['run']
            self.assertEqual(saved['run_kind'],'DIAGNOSTIC')
            replay=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,
                request=case.request,context=case.context,artifact_dir=root/'artifacts')
            self.assertEqual(replay,response)
            db=store._connect()
            try:
                after=db.execute('SELECT run_id FROM feature_heads WHERE worktree=? AND sprint=?',(req['source_worktree'],req['sprint_id'])).fetchone()[0]
                count=db.execute("SELECT COUNT(*) FROM runs WHERE json_extract(run_json,'$.run_kind')='DIAGNOSTIC'").fetchone()[0]
            finally: db.close()
            self.assertEqual(after,before)
            self.assertEqual(count,1)

    def test_diagnostic_cannot_replace_feature(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case=make_diagnostic_case(root,key='replacement')
            case.request['replaces_run_id']='run_feature'
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            response=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,
                request=case.request,context=case.context,artifact_dir=root/'artifacts')
            self.assertNotEqual(response['status'],'ACCEPTED')
            self.assertIsNone(store.get_run(response.get('run_id','missing')))

    def test_corrected_fresh_diagnostic_path_is_admitted_without_launch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case=make_diagnostic_case(root,key='corrected-fresh')
            check=case.request['diagnostic_plan']['checks'][0]
            fixture=f"build/workflow-fixtures/{case.request['input_digest'][:12]}"
            check.update(kind='gametest',argv=['./gradlew','runGameTestServer','-Pms14GameTestDir='+fixture],
                         log=fixture+'/logs/latest.log')
            from workflow_check_packs import resolve_diagnostic_plan
            case.request['plan_digest']=workflow_store.digest(resolve_diagnostic_plan(case.request,repo=case.repo,policy=case.policy))
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            response=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,
                request=case.request,context=case.context,artifact_dir=root/'artifacts')
            self.assertEqual(response['status'],'ACCEPTED')
            self.assertEqual(store.get_run(response['run_id'])['run']['run_kind'],'DIAGNOSTIC')


if __name__=='__main__': unittest.main()
