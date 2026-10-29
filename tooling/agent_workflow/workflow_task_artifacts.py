"""Shared immutable TASK artifact writer/registrar."""
import hashlib
import os
import re
from collections import deque
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
                    secret_values=(), max_source=None, tail_bytes=8192, offset=None):
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
    if not isinstance(tail_bytes, int) or isinstance(tail_bytes, bool) or tail_bytes < 0:
        tail_bytes = 8192
    tail_bytes = min(tail_bytes, 8192)
    if offset is not None and (not isinstance(offset,int) or isinstance(offset,bool) or offset<0 or offset > (1 << 63)-1):
        return unavailable('invalid_offset',meta)
    from workflow_coordinator_package import assert_no_reparse
    root = Path(os.path.abspath(artifact_dir))
    expected = root / 'immutable' / task_id / ('log-' + sha)
    try:
        path = Path(artifact.get('path', ''))
        if path != expected: return unavailable('path_mismatch',meta)
        assert_no_reparse(root, expected)
        digest=hashlib.sha256(); actual_size=0; sanitized_size=0
        tail=bytearray(); page=bytearray(); page_start=offset
        secrets=tuple(x for x in secret_values if isinstance(x,str) and x)
        if any('\n' in value or '\r' in value for value in secrets):
            return unavailable('unsupported_multiline_secret',meta)
        record=bytearray(); oversized=False
        redact_continuation=False; pending_header=''
        key_start=re.compile(r'(?i)^\s*(?:authorization|password|token|api[_-]?key)\b')
        incomplete_header=re.compile(r'(?i)^\s*(?:authorization\s*(?::\s*(?:(?:bearer|basic)\s*)?)?|(?:password|token|api[_-]?key)\s*(?:[=:]\s*)?)$')
        credential_header=re.compile(r'(?i)^\s*(?:authorization\s*:\s*(?:bearer|basic)\s*|(?:password|token|api[_-]?key)\s*[=:]\s*)$')
        def sanitized_record(raw):
            nonlocal redact_continuation,pending_header
            text=raw.decode('utf-8',errors='replace')
            if pending_header:
                if not text.strip(): return ''
                combined=pending_header+' '+text.strip(); pending_header=''
                if credential_header.fullmatch(combined):
                    redact_continuation=True
                    return sanitize_text(combined,secrets)
                if incomplete_header.fullmatch(combined) and len(combined)<=256:
                    pending_header=combined
                    return ''
                text=combined
            elif key_start.match(text) and incomplete_header.fullmatch(text.strip()):
                pending_header=text.strip()
                return ''
            if redact_continuation and text.strip():
                text='[REDACTED]'; redact_continuation=False
            elif not text.strip():
                return sanitize_text(text,secrets)
            redacted=sanitize_text(text,secrets)
            return redacted
        def accept(text):
            nonlocal sanitized_size
            encoded=text.encode('utf-8')
            start=sanitized_size; end=start+len(encoded); sanitized_size=end
            tail.extend(encoded)
            if len(tail)>8192: del tail[:-8192]
            if offset is not None and end>offset and start<offset+tail_bytes:
                lo=max(offset,start)-start; hi=min(offset+tail_bytes,end)-start
                page.extend(encoded[lo:hi])
        with expected.open('rb') as stream:
            while True:
                block=stream.read(64*1024)
                if not block: break
                digest.update(block); actual_size+=len(block)
                for byte in block:
                    if oversized:
                        if byte==10:
                            accept('[oversized log record omitted]\n'); oversized=False
                        continue
                    if byte==10:
                        accept(sanitized_record(bytes(record))+'\n'); record.clear()
                    else:
                        record.append(byte)
                        if len(record)>1024*1024:
                            record.clear(); oversized=True
            if oversized:
                accept('[oversized log record omitted]')
            elif record:
                accept(sanitized_record(bytes(record)))
        if pending_header:
            accept(sanitize_text(pending_header,secrets))
    except FileNotFoundError: return unavailable('missing',meta)
    except (OSError, ValueError): return unavailable('unsafe_path',meta)
    if actual_size != size or digest.hexdigest() != sha:
        return unavailable('integrity_mismatch',meta)
    if offset is not None and offset > sanitized_size: return unavailable('invalid_offset',meta)
    if offset is None:
        page_start=max(0,sanitized_size-tail_bytes); chunk=bytes(tail)[-tail_bytes:] if tail_bytes else b''
    else:
        page_start=offset; chunk=bytes(page)
    end=page_start+len(chunk)
    return {'available': True, **meta, 'text': chunk.decode('utf-8', errors='replace'),
        'offset':page_start,'offset_basis':'sanitized_utf8','content_size_bytes':sanitized_size,
        'next_offset':end if end<sanitized_size else None,'truncated':page_start>0 or end<sanitized_size}


def sanitize_text(text, secret_values=()):
    """Limited shared redaction for author-visible check diagnostics (not universal)."""
    for secret in sorted({x for x in secret_values if isinstance(x, str) and x}, key=len, reverse=True):
        text = text.replace(secret, '[REDACTED]')
    text = re.sub(r'(?i)(authorization\s*:\s*(?:bearer|basic)\s+)\S+', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)\b(password|token|api[_-]?key)\s*([=:])\s*([^\s,;]+)', r'\1\2[REDACTED]', text)
    text = re.sub(r'\x1b(?:\[[0-?]*[ -/]*[@-~]|\][^\x07]*(?:\x07|\x1b\\))', '', text)
    return ''.join(ch for ch in text if ch in '\n\r\t' or ord(ch) >= 32 and ord(ch) != 127)
