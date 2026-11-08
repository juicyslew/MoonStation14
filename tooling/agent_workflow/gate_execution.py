"""Durable admission and single-claim execution records for integration gates.

Execution is deliberately started only by the existing observer's reconcile pass.
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

STARTUP_GRACE_SECONDS = 30
RECORD_READ_ATTEMPTS = 5
RECORD_READ_DELAY_SECONDS = .02


def _load_workflow():
    try:
        from owner_attention import _load_workflow as load
    except ModuleNotFoundError:
        from tooling.agent_workflow.owner_attention import _load_workflow as load
    return load()


def _path(root, ident):
    return Path(root) / 'gate-execution' / (ident + '.json')


def _write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name, suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf8') as stream:
            json.dump(value, stream, sort_keys=True, indent=2); stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
        os.replace(tmp, path)
    finally:
        try: os.unlink(tmp)
        except FileNotFoundError: pass


def read(root, ident):
    path = _path(root, ident)
    for attempt in range(RECORD_READ_ATTEMPTS):
        try:
            return json.loads(path.read_text(encoding='utf8')) if path.is_file() else None
        except PermissionError:
            # Windows may briefly deny a read while another process replaces or
            # closes the atomically-written record. Never treat that as absence:
            # admission could otherwise claim the same work a second time.
            if attempt + 1 == RECORD_READ_ATTEMPTS:
                raise
            time.sleep(RECORD_READ_DELAY_SECONDS * (attempt + 1))


def request(root, candidate, *, workflow):
    """Fast admission; performs no candidate/Git verification and starts no process."""
    return _request(root, candidate, workflow=workflow, observer=False)


def request_admitted_observer(root, candidate, *, workflow):
    """Trusted liveness entry point for an already admitted frozen request."""
    return _request(root, candidate, workflow=workflow, observer=True)


def _request(root, candidate, *, workflow, observer):
    wf = workflow or _load_workflow()
    ident = wf.validid(candidate)
    source = Path(root) / 'candidates' / (ident + '.json')
    if not source.is_file(): raise ValueError('unknown candidate')
    c = wf.readjson(source)
    if c.get('id') != ident: raise ValueError('candidate filename/identity mismatch')
    gate = c.get('gate_request') or {}
    if not gate.get('prompt_admitted') or not gate.get('session_id') or gate.get('snapshot_key') != c.get('snapshot_key'):
        raise ValueError('an admitted integration-lead session bound to this candidate is required')
    if observer:
        if gate.get('dispatch_state') != 'admitted' or not isinstance(gate.get('requested_at'), (int, float)):
            raise ValueError('observer requires a program-admitted new-style request')
    else:
        sid = os.environ.get('OPENCODE_SESSION_ID')
        if sid and sid != gate['session_id']: raise ValueError('caller session differs from admitted integration-lead session')
    plan = c.get('gate_plan')
    if not isinstance(plan, dict): raise ValueError('candidate has no structured gate plan')
    path = _path(root, ident)
    lock = wf.gate_lock(Path(root) / 'gate-execution-locks' / ident)
    try:
        old = read(root, ident)
        if old:
            if old.get('snapshot_key') != c.get('snapshot_key') or old.get('plan_digest') != wf.snapshot_key(plan) or old.get('lead_session_id') != gate['session_id']:
                raise ValueError('existing execution job is bound to another candidate, plan, or lead session')
            return old
        # Terminality is checked under the same admission lock as job creation.
        # Existing jobs remain idempotently readable above; never mint a new job
        # against a candidate whose lifecycle has already closed.
        fresh = wf.readjson(source)
        if fresh.get('gate_outcome', {}).get('result') in ('needs_changes', 'ready_for_owner', 'diagnostic_complete') or fresh.get('owner_decision'):
            raise ValueError('gate finalized; inspect status, no new execution jobs')
        claim = hashlib.sha256(os.urandom(32)).hexdigest()
        job = {'job_id': ident, 'candidate_id': ident, 'snapshot_key': c['snapshot_key'], 'plan_digest': wf.snapshot_key(plan),
               'lead_session_id': gate['session_id'], 'origin_session_id': gate.get('origin_session_id'),
               'state': 'requested', 'claim': claim,
               'attempt': 0, 'requested_at': time.time()}
        _write(path, job)
        return job
    finally: lock.close()


def reconcile(root, *, workflow, now=None):
    root = Path(root).resolve()
    try:
        lock = workflow.gate_lock(root / 'gate-execution-launch', nonblocking=True)
    except (BlockingIOError, TimeoutError):
        return {'pending': 0, 'active': 0, 'blocked': 0, 'launched': 0, 'errors': []}
    try:
        return _reconcile_locked(root, workflow=workflow, now=now)
    finally:
        lock.close()


def _reconcile_locked(root, *, workflow, now=None):
    """Observer hook: launch requested jobs hidden/detached, never from CLI caller."""
    wf = workflow; root = Path(root).resolve(); launched = 0; errors = []
    for path in (root / 'gate-execution').glob('*.json') if (root / 'gate-execution').is_dir() else ():
        try: lock = wf.gate_lock(root / 'gate-execution-locks' / path.stem, nonblocking=True)
        except (BlockingIOError, TimeoutError): continue
        try:
            job = read(root, path.stem)
            if not job: continue
            if job.get('state') in ('launching','running'):
                status = identity_state(job.get('worker_identity'))
                launch_at=job.get('launch_started_at')
                elapsed=(now if now is not None else time.time())-launch_at if isinstance(launch_at,(int,float)) else 0
                if status=='unknown' and elapsed < STARTUP_GRACE_SECONDS:
                    continue
                if status == 'dead':
                    child = job.get('owned_child')
                    cleanup = _cleanup_interrupted_child(wf, child) if child else {'status':'no_child_recorded'}
                    if cleanup['status'] not in ('no_child_recorded','terminated_and_absent'):
                        reason = cleanup.get('reason','owned child cleanup could not be verified')
                        try: wf.writejson(wf.blocker_path(root),{'candidate_id':job['candidate_id'],'pid':(child or {}).get('pid') or job.get('worker_pid'),'created':time.time(),'reason':reason,'child_identity':(child or {}).get('identity')})
                        except Exception: pass
                    else: reason = 'worker process identity is no longer alive; owned child cleanup verified'
                    job['cleanup'] = cleanup
                    job.update(state='interrupted',reason=reason,interrupted_at=now if now is not None else time.time())
                    _write(path,job)
                    try:
                        c = wf.readjson(root/'candidates'/(job['candidate_id']+'.json'))
                        wf._gate_progress(root,c,'interrupted',runner_pid=job.get('worker_pid'),child_pid=(child or {}).get('pid'),detail=job['reason'])
                    except Exception: pass
                elif status == 'unknown':
                    reason=f'worker identity remains unknown after {STARTUP_GRACE_SECONDS}s startup grace; quarantine retained and no retry is safe'
                    job.update(state='interrupted',reason=reason,interrupted_at=now if now is not None else time.time())
                    _write(path,job)
                    try: wf.writejson(wf.blocker_path(root),{'candidate_id':job['candidate_id'],'pid':job.get('worker_pid'),'created':time.time(),'reason':reason,'worker_identity':job.get('worker_identity'),'claim':job.get('claim')})
                    except Exception: pass
                    try:
                        c=wf.readjson(root/'candidates'/(job['candidate_id']+'.json'))
                        wf._gate_progress(root,c,'interrupted',runner_pid=job.get('worker_pid'),detail=reason)
                    except Exception: pass
                continue
            if job.get('state') != 'requested': continue
            wf.require_clear_gate(root)
            # A single finite runner keeps the durable execution boundary simple.
            if any(x.get('state') in ('launching', 'running') for p in (root/'gate-execution').glob('*.json') if p != path for x in [read(root,p.stem)] if x):
                continue
            launch_started=now if now is not None else time.time()
            job.update(state='launching', attempt=job.get('attempt', 0) + 1,launch_started_at=launch_started); _write(path, job)
            try:
                proc = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), '--worker', str(root), path.stem, job['claim'], str(job['attempt'])],
                    stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0) if os.name == 'nt' else 0,
                    start_new_session=os.name != 'nt')
            except OSError as exc:
                failed=read(root,path.stem)
                if failed and failed.get('claim')==job['claim'] and failed.get('attempt')==job['attempt'] and failed.get('state')=='launching':
                    failed.update(state='blocked',reason=f'worker process could not be started: {str(exc)[:500]}',error=str(exc)[:1000],finished_at=now if now is not None else time.time())
                    _write(path,failed)
                    try:
                        c=wf.readjson(root/'candidates'/(failed['candidate_id']+'.json'))
                        wf._gate_progress(root,c,'blocked',detail=failed['reason'])
                    except Exception: pass
                errors.append(str(exc)[:300]); continue
            latest = read(root, path.stem)
            if latest and latest.get('claim') == job['claim'] and latest.get('attempt') == job['attempt'] and latest.get('state') == 'launching':
                identity = process_identity(proc.pid)
                latest.update(state='running', worker_pid=proc.pid, worker_identity=identity); _write(path, latest)
            launched += 1
        except (OSError, ValueError) as exc:
            # Keep launching state: without identity proof it is unsafe to retry.
            errors.append(str(exc)[:300])
            continue
        finally: lock.close()
    jobs = [read(root,p.stem) for p in (root/'gate-execution').glob('*.json')] if (root/'gate-execution').is_dir() else []
    active = sum(j.get('state') in ('launching','running') for j in jobs if j)
    return {'pending': sum(j.get('state')=='requested' for j in jobs if j), 'active': active,
            'blocked': sum(j.get('state') in ('blocked','interrupted') for j in jobs if j), 'launched': launched, 'errors': errors}


def run_worker(root, ident, claim, attempt, workflow=None):
    wf = workflow or _load_workflow(); path = _path(root, ident)
    claim_lock = wf.gate_lock(Path(root) / 'gate-execution-locks' / (ident + '-worker'), nonblocking=True)
    metadata_lock = wf.gate_lock(Path(root) / 'gate-execution-locks' / ident)
    try:
        job = read(root, ident)
        if not job or job.get('claim') != claim or job.get('attempt') != int(attempt) or job.get('state') not in ('launching', 'running'):
            raise ValueError('worker claim is stale or mismatched')
        job['state'] = 'running'; job['worker_pid'] = os.getpid(); job['worker_identity'] = process_identity(os.getpid()); _write(path, job)
    except Exception:
        metadata_lock.close(); claim_lock.close(); raise
    metadata_lock.close()
    try:
        cpath = Path(root) / 'candidates' / (ident + '.json'); c = wf.readjson(cpath)
        if c.get('snapshot_key') != job['snapshot_key'] or wf.snapshot_key(c.get('gate_plan')) != job['plan_digest']:
            raise ValueError('candidate or plan changed after admission')
        gate = c.get('gate_request') or {}
        if not gate.get('prompt_admitted') or gate.get('session_id') != job['lead_session_id']:
            raise ValueError('admitted lead binding changed')
        wf.verify_gate(c)
        from types import SimpleNamespace
        wf._run_gate_validation(SimpleNamespace(id=ident, actor='integration-lead'), Path(root),
                                child_started=lambda index,pid:_record_child(root,ident,claim,index,pid,wf))
        state = 'completed'
        result = None
        try:
            c = wf.readjson(cpath); receipt, _ = wf.verify_gate_receipt(c, require_pass=False)
            result = {'path': str(receipt), 'sha256': wf.sha(receipt)}
        except Exception as exc:
            state = 'blocked'; error = f'runner completed without a verified receipt: {exc}'
    except Exception as exc:
        state = 'blocked'; result = None; error = str(exc)[:1000]
        try:
            c = wf.readjson(Path(root)/'candidates'/(ident+'.json'))
            receipt, _ = wf.verify_gate_receipt(c, require_pass=False)
            result = {'path':str(receipt),'sha256':wf.sha(receipt)}
            state = 'completed'
        except Exception: pass
    try:
        metadata_lock = wf.gate_lock(Path(root) / 'gate-execution-locks' / ident)
        job = read(root, ident); job.update(state=state, finished_at=time.time(), receipt=result)
        if state == 'blocked': job['error'] = error
        _write(path, job)
        return job
    finally:
        try: metadata_lock.close()
        except Exception: pass
        claim_lock.close()


def process_identity(pid, *, handle=None):
    """Return PID plus OS creation identity; unknown is never treated as dead."""
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME)]
        kernel.GetProcessTimes.restype = wintypes.BOOL
        kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
        kernel.GetExitCodeProcess.restype = wintypes.BOOL
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        owned_handle = handle is None
        process_handle = handle if handle is not None else kernel.OpenProcess(0x1000, False, int(pid))
        if not process_handle:
            error = ctypes.get_last_error()
            if error in (6, 87): return None
            raise PermissionError(error, 'OpenProcess identity query failed')
        creation, exit_time, kernel_time, user_time = (wintypes.FILETIME() for _ in range(4))
        try:
            if not kernel.GetProcessTimes(process_handle, ctypes.byref(creation), ctypes.byref(exit_time), ctypes.byref(kernel_time), ctypes.byref(user_time)):
                raise OSError(ctypes.get_last_error(), 'GetProcessTimes failed')
            exit_code = wintypes.DWORD()
            if not kernel.GetExitCodeProcess(process_handle, ctypes.byref(exit_code)):
                raise OSError(ctypes.get_last_error(), 'GetExitCodeProcess failed')
            if exit_code.value != 259: return None
            return {'pid': int(pid), 'created': (creation.dwHighDateTime << 32) | creation.dwLowDateTime}
        finally:
            if owned_handle: kernel.CloseHandle(process_handle)
    try:
        stat = Path('/proc') / str(pid) / 'stat'
        text = stat.read_text(); tail = text[text.rfind(')')+2:].split()
        if tail[0] in ('Z','X'): return None
        return {'pid': int(pid), 'created': tail[19]}
    except FileNotFoundError: return None
    except PermissionError: raise
    except OSError as exc:
        if exc.errno == 2: return None
        raise
    except (IndexError, ValueError): return None


def identity_state(identity):
    if (not isinstance(identity, dict) or isinstance(identity.get('pid'), bool) or
        not isinstance(identity.get('pid'), int) or identity.get('created') is None): return 'unknown'
    try: current = process_identity(identity['pid'])
    except PermissionError:
        # Protected handles can deny GetProcessTimes although the read-only CIM
        # provider can still expose creation time. Its timestamp precision is
        # only microseconds, so require >1ms separation before declaring reuse.
        if os.name != 'nt': return 'unknown'
        created = identity.get('created')
        if isinstance(created, bool) or not isinstance(created, int) or created <= 0: return 'unknown'
        pid = identity['pid']
        if pid <= 0: return 'unknown'
        script = ("$ErrorActionPreference='Stop'; "
                  f"Get-CimInstance Win32_Process -Filter \"ProcessId = {pid}\" | "
                  "Select-Object @{N='ProcessId';E={$_.ProcessId}},"
                  "@{N='Created';E={$_.CreationDate.ToUniversalTime().ToFileTimeUtc()}} | ConvertTo-Json -Compress")
        try:
            result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', script],
                                    capture_output=True, text=True, timeout=5, check=True)
            rows = json.loads(result.stdout) if result.stdout.strip() else None
            if isinstance(rows, list):
                if len(rows) != 1: return _windows_preboot_identity_state(created)
                rows = rows[0]
            if not isinstance(rows, dict) or isinstance(rows.get('ProcessId'), bool) or rows.get('ProcessId') != pid:
                return _windows_preboot_identity_state(created)
            now = rows.get('Created')
            if isinstance(now, bool) or not isinstance(now, int) or now <= 0:
                return _windows_preboot_identity_state(created)
            return 'dead' if abs(now - created) > 10000 else 'unknown'
        except Exception:
            return _windows_preboot_identity_state(created)
    except OSError:
        if os.name == 'nt':
            created = identity.get('created')
            return _windows_preboot_identity_state(created)
        return 'unknown'
    if current is None: return 'dead'
    return 'alive' if current == identity else 'dead'


def _windows_boot_filetime():
    """Read the OS boot identity from CIM; wall-clock uptime arithmetic is unsafe."""
    script = ("$ErrorActionPreference='Stop'; Get-CimInstance Win32_OperatingSystem | "
              "Select-Object @{N='Boot';E={$_.LastBootUpTime.ToUniversalTime().ToFileTimeUtc()}} | ConvertTo-Json -Compress")
    result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', script],
                            capture_output=True, text=True, timeout=5, check=True)
    row = json.loads(result.stdout) if result.stdout.strip() else None
    if isinstance(row, list):
        if len(row) != 1: return None
        row = row[0]
    if not isinstance(row, dict): return None
    boot = row.get('Boot')
    return boot if isinstance(boot, int) and not isinstance(boot, bool) and boot > 0 else None


def _windows_preboot_identity_state(created):
    if isinstance(created, bool) or not isinstance(created, int) or created <= 0:
        return 'unknown'
    try:
        boot = _windows_boot_filetime()
    except Exception:
        return 'unknown'
    # Keep a full second of slack for provider timestamp rounding.
    return 'dead' if isinstance(boot, int) and not isinstance(boot, bool) and created < boot - 10_000_000 else 'unknown'


def _record_child(root, ident, claim, index, pid, workflow):
    lock=workflow.gate_lock(Path(root)/'gate-execution-locks'/ident)
    try:
        job=read(root,ident)
        if not job or job.get('claim')!=claim or job.get('state')!='running': raise ValueError('worker job binding changed while starting check')
        identity=process_identity(pid)
        job['owned_child']={'candidate_id':ident,'snapshot_key':job['snapshot_key'],'claim':claim,'check_index':index,'pid':pid,'identity':identity,'started_at':time.time()}
        _write(_path(root,ident),job)
        if not identity:
            workflow.writejson(workflow.blocker_path(root),{'candidate_id':ident,'pid':pid,'created':time.time(),'reason':'started check process identity unknown; manual cleanup required'})
            raise OSError('started check process identity could not be established; gate quarantined')
    finally: lock.close()


def _cleanup_interrupted_child(workflow, child):
    if not isinstance(child,dict) or child.get('candidate_id') is None or child.get('claim') is None or child.get('pid') is None or not child.get('identity'):
        return {'status':'quarantined','reason':'owned child identity metadata missing or malformed'}
    state=identity_state(child['identity'])
    if state=='unknown': return {'status':'quarantined','reason':'owned child identity is unknown; refusing PID-only cleanup'}
    if state=='dead':
        return {'status':'quarantined','reason':'owned child already exited; descendants cannot be verified absent'}
    class OwnedProcess:
        pid=child['pid']
        def poll(self):
            status=identity_state(child['identity'])
            if status=='unknown': raise OSError('owned process identity became unknown')
            return None if status=='alive' else 0
        def wait(self,timeout=None):
            deadline=time.monotonic()+(timeout or 5)
            while time.monotonic()<deadline:
                status=identity_state(child['identity'])
                if status=='dead': return 0
                if status=='unknown': raise OSError('owned process identity became unknown')
                time.sleep(.05)
            raise subprocess.TimeoutExpired('owned-check',timeout)
    try:
        workflow.stop_owned_tree(OwnedProcess(),timeout=5)
        if identity_state(child['identity'])!='dead': return {'status':'quarantined','reason':'owned process termination did not prove identity absence'}
        return {'status':'terminated_and_absent','pid':child['pid'],'identity':child['identity'],
                'candidate_id':child['candidate_id'],'snapshot_key':child.get('snapshot_key'),
                'claim':child['claim'],'check_index':child.get('check_index'),
                'method':'workflow.stop_owned_tree','tree_stop_returned':True,'verified_at':time.time()}
    except Exception as exc:
        return {'status':'quarantined','reason':f'owned child cleanup unverified: {str(exc)[:300]}'}


def verify_cleanup_proof(root, blocker, workflow):
    """Validate candidate/job-bound native stop evidence before quarantine removal."""
    ident=blocker.get('candidate_id')
    if not isinstance(ident,str): raise ValueError('legacy blocker has no candidate-bound tree cleanup proof')
    job=read(root,ident)
    if not job or job.get('state')!='interrupted': raise ValueError('interrupted execution job is required for cleanup proof')
    child=job.get('owned_child') or {}; cleanup=job.get('cleanup') or {}
    if (job.get('candidate_id')!=ident or child.get('candidate_id')!=ident or child.get('snapshot_key')!=job.get('snapshot_key') or
        child.get('claim')!=job.get('claim') or cleanup.get('status')!='terminated_and_absent' or
        cleanup.get('candidate_id')!=ident or cleanup.get('snapshot_key')!=job.get('snapshot_key') or
        cleanup.get('claim')!=job.get('claim') or cleanup.get('identity')!=child.get('identity') or
        cleanup.get('method')!='workflow.stop_owned_tree' or cleanup.get('tree_stop_returned') is not True):
        raise ValueError('blocker lacks a matching verified owned-tree cleanup receipt')
    if blocker.get('pid') not in (job.get('worker_pid'),child.get('pid')):
        raise ValueError('blocker PID does not match the execution job tree')
    for identity in (job.get('worker_identity'),child.get('identity')):
        if identity_state(identity)!='dead': raise ValueError('recorded worker/tree identity is alive or unknown; quarantine retained')
    candidate=workflow.readjson(Path(root)/'candidates'/(ident+'.json'))
    if candidate.get('snapshot_key')!=job.get('snapshot_key'):
        raise ValueError('cleanup proof candidate snapshot is stale')
    return job,cleanup


def process_presence(pid):
    """Return present/absent/unknown using process-table evidence, never kill by PID."""
    if not isinstance(pid,int) or pid < 1: return 'unknown'
    if os.name != 'nt':
        path=Path('/proc')/str(pid)/'stat'
        try:
            fields=path.read_text(); tail=fields[fields.rfind(')')+2:].split()
            return 'absent' if tail[0] in ('Z','X') else 'present'
        except FileNotFoundError: return 'absent'
        except (OSError,IndexError): return 'unknown'
    try:
        return 'absent' if process_identity(pid) is None else 'present'
    except PermissionError: return 'unknown'
    except Exception: return 'unknown'


if __name__ == '__main__':
    if len(sys.argv) != 6 or sys.argv[1] != '--worker': raise SystemExit(2)
    run_worker(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
