import unittest
from types import SimpleNamespace

import workflow_review_cancellation
import workflow_review_pipeline
import workflow_review_transport
import workflow_store


class CancelledReadinessTests(unittest.TestCase):
    def setUp(self):
        self.task={'task_id':'task-r','run_id':'run-r','evaluation_generation':3,
            'assignment_generation':1,'input_digest':'d'*64,'kind':'REVIEW','state':'CANCELLED'}
        self.payload={'contract_version':1,'event_id':'event-r','kind':'TASK_READY','run_id':'run-r',
            'evaluation_generation':3,'task_id':'task-r','assignment_generation':1,'input_digest':'d'*64}
        self.event={'event_id':'event-r','run_id':'run-r','generation':3,'kind':'TASK_READY',
            'payload':self.payload,'payload_digest':workflow_store.digest(self.payload)}
        self.cancel={'confirmed':1,'proof':{'task_id':'task-r','run_id':'run-r','generation':3,
            'assignment_generation':1,'input_digest':'d'*64,'kind':'DB_NEVER_STARTED'}}
        self.resources=[]

    def test_exact_never_started_proof_settles_locally_without_transport(self):
        self.assertTrue(workflow_review_cancellation.cancelled_review_readiness_settled(
            self.task,self.event,self.cancel,self.resources))
        class Store:
            def get_task(_, _id): return self.task
            def get_event(_, _id): return self.event
            def get_task_cancellation(_, _id): return self.cancel
            def get_task_resources(_, _id): return self.resources
        transport=workflow_review_transport.ReviewDispatchBackend.__new__(workflow_review_transport.ReviewDispatchBackend)
        transport.store=Store()
        transport.request=lambda *a: self.fail('cancelled readiness must not call reviewer API')
        self.assertEqual('SETTLED',transport.observe('event-r',self.payload))

    def test_mismatches_and_active_or_unconfirmed_state_fail_closed(self):
        for cancellation,resources in [(None,[]),({'confirmed':0,'proof':self.cancel['proof']},[]),
            ({'confirmed':1,'proof':{**self.cancel['proof'],'kind':'OTHER'}},[]),
            (self.cancel,[{'state':'HELD'}])]:
            self.assertFalse(workflow_review_cancellation.cancelled_review_readiness_settled(
                self.task,self.event,cancellation,resources))
        for key,value in [('event_id','wrong'),('input_digest','e'*64),('assignment_generation',9)]:
            bad={**self.payload,key:value}; event={**self.event,'payload':bad,'payload_digest':workflow_store.digest(bad)}
            self.assertFalse(workflow_review_cancellation.cancelled_review_readiness_settled(
                self.task,event,self.cancel,[]))
        self.assertFalse(workflow_review_cancellation.cancelled_review_readiness_settled(
            {**self.task,'state':'RUNNING'},self.event,self.cancel,[]))

    def test_pipeline_returns_distinct_settled_disposition(self):
        class Transport:
            def observe(_, event_id, payload): return 'SETTLED'
        pipeline=workflow_review_pipeline.ReviewPipeline.__new__(workflow_review_pipeline.ReviewPipeline)
        pipeline.store=SimpleNamespace(get_task=lambda _:self.task)
        pipeline.transport=Transport()
        self.assertEqual('SETTLED',pipeline.dispatch('event-r',self.payload))

    def test_coordinator_acknowledges_settled_first_delivery_and_reclaim_only_for_ready(self):
        import workflow_coordinator
        class Store:
            def __init__(self): self.acks=[]
            def acknowledge_outbox(self,event_id,generation): self.acks.append((event_id,generation))
        class Handler:
            def __init__(self, disposition): self.disposition=disposition; self.observed=0; self.dispatched=0
            def observe(self,*_): self.observed+=1; return self.disposition
            def dispatch(self,*_): self.dispatched+=1; return self.disposition
        coordinator=workflow_coordinator.ExplicitCoordinator.__new__(workflow_coordinator.ExplicitCoordinator)
        coordinator.store=Store(); coordinator.handlers={'TASK_READY':Handler('SETTLED')}
        coordinator._set_error=lambda _:None; coordinator._incident=lambda *a: self.fail('settlement must not be incident')
        coordinator._record_latency=lambda *_:None
        for generation in (1,2):
            coordinator._deliver({'event_id':'event-r','claim_generation':generation,'payload':self.payload})
        self.assertEqual([('event-r',1),('event-r',2)],coordinator.store.acks)
        self.assertEqual(1,coordinator.handlers['TASK_READY'].observed)
        self.assertEqual(1,coordinator.handlers['TASK_READY'].dispatched)
        bad=workflow_coordinator.ExplicitCoordinator.__new__(workflow_coordinator.ExplicitCoordinator)
        bad.store=Store(); bad.handlers={'TASK_COMPLETED':Handler('SETTLED')}
        bad._set_error=lambda _:None; bad._record_latency=lambda *_:None
        incidents=[]; bad._incident=lambda *a:incidents.append(a)
        bad._deliver({'event_id':'event-r','claim_generation':1,
            'payload':{**self.payload,'kind':'TASK_COMPLETED'}})
        self.assertEqual([],bad.store.acks)
        self.assertEqual(1,len(incidents))


if __name__ == '__main__': unittest.main()
