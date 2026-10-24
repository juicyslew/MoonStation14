"""Single bounded scheduler for owner feedback and registered gate liveness."""
import argparse
import importlib.util
import json
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def _component(name):
    """Resolve siblings in this module's own layout, never a competing flat module."""
    if __package__:
        from importlib import import_module
        return import_module('.' + name, __package__)
    return __import__(name)


def _workflow():
    owner_attention = _component('owner_attention')
    return owner_attention._load_workflow()


def ensure_started(root, workflow=None):
    root = Path(root).resolve()
    script = Path(__file__).resolve()
    args = [sys.executable, str(script), '--exchange', str(root), '--watch']
    try:
        return subprocess.Popen(args, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0) if os.name == 'nt' else 0,
            start_new_session=os.name != 'nt')
    except OSError:
        return None


def _render(root):
    gate_progress = _component('gate_progress')
    gate_progress.render(root)


def reconcile_components(root, workflow, request, *, now=None, refresh=True):
    """One bounded pass; component failures are isolated and represented in health."""
    owner_feedback = _component('owner_feedback')
    explicit_now = now is not None
    now = time.time() if now is None else now
    try: owner = owner_feedback.reconcile(root, workflow=workflow, request=request, now=now, refresh=refresh, render=False)
    except Exception as exc:
        # Unknown owner state must fail closed: only a successful read can prove idle.
        owner = {'sessions': 0, 'pending_owner': 1, 'unknown_work': True, 'errors': [str(exc)[:250]]}
    try: gate = _gate_reconcile(root, workflow, request, now)
    except Exception as exc: gate = {'pending': 1, 'active': 0, 'errors': [str(exc)[:250]]}
    agent = _reconcile_agent_status(root, workflow, request, now if explicit_now else time.time())
    errors = list(owner.get('errors', [])) + list(gate.get('errors', [])) + list(agent.get('errors', []))
    try: _render(root)
    except Exception as exc: errors.append('board render failed: ' + str(exc)[:200])
    return owner, gate, agent, errors


def _reconcile_agent_status(root, workflow, request, now):
    try:
        module = _component('agent_status')
        return module.reconcile(root, workflow=workflow, request=request, now=now, notify=True)
    except Exception as exc:
        # A failed poll cannot prove the core-agent set is empty.
        return {'monitored': 0, 'core_monitored': 1, 'unknown_work': True,
                'errors': [str(exc)[:250]]}


def watch(root, workflow=None, request=None, *, clock=time.monotonic, sleep=time.sleep, executor_factory=ThreadPoolExecutor):
    root = Path(root).resolve()
    wf = workflow or _workflow()
    lock = wf.gate_lock(root / 'workflow-observer', nonblocking=True)
    try:
        if request is None:
            gate_dispatch = _component('gate_dispatch')
            request = gate_dispatch._request
        interval_owner, interval_gate, idle_grace = 10, 30, 60
        executor = executor_factory(max_workers=1, thread_name_prefix='workflow-owner-feedback')
        gate_executor = executor_factory(max_workers=1, thread_name_prefix='workflow-gate-liveness')
        owner_feedback = _component('owner_feedback')
        owner_stats = {'sessions': 0, 'pending_owner': 1, 'unknown_work': True, 'owner_pending': True, 'errors': []}
        gate_stats = {'pending': 1, 'active': 0, 'errors': []}
        agent_stats = _reconcile_agent_status(root, wf, request, time.time())
        render_errors = []
        start = clock()
        last_owner = last_readiness = last_gate = start
        last_agent = start
        owner_future_refresh = True
        owner_future = executor.submit(owner_feedback.reconcile, root, workflow=wf, request=request, refresh=True, render=False)
        owner_pending = True
        gate_future = gate_executor.submit(_gate_reconcile, root, wf, request, time.time())
        gate_pending = True
        gate_started = start
        idle_since = None
        while True:
            tick = clock()
            if owner_pending and owner_future.done():
                try:
                    owner_stats = owner_future.result()
                    owner_stats = dict(owner_stats)
                    owner_stats['owner_pending'] = False
                    if owner_future_refresh: last_readiness = tick
                except Exception as exc:
                    owner_stats = dict(owner_stats)
                    owner_stats.update(errors=[str(exc)[:250]], pending_owner=max(1, owner_stats.get('pending_owner', 0)), unknown_work=True, owner_pending=False)
                owner_pending = False
                last_owner = tick
            if gate_pending and gate_future.done():
                try: gate_stats = gate_future.result()
                except Exception as exc: gate_stats = {'pending': 1, 'active': 0, 'errors': [str(exc)[:250]]}
                gate_pending = False
                last_gate = tick
            if not owner_pending and tick-last_owner >= interval_owner:
                owner_future = executor.submit(owner_feedback.reconcile, root, workflow=wf, request=request,
                                               refresh=(tick-last_readiness >= 30), render=False)
                owner_future_refresh = tick-last_readiness >= 30
                owner_pending = True
                owner_stats = dict(owner_stats)
                owner_stats['owner_pending'] = True
                last_owner = tick
            if tick - last_agent >= 10:
                agent_stats = _reconcile_agent_status(root, wf, request, time.time())
                last_agent = tick
            if tick - last_gate >= interval_gate:
                if not gate_pending:
                    gate_future = gate_executor.submit(_gate_reconcile, root, wf, request, time.time())
                    gate_pending = True
                    gate_started = tick
                    gate_stats = dict(gate_stats, reconciliation_pending=True)
                    last_gate = tick
            if gate_pending and tick - gate_started >= interval_gate:
                gate_stats = dict(gate_stats, pending=max(1, gate_stats.get('pending', 0)),
                                  reconciliation_pending=True,
                                  errors=list(gate_stats.get('errors', [])) + ['gate reconciliation worker has exceeded its pass interval'])
            errors = (list(owner_stats.get('errors', [])) + list(gate_stats.get('errors', [])) +
                      list(agent_stats.get('errors', [])) + render_errors)
            active = bool(owner_stats.get('unknown_work') or owner_stats.get('sessions') or owner_stats.get('pending_owner') or gate_stats.get('pending') or gate_stats.get('active') or gate_stats.get('blocked') or agent_stats.get('core_monitored') or agent_stats.get('unknown_work'))
            if owner_pending and owner_stats.get('unknown_work'): active = True
            idle_since = None if active else (clock() if idle_since is None else idle_since)
            status = 'error' if errors else ('active' if active or owner_pending else 'idle')
            health = {'pid': os.getpid(), 'updated_at': time.time(), 'status': status,
                       'components': {'owner_feedback': dict(owner_stats, owner_pending=owner_pending), 'gate_liveness': dict(gate_stats, reconciliation_pending=gate_pending), 'agent_status': agent_stats}, 'errors': errors}
            try: wf.writejson(root / 'workflow-observer.json', health)
            except Exception: pass
            if not active and not owner_pending and idle_since is not None and clock()-idle_since >= idle_grace:
                health.update(status='stopped', updated_at=time.time())
                try: wf.writejson(root / 'workflow-observer.json', health)
                except Exception: pass
                return 0
            try: _render(root); render_errors = []
            except Exception as exc: render_errors = ['board render failed: ' + str(exc)[:200]]
            sleep(1)
    finally:
        if 'executor' in locals(): executor.shutdown(wait=False, cancel_futures=True)
        if 'gate_executor' in locals(): gate_executor.shutdown(wait=False, cancel_futures=True)
        lock.close()


def _gate_reconcile(root, workflow, request, now):
    if __package__:
        try:
            module = _component('gate_liveness')
            return module.reconcile(root, workflow=workflow, request=request, now=now)
        except ModuleNotFoundError:
            pass
    module = None
    for name in ('gate_liveness', 'tooling.agent_workflow.gate_liveness'):
        try:
            module = __import__(name, fromlist=['reconcile']); break
        except ModuleNotFoundError: continue
    if module is None:
        path = Path(__file__).resolve().with_name('gate_liveness.py')
        if not path.is_file(): return {'pending': 0, 'active': 0, 'errors': []}
        spec = importlib.util.spec_from_file_location('_observer_gate_liveness', path)
        module = importlib.util.module_from_spec(spec); sys.modules[spec.name] = module; spec.loader.exec_module(module)
    return module.reconcile(root, workflow=workflow, request=request, now=now)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--exchange', type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--once', action='store_true'); mode.add_argument('--watch', action='store_true'); mode.add_argument('--ensure-started', action='store_true')
    args = parser.parse_args(argv)
    if args.ensure_started:
        proc = ensure_started(args.exchange)
        print(json.dumps({'status': 'started' if proc else 'unavailable', 'pid': getattr(proc, 'pid', None)}))
        return 0 if proc else 1
    if args.once:
        wf = _workflow()
        gate_dispatch = _component('gate_dispatch')
        owner, gate, agent, errors = reconcile_components(args.exchange, wf, gate_dispatch._request, refresh=True)
        stats = {'owner_feedback': owner, 'gate_liveness': gate, 'agent_status': agent, 'errors': errors}
        print(json.dumps(stats)); return 1 if errors else 0
    try: return watch(args.exchange)
    except (BlockingIOError, TimeoutError): return 0


if __name__ == '__main__': raise SystemExit(main())
