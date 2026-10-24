"""Bounded read-only inventory for a receipt-bound legacy observer cutover."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

try:
    import gate_execution, workflow_cutover_process as stop
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_execution, workflow_cutover_process as stop


def _query_processes():
    script = ("$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process | "
              "Select-Object ProcessId,ParentProcessId,CreationDate,ExecutablePath,CommandLine,Name | ConvertTo-Json -Compress")
    result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', script],
                            capture_output=True, text=True, timeout=8, check=True)
    data = json.loads(result.stdout) if result.stdout.strip() else []
    if isinstance(data, dict): data = [data]
    if not isinstance(data, list) or any(not isinstance(x, dict) for x in data):
        raise ValueError('CIM process snapshot malformed')
    return data


def _digest(body):
    return hashlib.sha256(json.dumps(body, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


_PYTHON_NAMES = {'python.exe','pythonw.exe','python','pythonw'}
_NATIVE_NAMES = {'java.exe','javaw.exe','gradle.exe','java','javaw','gradle'}


def _python_entry(argv):
    """Return the executed Python file/module, never strings embedded in -c code."""
    index=1
    while index < len(argv):
        arg=argv[index]
        if arg == '-c': return None
        if arg == '-m': return ('module',argv[index+1]) if index+1 < len(argv) else None
        if arg in ('-W','-X'):
            index+=2; continue
        if arg.startswith('-'):
            index+=1; continue
        return ('script',arg)
    return None


def _inside(path, roots):
    try:
        candidate=Path(path).resolve()
        return any(candidate == root or candidate.is_relative_to(root) for root in roots)
    except (OSError,TypeError,ValueError):
        return False


def _classify_worker(argv, process_name, executable, roots):
    """Classify only exact OS executable/argv entrypoints and root-bound args."""
    name=Path(str(process_name or '')).name.casefold()
    exe=Path(str(executable or '')).name.casefold()
    if name not in _PYTHON_NAMES and exe not in _PYTHON_NAMES: return None
    entry=_python_entry(argv)
    if not entry: return None
    kind,value=entry
    basename=Path(value).name.casefold() if kind=='script' else value.casefold()
    targets={
        'workflow_native_worker.py':'native_worker',
        'gate_execution.py':'gate_execution_worker',
        'workflow_mcp.py':'managed_adapter',
    }
    module_targets={
        'tooling.agent_workflow.workflow_native_worker':'native_worker',
        'tooling.agent_workflow.gate_execution':'gate_execution_worker',
        'tooling.agent_workflow.workflow_mcp':'managed_adapter',
    }
    classification=(targets.get(basename) if kind=='script' else module_targets.get(basename))
    if classification is None: return None
    args=argv[2:] if kind=='module' else argv[argv.index(value)+1:]
    if classification=='gate_execution_worker' and '--worker' not in args: return None
    if not any(_inside(arg,roots) for arg in args if not arg.startswith('-')):
        return None
    return classification


def collect(barrier, previous_receipt_path, expected_receipt_sha, expected_observer_identity,
            shared_root, query_processes=None, clock_ms=lambda: int(time.time() * 1000), argv_parser=None):
    """Return fresh narrowly-scoped evidence; never changes process or source state."""
    try:
        first = barrier.recheck()
        if first.get('status') != 'OBSERVED_ZERO': raise ValueError('barrier initial recheck held')
        job_rows = first.get('jobs')
        if not isinstance(job_rows, list) or any(not isinstance(r, dict) or not isinstance(r.get('job_id'), str) or
                not isinstance(r.get('sha256'), str) for r in job_rows):
            raise ValueError('barrier job snapshot malformed')
        jobs = [(r['job_id'], r['sha256']) for r in job_rows]
        if len({job for job, _ in jobs}) != len(jobs): raise ValueError('duplicate barrier job')
        source, receipt_sha = stop._expected_from_receipt(previous_receipt_path, shared_root, expected_receipt_sha)
        rows = (query_processes or _query_processes)()
        if os.name != 'nt' and query_processes is None: raise ValueError('production CIM inventory requires Windows')
        if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows): raise ValueError('process snapshot is not a list of rows')
        potential = []; allowed_adapters = []; foreign = []
        root = str(Path(barrier.root).absolute())
        matches = []
        for row in rows:
            cmd = row.get('CommandLine') if isinstance(row, dict) else None
            if not isinstance(cmd, str) or not cmd.strip(): continue
            try: argv = (argv_parser or stop._argv_windows)(cmd)
            except Exception: continue
            if len(argv) == 5 and stop._same_path(argv[1], source) and argv[2] == '--exchange' and stop._same_path(argv[3], root) and argv[4] == '--watch': matches.append(row)
        if len(matches) != 1: raise ValueError('legacy observer is missing or ambiguous')
        observed = matches[0]
        roots=(Path(root).resolve(),Path(shared_root).resolve())
        for row in rows:
            pid=row.get('ProcessId'); cmd=row.get('CommandLine'); exe=row.get('ExecutablePath'); name=row.get('Name')
            if type(pid) is not int or pid < 0: raise ValueError('process snapshot row has invalid PID')
            if pid == 0 and name == 'System Idle Process':
                foreign.append({'pid':0,'name':'System Idle Process','command_line_sha256':hashlib.sha256(str(cmd or '').encode()).hexdigest()})
                continue
            if not isinstance(name,str) or not name.strip(): raise ValueError('process snapshot row name unavailable; process classification unknown')
            if row is observed: continue
            name_base=Path(name).name.casefold(); exe_base=Path(str(exe or '')).name.casefold()
            python=name_base in _PYTHON_NAMES or exe_base in _PYTHON_NAMES
            native=name_base in _NATIVE_NAMES or exe_base in _NATIVE_NAMES
            if (python or native) and (not isinstance(cmd,str) or not cmd.strip()):
                raise ValueError('potential Python/native process command line unavailable; ownership unknown')
            argv=[]
            if isinstance(cmd,str) and cmd.strip():
                try: argv=(argv_parser or stop._argv_windows)(cmd)
                except Exception:
                    if python or native: raise ValueError('potential Python/native command line could not be parsed')
            classification=_classify_worker(argv,name,exe,roots) if python and argv else None
            if classification == 'managed_adapter':
                allowed_adapters.append({'pid':pid,'classification':'managed_adapter_requires_deactivation_proof'})
            elif classification in ('native_worker','gate_execution_worker'):
                potential.append({'pid':pid,'classification':'recognized_workflow_worker'})
            elif argv and name_base in ('cmd.exe','powershell.exe','pwsh.exe') and any(
                    Path(arg.strip('"')).name.casefold() in ('gradlew.bat','gradlew.cmd','gradle.bat') for arg in argv[1:]):
                potential.append({'pid':pid,'classification':'executed_gradle_wrapper'})
            elif native:
                potential.append({'pid':pid,'classification':'native_ownership_unresolved',
                                  'command_line_sha256':hashlib.sha256(cmd.encode()).hexdigest()})
            else:
                # Never emit foreign command lines, which may contain private prompts.
                foreign.append({'pid':pid,'name':name_base,
                                'command_line_sha256':hashlib.sha256(str(cmd or '').encode()).hexdigest()})
        if potential: raise ValueError('native or workflow-owned process ownership unresolved')
        pid = int(observed['ProcessId'])
        current = gate_execution.process_identity(pid)
        if not isinstance(current, dict) or expected_observer_identity != current:
            raise ValueError('observer identity differs from explicit expected identity')
        created = current.get('created')
        known = []
        for job in job_rows:
            record = gate_execution.read(barrier.root, job['job_id'])
            if not isinstance(record, dict): raise ValueError('known barrier job record unavailable')
            state = record.get('state')
            if state not in ('completed', 'blocked', 'interrupted'):
                raise ValueError('known barrier job is not in a supported terminal state')
            record_path = Path(barrier.root) / 'gate-execution' / (job['job_id'] + '.json')
            if hashlib.sha256(record_path.read_bytes()).hexdigest() != job['sha256']:
                raise ValueError('known barrier job record changed during inventory')
            identities = [record.get('worker_identity')]
            child = record.get('owned_child')
            if isinstance(child, dict): identities.extend((child.get('identity'), child.get('process_identity')))
            if isinstance(record.get('owned_children'), list):
                identities.extend(x.get('identity') or x.get('process_identity') for x in record['owned_children'] if isinstance(x, dict))
            recorded = [x for x in identities if x is not None]
            for ident in recorded:
                if not isinstance(ident, dict) or gate_execution.identity_state(ident) != 'dead': raise ValueError('recorded owned identity remains active or unknown')
            historical = job.get('limitation') == 'historical_no_child_recorded_not_cleanup_proof' and not recorded
            if historical and state != 'interrupted':
                raise ValueError('historical no-child limitation is valid only for interrupted jobs')
            identity_state = 'historical_unrecorded' if historical else 'dead'
            if not recorded and not historical: raise ValueError('known job has no recorded process identity')
            known.append({'job_id':job['job_id'],'sha256':job['sha256'],'state':state,'identity_state':identity_state})
        second = barrier.recheck()
        second_jobs = second.get('jobs')
        if (second.get('status') != 'OBSERVED_ZERO' or not isinstance(second_jobs, list) or
                any(not isinstance(r, dict) or not isinstance(r.get('job_id'), str) or not isinstance(r.get('sha256'), str) for r in second_jobs) or
                [(r['job_id'],r['sha256']) for r in second_jobs] != jobs):
            raise ValueError('barrier/job set changed during inventory')
        now = clock_ms()
        snapshot_sha = hashlib.sha256(json.dumps(rows,sort_keys=True,default=str).encode()).hexdigest()
        inv = {'version':1,'exchange_root':root,'observer_identity':{'pid':pid,'created':created},
               'captured_at_ms':now,'known_owned_states':known,'eligible_active':[], 'unknown':[],
               'source_digest':'','checked_scope':'CIM command lines, supported native executables, recorded barrier identities',
               'scope_owner':'tooling.agent_workflow.workflow_cutover_inventory:'+snapshot_sha}
        inv['source_digest'] = _digest({k:v for k,v in inv.items() if k != 'source_digest'})
        plan = {'publication_receipt':str(previous_receipt_path),'expected_publication_receipt_sha256':receipt_sha,
                'shared_root':str(shared_root),'exchange_root':root,'pid':pid,'created':created,
                'executable_path':observed.get('ExecutablePath'),'command_line':observed.get('CommandLine'),
                'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'observed_at_ms':now,'inventory':inv}
        evidence = {'snapshot_sha256':snapshot_sha,
                    'row_count':len(rows),'native_potential':potential,'managed_adapters':allowed_adapters,
                     'foreign_process_count':len(foreign),'foreign_command_line_hashes':foreign,
                     'observer_fields':{'pid':pid,'created':created,'executable_path':observed.get('ExecutablePath'),'command_line':observed.get('CommandLine')}}
        return {'status':'OBSERVED_CLEAR','inventory':inv,'observer_plan':plan,'evidence':evidence}
    except Exception as exc:
        return {'status':'HELD','reasons':[str(exc)[:300]]}
