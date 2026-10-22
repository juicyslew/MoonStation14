"""Fail-closed, single-handle retirement of a proven workflow observer.

This is an administrative primitive, not scheduler/recovery authorization.  The
caller must provide a fresh process plan and a live DrainBarrier.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import threading

try:
    import gate_execution, workflow_coordinator_package
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_execution, workflow_coordinator_package

OBSERVER_RELATIVE = Path('tooling/agent_workflow/workflow_observer.py')
WAIT_TIMEOUT_MS = 5000


def _query_cim(pid):
    # PID is validated integer-only before interpolation; query is read-only.
    script = ("$ErrorActionPreference='Stop'; "
              f"Get-CimInstance Win32_Process -Filter \"ProcessId = {pid}\" | "
              "Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine | "
              "ConvertTo-Json -Compress")
    result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', script],
                            capture_output=True, text=True, timeout=5, check=True)
    data = json.loads(result.stdout) if result.stdout.strip() else None
    if isinstance(data, list):
        if len(data) != 1: raise ValueError('CIM returned ambiguous process rows')
        data = data[0]
    return data


def _expected_from_receipt(receipt_path, shared_root, expected_receipt_sha256):
    raw_path = Path(receipt_path).absolute()
    local_app_data = os.environ.get('LOCALAPPDATA')
    if not local_app_data: raise ValueError('LOCALAPPDATA is unavailable')
    approved = Path(local_app_data) / 'Temp' / 'opencode'
    approved = Path(os.path.abspath(approved))
    root = Path(os.path.abspath(shared_root))
    if approved == Path(os.path.abspath('.')) or not raw_path.is_relative_to(approved):
        raise ValueError('publication receipt is outside approved OpenCode temporary root')
    if raw_path.is_relative_to(root) or root.is_relative_to(raw_path):
        raise ValueError('publication receipt must be outside shared root')
    # Fence lexical paths before resolution/opening; source has its own fence.
    workflow_coordinator_package.assert_no_reparse(approved, raw_path)
    receipt_path = raw_path.resolve()
    raw = receipt_path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_receipt_sha256:
        raise ValueError('publication receipt differs from fresh pointer-bound hash')
    receipt = json.loads(raw)
    # Publication receipt itself must name the authoritative shared root and
    # own the exact observer source row/hash.
    shared = Path(receipt.get('shared_root') or '').absolute()
    if os.path.normcase(str(shared)) != os.path.normcase(str(root)):
        raise ValueError('publication receipt shared root differs from requested root')
    rows = receipt.get('files')
    matches = [r for r in rows if isinstance(r, dict) and r.get('target') == 'shared' and
               str(r.get('path', '')).replace('\\', '/') == OBSERVER_RELATIVE.as_posix()] if isinstance(rows, list) else []
    if len(matches) != 1:
        raise ValueError('publication receipt must have exactly one shared observer ownership row')
    row = matches[0]
    if row.get('deleted') is not False:
        raise ValueError('publication ownership row is deleted or lacks explicit live disposition')
    if not isinstance(row.get('new_sha256'), str):
        raise ValueError('publication receipt does not own observer source hash')
    source = root / OBSERVER_RELATIVE
    workflow_coordinator_package.assert_no_reparse(root, source)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if digest != row['new_sha256']:
        raise ValueError('published observer source hash is stale')
    return source.resolve(), hashlib.sha256(raw).hexdigest()


def _argv_windows(command_line):
    import ctypes
    from ctypes import wintypes
    shell = ctypes.WinDLL('shell32', use_last_error=True)
    shell.CommandLineToArgvW.argtypes = [wintypes.LPCWSTR, ctypes.POINTER(ctypes.c_int)]
    shell.CommandLineToArgvW.restype = ctypes.POINTER(wintypes.LPWSTR)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [wintypes.HLOCAL]
    kernel.LocalFree.restype = wintypes.HLOCAL
    count = ctypes.c_int()
    ptr = shell.CommandLineToArgvW(command_line, ctypes.byref(count))
    if not ptr: raise OSError(ctypes.get_last_error(), 'CommandLineToArgvW failed')
    try: return [ptr[i] for i in range(count.value)]
    finally: kernel.LocalFree(ptr)


def _same_path(a, b):
    return os.path.normcase(os.path.abspath(str(a))) == os.path.normcase(os.path.abspath(str(b)))


def _validate_inventory(plan, barrier_snapshot, clock_ms, max_age_ms):
    """Validate caller-owned point-in-time evidence, not perform OS-wide discovery.

    A future live collector must populate a trustworthy bounded OS inventory and
    bind it to the current barrier records. Empty ``unknown`` alone is never
    interpreted here as proof that all machine processes were scanned.
    """
    inventory = plan['inventory']
    required = {'version','exchange_root','observer_identity','captured_at_ms','known_owned_states',
                'eligible_active','unknown','source_digest','checked_scope','scope_owner'}
    if not isinstance(inventory, dict) or set(inventory) != required:
        raise ValueError('owned-process inventory schema is incomplete or unexpected')
    if inventory['version'] != 1 or inventory['exchange_root'] != str(Path(plan['exchange_root']).absolute()):
        raise ValueError('owned-process inventory version or root mismatch')
    if inventory['observer_identity'] != {'pid':plan['pid'],'created':plan['created']}:
        raise ValueError('inventory observer identity differs from fresh target')
    captured = inventory['captured_at_ms']; now = clock_ms()
    if type(captured) is not int or captured <= 0 or now < captured or now-captured > max_age_ms:
        raise ValueError('owned-process inventory is stale or has invalid capture time')
    body = {k:v for k,v in inventory.items() if k != 'source_digest'}
    actual = hashlib.sha256(json.dumps(body,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    if inventory['source_digest'] != actual:
        raise ValueError('owned-process inventory digest mismatch')
    if inventory['eligible_active'] != [] or inventory['unknown'] != []:
        raise ValueError('inventory reports active or unknown owned work')
    scope = inventory['checked_scope']; owner = inventory['scope_owner']
    if not isinstance(scope, str) or not scope or not isinstance(owner,str) or not owner:
        raise ValueError('inventory does not disclose a checked scope and owner')
    expected = {row['job_id']:row['sha256'] for row in barrier_snapshot.get('jobs',[])}
    known = inventory['known_owned_states']
    if not isinstance(known,list): raise ValueError('known owned states malformed')
    observed = {}
    for row in known:
        if not isinstance(row,dict) or set(row) != {'job_id','sha256','state','identity_state'}:
            raise ValueError('known owned-state row malformed')
        historical = row['identity_state'] == 'historical_unrecorded'
        limitation = next((j.get('limitation') for j in barrier_snapshot.get('jobs',[])
                           if j.get('job_id') == row['job_id']), None)
        if (row['state'] not in ('completed','blocked','interrupted') or
                (row['identity_state'] != 'dead' and
                 not (historical and row['state'] == 'interrupted' and
                      limitation == 'historical_no_child_recorded_not_cleanup_proof'))):
            raise ValueError('known owned process is active or unknown')
        if row['job_id'] in observed: raise ValueError('duplicate known owned-state row')
        observed[row['job_id']] = row['sha256']
    if observed != expected: raise ValueError('known owned-state coverage differs from maintained barrier records')


def stop_observer(plan, *, barrier, query_cim=None, win_api=None, argv_parser=None,
                  clock_ms=lambda: int(time.time() * 1000), max_plan_age_ms=30000,
                  timeout_ms=WAIT_TIMEOUT_MS):
    """Stop only a receipt-bound observer after two fresh OBSERVED_ZERO checks.

    Fresh inventory is digest-bound to exchange root, observer identity, scope,
    timestamp, and exact barrier-record coverage. A live collector must still
    supply the actual owned OS-process scan; this helper does not invent it.
    Injectable adapters exist solely for pure tests; production defaults are
    Windows APIs and a read-only CIM query.
    """
    if os.name != 'nt': return {'status': 'ERROR', 'reason': 'Windows-only operation'}
    handle = None; kernel = None; outcome = None
    try:
        required = ('exchange_root','shared_root','publication_receipt','expected_publication_receipt_sha256',
                    'pid','created','executable_path','command_line','source_sha256','observed_at_ms','inventory')
        if not isinstance(plan, dict) or any(k not in plan for k in required):
            raise ValueError('fresh owned-process plan is incomplete')
        inv = plan['inventory']
        if type(timeout_ms) is not int or not 1 <= timeout_ms <= 30000:
            raise ValueError('termination wait must be a bounded positive integer')
        if type(max_plan_age_ms) is not int or not 1 <= max_plan_age_ms <= 30000:
            raise ValueError('plan age limit must be a bounded positive integer')
        exchange = Path(os.path.abspath(plan['exchange_root']))
        if not _same_path(exchange, barrier.root):
            raise ValueError('fresh plan exchange root differs from maintained barrier root')
        if (getattr(barrier, '_owner_thread', None) != threading.get_ident() or
                not getattr(barrier, '_locks', None) or getattr(barrier, '_hold', None) is None):
            raise ValueError('maintained barrier is inactive or not owned by this thread')
        observed = plan['observed_at_ms']
        if type(observed) is not int or observed <= 0 or clock_ms() - observed < 0 or clock_ms() - observed > max_plan_age_ms:
            raise ValueError('process plan is stale or has invalid capture time')
        pid = plan['pid']
        if type(pid) is not int or pid <= 0 or type(plan['created']) is not int or plan['created'] <= 0:
            raise ValueError('invalid numeric PID or creation identity')
        initial_barrier = barrier.recheck()
        if initial_barrier.get('status') != 'OBSERVED_ZERO':
            outcome = {'status':'HELD','reason':'maintained drain barrier is not OBSERVED_ZERO'}
            return outcome
        _validate_inventory(plan, initial_barrier, clock_ms, max_plan_age_ms)
        source, _ = _expected_from_receipt(plan['publication_receipt'], plan['shared_root'], plan['expected_publication_receipt_sha256'])
        if hashlib.sha256(source.read_bytes()).hexdigest() != plan['source_sha256']:
            raise ValueError('fresh plan source hash mismatch')
        if win_api is None:
            import ctypes
            from ctypes import wintypes
            kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            kernel.OpenProcess.restype = wintypes.HANDLE
            kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
            kernel.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
            kernel.TerminateProcess.restype = wintypes.BOOL
            kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, __import__('ctypes').POINTER(wintypes.DWORD)]
            kernel.GetExitCodeProcess.restype = wintypes.BOOL
            kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
            kernel.WaitForSingleObject.restype = wintypes.DWORD
            kernel.CloseHandle.argtypes = [wintypes.HANDLE]
            kernel.CloseHandle.restype = wintypes.BOOL
            handle = kernel.OpenProcess(0x1000 | 0x0001 | 0x00100000, False, pid)
            if not handle: raise OSError('OpenProcess failed', ctypes.get_last_error())
        else:
            kernel = win_api
            handle = kernel.open_process(pid)
            if not handle: raise OSError('OpenProcess failed')
        current = gate_execution.process_identity(pid, handle=handle)
        if current != {'pid':pid, 'created':plan['created']}:
            raise ValueError('PID creation identity changed or is unavailable')
        cim = (query_cim or _query_cim)(pid)
        if not isinstance(cim, dict) or int(cim.get('ProcessId', -1)) != pid:
            raise ValueError('CIM process row missing or mismatched')
        cmd = cim.get('CommandLine') or ''
        exe = cim.get('ExecutablePath') or ''
        expected_script = str(source)
        if not _same_path(exe, plan['executable_path']):
            raise ValueError('executable path differs from fresh plan')
        if cmd != plan['command_line']: raise ValueError('full command line differs from fresh inventory')
        argv = (argv_parser or _argv_windows)(cmd)
        # Executable, script, exchange, watch are the whole supported invocation;
        # no extra switches or substring lookalikes are accepted.
        if len(argv) != 5 or not _same_path(argv[0], exe) or not _same_path(argv[1], expected_script) or argv[2] != '--exchange' or \
                not _same_path(argv[3], plan['exchange_root']) or argv[4] != '--watch':
            raise ValueError('command line is not the exact expected observer exchange/watch invocation')
        fresh_barrier = barrier.recheck()
        if fresh_barrier.get('status') != 'OBSERVED_ZERO':
            outcome = {'status':'HELD','reason':'fresh drain barrier changed before termination'}
            return outcome
        _validate_inventory(plan, fresh_barrier, clock_ms, max_plan_age_ms)
        if win_api is None:
            if not kernel.TerminateProcess(handle, 1): raise OSError('TerminateProcess failed', ctypes.get_last_error())
            waited = kernel.WaitForSingleObject(handle, timeout_ms)
            if waited != 0: raise TimeoutError('observer termination wait was not signaled')
            code = wintypes.DWORD()
            if not kernel.GetExitCodeProcess(handle, ctypes.byref(code)): raise OSError('GetExitCodeProcess failed')
            exit_code = code.value
        else:
            if not kernel.terminate(handle): raise OSError('TerminateProcess failed')
            if not kernel.wait(handle, timeout_ms): raise TimeoutError('observer termination wait was not signaled')
            exit_code = kernel.exit_code(handle)
        if exit_code == 259: raise RuntimeError('process remains active after wait')
        outcome = {'status':'STOPPED','pid':pid,'created':plan['created'],'exit_code':exit_code}
        return outcome
    except Exception as exc:
        outcome = {'status':'ERROR','reason':str(exc)[:300]}
        return outcome
    finally:
        if handle is not None:
            try:
                if win_api is None:
                    closed = bool(kernel.CloseHandle(handle))
                else: closed = bool(kernel.close(handle))
            except Exception: closed = False
            if not closed:
                prior = outcome or {'reason':'operation returned before handle cleanup'}
                prior.update(status='ERROR', reason=prior.get('reason',''),
                             primary_status=prior.get('status'), cleanup='CloseHandle failed')
