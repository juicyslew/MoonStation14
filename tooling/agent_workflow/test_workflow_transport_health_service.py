import tempfile
import unittest
from pathlib import Path
import json
import subprocess
from unittest.mock import patch

from workflow_store import WorkflowStore
from workflow_transport_health import WorkflowTransportHealth, LocalTransportUnavailable
from transport_health import TransportHealth
import workflow_coordinator
from tooling.agent_workflow import gate_dispatch


class HealthServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.store=WorkflowStore(Path(self.temp.name)/'db')
        self.server='http://127.0.0.1:4096'; self.now=[0]; self.calls=[]
        self.circuit=WorkflowTransportHealth(self.store,self.server,request=self.base)
        self.health=TransportHealth(self.circuit,self.base,clock_ms=lambda:self.now[0])
    def tearDown(self): self.temp.cleanup()
    def base(self,server,method,path,body=None,timeout=30):
        self.calls.append((method,path))
        if path=='/api/info' and self.success_info:
            return 200,{'version':'2.0','pid':55,'urls':[server],'paths':{'tmp':'/tmp'}}
        return 503,{}
    success_info=False

    def test_first_failure_blocks_repeated_calls_until_due_probe_then_local_connected(self):
        with self.assertRaises(LocalTransportUnavailable): self.health.request(self.server,'GET','/api/session')
        self.assertEqual(self.health.snapshot()['state'],'BACKOFF')
        for _ in range(3):
            with self.assertRaises(LocalTransportUnavailable): self.health.request(self.server,'POST','/session/prompt',body={'secret':'never persisted'})
        self.assertEqual(len(self.calls),1)
        self.now[0]=5000; self.success_info=True
        result=self.health.tick()
        self.assertEqual(result['state'],'CONNECTED')
        self.assertEqual(self.health.snapshot()['state'],'CLOSED')
        self.assertEqual(self.calls[-1],('GET','/api/info'))

    def test_three_due_probe_failures_open_and_health_snapshot_is_local(self):
        with self.assertRaises(LocalTransportUnavailable): self.health.request(self.server,'POST','/prompt')
        for due in (5000,20000,80000):
            self.now[0]=due; self.health.tick()
        self.assertEqual(self.health.snapshot()['state'],'OPEN')
        count=len(self.calls)
        self.assertEqual(self.health.snapshot()['state'],'OPEN')
        self.assertEqual(len(self.calls),count)
        with self.assertRaises(LocalTransportUnavailable): self.health.request(self.server,'GET','/api/info')
        self.assertEqual(len(self.calls),count)

    def test_coordinator_optional_hook_snapshots_without_network(self):
        c=workflow_coordinator.ExplicitCoordinator(self.store,bindings={'x':object()},artifact_dir=self.temp.name,
            identity_verifier=lambda _:True,transport_health=self.health)
        self.assertEqual(c.summary()['local_api']['state'],'UNKNOWN')
        self.assertEqual(self.calls,[])

    def test_failure_after_recovery_survives_transport_health_restart(self):
        with self.assertRaises(LocalTransportUnavailable): self.health.request(self.server,'GET','/api/session')
        self.now[0]=5002; self.success_info=True; self.health.tick()
        self.assertEqual(self.health.snapshot()['state'],'CLOSED')
        restarted=TransportHealth(self.circuit,self.base,clock_ms=lambda:self.now[0])
        self.success_info=False
        with self.assertRaises(LocalTransportUnavailable): restarted.request(self.server,'GET','/api/session')
        self.assertEqual(restarted.snapshot()['state'],'BACKOFF')

    def test_opted_in_cli_absence_is_not_transport_failure(self):
        request=lambda server,method,path,body=None,timeout=30: gate_dispatch._request(
            server,method,path,body,timeout,accepted_http_statuses=(404,))
        health=TransportHealth(self.circuit,request,clock_ms=lambda:self.now[0])
        response=subprocess.CompletedProcess(['opencode','api'],1,
            json.dumps({'_tag':'SessionNotFoundError','sessionID':'absent',
                        'message':'Session not found: absent'}),'HTTP 404 Not Found\n')
        with patch.object(gate_dispatch.subprocess,'run',return_value=response):
            self.assertEqual((404,None),health.request(self.server,'GET','/api/session/absent'))
        self.assertNotIn(health.snapshot()['state'],('BACKOFF','OPEN'))

    def test_retry_receipt_replay_after_probe_does_not_reset_but_new_command_does(self):
        for index,now in enumerate((0,1,2)):
            self.store.local_api_failure(key=self.circuit.key,failure_id='open'+str(index),now_ms=now,details={'kind':'test'})
        opened=self.circuit.snapshot(); self.assertEqual(opened['state'],'OPEN')
        first=self.health.retry('retry-a'); self.assertEqual(first['generation'],opened['generation']+1)
        self.now[0]=5002; self.success_info=True; self.health.tick()
        self.assertEqual(self.circuit.snapshot()['state'],'CLOSED')
        replay=self.health.retry('retry-a')
        self.assertEqual(replay,first)
        self.assertEqual(self.circuit.snapshot()['state'],'CLOSED')
        with self.assertRaises(Exception): self.store.local_api_retry(key='LOCAL_API:http://127.0.0.1:4097',command_id='retry-a')
        fresh=self.health.retry('retry-b')
        self.assertEqual(fresh['generation'],first['generation']+1)
        self.assertEqual(fresh['probes'],0)

    def test_owner_retry_route_only_receipts_and_background_probe_is_get_info(self):
        from starlette.testclient import TestClient
        from workflow_coordinator import ExplicitCoordinator, OwnerBinding, ReviewerBinding
        for i in range(3): self.store.local_api_failure(key=self.circuit.key,failure_id='route-open'+str(i),now_ms=i,details={'kind':'test'})
        owner=OwnerBinding('root-owner',str(Path(self.temp.name)))
        c=ExplicitCoordinator(self.store,bindings={'author-token':object()},artifact_dir=self.temp.name,
            identity_verifier=lambda _:True,transport_health=self.health,owner_bindings={'owner-token':owner},
            review_bindings={'review-token':ReviewerBinding('review-caller','worktree','parity')})
        client=TestClient(c.app())
        endpoint='/v1/recovery/retry-local-api'
        body={'command_id':'owner-retry'}
        self.assertEqual(client.post(endpoint,json=body,headers={'Authorization':'Bearer author-token'}).status_code,403)
        self.assertEqual(client.post(endpoint,json=body,headers={'Authorization':'Bearer review-token'}).status_code,403)
        self.assertEqual(client.post(endpoint,json=body,headers={'Authorization':'Bearer unknown'}).status_code,403)
        self.assertEqual(client.post(endpoint,json=body,headers={'Authorization':'Bearer owner-token'}).status_code,200)
        generation=self.circuit.snapshot()['generation']
        self.assertEqual(client.post(endpoint,json=body,headers={'Authorization':'Bearer owner-token'}).status_code,200)
        self.assertEqual(self.circuit.snapshot()['generation'],generation)
        self.assertEqual(client.post(endpoint,json={'command_id':'bad','server':self.server},headers={'Authorization':'Bearer owner-token'}).status_code,422)
        self.assertEqual(client.get('/v1/health',headers={'Authorization':'Bearer owner-token'}).json()['local_api']['state'],'BACKOFF')
        self.now[0]=1000; self.success_info=True
        self.health.tick()
        self.assertEqual(self.calls[-1],('GET','/api/info'))
        self.assertTrue(all(method=='GET' and path=='/api/info' for method,path in self.calls))


if __name__=='__main__': unittest.main()
