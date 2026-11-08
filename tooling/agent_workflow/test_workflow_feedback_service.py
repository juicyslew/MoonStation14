"""Coordinator delivery of the real typed terminal result event."""
import tempfile
import time
import unittest
from pathlib import Path

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_native_execution
from workflow_coordinator import CoordinatorBinding
from workflow_result_delivery import AuthorResultDelivery
from test_workflow_native_execution import admit_native_fixture


class FeedbackServiceTests(unittest.TestCase):
    def test_kindless_terminal_event_reaches_author_and_acknowledges(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,receipt,task_id=admit_native_fixture(root,caller='feedback-case',key='feedback-case',delay=.02)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            backend.dispatch('native-fixture-event',{'task_id':task_id})
            backend.workers[task_id].wait(timeout=20)
            seq=store.get_run(receipt['run_id'])['run']['sequence']
            store.transition_attempt(receipt['run_id'],1,expected_sequence=seq,phase='STOPPING',proposed_result='PASS')
            terminal=store.transition_attempt(receipt['run_id'],1,expected_sequence=seq+1,phase='DECIDED',terminal_result='PASS',cleanup_state='NOT_REQUIRED')
            event_id=terminal['event_id']; payload=store.get_event(event_id)['payload']
            self.assertNotIn('kind',payload)
            self.assertEqual(workflow_coordinator.event_kind(payload),'RESULT_AVAILABLE')
            author=store.get_run(receipt['run_id'])['run']['author']
            import hashlib
            from workflow_store import canonical_json
            delivery_id='delivery_'+hashlib.sha256((event_id+'AUTHOR'+canonical_json(author)).encode()).hexdigest()[:24]
            store.enqueue_delivery(event=payload,delivery_id=delivery_id,recipient=author,target_kind='AUTHOR')
            calls=[]
            def request(server,method,path,body=None):
                calls.append((method,path,body))
                if method=='GET': return 200,{'id':author['session_id'],'directory':author['worktree'],'agent':'orchestrate-author'}
                self.assertEqual(method,'POST'); self.assertTrue(path.endswith('/prompt'))
                return 200,{'id':body['id'],'sessionID':author['session_id'],'type':'user','delivery':body['delivery'],'payload':{'text':body['text'],'metadata':body['metadata']}}
            handler=AuthorResultDelivery(store,server='http://127.0.0.1:4096',request=request)
            delivery_id=handler._delivery_id(event_id,payload)
            context=type('Context',(),{'authenticated_author':author,'repo':Path(author['worktree'])})()
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'token':CoordinatorBinding('feedback-case',context)},artifact_dir=root/'artifacts',handlers={'RESULT_AVAILABLE':handler},identity_verifier=lambda _:False,reconcile_interval=.05)
            with TestClient(coordinator.app()) as client:
                deadline=time.monotonic()+5
                while len([c for c in calls if c[0]=='POST'])<1 and time.monotonic()<deadline: time.sleep(.01)
                self.assertEqual([c[0] for c in calls],['GET','POST'])
                while store.get_delivery(delivery_id)['state']!='TRANSPORT_ACCEPTED' and time.monotonic()<deadline: time.sleep(.01)
                saved_delivery=store.get_delivery(delivery_id)
                self.assertEqual(saved_delivery['state'],'TRANSPORT_ACCEPTED',
                                 f"transport receipt not committed by deadline; state={saved_delivery['state']!r}, calls={[(c[0], c[1]) for c in calls]!r}")
                ack={'contract_version':1,**{k:payload[k] for k in ('event_id','run_id','evaluation_generation','result_id','result_digest')}}
                ack['command_id']='feedback-ack'
                response=client.post('/v1/acknowledge-result',headers={'Authorization':'Bearer token'},json=ack)
                self.assertEqual(response.status_code,200,response.text)
                self.assertEqual(response.json().get('next_action'),payload.get('next_action'))
                deadline=time.monotonic()+1
                while store.get_delivery(delivery_id)['state']!='ACKNOWLEDGED' and time.monotonic()<deadline: time.sleep(.01)
                self.assertEqual(store.get_delivery(delivery_id)['state'],'ACKNOWLEDGED')
                self.assertEqual(len([c for c in calls if c[0]=='POST']),1)
                self.assertEqual(store.get_terminal_result(receipt['run_id'],1)['result'],'PASS')
                self.assertEqual(store.get_task(task_id)['state'],'COMPLETED')
