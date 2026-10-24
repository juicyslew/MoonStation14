import asyncio
import json
import os
import socket
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path

import test_workflow_reviewer_api as reviewer_fixture


class ReviewerSDKTests(unittest.TestCase):
    def test_official_sdk_claim_and_complete_review(self):
        from mcp import ClientSession
        from mcp.client.stdio import StdioServerParameters, stdio_client
        import uvicorn

        fixture=reviewer_fixture.ReviewerAPITests(); fixture.setUp()
        root=fixture.root
        coordinator=fixture.client.app
        # Recover the configured ASGI coordinator from the test client's router.
        app=fixture.client.app
        sock=socket.socket(); sock.bind(('127.0.0.1',0)); sock.listen()
        endpoint=f'http://127.0.0.1:{sock.getsockname()[1]}'
        server=uvicorn.Server(uvicorn.Config(app,log_level='error',lifespan='off'))
        thread=threading.Thread(target=server.run,kwargs={'sockets':[sock]},daemon=True); thread.start()
        with tempfile.TemporaryDirectory() as td:
            td=Path(td); token=td/'token'; token.write_text('review-token',encoding='utf-8')
            result=fixture.result(); result_path=root/'review-result.json'; result_path.write_text(json.dumps(result),encoding='utf-8')
            script=Path(__file__).with_name('workflow_coordinator_mcp.py').resolve()
            env=os.environ.copy(); env['PYTHONPATH']=str(script.parent)+os.pathsep+env.get('PYTHONPATH',''); env['PYTHONDONTWRITEBYTECODE']='1'
            params=StdioServerParameters(command=sys.executable,args=[str(script),'--profile','reviewer','--repo',str(root),'--endpoint',endpoint,'--token-file',str(token)],env=env)
            async def scenario():
                async with asyncio.timeout(30):
                    async with stdio_client(params) as (reader,writer):
                        async with ClientSession(reader,writer) as session:
                            await session.initialize(); catalog=await session.list_tools()
                            self.assertEqual({'claim_task','complete_review','report_error','status'},{t.name for t in catalog.tools})
                            task=fixture.task
                            sid=task['assigned_session_id']
                            claim={'contract_version':1,'command_id':'claim-'+task['task_id'],**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},'session_id':sid}
                            claim_reply=await session.call_tool('claim_task',claim)
                            self.assertFalse(getattr(claim_reply,'isError',False),repr(claim_reply))
                            self.assertTrue(claim_reply.content, repr(claim_reply))
                            response=json.loads(claim_reply.content[0].text)
                            self.assertTrue(response['accepted'],response)
                            complete_reply=await session.call_tool('complete_review',{'result_path':str(result_path),'session_id':sid})
                            self.assertFalse(getattr(complete_reply,'isError',False),repr(complete_reply))
                            completed=json.loads(complete_reply.content[0].text)
                            self.assertTrue(completed['accepted'],completed)
                            status_reply=await session.call_tool('status',{'task_id':task['task_id'],'session_id':sid})
                            self.assertFalse(getattr(status_reply,'isError',False),repr(status_reply))
                            status=json.loads(status_reply.content[0].text)
                            self.assertEqual('COMPLETED',status['task']['state'])
                            self.assertEqual('not_checked',status['freshness'])
                            self.assertTrue(any(a['type']=='CHECK_RECEIPT' and a['path'] for a in status['artifacts']),status)
            try:
                deadline=time.monotonic()+5
                while not server.started and time.monotonic()<deadline: time.sleep(.01)
                self.assertTrue(server.started); asyncio.run(scenario())
                self.assertEqual('COMPLETED',fixture.store.get_task(fixture.task['task_id'])['state'])
            finally:
                server.should_exit=True; thread.join(timeout=5); sock.close(); fixture.tearDown()


if __name__=='__main__': unittest.main()
