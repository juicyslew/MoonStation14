"""Bounded complete message-history reads for review-session verification."""
import urllib.parse


class HistoryUnavailable(ValueError):
    """History could not be proven complete and well-formed."""


def read_since(request, server, sid, lower_bound_ms, max_pages=100, page_size=50, full_scan=False):
    """Return original records to the older boundary, or exhaust cursors when full_scan is true."""
    if isinstance(lower_bound_ms, bool) or not isinstance(lower_bound_ms, int):
        raise HistoryUnavailable('invalid history lower bound')
    if isinstance(max_pages, bool) or not isinstance(max_pages, int) or max_pages < 1:
        raise HistoryUnavailable('invalid history page bound')
    if isinstance(page_size, bool) or not isinstance(page_size, int) or not 1 <= page_size <= 50:
        raise HistoryUnavailable('invalid history page size')
    if not isinstance(full_scan, bool):
        raise HistoryUnavailable('invalid full history scan flag')
    escaped = urllib.parse.quote(sid, safe='')
    records = []
    cursor = None
    used_cursors = set()
    for page_num in range(max_pages):
        query = 'limit=' + str(page_size) + '&cursor=' + urllib.parse.quote(cursor, safe='') if cursor is not None else 'limit=' + str(page_size) + '&order=desc'
        try:
            status, body = request(server, 'GET', f'/api/session/{escaped}/message?{query}')
        except Exception as exc:
            if getattr(exc, 'code', None) != 'REPLY_TOO_LARGE':
                raise
            if page_size <= 1:
                raise HistoryUnavailable('single-message history reply exceeds limit') from exc
            page_size = max(1, page_size // 2)
            continue
        if status != 200 or not isinstance(body, dict) or not isinstance(body.get('data'), list):
            raise HistoryUnavailable('message history unavailable')
        paging = body.get('cursor')
        if not isinstance(paging, dict):
            raise HistoryUnavailable('message history cursor malformed')
        page = body['data']
        times = []
        for record in page:
            if not isinstance(record, dict):
                raise HistoryUnavailable('message history record malformed')
            stamp = record.get('time', {}).get('created') if isinstance(record.get('time'), dict) else None
            if isinstance(stamp, bool) or not isinstance(stamp, int):
                raise HistoryUnavailable('message history timestamp malformed')
            times.append(stamp)
        if not full_scan and any(times[i] < times[i + 1] for i in range(len(times) - 1)):
            raise HistoryUnavailable('message history page is not descending')
        records.extend(page)
        if not full_scan and times and times[-1] < lower_bound_ms:
            return records
        next_cursor = paging.get('next')
        if next_cursor is None:
            return records
        if not isinstance(next_cursor, str) or not next_cursor:
            raise HistoryUnavailable('message history cursor malformed')
        if next_cursor in used_cursors or next_cursor == cursor:
            raise HistoryUnavailable('message history cursor repeated')
        used_cursors.add(next_cursor)
        cursor = next_cursor
    raise HistoryUnavailable('message history page limit exceeded')
