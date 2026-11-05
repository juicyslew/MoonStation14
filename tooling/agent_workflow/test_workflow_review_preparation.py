import tempfile
import unittest
from pathlib import Path

import workflow_review_transport
import workflow_review_pipeline
import workflow_review_results
import workflow_preflight


class ReviewPreparationTests(unittest.TestCase):
    def prepared(self, root):
        from test_workflow_review import ReviewPlanningTests
        fixture=ReviewPlanningTests()
        store,run_id,check_id=fixture.fixture(root,roles=('implementation',))
        fixture.run_check(root,store,run_id,check_id)
        planner=fixture.coordinator(store,run_id)
        planner.artifact_dir=root/'review-artifacts'; planner.artifact_dir.mkdir()
        task=planner.plan_reviews(run_id)[0]
        return store,planner,task

    def test_oversized_queued_assignment_commits_error_without_transport(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task=self.prepared(Path(tmp))
            error=workflow_preflight.ReviewInputPreparationError('implementation',40000,16384)
            planner.build_assignment=lambda _task: (_ for _ in ()).throw(error)
            calls=[]
            backend=workflow_review_transport.ReviewDispatchBackend(planner,server='http://127.0.0.1:4096',
                agents={'implementation':'reviewer'},request=lambda *args: calls.append(args))
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':task['task_id']}))
            saved=store.get_task(task['task_id'])
            self.assertEqual('COMPLETED',saved['state'])
            self.assertEqual('ERROR',saved['result']['decision'])
            db=store._connect()
            try:
                incident=__import__('json').loads(db.execute("SELECT incident_json FROM incidents WHERE run_id=?",(task['run_id'],)).fetchone()[0])
                self.assertEqual('REVIEW_ENVELOPE_OVERSIZE',incident['code'])
                self.assertIn('40000 bytes; limit 16384 bytes',incident['message'])
                event=db.execute("SELECT e.payload FROM outbox o JOIN events e ON e.id=o.event_id WHERE e.kind='TASK_COMPLETED' AND json_extract(e.payload,'$.task_id')=?",(task['task_id'],)).fetchone()
                self.assertIsNotNone(event)
            finally: db.close()
            self.assertEqual([],calls)
            pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,workflow_review_results.ReviewResults(store,planner),backend,object())
            result=pipeline.completed(__import__('json').loads(event['payload']))
            self.assertEqual('ERROR',result['decision'])
            self.assertEqual('DECIDED',store.get_run(task['run_id'])['attempt']['phase'])

    def test_reclaimed_queued_wake_observation_settles_typed_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task=self.prepared(Path(tmp))
            error=workflow_preflight.ReviewInputPreparationError('implementation',40000,16384)
            planner.build_assignment=lambda _task: (_ for _ in ()).throw(error)
            calls=[]
            backend=workflow_review_transport.ReviewDispatchBackend(planner,server='http://127.0.0.1:4096',
                agents={'implementation':'reviewer'},request=lambda *args: calls.append(args))
            self.assertEqual('APPLIED',backend.observe('reclaimed-wake',{'task_id':task['task_id']}))
            self.assertEqual('ERROR',store.get_task(task['task_id'])['result']['decision'])
            self.assertEqual([],calls)
            self.assertEqual('COMPLETED',store.get_task(task['task_id'])['state'])

    def test_observation_does_not_turn_task_artifact_history_into_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task=self.prepared(Path(tmp))
            error=workflow_preflight.ReviewInputPreparationError('implementation',40000,16384)
            planner.build_assignment=lambda _task: (_ for _ in ()).throw(error)
            store.register_artifact({'contract_version':1,'artifact_id':'artifact_preparation_history',
                'run_id':task['run_id'],'producer_task_id':task['task_id'],'type':'PROVENANCE',
                'path':str(Path(tmp)/'proof'),'sha256':'0'*64,'size_bytes':1,'immutable':True,
                'producer_kind':'TASK','producer_id':task['task_id']},input_digest=task['input_digest'])
            calls=[]
            backend=workflow_review_transport.ReviewDispatchBackend(planner,server='http://127.0.0.1:4096',
                agents={'implementation':'reviewer'},request=lambda *args: calls.append(args))
            self.assertEqual('UNKNOWN',backend.observe('reclaimed-wake',{'task_id':task['task_id']}))
            self.assertIsNone(store.get_task(task['task_id'])['result'])
            self.assertEqual([],calls)

    def test_exact_repeat_is_replayed_but_resource_history_refuses(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,_,task=self.prepared(Path(tmp))
            args=dict(task_id=task['task_id'],run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
                assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],
                error_code='REVIEW_PREPARATION_FAILED',message='Local preparation failed safely.')
            self.assertEqual('ACCEPTED',store.fail_review_preparation(**args)['disposition'])
            self.assertEqual('REPLAYED',store.fail_review_preparation(**args)['disposition'])

    def test_failure_refuses_wrong_state_generation_kind_and_dispatch_evidence(self):
        for mode in ('generation','state','kind','dispatch','resource','artifact'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as tmp:
                store,planner,task=self.prepared(Path(tmp))
                args=dict(task_id=task['task_id'],run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
                    assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],
                    error_code='REVIEW_PREPARATION_FAILED',message='Local preparation failed safely.')
                if mode=='generation':args['evaluation_generation']+=1
                elif mode=='state':
                    from test_workflow_review_transport import ReviewTransportTests
                    ReviewTransportTests.set_task_state(store,task,'DISPATCHING')
                elif mode=='kind':
                    from test_workflow_review_transport import ReviewTransportTests
                    saved=store.get_task(task['task_id']); saved['kind']='CHECK'
                    db=store._connect()
                    try: db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_review_transport.workflow_store.canonical_json(saved),task['task_id'])); db.commit()
                    finally: db.close()
                elif mode=='dispatch':
                    from test_workflow_review_transport import ReviewTransportTests
                    ReviewTransportTests.reserve_review(store,planner,task)
                elif mode=='resource':
                    from test_workflow_review_transport import ReviewTransportTests
                    ReviewTransportTests.reserve_review(store,planner,task)
                elif mode=='artifact':
                    store.register_artifact({'contract_version':1,'artifact_id':'artifact_preparation_guard',
                        'run_id':task['run_id'],'producer_task_id':task['task_id'],'type':'PROVENANCE',
                        'path':str(Path(tmp)/'proof'),'sha256':'0'*64,'size_bytes':1,'immutable':True,
                        'producer_kind':'TASK','producer_id':task['task_id']},input_digest=task['input_digest'])
                with self.assertRaises(Exception):store.fail_review_preparation(**args)
                self.assertIsNone(store.get_task(task['task_id'])['result'])

    def test_failure_and_dispatch_fence_are_mutually_exclusive(self):
        from test_workflow_review_transport import ReviewTransportTests
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task=self.prepared(Path(tmp))
            args=dict(task_id=task['task_id'],run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
                assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],
                error_code='REVIEW_PREPARATION_FAILED',message='Local preparation failed safely.')
            store.fail_review_preparation(**args)
            with self.assertRaises(Exception): ReviewTransportTests.reserve_review(store,planner,task)
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task=self.prepared(Path(tmp))
            ReviewTransportTests.reserve_review(store,planner,task)
            args=dict(task_id=task['task_id'],run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
                assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],
                error_code='REVIEW_PREPARATION_FAILED',message='Local preparation failed safely.')
            with self.assertRaises(Exception): store.fail_review_preparation(**args)


if __name__=='__main__': unittest.main()
