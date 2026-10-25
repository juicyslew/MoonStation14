import tempfile
import unittest
import json
from pathlib import Path

import workflow_review_pipeline
import workflow_review_results
import test_workflow_review as review_fixtures


class ReviewPipelineTests(unittest.TestCase):
    def test_cancel_closure_retries_completed_review_cleanup_before_resource_gate(self):
        class Store:
            def __init__(self):
                self.resources=[{'resource_key':'review-session:x','state':'HELD'}]
                self.saved={'attempt':{'phase':'STOPPING','generation':1,'proposed_closure':'CANCELLED'},
                    'run':{'sequence':3}}
                self.tasks=[{'task_id':'review','kind':'REVIEW','state':'COMPLETED'},
                            {'task_id':'check','kind':'CHECK','state':'COMPLETED'}]
            def get_run(self, run_id): return self.saved
            def list_tasks(self, run_id, generation): return self.tasks
            def get_task_resources(self, task_id):
                return self.resources if task_id=='review' else [{'resource_key':'native','state':'RELEASED'}]
            def transition_attempt(self, *args, **kwargs):
                self.saved['attempt'].update(phase='CLOSED',cleanup_state='CONFIRMED')
                return {}
        store=Store()
        class Owner:
            calls=0
            def cleanup_completed(self, task_id):
                self.calls+=1
                if self.calls==1: return 'PENDING'
                store.resources[0]['state']='RELEASED'
                return 'APPLIED'
        owner=Owner()
        pipeline=workflow_review_pipeline.ReviewPipeline(store,None,None,None,None)
        pipeline.review_cancellation=owner
        first=pipeline._evaluate('run')
        self.assertEqual(['review-session:x'],first['unresolved_resources'])
        self.assertEqual('HELD',store.resources[0]['state'])
        second=pipeline._evaluate('run')
        self.assertTrue(second['closed'])
        self.assertEqual('RELEASED',store.resources[0]['state'])
        self.assertEqual('CLOSED',store.saved['attempt']['phase'])
        self.assertEqual(2,owner.calls)

    def test_known_busy_review_dispatch_propagates_pending(self):
        helper=review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=helper.fixture(root)
            helper.run_check(root,store,run_id,check_id)
            planner=helper.coordinator(store,run_id); task=planner.plan_reviews(run_id)[0]
            class BusyTransport:
                def dispatch(self,event_id,payload): return 'PENDING'
                def observe(self,event_id,payload): return 'ABSENT'
            pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,object(),BusyTransport(),object())
            self.assertEqual('PENDING',pipeline.dispatch('review-wake',{'task_id':task['task_id']}))
            self.assertEqual('QUEUED',store.get_task(task['task_id'])['state'])
            self.assertEqual([],store.get_task_resources(task['task_id']))

    def test_unanswered_dispatched_review_expires_as_durable_error(self):
        helper = review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            store, run_id, check_id = helper.fixture(root)
            helper.run_check(root, store, run_id, check_id)
            planner = helper.coordinator(store, run_id)
            api = workflow_review_results.ReviewResults(store, planner)
            pipeline = workflow_review_pipeline.ReviewPipeline(store, planner, api, object(), object())
            task = next(t for t in pipeline.planner.plan_reviews(run_id))
            envelope = planner.build_assignment(task['task_id'])['body']['envelope']
            store.dispatch_task(task_id=task['task_id'], event_id=envelope['event_id'],
                assigned_session_id=task['assigned_session_id'], dispatch_envelope=envelope)
            deadline = store.get_task(task['task_id'])['deadline_ms']

            pipeline.reconcile_pending(now_ms=deadline - 1)
            self.assertEqual('REVIEWING', store.get_run(run_id)['attempt']['phase'])
            self.assertIsNone(store.get_run(run_id)['attempt']['result'])

            pipeline.reconcile_pending(now_ms=deadline + 1)
            saved = store.get_run(run_id)
            self.assertEqual('ERROR', saved['attempt']['result'])
            self.assertEqual('DECIDED', saved['attempt']['phase'])
            self.assertIsNone(saved['attempt']['proposed_result'])
            self.assertEqual('UNRESOLVED', saved['attempt']['cleanup_state'])
            self.assertEqual('CANCEL_REQUESTED', store.get_task(task['task_id'])['state'])
            db = store._connect()
            try:
                incidents = db.execute("SELECT incident_json FROM incidents WHERE run_id=?", (run_id,)).fetchall()
                results = db.execute("SELECT payload FROM events WHERE json_extract(payload,'$.run_id')=? AND json_extract(payload,'$.result')='ERROR'", (run_id,)).fetchall()
            finally:
                db.close()
            self.assertEqual(1, len(incidents))
            incident = json.loads(incidents[0][0])
            self.assertEqual('MISSING_REVIEW_RESULT', incident['code'])
            self.assertEqual('OPEN', incident['state'])
            self.assertEqual(1, len(results))
            result_event = json.loads(results[0][0])
            self.assertEqual('WAIT_FOR_RECOVERY', result_event['next_action'])
            self.assertEqual('ERROR', result_event['result'])
            before = (store.get_run(run_id)['run']['sequence'], len(incidents), len(results), len(store.list_tasks(run_id, 1)))
            pipeline.reconcile_pending(now_ms=deadline + 2)
            db = store._connect()
            try:
                after_counts = (db.execute("SELECT COUNT(*) FROM incidents WHERE run_id=?", (run_id,)).fetchone()[0],
                                db.execute("SELECT COUNT(*) FROM events WHERE json_extract(payload,'$.run_id')=? AND json_extract(payload,'$.result')='ERROR'", (run_id,)).fetchone()[0])
            finally:
                db.close()
            self.assertEqual(before, (store.get_run(run_id)['run']['sequence'], *after_counts, len(store.list_tasks(run_id, 1))))

    def test_completed_error_retries_cleanup_callback_without_claiming_stub_proof(self):
        helper=review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=helper.fixture(root)
            helper.run_check(root,store,run_id,check_id)
            planner=helper.coordinator(store,run_id); results=workflow_review_results.ReviewResults(store,planner)
            class CleanupUnknown:
                disposition='UNKNOWN'
                calls=0
                def cleanup_completed(self, task_id): self.calls+=1; return self.disposition
            cleanup=CleanupUnknown()
            pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,results,object(),object())
            pipeline.review_cancellation=cleanup
            task=next(iter(planner.plan_reviews(run_id)))
            assignment=planner.build_assignment(task['task_id'])
            envelope=assignment['body']['envelope']; sid=task['assigned_session_id']
            message_id='msg_'+__import__('hashlib').sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
            store.dispatch_task(task_id=task['task_id'],event_id=envelope['event_id'],
                assigned_session_id=sid,dispatch_envelope=envelope,
                review_session_claim={'server':'http://127.0.0.1:4096','session_id':sid,
                    'message_id':message_id,'body_sha256':assignment['body_sha256']})
            claim={'contract_version':1,'command_id':'claim-error-review',
                **{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                'session_id':sid}
            self.assertTrue(results.claim(claim,session_id=sid)['accepted'])
            ref=next(ref for ref in envelope['artifact_refs']
                     if store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT')
            error={'contract_version':1,**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                'error_code':'REVIEW_SERVICE_FAULT','message':'Review failed before a result',
                'artifact_refs':[ref]}
            saved=results.error(error,session_id=sid)
            self.assertTrue(saved['accepted'],saved)
            completion=store.get_event(saved['result_id'])['payload']
            resource=store.get_task_resources(task['task_id'])[0]
            self.assertEqual('HELD',resource['state'])

            outcome=pipeline.completed(completion)
            terminal=store.get_run(run_id)['attempt']
            self.assertEqual('ERROR',outcome['decision'])
            self.assertEqual('DECIDED',terminal['phase'])
            self.assertEqual('ERROR',terminal['result'])
            self.assertEqual('UNRESOLVED',terminal['cleanup_state'])
            self.assertEqual('HELD',store.get_task_resources(task['task_id'])[0]['state'])
            immutable=store.get_terminal_result(run_id,1)
            self.assertEqual('ERROR',immutable['result'])

            sequence=store.get_run(run_id)['run']['sequence']
            terminal_result=store.get_terminal_result(run_id,1)
            terminal_attempt=dict(store.get_run(run_id)['attempt'])
            db=store._connect()
            try:
                result_events=db.execute("SELECT id,payload FROM events WHERE run_id=? AND json_extract(payload,'$.result')='ERROR'",(run_id,)).fetchall()
            finally: db.close()
            cleanup.disposition='APPLIED'
            pipeline.reconcile_pending()
            self.assertEqual(sequence,store.get_run(run_id)['run']['sequence'])
            self.assertEqual('UNRESOLVED',store.get_run(run_id)['attempt']['cleanup_state'])
            self.assertEqual('HELD',store.get_task_resources(task['task_id'])[0]['state'])
            # This stub's APPLIED is not cleanup proof: production cleanup and
            # actual release are covered by test_workflow_review_terminal_cleanup.
            self.assertEqual(2,cleanup.calls)
            self.assertEqual(terminal_attempt,store.get_run(run_id)['attempt'])
            self.assertEqual(terminal_result,store.get_terminal_result(run_id,1))
            db=store._connect()
            try:
                after_events=db.execute("SELECT id,payload FROM events WHERE run_id=? AND json_extract(payload,'$.result')='ERROR'",(run_id,)).fetchall()
            finally: db.close()
            self.assertEqual([tuple(row) for row in result_events],[tuple(row) for row in after_events])

    def test_actual_native_pass_wakes_reviews_and_two_accepted_results_finalize_once(self):
        helper = review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            store, run_id, check_id = helper.fixture(root, roles=('implementation', 'coverage'))
            helper.run_check(root, store, run_id, check_id)
            planner = helper.coordinator(store, run_id, roles=('implementation', 'coverage'))
            api = workflow_review_results.ReviewResults(store, planner)
            pipeline = workflow_review_pipeline.ReviewPipeline(store, planner, api, object(), object())
            initial = store.get_task(check_id)
            # Use the committed result event identity from the native completion record.
            db = store._connect()
            try:
                payload = json.loads(db.execute("SELECT payload FROM events WHERE kind='TASK_COMPLETED' AND json_extract(payload,'$.task_id')=?", (check_id,)).fetchone()[0])
            finally:
                db.close()
            pipeline.completed(payload, now_ms=0)
            reviews = [t for t in store.list_tasks(run_id, 1) if t['kind'] == 'REVIEW']
            self.assertEqual(2, len(reviews))
            self.assertIsNone(api.aggregate(run_id, now_ms=0)['decision'])
            for task in reviews:
                assignment = planner.build_assignment(task['task_id'])['body']['envelope']
                store.dispatch_task(task_id=task['task_id'], event_id=assignment['event_id'],
                    assigned_session_id=task['assigned_session_id'], dispatch_envelope=assignment)
                claim = {'contract_version':1,'command_id':'claim-'+task['task_id'],
                    **{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                    'session_id':task['assigned_session_id']}
                self.assertTrue(api.claim(claim, session_id=task['assigned_session_id'])['accepted'])
                receipt = next(ref for ref in assignment['artifact_refs'] if store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT')
                reply = api.complete(self.result(task, receipt), session_id=task['assigned_session_id'])
                self.assertTrue(reply['accepted'], reply)
                event = store.get_event(reply['result_id'])['payload']
                pipeline.completed(event, now_ms=0)
            self.assertEqual('DECIDED', store.get_run(run_id)['attempt']['phase'])
            self.assertEqual('PASS', store.get_run(run_id)['attempt']['result'])
            before = store.get_run(run_id)['run']['sequence']
            task_count = len(store.list_tasks(run_id, 1))
            event_count = store.outbox_summary().copy()
            pipeline.completed(event, now_ms=0)
            self.assertEqual(before, store.get_run(run_id)['run']['sequence'])
            self.assertEqual(task_count, len(store.list_tasks(run_id, 1)))
            self.assertEqual(event_count, store.outbox_summary())

    def test_completion_event_must_match_immutable_saved_event(self):
        helper=review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=helper.fixture(root)
            helper.run_check(root,store,run_id,check_id)
            planner=helper.coordinator(store,run_id); api=workflow_review_results.ReviewResults(store,planner)
            pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,api,object(),object())
            db=store._connect()
            try: event=json.loads(db.execute("SELECT payload FROM events WHERE kind='TASK_COMPLETED' AND json_extract(payload,'$.task_id')=?",(check_id,)).fetchone()[0])
            finally: db.close()
            forged=dict(event,result_digest='0'*64)
            self.assertIsNone(pipeline.completed(forged))
            self.assertEqual(1,len(store.list_tasks(run_id,1)))

    @staticmethod
    def result(task, receipt, decision='PASS'):
        finding={'criterion_id':'criterion-1','category':'IMPLEMENTATION','message':'reviewed evidence','location':'source','evidence_refs':[receipt],'validation_stage':'TECHNICAL'}
        if decision == 'FAIL': finding.update(required_change='Correct defect', location='source file')
        return {'contract_version':1,**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                'decision':decision,'findings':[finding] if decision=='FAIL' else [],'owner_followups':[],'inspected_refs':[receipt]}

    def test_actual_native_check_failure_finalizes_without_review_admission(self):
        helper=review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=helper.fixture(root,fail=True)
            helper.run_check(root,store,run_id,check_id)
            planner=helper.coordinator(store,run_id); api=workflow_review_results.ReviewResults(store,planner)
            pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,api,object(),object())
            db=store._connect()
            try: event=json.loads(db.execute("SELECT payload FROM events WHERE kind='TASK_COMPLETED' AND json_extract(payload,'$.task_id')=?",(check_id,)).fetchone()[0])
            finally: db.close()
            pipeline.completed(event)
            self.assertEqual('FAIL',store.get_run(run_id)['attempt']['result'])
            self.assertEqual([], [t for t in store.list_tasks(run_id,1) if t['kind']=='REVIEW'])

    def test_failed_review_aggregates_only_fail_findings(self):
        helper=review_fixtures.ReviewPlanningTests()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=helper.fixture(root,roles=('implementation','coverage'))
            helper.run_check(root,store,run_id,check_id); planner=helper.coordinator(store,run_id,roles=('implementation','coverage'))
            api=workflow_review_results.ReviewResults(store,planner); pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,api,object(),object())
            db=store._connect()
            try: event=json.loads(db.execute("SELECT payload FROM events WHERE kind='TASK_COMPLETED' AND json_extract(payload,'$.task_id')=?",(check_id,)).fetchone()[0])
            finally: db.close()
            pipeline.completed(event)
            for index,task in enumerate(t for t in store.list_tasks(run_id,1) if t['kind']=='REVIEW'):
                envelope=planner.build_assignment(task['task_id'])['body']['envelope']
                store.dispatch_task(task_id=task['task_id'],event_id=envelope['event_id'],assigned_session_id=task['assigned_session_id'],dispatch_envelope=envelope)
                claim={'contract_version':1,'command_id':'claim-'+task['task_id'],**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},'session_id':task['assigned_session_id']}
                api.claim(claim,session_id=task['assigned_session_id'])
                receipt=next(r for r in envelope['artifact_refs'] if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT')
                api.complete(self.result(task,receipt,'FAIL' if index==0 else 'PASS'),session_id=task['assigned_session_id'])
                db=store._connect()
                try: event=json.loads(db.execute("SELECT payload FROM events WHERE kind='TASK_COMPLETED' AND json_extract(payload,'$.task_id')=? ORDER BY created_ms DESC LIMIT 1",(task['task_id'],)).fetchone()[0])
                finally: db.close()
                pipeline.completed(event)
            self.assertEqual('FAIL',store.get_run(run_id)['attempt']['result'])
            db=store._connect()
            try:
                terminal=json.loads(db.execute("SELECT payload FROM events WHERE json_extract(payload,'$.result')='FAIL' ORDER BY created_ms DESC LIMIT 1").fetchone()[0])
            finally: db.close()
            self.assertEqual(1,len(terminal['findings']))
            self.assertEqual('Correct defect',terminal['findings'][0]['required_change'])


if __name__ == '__main__':
    unittest.main()
