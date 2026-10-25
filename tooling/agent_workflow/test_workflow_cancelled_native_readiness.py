import unittest

import workflow_native_cancellation
import workflow_native_execution
import workflow_store


class CancelledNativeReadinessTests(unittest.TestCase):
    def setUp(self):
        self.task={'task_id':'task-n','run_id':'run-n','evaluation_generation':4,
            'assignment_generation':2,'input_digest':'a'*64,'kind':'CHECK','state':'CANCELLED'}
        self.payload={'contract_version':1,'event_id':'event-n','kind':'TASK_READY','run_id':'run-n',
            'evaluation_generation':4,'task_id':'task-n','assignment_generation':2,'input_digest':'a'*64}
        self.event={'event_id':'event-n','run_id':'run-n','generation':4,'kind':'TASK_READY',
            'payload':self.payload,'payload_digest':workflow_store.digest(self.payload)}
        self.cancellation={'confirmed':1,'proof':{'task_id':'task-n','run_id':'run-n','generation':4,
            'assignment_generation':2,'input_digest':'a'*64,'kind':'DB_NEVER_STARTED'}}

    def test_exact_proof_settles_and_backend_never_dispatches(self):
        self.assertTrue(workflow_native_cancellation.cancelled_native_readiness_settled(
            self.task,self.event,self.cancellation,[],None,None))
        class Store:
            def get_task(_, _id): return self.task
            def get_event(_, _id): return self.event
            def get_task_cancellation(_, _id): return self.cancellation
            def get_task_resources(_, _id): return []
            def get_native_claim(_, _id): return None
            def get_native_scope(_, _id): return None
        backend=workflow_native_execution.NativeExecutionBackend.__new__(workflow_native_execution.NativeExecutionBackend)
        backend.store=Store()
        self.assertEqual('SETTLED',backend.observe('event-n',self.payload))
        backend.dispatch=lambda *_: self.fail('settled event must not launch')

    def test_mismatches_fail_closed(self):
        cases=[(None,[],None,None),(self.cancellation,[{'state':'HELD'}],None,None),
            (self.cancellation,[],{'claim_id':'x'},None),(self.cancellation,[],None,{'state':'CLEANED'})]
        for cancel,resources,claim,scope in cases:
            self.assertFalse(workflow_native_cancellation.cancelled_native_readiness_settled(
                self.task,self.event,cancel,resources,claim,scope))
        for field,value in [('input_digest','b'*64),('assignment_generation',9),('evaluation_generation',5)]:
            payload={**self.payload,field:value}; event={**self.event,'payload':payload,
                'payload_digest':workflow_store.digest(payload)}
            self.assertFalse(workflow_native_cancellation.cancelled_native_readiness_settled(
                self.task,event,self.cancellation,[],None,None))


if __name__ == '__main__': unittest.main()
