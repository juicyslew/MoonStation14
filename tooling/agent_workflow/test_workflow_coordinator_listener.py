import unittest
import socket
import struct
import threading
import time
import urllib.request

import uvicorn

import workflow_coordinator


class CoordinatorListenerLoopTests(unittest.TestCase):
    def test_uvicorn_uses_selector_loop_on_windows(self):
        if workflow_coordinator.os.name != 'nt':
            self.skipTest('Windows-specific listener loop')
        config = uvicorn.Config(lambda scope, receive, send: None,
                                loop=workflow_coordinator._uvicorn_loop_choice())
        self.assertIs(config.get_loop_factory(), __import__('asyncio').SelectorEventLoop)

    def test_default_loop_choice_is_unchanged_off_windows(self):
        if workflow_coordinator.os.name == 'nt':
            self.skipTest('non-Windows default selection')
        self.assertEqual(workflow_coordinator._uvicorn_loop_choice(), 'auto')

    def test_windows_listener_survives_abortive_and_partial_clients(self):
        if workflow_coordinator.os.name != 'nt':
            self.skipTest('Windows-specific IOCP accept regression')

        async def app(scope, receive, send):
            if scope['type'] == 'http':
                await send({'type': 'http.response.start', 'status': 200, 'headers': []})
                await send({'type': 'http.response.body', 'body': b'ok'})

        config = uvicorn.Config(app, host='127.0.0.1', port=0,
                                loop=workflow_coordinator._uvicorn_loop_choice(),
                                log_level='critical')
        server = uvicorn.Server(config)
        thread = threading.Thread(target=server.run, daemon=True)
        thread.start()
        try:
            deadline = time.monotonic() + 5
            while not server.started and time.monotonic() < deadline:
                time.sleep(.01)
            self.assertTrue(server.started, 'Uvicorn listener did not start')
            port = server.servers[0].sockets[0].getsockname()[1]
            for _ in range(8):
                client = socket.create_connection(('127.0.0.1', port), timeout=1)
                if _ % 2:
                    client.sendall(b'GET / HTTP/1.1\r\nHost: localhost\r\n')
                client.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER,
                                  struct.pack('hh', 1, 0))
                client.close()
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/health', timeout=2) as response:
                self.assertEqual((response.status, response.read()), (200, b'ok'))
        finally:
            server.should_exit = True
            thread.join(timeout=3)
            self.assertFalse(thread.is_alive(), 'Uvicorn listener thread leaked')
