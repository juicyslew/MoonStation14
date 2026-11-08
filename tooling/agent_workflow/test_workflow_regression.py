import tempfile
import unittest
import json
import uuid
from pathlib import Path
from unittest.mock import patch

import workflow_native_execution
import workflow_owner
import workflow_store
import workflow_regression
import workflow_coordinator
from test_workflow_native_execution import admit_native_fixture


class NativeRegressionTests(unittest.TestCase):
    def test_admission_is_idempotent_and_native_aggregate_is_finality_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho All 3 required tests passed\nexit /b 0\n'
            case,store,feature,accepted,admitted,frozen,backend=self._prepared(root,bat=bat,key='admit_aggregate')
            try:
                wake=store.get_event('event_regression_ready_'+accepted['acceptance_id'][8:])['payload']
                admission=workflow_regression.RegressionAdmission(store)
                self.assertEqual('APPLIED',admission.observe(wake['event_id'],wake))
                self.assertEqual('APPLIED',admission.dispatch(wake['event_id'],wake))
                self.assertIsNone(workflow_regression.aggregate_regression(store,admitted['run_id'])['decision'])
                backend.dispatch('aggregate-check',{'task_id':admitted['task_id']})
                proc=backend.workers[admitted['task_id']]; proc.wait(timeout=30)
                aggregate=workflow_regression.aggregate_regression(store,admitted['run_id'])
                self.assertEqual('PASS',aggregate['decision'],aggregate)
                class Identity:
                    server='http://127.0.0.1:9'
                    def __call__(self, context): return True
                coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'unused':object()},artifact_dir=root/'artifacts',
                    handlers={'TASK_READY':backend},identity_verifier=Identity())
                workflow_coordinator.configure_review_pipeline(coordinator,{}, {}, {},transport_request=lambda *a,**k: (_ for _ in ()).throw(AssertionError('unexpected review API call')))
                task=store.get_task(admitted['task_id'])
                completed_id=store._task_result_event_id(task['task_id'],store.get_task_result_digest(task['task_id']))
                completed=store.get_event(completed_id)['payload']
                coordinator.handlers['TASK_COMPLETED'].dispatch(completed_id,completed)
                terminal=store.get_run(admitted['run_id'])
                self.assertEqual(('DECIDED','PASS'),(terminal['attempt']['phase'],terminal['attempt']['result']))
                self.assertEqual(('DECIDED','PASS'),(store.get_regression_job(accepted['regression_id'])['status'],store.get_regression_job(accepted['regression_id'])['result']))
                self.assertEqual(frozen,store.get_run(feature['run_id']))
            finally:
                backend.close()

    def _prepared(self, root, *, bat, key):
        case,store,feature,feature_task=admit_native_fixture(root,caller=key,key=key,delay=.01,extra_files={'gradlew.bat':bat})
        backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
        proc=None
        try:
            backend.dispatch('feature-dispatch-'+key,{'task_id':feature_task})
            proc=backend.workers[feature_task]; proc.wait(timeout=20)
            seq=store.get_run(feature['run_id'])['run']['sequence']
            store.transition_attempt(feature['run_id'],1,expected_sequence=seq,phase='STOPPING',proposed_result='PASS')
            terminal=store.transition_attempt(feature['run_id'],1,expected_sequence=seq+1,phase='DECIDED',terminal_result='PASS',cleanup_state='NOT_REQUIRED')
            event=store.get_event(terminal['event_id'])['payload']
            owner={'session_id':None,'worktree':str(case.repo.resolve()),'role':'owner'}
            decision={'contract_version':1,'command_id':'accept-'+key,'acceptance_id':'accept_'+key,
                'run_id':feature['run_id'],'evaluation_generation':1,'input_digest':case.request['input_digest'],
                'owner':owner,'kind':'LOCAL_OWNER','decision':'ACCEPT','findings':[],'expected_sequence':seq+2}
            accepted=workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            (case.repo/'tracked.txt').write_text('dirty live worktree')
            frozen_feature=store.get_run(feature['run_id'])
            admitted=store.admit_regression_job(accepted['regression_id'])
            return case,store,feature,accepted,admitted,frozen_feature,backend
        except BaseException:
            backend.close()
            if proc is not None and proc.poll() is None: proc.wait(timeout=3)
            raise

    def _terminal_regression(self, store, feature, admitted, result):
        sequence=store.get_run(admitted['run_id'])['run']['sequence']
        store.transition_attempt(admitted['run_id'],1,expected_sequence=sequence,phase='STOPPING',proposed_result=result)
        return store.transition_attempt(admitted['run_id'],1,expected_sequence=sequence+1,phase='DECIDED',terminal_result=result,cleanup_state='CONFIRMED')

    def test_accepted_snapshot_runs_full_typed_gametest_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho All 3 required tests passed\nexit /b 0\n'
            case,store,feature,accepted,admitted,frozen_feature,backend=self._prepared(root,bat=bat,key='regression')
            proc=None
            try:
                self.assertEqual('QUEUED',store.get_task(admitted['task_id'])['state'])
                store.transition_attempt(admitted['run_id'],1,expected_sequence=0,phase='CHECKING')
                backend.dispatch('regression-dispatch',{'task_id':admitted['task_id']})
                proc=backend.workers[admitted['task_id']]; proc.wait(timeout=30)
                task=store.get_task(admitted['task_id'])
                if task.get('result',{}).get('decision')!='PASS':
                    attempts=Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'
                    out=attempts/('regression-failure-'+uuid.uuid4().hex)
                    out.mkdir(parents=True,exist_ok=False)
                    (out/'task.json').write_text(__import__('json').dumps(task,indent=2))
                    diagnostics=[]
                    for ref in task.get('result',{}).get('artifact_refs',[]):
                        binding=store.get_artifact(ref)
                        if binding:
                            art=binding['artifact']; path=Path(art['path'])
                            if path.exists():
                                dest=out/(art['type'].lower()+'-'+path.name); dest.write_bytes(path.read_bytes())
                                diagnostics.append({'artifact':art,'saved_as':str(dest)})
                    (out/'diagnostics.json').write_text(__import__('json').dumps(diagnostics,indent=2))
                    print('regression-failure-evidence-retained:'+str(out),flush=True)
                self.assertEqual('COMPLETED',task['state'])
                self.assertEqual('PASS',task['result']['decision'],task['result'])
                self.assertEqual(frozen_feature,store.get_run(feature['run_id']))
                receipts=[store.get_artifact(ref)['artifact'] for ref in task['result']['artifact_refs']]
                receipt=next(a for a in receipts if a['type']=='CHECK_RECEIPT')
                import json
                checks=json.loads(Path(receipt['path']).read_text())['checks']
                self.assertEqual(1,len(checks))
                evidence=checks[0]['evidence']
                self.assertEqual('PASS',evidence['status'])
                self.assertEqual({'selected':3,'total':3,'passed':3}, {k:evidence['counts'][k] for k in ('selected','total','passed')})
                self.assertEqual('1',(case.repo/'counter.txt').read_text())
                terminal=self._terminal_regression(store,feature,admitted,'PASS')
                event=store.get_event(terminal['event_id'])['payload']
                self.assertEqual('REGRESSION_DECIDED',event['kind'])
                self.assertEqual({'contract_version':1,'regression_id':accepted['regression_id'],'acceptance_id':'accept_regression',
                    'run_id':admitted['run_id'],'evaluation_generation':1,'input_digest':case.request['input_digest'],
                    'result':'PASS'}, {k:event[k] for k in ('contract_version','regression_id','acceptance_id','run_id','evaluation_generation','input_digest','result')})
                self.assertEqual(('DECIDED','PASS'),(store.get_regression_job(accepted['regression_id'])['status'],store.get_regression_job(accepted['regression_id'])['result']))
                self.assertEqual(frozen_feature,store.get_run(feature['run_id']))
                before=self._regression_event_counts(store,admitted['run_id'])
                replay=store.transition_attempt(admitted['run_id'],1,expected_sequence=2,phase='DECIDED',terminal_result='PASS',cleanup_state='CONFIRMED')
                self.assertEqual(terminal,replay)
                self.assertEqual(before,self._regression_event_counts(store,admitted['run_id']))
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    def test_regression_dispatch_waits_for_global_submission_lane(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho All 3 required tests passed\nexit /b 0\n'
            case,store,feature,accepted,admitted,frozen_feature,backend=self._prepared(root,bat=bat,key='regression_lane')
            proc=None
            try:
                other_case,_,other_run,other_task=admit_native_fixture(root/'other-worktree',caller='other-lane-author',
                    key='other-lane-feature',shared_store=store,delay=.01)
                other_run_state=store.get_run(other_run['run_id'])
                store.transition_attempt(other_run['run_id'],1,expected_sequence=other_run_state['run']['sequence'],phase='CHECKING')
                self.assertNotEqual(case.author['worktree'],other_case.author['worktree'])
                self.assertEqual('QUEUED',store.get_run(admitted['run_id'])['attempt']['phase'])
                self.assertEqual('QUEUED',store.get_task(admitted['task_id'])['state'])
                resources_before=store.get_resource_history('native_validation')
                regression_worker_log=root/'artifacts'/admitted['task_id']/'worker.log'
                with patch.object(workflow_native_execution.subprocess,'Popen') as popen:
                    self.assertEqual('PENDING',backend.dispatch('regression-lane-busy',{'task_id':admitted['task_id']}))
                popen.assert_not_called()
                self.assertEqual('QUEUED',store.get_run(admitted['run_id'])['attempt']['phase'])
                self.assertEqual('QUEUED',store.get_task(admitted['task_id'])['state'])
                self.assertFalse((root/'artifacts'/admitted['task_id']).exists())
                self.assertFalse(regression_worker_log.exists())
                self.assertIsNone(store.get_native_claim(admitted['task_id']))
                self.assertIsNone(store.get_native_scope(admitted['task_id']))
                self.assertEqual(resources_before,store.get_resource_history('native_validation'))
                self.assertEqual(frozen_feature,store.get_run(feature['run_id']))

                seq=store.get_run(other_run['run_id'])['run']['sequence']
                store.transition_attempt(other_run['run_id'],1,expected_sequence=seq,phase='STOPPING',closure='CANCELLED')
                store.transition_attempt(other_run['run_id'],1,expected_sequence=seq+1,phase='CLOSED',cleanup_state='NOT_REQUIRED')
                db=store._connect()
                try: cancellation=json.loads(db.execute('SELECT proof_json FROM task_cancellations WHERE task_id=?',(other_task,)).fetchone()[0])
                finally: db.close()
                self.assertEqual('DB_NEVER_STARTED',cancellation['kind'])

                backend.dispatch('regression-lane-released',{'task_id':admitted['task_id']})
                proc=backend.workers[admitted['task_id']]; proc.wait(timeout=30)
                regression_task=store.get_task(admitted['task_id'])
                self.assertEqual(('COMPLETED','PASS'),(regression_task['state'],regression_task['result']['decision']))
                self.assertEqual(frozen_feature,store.get_run(feature['run_id']))
                self.assertEqual('PASS',store.get_run(feature['run_id'])['attempt']['result'])
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    @staticmethod
    def _regression_event_counts(store, run_id):
        db=store._connect()
        try:
            return (db.execute("SELECT count(*) FROM events WHERE run_id=? AND kind='REGRESSION_DECIDED'",(run_id,)).fetchone()[0],
                    db.execute("SELECT count(*) FROM deliveries d JOIN events e ON e.id=d.event_id WHERE e.run_id=?",(run_id,)).fetchone()[0],
                    db.execute("SELECT count(*) FROM events WHERE run_id=? AND kind='RESULT_AVAILABLE'",(run_id,)).fetchone()[0])
        finally: db.close()

    def test_failed_regression_does_not_rewrite_accepted_feature(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho 1 required tests failed\nexit /b 1\n'
            case,store,feature,accepted,admitted,frozen_feature,backend=self._prepared(root,bat=bat,key='regression_fail')
            proc=None
            try:
                store.transition_attempt(admitted['run_id'],1,expected_sequence=0,phase='CHECKING')
                backend.dispatch('regression-fail-dispatch',{'task_id':admitted['task_id']})
                proc=backend.workers[admitted['task_id']]; proc.wait(timeout=30)
                task=store.get_task(admitted['task_id'])
                self.assertEqual('FAIL',task['result']['decision'],task['result'])
                terminal=self._terminal_regression(store,feature,admitted,'FAIL')
                self.assertEqual('REGRESSION_DECIDED',store.get_event(terminal['event_id'])['payload']['kind'])
                job=store.get_regression_job(accepted['regression_id'])
                self.assertEqual(('DECIDED','FAIL'),(job['status'],job['result']))
                self.assertEqual(frozen_feature,store.get_run(feature['run_id']))
                self.assertEqual((1,1,0),self._regression_event_counts(store,admitted['run_id']))
                db=store._connect()
                try:
                    row=db.execute("SELECT e.kind,e.run_id FROM deliveries d JOIN events e ON e.id=d.event_id WHERE e.run_id=? AND d.target_kind='AUTHOR'",(admitted['run_id'],)).fetchone()
                    self.assertEqual(('REGRESSION_DECIDED',admitted['run_id']),tuple(row))
                finally: db.close()
                self.assertEqual(0,len(store.list_tasks(admitted['run_id'],1))-1)
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    def test_extra_file_in_accepted_cache_blocks_child_execution(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho All 3 required tests passed\nexit /b 0\n'
            case,store,feature,accepted,admitted,frozen,backend=self._prepared(root,bat=bat,key='cache_extra')
            proc=None
            try:
                cache=Path(store.get_regression_for_run(admitted['run_id'])['inputs']['snapshot_root'])
                extra=cache/'Extra.java'; extra.write_text('class Extra {}')
                store.transition_attempt(admitted['run_id'],1,expected_sequence=0,phase='CHECKING')
                backend.dispatch('extra-cache-dispatch',{'task_id':admitted['task_id']})
                proc=backend.workers[admitted['task_id']]; proc.wait(timeout=30)
                task=store.get_task(admitted['task_id'])
                self.assertEqual(('COMPLETED','ERROR'),(task['state'],task['result']['decision']))
                self.assertEqual('0',(cache/'counter.txt').read_text() if (cache/'counter.txt').exists() else '0')
                self.assertTrue(extra.exists())
                self.assertEqual(frozen,store.get_run(feature['run_id']))
                self.assertEqual('PASS',store.get_run(feature['run_id'])['attempt']['result'])
                self.assertEqual('RELEASED',store.get_resource_history('native_validation')[-1]['state'])
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    def test_filtered_game_test_evidence_cannot_pass_regression(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); bat='@echo off\necho GameTest tag preflight: 1/3 fixtures selected\necho All 1 required tests passed\nexit /b 0\n'
            case,store,feature,accepted,admitted,frozen,backend=self._prepared(root,bat=bat,key='filtered_regression')
            proc=None
            try:
                store.transition_attempt(admitted['run_id'],1,expected_sequence=0,phase='CHECKING')
                backend.dispatch('filtered-regression-dispatch',{'task_id':admitted['task_id']})
                proc=backend.workers[admitted['task_id']]; proc.wait(timeout=30)
                task=store.get_task(admitted['task_id'])
                self.assertEqual(('COMPLETED','ERROR'),(task['state'],task['result']['decision']))
                incident=store.get_task_incident(admitted['task_id'])
                self.assertEqual('FULL_REGRESSION_EVIDENCE_REQUIRED',incident['code'])
                aggregate=workflow_regression.aggregate_regression(store,admitted['run_id'])
                self.assertEqual(('ERROR','FULL_REGRESSION_EVIDENCE_REQUIRED'),(aggregate['decision'],aggregate['error']['code']))
                receipts=[store.get_artifact(ref)['artifact'] for ref in task['result']['artifact_refs']]
                fault=None
                for artifact in receipts:
                    if artifact['type']!='PROVENANCE': continue
                    try: candidate=json.loads(Path(artifact['path']).read_text())
                    except (ValueError,UnicodeDecodeError): continue
                    if candidate.get('code')=='FULL_REGRESSION_EVIDENCE_REQUIRED': fault=candidate; break
                self.assertIsNotNone(fault)
                evidence=fault['checks'][0]['evidence']
                self.assertEqual((1,3),(evidence['counts']['selected'],evidence['counts']['total']))
                self.assertEqual(frozen,store.get_run(feature['run_id']))
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)
