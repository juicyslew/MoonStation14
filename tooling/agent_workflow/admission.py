"""Small fail-closed guard for an explicitly maintained workflow intake hold."""
import json
import hashlib
import os
import secrets
import argparse
from pathlib import Path


def maintenance(root, candidate_id=None, *, admitted=False):
    """Return None when intake is open, otherwise a JSON-safe held result."""
    path = Path(root) / 'gate-maintenance.json'
    if not path.exists():
        return None
    note = 'Maintenance hold is active; continue scoped work and wait for explicit resume.'
    owner = None
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(data, dict) or not isinstance(data.get('owner'), str) or not data['owner'].strip():
            raise ValueError('expected non-empty owner')
        allowed = data.get('allowed_candidate_ids', [])
        if not isinstance(allowed, list) or any(not isinstance(x, str) for x in allowed):
            raise ValueError('allowed_candidate_ids must be an array of strings')
        owner = data['owner'].strip()
        note = str(data.get('note') or note)
        if admitted or candidate_id in allowed:
            return None
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        note = f'Maintenance hold is malformed; intake fails closed ({exc}).'
    return {'status': 'held', 'candidate_id': candidate_id, 'note': note, 'owner': owner}


def _hold_path(root):
    """Fence the caller's lexical root before resolving it (including symlink roots)."""
    raw = Path(root).absolute()
    current = raw
    while True:
        if _is_reparse(current):
            raise ValueError('maintenance root contains a symlink/reparse point')
        if current.parent == current:
            break
        current = current.parent
    target = raw / 'gate-maintenance.json'
    if _is_reparse(target):
        raise ValueError('maintenance file is a symlink/reparse point')
    resolved = raw.resolve(strict=False)
    if resolved != raw:
        raise ValueError('maintenance root changed during canonicalization')
    return resolved / 'gate-maintenance.json'


def _is_reparse(path):
    try:
        info = path.lstat()
    except FileNotFoundError:
        return False
    attrs = getattr(info, 'st_file_attributes', 0)
    reparse = getattr(__import__('stat'), 'FILE_ATTRIBUTE_REPARSE_POINT', 0x400)
    return path.is_symlink() or bool(attrs & reparse)


def begin_hold(root, *, owner, control_id, note, drain_candidate_ids=()):
    """Create one exclusive hold; return a private receipt required to resume it."""
    if not all(isinstance(x, str) and x.strip() for x in (owner, control_id, note)):
        raise ValueError('owner, control_id and note must be non-empty strings')
    if not isinstance(drain_candidate_ids, (list, tuple)) or any(not isinstance(x, str) for x in drain_candidate_ids):
        raise ValueError('drain_candidate_ids must be strings')
    path = _hold_path(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    _hold_path(root)
    token = secrets.token_urlsafe(32)
    data = {'owner': owner, 'control_id': control_id, 'note': note,
            'allowed_candidate_ids': list(drain_candidate_ids), '_operation_token': token}
    payload = (json.dumps(data, indent=2, sort_keys=True) + '\n').encode()
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(payload); stream.flush(); os.fsync(stream.fileno())
    except BaseException as exc:
        raise OSError(f'hold write failed; fail-closed partial hold remains at {path} (owner {owner}, control {control_id}): {exc}') from exc
    return {'path': str(path), 'token': token, 'sha256': hashlib.sha256(payload).hexdigest(),
            'owner': owner, 'control_id': control_id}


def resume_hold(root, receipt):
    """Remove only the exact unchanged hold represented by a private receipt."""
    if not isinstance(receipt, dict) or not all(isinstance(receipt.get(k), str) for k in ('path', 'token', 'sha256', 'owner', 'control_id')):
        raise ValueError('valid private hold receipt required')
    path = _hold_path(root)
    if Path(receipt['path']) != path:
        raise ValueError('hold receipt path does not match root')
    if os.name != 'nt':
        raise OSError('owned-handle hold resume is supported only on Windows; hold remains active')
    _resume_windows_handle(path, receipt)


def verified_hold_handle(root, receipt):
    """Open and verify the exact owned hold without deleting it; caller retains handle."""
    path = _hold_path(root)
    if not isinstance(receipt, dict) or Path(receipt.get('path', '')) != path:
        raise ValueError('hold receipt path does not match root')
    if os.name != 'nt':
        raise OSError('owned-handle hold verification is supported only on Windows')
    return _open_verified_windows_hold(path, receipt)


def close_verified_hold(handle):
    """Close a verified hold handle using the same correctly typed Win32 owner."""
    import ctypes
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.restype = wintypes.BOOL
    if not kernel.CloseHandle(handle):
        raise OSError(ctypes.get_last_error(), 'verified hold handle close failed; ownership remains uncertain')


def _resume_windows_handle(path, receipt):
    """Verify and delete the same Windows file object while denying replacement/mutation."""
    handle, kernel = _open_verified_windows_hold(path, receipt)
    try:
        disposition = __import__('ctypes').c_ubyte(1)
        if not kernel.SetFileInformationByHandle(handle, 4, __import__('ctypes').byref(disposition), __import__('ctypes').sizeof(disposition)):
            raise OSError(__import__('ctypes').get_last_error(), 'cannot mark owned hold for deletion; hold remains active')
    finally:
        if not kernel.CloseHandle(handle):
            raise OSError(__import__('ctypes').get_last_error(), 'hold handle close failed; resume outcome may be uncertain; inspect hold path')


def _open_verified_windows_hold(path, receipt):
    """Verify identity/content and retain handle denying all competing access."""
    import ctypes
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.restype = wintypes.HANDLE
    kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, wintypes.LPVOID,
                                   wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    kernel.GetFileInformationByHandleEx.argtypes = [wintypes.HANDLE, ctypes.c_int, wintypes.LPVOID, wintypes.DWORD]
    kernel.ReadFile.argtypes = [wintypes.HANDLE, wintypes.LPVOID, wintypes.DWORD, ctypes.POINTER(wintypes.DWORD), wintypes.LPVOID]
    kernel.SetFileInformationByHandle.argtypes = [wintypes.HANDLE, ctypes.c_int, wintypes.LPVOID, wintypes.DWORD]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.CreateFileW(str(path), 0x80000000 | 0x00010000, 0, None, 3, 0x00200000, None)
    invalid = wintypes.HANDLE(-1).value
    if handle == invalid:
        raise OSError(ctypes.get_last_error(), f'cannot acquire exclusive hold handle; hold remains active: {path}')
    try:
        class AttributeTag(ctypes.Structure):
            _fields_ = [('attributes', wintypes.DWORD), ('tag', wintypes.DWORD)]
        info = AttributeTag()
        if not kernel.GetFileInformationByHandleEx(handle, 9, ctypes.byref(info), ctypes.sizeof(info)):
            raise OSError(ctypes.get_last_error(), 'cannot verify hold handle; hold remains active')
        if info.attributes & (0x400 | 0x10):
            raise ValueError('hold handle is reparse point or directory; refusing resume')
        # ReadFile from the held object (not a reopened path).
        chunks = []; size = 0; buffer = ctypes.create_string_buffer(65536); count = wintypes.DWORD()
        while True:
            if not kernel.ReadFile(handle, buffer, len(buffer), ctypes.byref(count), None):
                raise OSError(ctypes.get_last_error(), 'cannot read held file; hold remains active')
            if not count.value: break
            size += count.value
            if size > 1024 * 1024: raise ValueError('hold file exceeds 1 MiB; refusing resume')
            chunks.append(buffer.raw[:count.value])
        payload = b''.join(chunks)
        if hashlib.sha256(payload).hexdigest() != receipt['sha256']:
            raise ValueError('hold changed since operation began')
        data = json.loads(payload)
        if (data.get('_operation_token') != receipt['token'] or data.get('owner') != receipt['owner']
                or data.get('control_id') != receipt['control_id']):
            raise ValueError('hold operation identity mismatch')
        return handle, kernel
    except BaseException:
        if not kernel.CloseHandle(handle):
            raise OSError(ctypes.get_last_error(), 'hold handle close failed; hold remains active')
        raise


def main(argv=None):
    parser = argparse.ArgumentParser(description='Explicit workflow intake maintenance control')
    sub = parser.add_subparsers(dest='command', required=True)
    hold = sub.add_parser('hold'); hold.add_argument('--root', required=True); hold.add_argument('--owner', required=True)
    hold.add_argument('--control-id', required=True); hold.add_argument('--note', required=True)
    hold.add_argument('--drain-candidate-id', action='append', default=[]); hold.add_argument('--receipt', required=True)
    resume = sub.add_parser('resume'); resume.add_argument('--root', required=True); resume.add_argument('--receipt', required=True)
    args = parser.parse_args(argv)
    try:
        receipt_path = Path(args.receipt).absolute()
        if args.command == 'hold' and (receipt_path.exists() or _is_reparse(receipt_path)):
            raise ValueError('private receipt path must be new and not a reparse point')
        if args.command == 'hold':
            receipt = begin_hold(args.root, owner=args.owner, control_id=args.control_id, note=args.note,
                                 drain_candidate_ids=args.drain_candidate_id)
            try:
                receipt_path.parent.mkdir(parents=True, exist_ok=True)
                fd = os.open(receipt_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                with os.fdopen(fd, 'w', encoding='utf-8') as stream:
                    json.dump(receipt, stream); stream.flush(); os.fsync(stream.fileno())
            except BaseException as exc:
                raise OSError(f'hold remains active at {receipt["path"]}; private receipt write failed at {receipt_path}: {exc}') from exc
            print(json.dumps({'status': 'held', 'root': str(Path(args.root).absolute()), 'receipt': str(receipt_path)}))
        else:
            if _is_reparse(receipt_path): raise ValueError('private receipt path is a reparse point')
            if not receipt_path.is_file(): raise ValueError('private receipt file is required')
            resume_hold(args.root, json.loads(receipt_path.read_text(encoding='utf-8')))
            receipt_path.unlink()
            print(json.dumps({'status': 'resumed', 'root': str(Path(args.root).absolute())}))
        return 0
    except Exception as exc:
        parser.exit(2, f'intake control failed: {exc}\n')


if __name__ == '__main__':
    raise SystemExit(main())
