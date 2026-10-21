import json
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore
from workflow_transport_health import WorkflowTransportHealth


class Response:
    status=200
    def __init__(self,body=None): self.body=body or {'version':'1.2','pid':44,'urls':['http://127.0.0.1:4096'],'paths':{'tmp':'/tmp'}}
    def read(self): return json.dumps(self.body).encode()


class WorkflowTransportHealthTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.path=Path(self.temp.name)/'workflow.sqlite'
        self.store=WorkflowStore(self.path); self.url='http://127.0.0.1:4096'
    def tearDown(self): self.temp.cleanup()

    def test_three_failures_open_and_year_later_no_probe(self):
        h=WorkflowTransportHealth(self.store,self.url,opener=lambda *a,**k:Response())
        for i,t in enumerate((0,5000,20000)): h.observe_connection_failure(self.url,f'f{i}',t)
        self.assertEqual(h.snapshot()['state'],'OPEN')
        result=h.probe(99_999_999_999,'late')
        self.assertFalse(result['executed']); self.assertEqual(h.store.local_api_snapshot(h.key)['probes'],0)

    def test_atomic_persisted_permit_across_store_instances(self):
        h=WorkflowTransportHealth(self.store,self.url,opener=lambda *a,**k:Response())
        h.observe_connection_failure(self.url,'f',0)
        other=WorkflowTransportHealth(WorkflowStore(self.path),self.url,opener=lambda *a,**k:Response())
        self.assertFalse(h.probe(4999,'early')['executed'])
        self.assertTrue(h.probe(5000,'winner')['executed'])
        self.assertFalse(other.probe(5000,'loser')['executed'])
        self.assertEqual(self.store.local_api_snapshot(h.key)['probes'],1)

    def test_connected_is_local_only_and_auth_opens(self):
        h=WorkflowTransportHealth(self.store,self.url,opener=lambda *a,**k:Response())
        h.observe_connection_failure(self.url,'f',0)
        connected=h.probe(5000,'p1')
        self.assertEqual(connected['scope'],'LOCAL_API_ONLY')
        self.assertEqual(connected['inference_health'],'UNKNOWN'); self.assertFalse(connected['task_resumed'])

        class Auth:
            def __call__(self,*a,**k):
                from urllib.error import HTTPError
                raise HTTPError('url',401,'unauthorized',{},None)
        other=WorkflowTransportHealth(self.store,'http://127.0.0.1:4097',opener=Auth())
        other.observe_connection_failure(other.server,'auth-f',0)
        self.assertEqual(other.probe(5000,'auth-probe')['action'],'REPAIR_LOCAL_API')
        self.assertEqual(other.snapshot()['state'],'OPEN')

    def test_actual_flat_server_info_shape_and_nested_is_invalid(self):
        h=WorkflowTransportHealth(self.store,self.url,opener=lambda *a,**k:Response())
        h.observe_connection_failure(self.url,'f',0)
        self.assertEqual(h.probe(5000,'flat')['state'],'CONNECTED')

        other=WorkflowTransportHealth(self.store,'http://127.0.0.1:4097',opener=lambda *a,**k:Response({'data':{'version':'1.2','pid':44}}))
        other.observe_connection_failure(other.server,'nested-f',0)
        self.assertEqual(other.probe(5000,'nested')['state'],'BACKOFF')

    def test_existing_authenticated_request_adapter_is_used_for_get_only(self):
        calls=[]
        def request(server,method,path,timeout):
            calls.append((server,method,path,timeout)); return 200,{'version':'1.2','pid':44,'urls':[server],'paths':{'tmp':'/tmp'}}
        h=WorkflowTransportHealth(self.store,self.url,request=request)
        h.observe_connection_failure(self.url,'f',0)
        self.assertEqual(h.probe(5000,'adapter')['state'],'CONNECTED')
        self.assertEqual(calls,[(self.url,'GET','/api/info',2)])


if __name__=='__main__': unittest.main()
