import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import workflow_coordinator
import workflow_store
from workflow_store_test_support import admission_records
from starlette.testclient import TestClient


class LocalOwnerUITests(unittest.TestCase):
    def app(self, root, owners=1):
        store=workflow_store.WorkflowStore(root/'state.sqlite')
        bindings={} if owners==0 else {'secret-'+str(i):workflow_coordinator.OwnerBinding('owner-'+str(i),str(root/'repo')) for i in range(owners)}
        coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'unused':object()},artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,owner_bindings=bindings)
        return coordinator,store

    def test_page_mints_private_cookie_and_owner_routes_accept_cookie_not_bearer(self):
        with tempfile.TemporaryDirectory() as tmp:
            coordinator,_=self.app(Path(tmp)); client=TestClient(coordinator.app(),base_url='http://127.0.0.1:59231',client=('127.0.0.1',12345))
            page=client.get('/integration-status')
            self.assertEqual(200,page.status_code)
            self.assertEqual('no-store',page.headers['cache-control'])
            self.assertEqual('no-referrer',page.headers['referrer-policy'])
            self.assertIn("frame-ancestors 'none'",page.headers['content-security-policy'])
            cookie=page.headers['set-cookie'].lower()
            self.assertIn('httponly',cookie); self.assertIn('samesite=strict',cookie)
            self.assertNotIn('secret-0',page.text); self.assertNotIn('type="password"',page.text)
            self.assertNotIn('owner-token',page.text); self.assertNotIn('Authorization',page.text)
            self.assertEqual(200,client.get('/v1/board/snapshot').status_code)
            async def one_frame():
                yield b'event: board-revision\ndata: {"revision":0}\n\n'
            with patch.object(coordinator,'_board_event_stream',one_frame):
                self.assertEqual(200,client.get('/v1/board/events').status_code)
            self.assertEqual(403,client.get('/v1/board/snapshot',headers={'Authorization':'Bearer invalid'}).status_code)
            self.assertEqual(200,client.get('/v1/board/snapshot',headers={'Authorization':'Bearer secret-0'}).status_code)
            self.assertEqual(403,client.post('/v1/submit',json={},headers={'Origin':'http://127.0.0.1:59231'}).status_code)
            self.assertEqual(403,client.get('/v1/review/status?task_id=x').status_code)
            self.assertIn(client.get('/mcp/author').status_code,(401,403))

    def test_rejects_foreign_host_wrong_cookie_and_cookie_cross_origin_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            coordinator,_=self.app(Path(tmp)); client=TestClient(coordinator.app(),base_url='http://127.0.0.1:59232',client=('127.0.0.1',12345))
            self.assertEqual(403,client.get('/integration-status',headers={'Host':'evil.example'}).status_code)
            page=client.get('/integration-status'); cookie=page.cookies.get('ms14_owner_ui')
            self.assertEqual(403,client.get('/v1/board/snapshot',headers={'Cookie':'ms14_owner_ui=wrong'}).status_code)
            self.assertEqual(403,client.get('/v1/board/snapshot',headers={'Cookie':'ms14_owner_ui='+cookie,'Authorization':'Bearer nope'}).status_code)
            foreign=TestClient(coordinator.app(),base_url='http://127.0.0.1:59232',client=('192.0.2.1',12345))
            self.assertEqual(403,foreign.get('/integration-status').status_code)
            headers={'Cookie':'ms14_owner_ui='+cookie,'Origin':'http://127.0.0.1:59233','Content-Type':'application/json'}
            self.assertEqual(403,client.post('/v1/board/incidents/not-real/seen',headers=headers,json={'command_id':'x'}).status_code)
            self.assertEqual(403,client.post('/v1/board/incidents/not-real/seen',headers={'Cookie':'ms14_owner_ui='+cookie,'Content-Type':'application/json'},json={'command_id':'x'}).status_code)
            self.assertEqual(403,client.get('/v1/board/snapshot',headers={'Cookie':'ms14_owner_ui='+cookie,'Sec-Fetch-Site':'same-site'}).status_code)

    def test_same_origin_cookie_action_uses_existing_owner_service_binding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); repo=root/'repo'; repo.mkdir()
            store=workflow_store.WorkflowStore(root/'state.sqlite')
            request,run,task,event=admission_records('local-owner-ui')
            request['author']['worktree']=str(repo)
            store.submit(caller='test-author',request=request,run=run,first_task=task,outbox_event=event,logical_task_key='initial')
            coordinator=workflow_coordinator.ExplicitCoordinator(store,
                bindings={'unused':workflow_coordinator.CoordinatorBinding('test-author',SimpleNamespace(
                    repo=str(repo),authenticated_author={'session_id':'author-session'}))},
                artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                owner_bindings={'secret-0':workflow_coordinator.OwnerBinding('owner-0',str(repo))})
            calls=[]
            class OwnerService:
                def decide(self,decision,**kwargs):
                    calls.append((decision,kwargs)); return {'status':'RECORDED'}
            coordinator.owner_service=OwnerService()
            client=TestClient(coordinator.app(),base_url='http://127.0.0.1:59235',client=('127.0.0.1',12345))
            page=client.get('/integration-status'); cookie=page.cookies.get('ms14_owner_ui')
            payload={'decision':{'decision':'ACCEPT','command_id':'test-command','run_id':run['run_id']},'displayed_result_id':'r','displayed_result_digest':'d'}
            headers={'Cookie':'ms14_owner_ui='+cookie,'Origin':'http://127.0.0.1:59235','Sec-Fetch-Site':'same-origin'}
            response=client.post('/v1/owner/decision',headers=headers,json=payload)
            self.assertEqual(200,response.status_code)
            self.assertEqual(1,len(calls))
            self.assertEqual({'session_id':None,'worktree':str(repo),'role':'owner'},calls[0][0]['owner'])
            missing={**payload,'decision':{k:v for k,v in payload['decision'].items() if k!='run_id'}}
            self.assertEqual(400,client.post('/v1/owner/decision',headers=headers,json=missing).status_code)
            self.assertEqual(1,len(calls))
            self.assertEqual(403,client.post('/v1/owner/decision',headers={**headers,'Origin':'http://localhost:59235'},json=payload).status_code)

    def test_zero_and_multiple_owner_configuration_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for count in (0,2):
                (root/str(count)).mkdir(exist_ok=True)
                coordinator,_=self.app(root/str(count),count)
                response=TestClient(coordinator.app(),base_url='http://127.0.0.1:59234',client=('127.0.0.1',12345)).get('/integration-status')
                self.assertEqual(503,response.status_code)


if __name__=='__main__': unittest.main()
