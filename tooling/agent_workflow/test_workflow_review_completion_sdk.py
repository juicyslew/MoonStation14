"""Public reviewer SDK regression: prose is not an accepted review result."""
import asyncio
import hashlib
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
import test_workflow_review_retry as retry_fixture
import workflow_review_retry
import workflow_store


class ReviewerCompletionSDKTests(unittest.TestCase):
    def test_prose_only_ended_turn_does_not_complete_task_before_sdk_result(self):
        from mcp import ClientSession
        from mcp.client.stdio import StdioServerParameters, stdio_client
        import uvicorn

        fixture = reviewer_fixture.ReviewerAPITests()
        fixture.setUp()
        sock = socket.socket()
        sock.bind(('127.0.0.1', 0))
        sock.listen()
        endpoint = f'http://127.0.0.1:{sock.getsockname()[1]}'
        server = uvicorn.Server(uvicorn.Config(fixture.client.app, log_level='error', lifespan='off'))
        thread = threading.Thread(target=server.run, kwargs={'sockets': [sock]}, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as td:
                td = Path(td)
                token = td / 'token'
                token.write_text('review-token', encoding='utf-8')
                result_path = fixture.root / 'sdk-review-result.json'
                result_path.write_text(json.dumps(fixture.result()), encoding='utf-8')
                script = Path(__file__).with_name('workflow_coordinator_mcp.py').resolve()
                env = os.environ.copy()
                env['PYTHONPATH'] = str(script.parent) + os.pathsep + env.get('PYTHONPATH', '')
                env['PYTHONDONTWRITEBYTECODE'] = '1'
                params = StdioServerParameters(command=sys.executable,
                    args=[str(script), '--profile', 'reviewer', '--repo', str(fixture.root),
                          '--endpoint', endpoint, '--token-file', str(token)], env=env)

                async def scenario():
                    async with asyncio.timeout(30):
                        async with stdio_client(params) as (reader, writer):
                            async with ClientSession(reader, writer) as sdk:
                                await sdk.initialize()
                                tools = {tool.name for tool in (await sdk.list_tools()).tools}
                                self.assertTrue({'claim_task', 'complete_review', 'status'} <= tools)
                                task = fixture.task
                                sid = task['assigned_session_id']
                                claimed = await sdk.call_tool('claim_task', {'task_id':task['task_id'],'session_id':sid})
                                self.assertFalse(getattr(claimed, 'isError', False), repr(claimed))
                                self.assertTrue(json.loads(claimed.content[0].text)['accepted'])

                                # The original bound prompt plus a completed assistant turn is the
                                # positive stop evidence consumed by the production continuation owner.
                                task = fixture.task
                                assignment = fixture.planner.build_assignment(task['task_id'])
                                envelope = assignment['body']['envelope']
                                event = fixture.store.get_event(envelope['event_id'])
                                sid = task['assigned_session_id']
                                prompt_id = 'msg_' + hashlib.sha256(
                                    (task['task_id'] + assignment['body_sha256']).encode()).hexdigest()[:40]
                                original = {'id': prompt_id, 'type': 'user',
                                    'time': {'created': event['created_ms'] + 1},
                                    'text': workflow_store.canonical_json(assignment['body']),
                                    'metadata': {'review_event_id': envelope['event_id'], 'task_id': task['task_id'],
                                                 'body_sha256': assignment['body_sha256']}}
                                ended = {'id': 'assistant-ended', 'type': 'assistant',
                                    'content': [{'type': 'text', 'text': 'FAIL: prose only; no accepted SDK result'}],
                                    'finish': 'stop',
                                    'time': {'created': event['created_ms'] + 2,
                                             'completed': event['created_ms'] + 3}}
                                idle = {'id': 'idle-after-ended-turn', 'type': 'idle',
                                    'outcome': 'succeeded',
                                    'time': {'created': event['created_ms'] + 4}}
                                session = fixture.fake_transport.sessions[sid]
                                api = retry_fixture.EmptyCheckpointAPI(sid, session, [original, ended, idle])
                                owner = workflow_review_retry.ReviewRetryAdapter(fixture.planner,
                                    server='http://127.0.0.1:1234', request=api,
                                    agents={'implementation': 'workflow-reviewer-implementation'})
                                deadline = fixture.store.get_task(task['task_id'])['deadline_ms']
                                wake = owner.continue_unsubmitted(task['task_id'])
                                self.assertEqual('RESUMED', wake['status'], wake)
                                self.assertEqual(1, api.control_posts)
                                self.assertEqual(deadline, fixture.store.get_task(task['task_id'])['deadline_ms'])
                                self.assertEqual('CLAIMED', fixture.store.get_task(task['task_id'])['state'])
                                self.assertIsNone(fixture.store.get_task(task['task_id'])['result'])
                                again = owner.continue_unsubmitted(task['task_id'])
                                self.assertNotEqual('RESUMED', again['status'], again)
                                self.assertEqual(1, api.control_posts)
                                # Only the separately submitted, contract-valid SDK result is a verdict.
                                result=fixture.result()
                                for field in ('contract_version','run_id','evaluation_generation','assignment_generation','input_digest'): result.pop(field)
                                completed = await sdk.call_tool('complete_review',{'result':result,'session_id':sid})
                                self.assertFalse(getattr(completed, 'isError', False), repr(completed))
                                self.assertTrue(json.loads(completed.content[0].text)['accepted'])
                                saved = fixture.store.get_task(task['task_id'])
                                self.assertEqual('COMPLETED', saved['state'])
                                self.assertEqual('PASS', saved['result']['decision'])
                                self.assertEqual(1, api.control_posts)

                deadline = time.monotonic() + 5
                while not server.started and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertTrue(server.started)
                asyncio.run(scenario())
        finally:
            server.should_exit = True
            thread.join(timeout=5)
            sock.close()
            fixture.tearDown()


if __name__ == '__main__':
    unittest.main()
