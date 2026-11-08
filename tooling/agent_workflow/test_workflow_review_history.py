import unittest
import workflow_review_history as history
from tooling.agent_workflow import gate_dispatch


def record(mid, stamp):
    return {'id': mid, 'type': 'user', 'time': {'created': stamp}}


class ReviewHistoryTests(unittest.TestCase):
    def test_full_scan_preserves_timestamp_jitter_and_reads_past_time_boundary(self):
        pages = [
            {'data': [record('system-event', 1792135736379), record('assistant-event', 1792135737495)],
             'cursor': {'next': 'older'}},
            {'data': [record('old-pending-workflow', 50)], 'cursor': {'next': None}},
        ]
        result = history.read_since(lambda *a: (200, pages.pop(0)), 's', 'id', 100, full_scan=True)
        self.assertEqual(['system-event', 'assistant-event', 'old-pending-workflow'],
                         [row['id'] for row in result])
        with self.assertRaisesRegex(history.HistoryUnavailable, 'not descending'):
            history.read_since(lambda *a: (200, {'data': [record('system-event', 1792135736379),
                                                           record('assistant-event', 1792135737495)],
                                                 'cursor': {'next': None}}), 's', 'id', 0)

    def test_full_scan_rejects_nonboolean_flag_and_preserves_structural_bounds(self):
        for value in (None, 0, 1, 'true'):
            with self.subTest(value=value), self.assertRaisesRegex(history.HistoryUnavailable, 'scan flag'):
                history.read_since(lambda *a: None, 's', 'id', 0, full_scan=value)
        malformed = {'data': [record('bad', True)], 'cursor': {'next': 'next'}}
        with self.assertRaisesRegex(history.HistoryUnavailable, 'timestamp'):
            history.read_since(lambda *a: (200, malformed), 's', 'id', 0, full_scan=True)
        with self.assertRaisesRegex(history.HistoryUnavailable, 'repeated'):
            history.read_since(lambda *a: (200, {'data': [record('x', 20)], 'cursor': {'next': 'same'}}),
                               's', 'id', 0, full_scan=True)
        count = [0]
        def endless(*args):
            count[0] += 1
            return 200, {'data': [record(str(count[0]), 20)], 'cursor': {'next': str(count[0])}}
        with self.assertRaisesRegex(history.HistoryUnavailable, 'page limit'):
            history.read_since(endless, 's', 'id', 0, max_pages=2, full_scan=True)
        self.assertEqual(2, count[0])

    def test_oversize_first_page_retries_same_cursor_with_smaller_page(self):
        calls = []
        def request(server, method, path):
            calls.append(path)
            if len(calls) == 1: raise gate_dispatch.DispatchReplyTooLarge('large')
            return 200, {'data': [record('new', 90)], 'cursor': {'next': None}}
        self.assertEqual(['new'], [row['id'] for row in history.read_since(request, 's', 'id', 100, page_size=10)])
        self.assertEqual(['?limit=10&order=desc', '?limit=5&order=desc'], [x.split('/message')[1] for x in calls])

    def test_oversize_cursor_page_retries_without_losing_or_duplicating_records(self):
        calls = []
        def request(server, method, path):
            calls.append(path)
            if len(calls) == 1:
                return 200, {'data': [record('new', 300)], 'cursor': {'next': 'older'}}
            if len(calls) == 2: raise gate_dispatch.DispatchReplyTooLarge('large')
            return 200, {'data': [record('old-pending', 90)], 'cursor': {'next': None}}
        result = history.read_since(request, 's', 'id', 100, page_size=10)
        self.assertEqual(['new', 'old-pending'], [row['id'] for row in result])
        self.assertEqual(['?limit=10&order=desc', '?limit=10&cursor=older', '?limit=5&cursor=older'],
                         [x.split('/message')[1] for x in calls])

    def test_other_request_errors_propagate_and_single_message_oversize_holds(self):
        error = OSError('unrelated')
        with self.assertRaises(OSError) as raised:
            history.read_since(lambda *a: (_ for _ in ()).throw(error), 's', 'id', 0)
        self.assertIs(error, raised.exception)
        with self.assertRaisesRegex(history.HistoryUnavailable, 'single-message'):
            history.read_since(lambda *a: (_ for _ in ()).throw(gate_dispatch.DispatchReplyTooLarge()), 's', 'id', 0, page_size=1)

    def test_resize_retries_consume_existing_page_budget(self):
        calls = []
        def request(*args):
            calls.append(args[-1])
            raise gate_dispatch.DispatchReplyTooLarge()
        with self.assertRaises(history.HistoryUnavailable):
            history.read_since(request, 's', 'id', 0, max_pages=2, page_size=10)
        self.assertEqual(['limit=10&order=desc', 'limit=5&order=desc'],
                         [path.split('/message?')[1] for path in calls])

    def test_pages_to_older_boundary_and_preserves_every_record(self):
        pages = [
            {'data': [record('new', 300), record('foreign', 200)], 'cursor': {'next': 'a/b&c'}},
            {'data': [record('boundary', 90)], 'cursor': {'next': 'ignored'}},
        ]
        calls = []
        def request(server, method, path, body=None):
            calls.append(path)
            return 200, pages.pop(0)
        result = history.read_since(request, 'http://fake', 's/id', 100)
        self.assertEqual([x['id'] for x in result], ['new', 'foreign', 'boundary'])
        self.assertTrue(calls[0].endswith('/s%2Fid/message?limit=50&order=desc'))
        self.assertTrue(calls[1].endswith('?limit=50&cursor=a%2Fb%26c'))
        self.assertNotIn('order=', calls[1])

    def test_configured_page_size_is_used_on_first_and_cursor_pages(self):
        pages = [{'data': [record('new', 300)], 'cursor': {'next': 'next'}},
                 {'data': [record('old', 90)], 'cursor': {'next': None}}]
        calls = []
        def request(server, method, path):
            calls.append(path)
            return 200, pages.pop(0)
        result = history.read_since(request, 'http://fake', 'sid', 100, page_size=10)
        self.assertEqual([x['id'] for x in result], ['new', 'old'])
        self.assertTrue(calls[0].endswith('?limit=10&order=desc'))
        self.assertTrue(calls[1].endswith('?limit=10&cursor=next'))

    def test_invalid_page_sizes_rejected(self):
        for size in (True, False, 0, 51, '10'):
            with self.subTest(size=size), self.assertRaises(history.HistoryUnavailable):
                history.read_since(lambda *a: None, 's', 'id', 0, page_size=size)

    def test_malformed_cursor_and_timestamp_rejected(self):
        for body in ({'data': [], 'cursor': None}, {'data': [record('x', True)], 'cursor': {}}):
            with self.subTest(body=body), self.assertRaises(history.HistoryUnavailable):
                history.read_since(lambda *a: (200, body), 's', 'id', 0)

    def test_repeated_cursor_rejected(self):
        def request(*args): return 200, {'data': [record('x', 20)], 'cursor': {'next': 'same'}}
        with self.assertRaises(history.HistoryUnavailable):
            history.read_since(request, 's', 'id', 10)

    def test_page_ceiling_fails_closed(self):
        # A fresh cursor each time proves the bound, rather than loop detection.
        count = [0]
        def request(*args):
            count[0] += 1
            return 200, {'data': [record(str(count[0]), 20)], 'cursor': {'next': str(count[0])}}
        with self.assertRaises(history.HistoryUnavailable):
            history.read_since(request, 's', 'id', 10, max_pages=2)
        self.assertEqual(count[0], 2)

    def test_complete_empty_history(self):
        self.assertEqual(history.read_since(lambda *a: (200, {'data': [], 'cursor': {}}), 's', 'id', 0), [])


if __name__ == '__main__':
    unittest.main()
