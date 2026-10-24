"""Shared immutable TASK artifact writer/registrar."""
import hashlib
import os
import re
from pathlib import Path

try:
    import workflow_submission
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_submission


def register_bytes(store, task, artifact_dir, kind, data):
    data = bytes(data)
    sha = hashlib.sha256(data).hexdigest()
    path = workflow_submission._write_immutable(
        artifact_dir / 'immutable' / task['task_id'] / (kind.lower() + '-' + sha), data)
    artifact_id = 'artifact_' + task['task_id'] + '_' + kind.lower() + '_' + hashlib.sha256((kind + sha).encode()).hexdigest()[:20]
    artifact = {'contract_version': 1, 'artifact_id': artifact_id, 'run_id': task['run_id'],
        'producer_task_id': task['task_id'], 'type': kind, 'path': str(path), 'sha256': sha,
        'size_bytes': len(data), 'immutable': True, 'producer_kind': 'TASK', 'producer_id': task['task_id']}
    store.register_artifact(artifact, input_digest=task['input_digest'])
    return artifact_id, path


def read_log_excerpt(artifact, *, artifact_dir, task_id, run_id, input_digest,
                    secret_values=(), max_source=16 * 1024 * 1024, tail_bytes=8192, offset=None):
    """Return a bounded tail only for the exact immutable native TASK log."""
    def unavailable(reason, meta=None):
        out={'available':False,'reason':reason}
        if meta:
            out.update({k:meta.get(k) for k in ('artifact_id','sha256','size_bytes')})
        return out
    if (not isinstance(artifact, dict) or artifact.get('type') != 'LOG' or
            artifact.get('producer_kind') != 'TASK' or artifact.get('producer_task_id') != task_id or
            artifact.get('producer_id') != task_id or artifact.get('run_id') != run_id or
            artifact.get('input_digest') != input_digest or
            artifact.get('immutable') is not True): return unavailable('metadata_mismatch')
    sha, size = artifact.get('sha256'), artifact.get('size_bytes')
    if not isinstance(sha, str) or not re.fullmatch(r'[0-9a-f]{64}', sha) or not isinstance(size, int) or isinstance(size, bool) or size < 0:
        return unavailable('metadata_mismatch')
    meta={'artifact_id':artifact.get('artifact_id'),'sha256':sha,'size_bytes':size}
    if size > max_source: return unavailable('oversized',meta)
    from workflow_coordinator_package import assert_no_reparse
    root = Path(os.path.abspath(artifact_dir))
    expected = root / 'immutable' / task_id / ('log-' + sha)
    try:
        path = Path(artifact.get('path', ''))
        if path != expected: return unavailable('path_mismatch',meta)
        assert_no_reparse(root, expected)
        with expected.open('rb') as stream: raw = stream.read(max_source + 1)
    except FileNotFoundError: return unavailable('missing',meta)
    except (OSError, ValueError): return unavailable('unsafe_path',meta)
    if len(raw) != size or len(raw) > max_source or hashlib.sha256(raw).hexdigest() != sha:
        return unavailable('integrity_mismatch',meta)
    text = raw.decode('utf-8', errors='replace')
    text = sanitize_text(text,secret_values)
    encoded = text.encode('utf-8')
    if offset is None:
        offset=max(0,len(encoded)-tail_bytes)
    if not isinstance(offset,int) or isinstance(offset,bool) or offset<0: return unavailable('invalid_offset',meta)
    chunk=encoded[offset:offset+tail_bytes]
    end=offset+len(chunk)
    return {'available': True, **meta, 'text': chunk.decode('utf-8', errors='replace'),
        'offset':offset,'next_offset':end if end<len(encoded) else None,'truncated':offset>0 or end<len(encoded)}


def sanitize_text(text, secret_values=()):
    """Limited shared redaction for author-visible check diagnostics (not universal)."""
    for secret in sorted({x for x in secret_values if isinstance(x, str) and x}, key=len, reverse=True):
        text = text.replace(secret, '[REDACTED]')
    text = re.sub(r'(?i)(authorization\s*:\s*(?:bearer|basic)\s+)\S+', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)\b(password|token|api[_-]?key)\s*([=:])\s*([^\s,;]+)', r'\1\2[REDACTED]', text)
    text = re.sub(r'\x1b(?:\[[0-?]*[ -/]*[@-~]|\][^\x07]*(?:\x07|\x1b\\))', '', text)
    return ''.join(ch for ch in text if ch in '\n\r\t' or ord(ch) >= 32 and ord(ch) != 127)
