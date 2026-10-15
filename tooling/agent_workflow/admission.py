"""Small fail-closed guard for an explicitly maintained workflow intake hold."""
import json
from pathlib import Path


def maintenance(root, candidate_id=None, *, admitted=False):
    """Return None when intake is open, otherwise a JSON-safe held result."""
    path = Path(root) / 'gate-maintenance.json'
    if not path.exists():
        return None
    note = 'Maintenance hold is active; continue scoped work and wait for explicit resume.'
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(data, dict) or not isinstance(data.get('owner'), str) or not data['owner'].strip():
            raise ValueError('expected non-empty owner')
        allowed = data.get('allowed_candidate_ids', [])
        if not isinstance(allowed, list) or any(not isinstance(x, str) for x in allowed):
            raise ValueError('allowed_candidate_ids must be an array of strings')
        note = str(data.get('note') or note)
        if admitted or candidate_id in allowed:
            return None
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        note = f'Maintenance hold is malformed; intake fails closed ({exc}).'
    return {'status': 'held', 'candidate_id': candidate_id, 'note': note}
