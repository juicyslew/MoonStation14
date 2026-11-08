"""Public HTTP/MCP regression coverage for bounded large-log diagnostics."""
import asyncio
import hashlib
import json
import unittest
from pathlib import Path
from unittest import mock

import httpx


class LargeLogSDKTests(unittest.TestCase):
    def setUp(self):
        import test_workflow_coordinator as fixture_module
        self.fixture = fixture_module.CoordinatorHTTPTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        self.run = self.fixture.post().json()['response']
        run = self.fixture.store.get_run(self.run['run_id'])['run']
        self.run_record = run
        # More than the former source-size ceiling, with the interesting marker
        # beyond it. Newline-delimited records avoid partial-record ambiguity.
        marker = b'FINAL_NATIVE_FAILURE marker-after-sixteen-megabytes'
        self.raw = (b'x' * 8191 + b'\n') * ((16 * 1024 * 1024 // 8192) + 1) + b'Authorization: Bearer sentinel-secret\n\n' + marker + b'\n'
        self.marker = marker.decode()
        task_id = 'task_large_log_fixture'
        digest = hashlib.sha256(self.raw).hexdigest()
        artifact = {'artifact_id':'artifact-large-log','run_id':run['run_id'],
            'producer_task_id':task_id,'producer_id':task_id,'producer_kind':'TASK','type':'LOG',
            'path':str(self.fixture.root/'artifacts'/'immutable'/task_id/('log-'+digest)),
            'sha256':digest,'size_bytes':len(self.raw),'immutable':True}
        path = Path(artifact['path']); path.parent.mkdir(parents=True); path.write_bytes(self.raw)
        task = {'task_id':task_id,'kind':'CHECK','state':'COMPLETED',
            'result':{'decision':'FAIL','findings':[],'artifact_refs':[artifact['artifact_id']]},'error_report':None}
        self._original_tasks = self.fixture.store.list_tasks
        self._original_artifact = self.fixture.store.get_artifact
        self.fixture.store.list_tasks = lambda rid, generation: [task] if rid == run['run_id'] and generation == run['current_attempt'] else []
        self.fixture.store.get_artifact = lambda ref: {'artifact':artifact,'input_digest':run['input_digest']} if ref == artifact['artifact_id'] else None
        self.addCleanup(self._restore_store)
        self.artifact_id = artifact['artifact_id']

    def _restore_store(self):
        self.fixture.store.list_tasks = self._original_tasks
        self.fixture.store.get_artifact = self._original_artifact

    def test_http_large_log_tail_and_pagination_are_bounded_and_redacted(self):
        f = self.fixture
        base = f'/v1/status?run_id={self.run_record["run_id"]}&include_diagnostics=true&log_artifact_id={self.artifact_id}'
        headers = {'Authorization':'Bearer secret'}
        tail = f.client.get(base, headers=headers)
        self.assertEqual(tail.status_code, 200)
        log = tail.json()['diagnostics']['logs'][0]
        self.assertTrue(log.get('available'), log); self.assertEqual(log['offset_basis'],'sanitized_utf8')
        self.assertIn(self.marker, log['text']); self.assertNotIn('sentinel-secret', log['text'])
        self.assertLessEqual(len(tail.content), 64 * 1024)
        self.assertLessEqual(len(log['text'].encode()), 8192)
        high = len(self.raw[:16 * 1024 * 1024 + 8192].decode('utf-8'))
        page = f.client.get(base + '&log_offset=' + str(high), headers=headers).json()['diagnostics']['logs'][0]
        self.assertEqual(page['offset'], high); self.assertIn(self.marker, page['text'])
        self.assertNotIn('sentinel-secret', page['text'])
        bad = ('-1', str(2**63), 'not-an-offset')
        for offset in bad:
            response = f.client.get(base + '&log_offset=' + offset, headers=headers)
            self.assertEqual(response.status_code, 400)

    def test_real_author_mcp_status_schema_and_call_accept_large_offset(self):
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        from workflow_coordinator_http_mcp import PROFILE_PATHS
        f = self.fixture
        app = f.coordinator.app()
        def factory(headers=None, timeout=None, auth=None):
            return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                base_url='http://127.0.0.1:18443',headers={**(headers or {}),'Origin':'http://127.0.0.1:18443'})
        high = len(self.raw[:16 * 1024 * 1024 + 8192].decode('utf-8'))
        async def scenario():
            async with app.router.lifespan_context(app):
                async with streamablehttp_client('http://127.0.0.1:18443'+PROFILE_PATHS['author-static'],
                        {'Authorization':'Bearer secret'},httpx_client_factory=factory) as (reader, writer, _):
                    async with ClientSession(reader, writer) as client:
                        await client.initialize()
                        status = next(t for t in (await client.list_tools()).tools if t.name == 'status')
                        self.assertEqual(status.inputSchema['properties']['log_offset']['type'],'integer')
                        result = await client.call_tool('status', {'run_id':self.run_record['run_id'],
                            'include_diagnostics':True,'log_artifact_id':self.artifact_id,'log_offset':high})
                        self.assertFalse(result.isError, result.content[0].text)
                        text = result.content[0].text
                        decoded = json.loads(text)
                        log = decoded['diagnostics']['logs'][0]
                        self.assertIn(self.marker,log['text']); self.assertNotIn('sentinel-secret',text)
                        self.assertLessEqual(len(text.encode()),64*1024)
        asyncio.run(scenario())

    def test_http_reports_integrity_mismatch_without_exposing_page(self):
        f = self.fixture
        artifact = f.store.get_artifact(self.artifact_id)['artifact']
        path = Path(artifact['path'])
        original = path.read_bytes()
        # Same length, but corruption before the requested page must still fail
        # full-artifact hash verification rather than emit unverified text.
        path.write_bytes(b'Y' + original[1:])
        response = f.client.get(f'/v1/status?run_id={self.run_record["run_id"]}&include_diagnostics=true&log_artifact_id={self.artifact_id}&log_offset=16777216',headers={'Authorization':'Bearer secret'})
        self.assertEqual(response.status_code,200)
        log = response.json()['diagnostics']['logs'][0]
        self.assertEqual(log.get('reason'),'integrity_mismatch',log)
        self.assertNotIn('text',log)


if __name__ == '__main__': unittest.main()
