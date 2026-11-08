import functools
import json
import subprocess
import tempfile
import unittest
from unittest import mock
from pathlib import Path

import gate_dispatch
import transport_health
import workflow_review_transport
import workflow_transport_health

import test_workflow_review_transport as review_fixture


class CLIProcessAdapter:
    """Exercise gate_dispatch's actual subprocess boundary without a CLI process."""

    def __init__(self):
        self.api = review_fixture.FakeTransport()
        self.calls = []

    def __call__(self, argv, **kwargs):
        self.calls.append((argv, kwargs))
        request_index = argv.index('--server') + 2
        method, path = argv[request_index], argv[request_index + 1]
        body = json.loads(argv[argv.index('--data') + 1]) if '--data' in argv else None
        status, payload = self.api(argv[argv.index('--server') + 1], method, path, body)
        if status == 404:
            sid = path.rsplit('/', 1)[-1]
            return subprocess.CompletedProcess(argv, 1,
                json.dumps({'_tag': 'SessionNotFoundError', 'sessionID': sid,
                            'message': f'Session not found: {sid}'}),
                'HTTP 404 Not Found')
        return subprocess.CompletedProcess(argv, 0, json.dumps(payload), '')


class ReviewCLIBoundaryTests(unittest.TestCase):
    def test_actual_cli_request_path_treats_missing_session_404_as_absence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture, store, planner, task, binding = review_fixture.ReviewTransportTests().prepared(root)
            cli = CLIProcessAdapter()
            base_request = functools.partial(gate_dispatch._request,
                accepted_http_statuses=(404,))
            server = 'http://127.0.0.1:4096'
            circuit = workflow_transport_health.WorkflowTransportHealth(
                store, server, request=base_request)
            health = transport_health.TransportHealth(circuit, base_request)
            backend = workflow_review_transport.ReviewDispatchBackend(
                planner, server=server,
                agents={'implementation': 'workflow-reviewer-implementation'},
                request=health.request)

            wake = {'kind': 'TASK_READY', 'task_id': task['task_id']}
            with mock.patch.object(gate_dispatch.subprocess, 'run', side_effect=cli):
                self.assertEqual('ABSENT', backend.observe('wake', wake))
                self.assertEqual('APPLIED', backend.dispatch('wake', wake))
                self.assertEqual('APPLIED', backend.observe('wake', wake))
            self.assertEqual(1, len(cli.api.create_posts))
            self.assertEqual(1, len(cli.api.posts))
            self.assertEqual('DISPATCHING', store.get_task(task['task_id'])['state'])
            self.assertIsNone(store.get_task(task['task_id'])['result'])
            self.assertEqual(1, len(cli.api.posts))
            self.assertNotEqual('BACKOFF', circuit.snapshot()['state'])


if __name__ == '__main__':
    unittest.main()
