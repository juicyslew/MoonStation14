"""Same-origin page and bounded owner-authenticated board event route tests."""
import asyncio
import json
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_coordinator
import workflow_store
from starlette.testclient import TestClient


def _owner_coordinator(store, worktree):
    return workflow_coordinator.ExplicitCoordinator(store,bindings={'unused':object()},
        artifact_dir=Path(store.path).parent/'artifacts',identity_verifier=lambda *_:True,
        owner_bindings={'board-secret':workflow_coordinator.OwnerBinding('board-owner',str(worktree))})


async def _asgi_get(app, path, authorization=None, *, disconnect_after_first_body=False, method='GET'):
    messages=[]; first=True; disconnected=asyncio.Event()
    headers=[] if authorization is None else [(b'authorization',authorization.encode())]
    scope={'type':'http','asgi':{'version':'3.0'},'http_version':'1.1','method':method,'scheme':'http',
        'path':path,'raw_path':path.encode(),'query_string':b'','root_path':'','headers':headers,
        'client':('127.0.0.1',12345),'server':('testserver',80)}
    async def receive():
        nonlocal first
        if first:
            first=False
            return {'type':'http.request','body':b'','more_body':False}
        await disconnected.wait()
        return {'type':'http.disconnect'}
    async def send(message):
        messages.append(message)
        if disconnect_after_first_body and message['type']=='http.response.body' and message.get('body'):
            disconnected.set()
    await app(scope,receive,send)
    status=next(m['status'] for m in messages if m['type']=='http.response.start')
    response_headers=dict(next(m['headers'] for m in messages if m['type']=='http.response.start'))
    body=b''.join(m.get('body',b'') for m in messages if m['type']=='http.response.body')
    return status,response_headers,body


class BoardHTTPTests(unittest.TestCase):
    def test_public_status_page_is_generic_empty_and_uncredentialed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'state.sqlite')
            coordinator=_owner_coordinator(store,root/'configured-private-worktree')
            response=TestClient(coordinator.app(),base_url='http://127.0.0.1:59180',client=('127.0.0.1',12345)).get('/integration-status?worktree=attacker-selected')
            self.assertEqual(200,response.status_code)
            self.assertEqual('no-store',response.headers['cache-control'])
            self.assertEqual('no-referrer',response.headers['referrer-policy'])
            self.assertIn('"available": false',response.text)
            self.assertIn('"worktree": ""',response.text)
            for private in ('board-secret','configured-private-worktree','attacker-selected'):
                self.assertNotIn(private,response.text)

    def test_sse_owner_auth_initial_and_later_revision_frames_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'state.sqlite')
            coordinator=_owner_coordinator(store,root/'repo'); client=TestClient(coordinator.app())
            self.assertEqual(403,client.get('/v1/board/events').status_code)
            self.assertEqual(403,client.get('/v1/board/events',headers={'Authorization':'Bearer author-token'}).status_code)
            async def collect():
                return await _asgi_get(coordinator.app(),'/v1/board/events','Bearer board-secret',disconnect_after_first_body=True)
            status,headers,body=asyncio.run(collect())
            self.assertEqual(200,status)
            self.assertEqual('no-store',headers[b'cache-control'].decode())
            self.assertEqual('text/event-stream; charset=utf-8',headers[b'content-type'].decode())
            self.assertEqual(b'event: board-revision\ndata: {"revision":0,"workflow_revision":0}\n\n',body)
            self.assertEqual(0,coordinator._board_subscribers)

            async def next_two():
                coordinator.notify_committed()
                stream=coordinator._board_event_stream()
                first=await anext(stream)
                coordinator.notify_committed()
                second=await asyncio.wait_for(anext(stream),1)
                await stream.aclose()
                return first,second
            first,second=asyncio.run(next_two())
            self.assertEqual(b'event: board-revision\ndata: {"revision":1,"workflow_revision":1}\n\n',first)
            self.assertEqual(b'event: board-revision\ndata: {"revision":2,"workflow_revision":2}\n\n',second)
            self.assertNotIn(b'worktree',first+second)
            self.assertNotIn(b'token',first+second)

    def test_incident_projection_delivery_wakes_page_before_acknowledgement(self):
        class SavedStore:
            path='unused.sqlite'
            def __init__(self): self.coordinator=None; self.ack_revision=None
            def acknowledge_outbox(self,event_id,generation): self.ack_revision=self.coordinator._board_revision
        store=SavedStore(); coordinator=_owner_coordinator(store,Path('unused'))
        store.coordinator=coordinator
        coordinator._deliver({'event_id':'event_page_projection','claim_generation':1,
            'payload':{'kind':'INCIDENT_PAGE_PROJECTION'}})
        self.assertEqual(1,store.ack_revision)

    def test_slow_board_reads_are_off_loop_and_dont_delay_health(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'state.sqlite')
            coordinator=_owner_coordinator(store,root/'repo'); app=coordinator.app()
            entered=threading.Event(); release=threading.Event()
            original=workflow_store.WorkflowStore.readonly_board_snapshot
            def blocked_snapshot(*args,**kwargs):
                entered.set()
                if not release.wait(3): raise TimeoutError('test snapshot release timed out')
                return original(*args,**kwargs)
            async def exercise():
                with patch.object(workflow_store.WorkflowStore,'readonly_board_snapshot',side_effect=blocked_snapshot):
                    board_task=asyncio.create_task(_asgi_get(app,'/v1/board/snapshot','Bearer board-secret'))
                    self.assertTrue(await asyncio.to_thread(entered.wait,1),'snapshot worker never entered')
                    health=await asyncio.wait_for(_asgi_get(app,'/v1/health','Bearer board-secret'),.5)
                    self.assertEqual(200,health[0])
                    release.set()
                    self.assertEqual(200,(await asyncio.wait_for(board_task,2))[0])
            asyncio.run(exercise())

            class SlowIncidentStore:
                path=store.path
                def outbox_summary(self): return {}
                def get_incident(self,_ident):
                    entered.set()
                    if not release.wait(3): raise TimeoutError('incident read release timed out')
                    return {'incident':{'run_id':'run_scope'}}
                def get_run(self,_run): return {'run':{'author':{'worktree':str(root/'repo')}}}
                def mark_incident_seen(self,ident): return {'incident':{'incident_id':ident},'seen_ms':1,'resolved_ms':None}
            slow=SlowIncidentStore(); second=_owner_coordinator(slow,root/'repo'); app2=second.app()
            entered.clear(); release.clear()
            async def exercise_seen():
                seen_task=asyncio.create_task(_asgi_get(app2,'/v1/board/incidents/incident_slow/seen',
                    'Bearer board-secret',method='POST'))
                self.assertTrue(await asyncio.to_thread(entered.wait,1),'incident DB read never entered')
                health=await asyncio.wait_for(_asgi_get(app2,'/v1/health','Bearer board-secret'),.5)
                self.assertEqual(200,health[0])
                release.set()
                self.assertEqual(200,(await asyncio.wait_for(seen_task,2))[0])
            asyncio.run(exercise_seen())

    def test_stream_disconnect_does_not_stop_coordinator_or_native_work(self):
        from test_workflow_native_execution import admit_native_fixture
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); marker=root/'producer-started'
            case,store,_receipt,task_id=admit_native_fixture(root,caller='board-stream',key='board-stream',
                timeline=marker,delay=5)
            backend=__import__('workflow_native_execution').NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
            coordinator=_owner_coordinator(store,case.repo); proc=None
            try:
                backend.dispatch('board-stream-native',{'task_id':task_id}); proc=backend.workers[task_id]
                deadline=time.monotonic()+8
                while time.monotonic()<deadline and not marker.exists(): time.sleep(.02)
                self.assertTrue(marker.exists(),'native worker did not start')
                coordinator.start()
                status,_,body=asyncio.run(_asgi_get(coordinator.app(),'/v1/board/events',
                    'Bearer board-secret',disconnect_after_first_body=True))
                self.assertEqual(200,status)
                self.assertTrue(coordinator._thread and coordinator._thread.is_alive())
                self.assertFalse(coordinator._stop.is_set())
                self.assertIsNone(proc.poll(),'closing the SSE subscriber terminated the owned native worker')
                self.assertEqual(0,coordinator._board_subscribers)
            finally:
                coordinator.stop()
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=8)


if __name__=='__main__': unittest.main()
