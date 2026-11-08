import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_review_retry
import workflow_review_transport
import test_workflow_review_quiescence as _quiescence_fixture


class ReviewQuiescenceRetryTests(unittest.TestCase):
    def prepared(self):
        dispatch=workflow_review_transport.ReviewDispatchBackend.dispatch
        def inactive_dispatch(owner,event,payload):
            owner.request.active=False
            return dispatch(owner,event,payload)
        with patch.object(workflow_review_transport.ReviewDispatchBackend,'dispatch',inactive_dispatch):
            tmp,store,task,cancellation,api=_quiescence_fixture.ReviewQuiescenceTests().fixture()
        self.assertEqual('APPLIED',cancellation.quiesce(task['task_id']))
        # Exercise the difficult case: queued original was drained and never retained in history.
        api.history.clear()
        retry=workflow_review_retry.ReviewRetryAdapter(cancellation.planner,server=cancellation.server,
            request=api,agents={'implementation':'workflow-reviewer-implementation'},transport=cancellation.transport,cancellation=cancellation)
        return tmp,store,task,cancellation,api,retry

    def test_quiescence_proof_allows_one_control_retry_and_exact_replay(self):
        tmp,store,task,cancellation,api,retry=self.prepared()
        try:
            before=(task['task_id'],task['run_id'],task['assignment_generation'],task['evaluation_generation'],task['input_digest'],task['assigned_session_id'])
            calls=len(api.calls)
            result=retry.retry(task['task_id'],'quiescence-owner-retry','trusted')
            self.assertEqual('RESUMED',result['status'],result)
            wake_calls=[x for x in api.calls[calls:] if x[0]=='POST' and x[1].endswith('/prompt')]
            self.assertEqual(1,len(wake_calls)); self.assertEqual('steer',wake_calls[0][2]['delivery'])
            after=store.get_task(task['task_id'])
            self.assertEqual(before,(after['task_id'],after['run_id'],after['assignment_generation'],after['evaluation_generation'],after['input_digest'],after['assigned_session_id']))
            self.assertEqual('HELD',store.get_task_resources(task['task_id'])[0]['state'])
            count=len(api.calls)
            self.assertEqual(result,retry.retry(task['task_id'],'quiescence-owner-retry','trusted'))
            self.assertEqual(count,len(api.calls))
        finally: tmp.cleanup()

    def test_invalid_checkpoint_does_not_admit_control(self):
        tmp,store,task,cancellation,api,retry=self.prepared()
        try:
            resource=store.get_task_resources(task['task_id'])[0]
            artifact=store.get_artifact(resource['cleanup_proof'])['artifact']
            path=Path(artifact['path']); path.write_bytes(path.read_bytes()+b' ')
            count=len(api.calls)
            result=retry.retry(task['task_id'],'invalid-quiescence-proof','trusted')
            self.assertEqual('UNKNOWN',result['status'],result)
            self.assertFalse(any(x[0]=='POST' and x[1].endswith('/prompt') for x in api.calls[count:]))
        finally: tmp.cleanup()

    def test_missing_checkpoint_does_not_admit_control(self):
        tmp,store,task,cancellation,api,retry=self.prepared()
        try:
            cancellation.get_quiescence_checkpoint=lambda _task_id: None
            count=len(api.calls)
            result=retry.retry(task['task_id'],'missing-quiescence-proof','trusted')
            self.assertEqual('UNKNOWN',result['status'],result)
            self.assertFalse(any(x[0]=='POST' and x[1].endswith('/prompt') for x in api.calls[count:]))
        finally: tmp.cleanup()


if __name__=='__main__': unittest.main()
