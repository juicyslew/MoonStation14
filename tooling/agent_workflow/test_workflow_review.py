import hashlib
import tempfile
import time
import unittest
from unittest.mock import patch
from pathlib import Path

import workflow_native_execution
import workflow_native_worker
import workflow_review
import workflow_store
from test_workflow_native_execution import admit_native_fixture


class ReviewPlanningTests(unittest.TestCase):
    def test_default_review_claim_window_is_twenty_minutes(self):
        self.assertEqual(1_200_000, workflow_store.REVIEW_CLAIM_WINDOW_MS)

    def fixture(self, root, *, fail=False, roles=('implementation',)):
        _, store, accepted, check_id = admit_native_fixture(root, caller='review-case', key='review-case',
                                                             review_roles=roles, fail_check=fail)
        return store, accepted['run_id'], check_id

    def run_check(self, root, store, run_id, check_id):
        backend = workflow_native_execution.NativeExecutionBackend(store, root/'artifacts', root/'lock')
        try:
            backend.dispatch('event-check-'+check_id, {'task_id':check_id})
            proc = backend.workers[check_id]
            proc.wait(timeout=30)
        finally:
            backend.close()
        return store.get_task(check_id)

    def coordinator(self, store, run_id, roles=('implementation',), *, session='reviewer', artifact_dir=None):
        run = store.get_run(run_id)['run']
        bindings = {role:{'worktree':run['author']['worktree'],'role':'reviewer'} for role in roles}
        return workflow_review.ReviewCoordinator(store, reviewers=bindings,
                                                artifact_dir=artifact_dir or Path(store.path).parent/'artifacts')

    def dispatched_review(self, root):
        store,run_id,check_id=self.fixture(root)
        self.run_check(root,store,run_id,check_id)
        task= self.coordinator(store,run_id).plan_reviews(run_id)[0]
        db=store._connect()
        try:
            task['state']='DISPATCHING'
            db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_store.canonical_json(task),task['task_id']))
            db.commit()
        finally: db.close()
        return store,run_id,task

    def test_source_access_is_manifest_bound_and_pages_utf8(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,task=self.dispatched_review(root)
            request,bundle=workflow_native_worker.load_native_inputs(store,run_id)
            repo=Path(request['source_worktree']); rel='source-review-test.txt'
            data=('αβ\n'+('line\n'*100)).encode('utf-8'); (repo/rel).write_bytes(data)
            row={'path':rel,'state':'FILE','sha256':hashlib.sha256(data).hexdigest(),'size_bytes':len(data)}
            bundle['source_manifest']['files'].append(row)
            # Tests the owner against the admitted manifest shape without altering a submission.
            with patch.object(workflow_review.ReviewCoordinator,'_inputs',return_value=(request,bundle)):
                owner=self.coordinator(store,run_id,artifact_dir=root/'artifacts')
                matches=owner.source_access(task['task_id'],source_query='source-review-test')
                self.assertEqual(rel,matches['matches'][0]['path'])
                page=owner.source_access(task['task_id'],source_path=rel)
                self.assertTrue(page['content'].startswith('αβ'))
                self.assertEqual(81,page['next_line'])
                second=owner.source_access(task['task_id'],source_path=rel,source_line=2)
                self.assertEqual('line',second['content'].splitlines()[0])
                bundle['source_manifest']['files'][-1]['size_bytes']=workflow_review.MAX_REVIEW_SOURCE_BYTES+1
                with patch.object(workflow_review.workflow_runtime,'safe',side_effect=AssertionError('oversized manifest must reject before file access')) as safe:
                    with self.assertRaises(workflow_review.SourceAccessError) as raised:
                        owner.source_access(task['task_id'],source_path=rel)
                    self.assertEqual(413,raised.exception.status)
                    safe.assert_not_called()
                with self.assertRaises(workflow_review.SourceAccessError): owner.source_access(task['task_id'],source_path='../outside')
                with self.assertRaises(workflow_review.SourceAccessError) as unknown:
                    owner.source_access(task['task_id'],source_path='missing')
                self.assertEqual('INVALID_QUERY',unknown.exception.code)
                self.assertIn('source_query',str(unknown.exception))
                guide_info=owner.review_guide(task['task_id'])
                self.assertTrue(Path(guide_info['path']).is_relative_to(root/'artifacts'))
                import json
                guide=json.loads(Path(guide_info['path']).read_text(encoding='utf-8'))
                self.assertIn('<assigned task_id>',guide['source_access']['query_example']['task_id'])
                self.assertEqual('<exact relative path returned by source_query>',guide['source_access']['page_example']['source_path'])
                self.assertNotIn('workflow_review.py',json.dumps(guide['source_access']))

    def test_changed_source_blocks_review_planning_before_task_ready(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root)
            self.run_check(root,store,run_id,check_id)
            request,bundle=workflow_native_worker.load_native_inputs(store,run_id)
            row=next(item for item in bundle['source_manifest']['files'] if item.get('state')=='FILE')
            source=Path(request['source_worktree'])/row['path']
            source.write_bytes(source.read_bytes()+b'\nchanged\n')
            with self.assertRaises((ValueError,workflow_store.ProtocolError)):
                self.coordinator(store,run_id).plan_reviews(run_id)
            self.assertEqual([], [task for task in store.list_tasks(run_id,1) if task['kind']=='REVIEW'])
            db=store._connect()
            try:
                ready=[row[0] for row in db.execute("SELECT payload FROM events")]
            finally: db.close()
            self.assertFalse(any('task_review_' in payload for payload in ready))

    @staticmethod
    def claim(store, run_id, task, *, session=None, generation=None):
        return store.claim_task(task_id=task['task_id'],run_id=run_id,
            evaluation_generation=task['evaluation_generation'],
            assignment_generation=task['assignment_generation'] if generation is None else generation,
            input_digest=task['input_digest'],session_id=session or task['assigned_session_id'])

    def test_valid_late_claim_preserves_deadline_and_duplicate_does_not_renew(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run_id,task=self.dispatched_review(Path(tmp))
            original=store.get_task(task['task_id'])['deadline_ms']
            with patch.object(workflow_store.time,'time',return_value=(original-60_000)/1000):
                self.assertEqual('ACCEPTED',self.claim(store,run_id,task)['disposition'])
            self.assertEqual(original,store.get_task(task['task_id'])['deadline_ms'])
            with patch.object(workflow_store.time,'time',return_value=(original+120_000)/1000):
                self.assertEqual('REPLAYED',self.claim(store,run_id,task)['disposition'])
            self.assertEqual(original,store.get_task(task['task_id'])['deadline_ms'])

    def test_rejected_claims_and_terminal_task_do_not_change_deadline(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run_id,task=self.dispatched_review(Path(tmp)); original=store.get_task(task['task_id'])['deadline_ms']
            for kwargs in ({'session':'ses_wrong'},{'generation':task['assignment_generation']+1}):
                with self.assertRaises(workflow_store.StaleCommand): self.claim(store,run_id,task,**kwargs)
                self.assertEqual(original,store.get_task(task['task_id'])['deadline_ms'])
            db=store._connect()
            try:
                db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_store.canonical_json(dict(task,state='COMPLETED',result={'decision':'PASS'})),task['task_id']))
                db.commit()
            finally: db.close()
            with self.assertRaises(workflow_store.StaleCommand): self.claim(store,run_id,task)
            self.assertEqual(original,store.get_task(task['task_id'])['deadline_ms'])

    def test_active_pause_rejects_claim_without_deadline_change_and_check_claim_is_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,task=self.dispatched_review(root)
            original=store.get_task(task['task_id'])['deadline_ms']
            db=store._connect()
            try:
                db.execute('INSERT INTO task_dependency_pauses VALUES(?,?,?,?,?)',(task['task_id'],'dep',int(time.time()*1000),0,'{}'))
                db.commit()
            finally: db.close()
            with self.assertRaises(workflow_store.StaleCommand): self.claim(store,run_id,task)
            self.assertEqual(original,store.get_task(task['task_id'])['deadline_ms'])
            check_root=root/'check'; check_root.mkdir()
            _,check_store,accepted,check_id=admit_native_fixture(check_root,caller='check-claim',key='check-claim')
            check=check_store.get_task(check_id)
            with self.assertRaises(workflow_store.ProtocolError):
                check_store.claim_task(task_id=check_id,run_id=accepted['run_id'],
                    evaluation_generation=check['evaluation_generation'],assignment_generation=check['assignment_generation'],
                    input_digest=check['input_digest'],session_id='ses_check')

    def test_preclaim_pause_is_not_double_counted_and_postclaim_pause_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run_id,task=self.dispatched_review(Path(tmp))
            db=store._connect()
            try:
                db.execute('INSERT INTO task_dependency_pauses VALUES(?,?,?,?,?)',(task['task_id'],'dep',-1,120_000,'{}'))
                db.commit()
            finally: db.close()
            now=task['deadline_ms']-120_000
            with patch.object(workflow_store.time,'time',return_value=now/1000): self.claim(store,run_id,task)
            claimed=store.get_task(task['task_id'])
            from workflow_dependency import task_deadline_due
            self.assertEqual(120_000,store.task_paused_duration_ms(task['task_id'],now))
            db=store._connect()
            try:
                db.execute('UPDATE task_dependency_pauses SET accumulated_paused_ms=150000 WHERE task_id=?',(task['task_id'],))
                db.commit()
            finally: db.close()
            postclaim_paused=store.task_paused_duration_ms(task['task_id'],now)
            self.assertEqual(150_000,postclaim_paused)
            expiry=task['deadline_ms']+postclaim_paused
            self.assertFalse(task_deadline_due(claimed,expiry-1,paused_ms=postclaim_paused))
            self.assertTrue(task_deadline_due(claimed,expiry,paused_ms=postclaim_paused))

    def test_dispatch_deadline_stays_bounded_and_claimed_deadline_expires(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run_id,task=self.dispatched_review(Path(tmp))
            from workflow_dependency import task_deadline_due
            self.assertTrue(task_deadline_due(task,task['deadline_ms']))
            original=task['deadline_ms']
            now=original-60_000
            with patch.object(workflow_store.time,'time',return_value=now/1000): self.claim(store,run_id,task)
            claimed=store.get_task(task['task_id'])
            self.assertFalse(task_deadline_due(claimed,original-1))
            self.assertTrue(task_deadline_due(claimed,original))

    def test_reserved_session_id_is_deterministic_across_assignment_coordinates(self):
        reserved=workflow_review.ReviewCoordinator._reserved_session
        base=reserved('task-one',1,1)
        self.assertEqual(base,reserved('task-one',1,1))
        self.assertEqual(4,len({base,reserved('task-two',1,1),reserved('task-one',2,1),reserved('task-one',1,2)}))

    def test_mutated_saved_session_id_is_rejected_by_planning_and_assignment(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root)
            self.run_check(root,store,run_id,check_id)
            coordinator=self.coordinator(store,run_id)
            task=coordinator.plan_reviews(run_id)[0]
            before=(task['evaluation_generation'],task['assignment_generation'])
            task['assigned_session_id']='ses_mutated'
            db=store._connect()
            try:
                db.execute('UPDATE tasks SET task_json=? WHERE id=?',
                    (workflow_store.canonical_json(task),task['task_id']))
                db.commit()
            finally:db.close()
            with self.assertRaises(workflow_store.ProtocolError):coordinator.plan_reviews(run_id)
            with self.assertRaises(workflow_store.ProtocolError):coordinator.build_assignment(task['task_id'])
            saved=store.get_task(task['task_id'])
            self.assertEqual('ses_mutated',saved['assigned_session_id'])
            self.assertEqual(before,(saved['evaluation_generation'],saved['assignment_generation']))

    def test_pass_creates_deterministic_review_tasks_and_verified_assignment(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root,roles=('implementation','coverage'))
            self.assertEqual('PASS',self.run_check(root,store,run_id,check_id)['result']['decision'])
            coordinator=self.coordinator(store,run_id,roles=('implementation','coverage'))
            first=coordinator.plan_reviews(run_id); second=coordinator.plan_reviews(run_id)
            self.assertEqual([t['task_id'] for t in first],[t['task_id'] for t in second])
            self.assertEqual([t['deadline_ms'] for t in first],[t['deadline_ms'] for t in second])
            self.assertEqual({'QUEUED'}, {t['state'] for t in first})
            assignment=coordinator.build_assignment(first[0]['task_id'])
            workflow_store.validate(assignment['body']['envelope'],'dispatch_envelope')
            refs=assignment['body']['envelope']['artifact_refs']
            receipts=[store.get_artifact(r)['artifact'] for r in refs if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT']
            self.assertTrue(receipts)
            for artifact in receipts:
                data=Path(artifact['path']).read_bytes()
                self.assertEqual((artifact['size_bytes'],artifact['sha256']),(len(data),hashlib.sha256(data).hexdigest()))

    def test_pending_check_admits_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,_=self.fixture(root)
            self.assertEqual([],self.coordinator(store,run_id).plan_reviews(run_id))
            self.assertEqual(1,len(store.list_tasks(run_id,1)))

    def test_actual_failed_check_admits_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root,fail=True)
            self.assertEqual('FAIL',self.run_check(root,store,run_id,check_id)['result']['decision'])
            self.assertEqual([],self.coordinator(store,run_id).plan_reviews(run_id))
            self.assertEqual(1,len(store.list_tasks(run_id,1)))

    def test_invalid_reviewer_role_or_worktree_is_rejected_before_admission(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root,roles=('implementation','coverage'))
            self.run_check(root,store,run_id,check_id)
            for invalid in ({'implementation':{'role':'author','worktree':str(root/'repo')}},
                            {'implementation':{'role':'reviewer','worktree':'  '} }):
                with self.assertRaises(workflow_store.ProtocolError):
                    workflow_review.ReviewCoordinator(store,reviewers=invalid,artifact_dir=root).plan_reviews(run_id)
                self.assertEqual(1,len(store.list_tasks(run_id,1)))

    def test_reserved_sessions_are_stable_distinct_and_saved_in_envelope(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root,roles=('implementation','coverage'))
            self.run_check(root,store,run_id,check_id)
            coordinator=self.coordinator(store,run_id,roles=('implementation','coverage'))
            tasks=coordinator.plan_reviews(run_id)
            self.assertEqual([t['assigned_session_id'] for t in tasks],
                             [t['assigned_session_id'] for t in coordinator.plan_reviews(run_id)])
            self.assertEqual(2,len({t['assigned_session_id'] for t in tasks}))
            for task in tasks:
                assignment=coordinator.build_assignment(task['task_id'])
                self.assertEqual(task['assigned_session_id'],assignment['body']['envelope']['recipient_binding']['session_id'])

    def test_tampered_receipt_blocks_assignment_without_changing_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root)
            self.run_check(root,store,run_id,check_id)
            coordinator=self.coordinator(store,run_id)
            task=coordinator.plan_reviews(run_id)[0]
            check=store.get_task(check_id)
            receipt=next(store.get_artifact(r)['artifact'] for r in check['result']['artifact_refs']
                         if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT')
            Path(receipt['path']).write_bytes(b'tampered')
            with self.assertRaises(workflow_store.ProtocolError): coordinator.build_assignment(task['task_id'])
            self.assertEqual('PASS',store.get_task(check_id)['result']['decision'])
            self.assertEqual('QUEUED',store.get_task(task['task_id'])['state'])

    def test_reserved_session_id_keeps_rendered_body_within_policy_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run_id,check_id=self.fixture(root)
            self.run_check(root,store,run_id,check_id)
            coordinator=self.coordinator(store,run_id)
            task=coordinator.plan_reviews(run_id)[0]
            assignment=coordinator.build_assignment(task['task_id'])
            self.assertEqual(task['assigned_session_id'],assignment['body']['envelope']['recipient_binding']['session_id'])
            self.assertEqual('QUEUED',store.get_task(task['task_id'])['state'])


if __name__=='__main__': unittest.main()
