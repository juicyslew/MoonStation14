import hashlib
import tempfile
import time
import unittest
from unittest.mock import patch
from pathlib import Path

import workflow_native_execution
import workflow_owner
import workflow_store
from test_workflow_native_execution import admit_native_fixture


class OwnerSnapshotHappyPath(unittest.TestCase):
    def _pass_case(self, root):
        case,store,receipt,task_id=admit_native_fixture(root,caller='owner-case',key='owner-case',delay=.05)
        backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
        try:
            backend.dispatch('owner-test-event',{'task_id':task_id})
            backend.workers[task_id].wait(timeout=20)
            self.assertEqual('COMPLETED',store.get_task(task_id)['state'])
            sequence=store.get_run(receipt['run_id'])['run']['sequence']
            store.transition_attempt(receipt['run_id'],1,expected_sequence=sequence,phase='STOPPING',proposed_result='PASS')
            terminal=store.transition_attempt(receipt['run_id'],1,expected_sequence=sequence+1,phase='DECIDED',terminal_result='PASS',cleanup_state='NOT_REQUIRED')
            event=store.get_event(terminal['event_id'])['payload']
            owner={'session_id':None,'worktree':str(case.repo.resolve()),'role':'owner'}
            return case,store,receipt,task_id,sequence,event,owner
        finally:
            backend.close()

    def _owner_decision(self, case, receipt, sequence, owner, action, command_id):
        decision={'contract_version':1,'command_id':command_id,'run_id':receipt['run_id'],
            'evaluation_generation':1,'input_digest':case.request['input_digest'],'owner':owner,
            'kind':'LOCAL_OWNER','decision':action,'findings':[], 'expected_sequence':sequence+2}
        if action=='REQUEST_CHANGES':
            decision['findings']=[{'criterion_id':'C1','category':'IMPLEMENTATION','message':'change required',
                'location':'tracked.txt','evidence_refs':['artifact_evidence'],'required_change':'fix tracked behavior'}]
        elif action=='ACCEPT':
            decision['acceptance_id']=command_id.replace('owner-', 'accept_')
        return decision

    def test_accepts_frozen_source_and_queues_full_regression(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,receipt,task_id=admit_native_fixture(root,caller='owner-case',key='owner-case',delay=.05)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            backend.dispatch('owner-test-event',{'task_id':task_id})
            proc=backend.workers[task_id]; proc.wait(timeout=20)
            backend.close()
            task=store.get_task(task_id)
            self.assertEqual('COMPLETED',task['state'])
            sequence=store.get_run(receipt['run_id'])['run']['sequence']
            store.transition_attempt(receipt['run_id'],1,expected_sequence=sequence,phase='STOPPING',proposed_result='PASS')
            terminal=store.transition_attempt(receipt['run_id'],1,expected_sequence=sequence+1,phase='DECIDED',terminal_result='PASS',cleanup_state='NOT_REQUIRED')
            event=store.get_event(terminal['event_id'])['payload']
            source_file=case.repo/'tracked.txt'
            frozen=source_file.read_bytes()
            source_file.write_bytes(b'changed after acceptance')
            owner={'session_id':None,'worktree':str(case.repo.resolve()),'role':'owner'}
            decision={'contract_version':1,'command_id':'owner-accept-one','acceptance_id':'accept_owner_case',
                'run_id':receipt['run_id'],'evaluation_generation':1,'input_digest':case.request['input_digest'],
                'owner':owner,'kind':'LOCAL_OWNER','decision':'ACCEPT','findings':[],
                'expected_sequence':sequence+2}
            result=workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            # A committed receipt is authoritative on exact retry, even after mutable evidence disappears.
            (root/'artifacts'/'source-snapshot.zip').unlink(missing_ok=True)
            replay=workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            self.assertEqual(result,replay)
            changed=dict(decision,expected_sequence=sequence+3)
            with self.assertRaises(workflow_store.IdempotencyConflict):
                workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(changed,
                    authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            source=Path(root/'snapshots'/'accept_owner_case'/'source'/'tracked.txt')
            self.assertEqual(frozen,source.read_bytes())
            self.assertNotEqual(source_file.read_bytes(),source.read_bytes())
            inputs=store.get_regression_inputs(result['regression_id'])
            self.assertEqual(['./gradlew','runGameTestServer','-Pms14GameTestDir=build/workflow-regression/accept_owner_case','--no-daemon'],inputs['plan']['checks'][0]['argv'])
            self.assertNotIn('tags',str(inputs['plan']).lower())
            self.assertEqual('QUEUED',store.get_regression_job(result['regression_id'])['status'])
            self.assertEqual('COMPLETED',store.get_task(task_id)['state'])
            feature_before=store.get_run(receipt['run_id'])
            admitted=store.admit_regression_job(result['regression_id'])
            self.assertFalse(admitted['replayed'])
            regression=store.get_run(admitted['run_id'])
            self.assertEqual('POST_ACCEPTANCE_REGRESSION',regression['run']['run_kind'])
            self.assertEqual('accept_owner_case',regression['run']['owner_acceptance_id'])
            self.assertEqual(case.request['input_digest'],regression['run']['input_digest'])
            self.assertEqual('QUEUED',store.get_task(admitted['task_id'])['state'])
            self.assertEqual(feature_before,store.get_run(receipt['run_id']))
            self.assertEqual(admitted,dict(store.admit_regression_job(result['regression_id']),replayed=False))
            self.assertIsNotNone(store.get_regression_for_run(admitted['run_id']))

    def test_incomplete_legacy_regression_inputs_are_not_admitted(self):
        import workflow_store
        from test_workflow_store_owner import OwnerAcceptanceTests
        case=OwnerAcceptanceTests()
        with tempfile.TemporaryDirectory() as tmp:
            case.setUp()
            try:
                source,build,event_id=case.acceptance_setup()
                case.perform_accept(source,build,event_id)
                with self.assertRaises(workflow_store.ProtocolError):
                    case.store.admit_regression_job('task_regression_finality')
                self.assertIsNone(case.store.get_run('run_regression_finality'))
            finally:
                case.tearDown()

    def test_request_changes_and_cancel_are_feedback_only(self):
        for action in ('REQUEST_CHANGES','CANCEL'):
            with self.subTest(action=action), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); case,store,receipt,_,sequence,event,owner=self._pass_case(root)
                decision=self._owner_decision(case,receipt,sequence,owner,action,'owner-'+action.lower())
                result=workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                    authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
                self.assertEqual(action,result['decision'])
                self.assertEqual('PASS',store.get_run(receipt['run_id'])['attempt']['result'])
                self.assertIsNone(store.get_acceptance(decision.get('acceptance_id','accept_missing')))
                self.assertIsNone(store.get_regression_job('task_regression_'+decision['command_id'].replace('owner-','')))
                self.assertFalse((root/'snapshots').exists())
                saved=store.get_owner_decision_receipt(owner,decision['command_id'])
                feedback=store.get_event('event_owner_feedback_'+saved['response']['decision_id'][6:])['payload']
                self.assertEqual('OWNER_FEEDBACK',feedback['kind'])

    def test_fresh_accept_rejects_tampered_archive_without_reserving_acceptance(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,_,sequence,event,owner=self._pass_case(root)
            submission=store.get_run_submission(receipt['run_id'])
            archive=next(x['artifact'] for x in submission['artifacts'] if Path(x['artifact']['path']).name=='source-snapshot.zip')
            Path(archive['path']).write_bytes(b'tampered archive')
            decision=self._owner_decision(case,receipt,sequence,owner,'ACCEPT','owner-tampered')
            with self.assertRaises(workflow_store.ProtocolError):
                workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                    authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            self.assertIsNone(store.get_acceptance(decision['acceptance_id']))
            self.assertIsNone(store.get_regression_job('task_regression_tampered'))
            self.assertFalse((root/'snapshots').exists())

    def test_ambiguous_registered_source_evidence_rejected(self):
        for artifact_type, basename in (('PROVENANCE','source-snapshot.zip'), ('SOURCE_MANIFEST','source-manifest.json')):
          with self.subTest(artifact_type=artifact_type), tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,_,sequence,event,owner=self._pass_case(root)
            submission=store.get_run_submission(receipt['run_id'])
            original=next(x['artifact'] for x in submission['artifacts'] if x['artifact']['type']==artifact_type and Path(x['artifact']['path']).name==basename)
            duplicate=dict(original); duplicate['artifact_id']='artifact_duplicate_'+artifact_type.lower()
            store.register_artifact(duplicate, input_digest=case.request['input_digest'])
            decision=self._owner_decision(case,receipt,sequence,owner,'ACCEPT','owner-ambiguous-'+artifact_type.lower())
            with self.assertRaises(workflow_store.ProtocolError):
                workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                    authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            self.assertIsNone(store.get_acceptance(decision['acceptance_id']))
            self.assertFalse((root/'snapshots').exists())

    def test_manifest_semantic_digest_must_match_run_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,_,sequence,event,owner=self._pass_case(root)
            submission=store.get_run_submission(receipt['run_id'])
            binding=next(x for x in submission['artifacts'] if x['artifact']['type']=='SOURCE_MANIFEST')
            artifact=dict(binding['artifact']); artifact['artifact_id']='artifact_wrong_semantic_manifest'
            bad=workflow_store.canonical_json({'files':[]}).encode()
            path=root/'wrong-manifest'/'source-manifest.json'; path.parent.mkdir(); path.write_bytes(bad)
            artifact.update(path=str(path),size_bytes=len(bad),sha256=hashlib.sha256(bad).hexdigest())
            store.register_artifact(artifact,input_digest=case.request['input_digest'])
            saved=dict(binding,artifact=artifact)
            submission['artifacts']=[x for x in submission['artifacts'] if x['artifact']['type']!='SOURCE_MANIFEST']+[saved]
            decision=self._owner_decision(case,receipt,sequence,owner,'ACCEPT','owner-wrong-manifest-semantic')
            with patch.object(store,'get_run_submission',return_value=submission):
                with self.assertRaisesRegex(workflow_store.ProtocolError,'semantic identity'):
                    workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                        authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            self.assertIsNone(store.get_acceptance(decision['acceptance_id']))
            self.assertIsNone(store.get_regression_job('task_regression_wrong-manifest-semantic'))
            self.assertFalse((root/'snapshots').exists())

    def test_unsafe_acceptance_id_is_rejected_before_snapshot_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,_,sequence,event,owner=self._pass_case(root)
            decision=self._owner_decision(case,receipt,sequence,owner,'ACCEPT','owner-unsafe')
            decision['acceptance_id']='accept_../escape'
            with self.assertRaises(workflow_store.ProtocolError):
                workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots').decide(decision,
                    authenticated_owner=owner,displayed_result_id=event['result_id'],displayed_result_digest=event['result_digest'])
            self.assertFalse((root/'escape').exists())
            self.assertFalse((root/'snapshots').exists())
            self.assertIsNone(store.get_acceptance('accept_../escape'))
            self.assertIsNone(store.get_regression_job('task_regression_../escape'))
