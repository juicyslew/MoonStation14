"""Explicit process-only supervisor for the workflow coordinator. Not a scheduler."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import time
import secrets
import subprocess
import sys
import tempfile
import threading

try:
    import workflow_runtime
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_runtime


BACKOFF = (5, 15, 60)
MAX_BACKOFF = 120
MAX_FAILURES = 3


class WorkflowSupervisor:
    def __init__(self, database, config, health_file, *, port=8030, command=None,
                 coordinator_script=None, supervisor_handshake=None, popen=None, sleep=time.sleep, clock=time.time, backoff=BACKOFF,
                 loss_reconciler=None, activation_nonce=None, on_running=None):
        self.config = Path(config).resolve()
        self.database = self._config_database(self.config)
        supplied_database = Path(database).resolve()
        if supplied_database != self.database:
            raise ValueError('supervisor database must exactly match coordinator config database')
        self.health_file = Path(health_file)
        if isinstance(port, bool) or not isinstance(port, int) or not 1 <= port <= 65535:
            raise ValueError('port must be an integer from 1 through 65535')
        # Production always uses the pinned current interpreter and coordinator entry.
        self.command = list(command) if command is not None else [
            sys.executable, str(coordinator_script or Path(__file__).with_name('workflow_coordinator.py')),
            'serve', '--config', str(self.config), '--port', str(port)]
        self._popen = popen or subprocess.Popen
        self._sleep, self._clock = sleep, clock
        self._backoff = tuple(backoff)
        self._stop = threading.Event()
        self._lock = None
        self._child = None
        self._scope = None
        self._scope_identity = None
        self._handshake = (command is None) if supervisor_handshake is None else bool(supervisor_handshake)
        self._state = 'starting'
        self._last_exit = None
        self._next_retry = None
        self._fault = None
        self._loss_reconciler = loss_reconciler
        self._review_quiescence = {'confirmed': [], 'unknown': [], 'pending': []}
        if activation_nonce is not None and (not isinstance(activation_nonce,str) or not activation_nonce.strip()):
            raise ValueError('activation_nonce must be a nonempty string when configured')
        self.activation_nonce = activation_nonce
        if on_running is not None and (not callable(on_running) or not self._handshake or activation_nonce is None):
            raise ValueError('on_running requires a callable, coordinator handshake, and activation nonce')
        self.on_running = on_running
        self._running_callback_done = False
        self._running_callback_failed = False
        self._self_identity = None
        self._config_sha256 = None
        self._runtime_path = Path(__file__).resolve()
        self._runtime_sha256 = None
        # Report the interpreter image running this PID, not the venv launcher
        # used to enter it. Child commands still use sys.executable to retain deps.
        base_executable=getattr(sys,'_base_executable',None)
        self._python_path = Path(base_executable).resolve() if isinstance(base_executable,str) and base_executable else None
        self._python_sha256 = None
        self._identity_error = None

    @staticmethod
    def _config_database(config):
        try:
            raw = json.loads(config.read_text(encoding='utf-8'))
            expected = {'database', 'artifact_dir', 'opencode_server', 'bindings'}
            if not isinstance(raw, dict) or set(raw)-expected-{'reviewers', 'owners', 'admission_root','author_worktrees'} or not expected.issubset(raw):
                raise ValueError('invalid coordinator config fields')
            value = raw['database']
            if not isinstance(value, str) or not value:
                raise ValueError('database must be a nonempty path')
            path = Path(value)
            return (config.parent / path).resolve() if not path.is_absolute() else path.resolve()
        except (OSError, json.JSONDecodeError, TypeError, ValueError) as exc:
            raise ValueError(f'invalid coordinator config {config}: {exc}') from exc

    def _publish(self):
        child = self._child
        child_report = None
        if child is not None and self._handshake:
            scope_name = getattr(self._scope, 'name', None)
            identity = self._scope_identity
            if self._scope is not None and isinstance(scope_name, str) and scope_name and self._valid_raw_identity(identity):
                # In handshake mode the Popen PID may be a venv launcher. Report
                # only the exact ready/Job-attached coordinator identity.
                child_report = {'pid': identity['pid'], 'creation_identity': dict(identity),
                    'coordinator_identity': dict(identity), 'scope_name': scope_name}
            elif self._state == 'running':
                self._state = 'paused'
                self._fault = 'coordinator running identity/scope proof unavailable'
        elif child is not None:
            # Custom non-handshake commands retain their historical Popen identity semantics.
            child_report = {'pid': child.pid, 'creation_identity': _creation_identity(child),
                'coordinator_identity': self._scope_identity,
                'scope_name': getattr(self._scope, 'name', None)}
        if self._handshake and self._state == 'running' and child is None:
            self._state = 'paused'
            self._fault = 'coordinator running identity/scope proof unavailable'
        value = {'state': self._state, 'child': child_report,
            'supervisor': {'identity': self._self_identity, 'identity_status':'verified' if (self._self_identity and self._config_sha256 and self._runtime_sha256 and self._python_sha256) else 'unknown',
                'identity_error':self._identity_error, 'activation_nonce':self.activation_nonce,
                'config_path':str(self.config), 'config_sha256':self._config_sha256,
                'runtime_entry_path':str(self._runtime_path), 'runtime_entry_sha256':self._runtime_sha256,
                'python_path':str(self._python_path), 'python_sha256':self._python_sha256},
            'last_exit': self._last_exit, 'next_retry': self._next_retry,
            'fault': self._fault, 'review_quiescence': self._review_quiescence}
        self.health_file.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.health_file.with_suffix(self.health_file.suffix + '.tmp')
        tmp.write_text(json.dumps(value, sort_keys=True), encoding='utf-8')
        for attempt in range(3):
            try:
                os.replace(tmp, self.health_file)
                return True
            except PermissionError:
                if attempt == 2:
                    self._fault = 'health projection temporarily unavailable'
                    try: tmp.unlink()
                    except OSError: pass
                    return False
                time.sleep(.01)

    def stop(self):
        self._stop.set()

    @staticmethod
    def _valid_raw_identity(identity):
        return (isinstance(identity, dict) and set(identity) == {'pid', 'created'} and
            type(identity.get('pid')) is int and identity['pid'] > 0 and
            type(identity.get('created')) is int and identity['created'] > 0)

    @classmethod
    def _verified_ready_identity(cls, reported, actual):
        if (not cls._valid_raw_identity(reported) or
                not cls._valid_raw_identity(actual) or reported != actual):
            raise RuntimeError('coordinator readiness identity unavailable or mismatched')
        return dict(actual)

    def run(self):
        try:
            try: import gate_execution
            except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
            identity = gate_execution.process_identity(os.getpid())
            if not isinstance(identity,dict) or identity.get('pid') != os.getpid() or identity.get('created') is None:
                raise RuntimeError('supervisor OS creation identity unavailable')
            self._self_identity = identity
            self._config_sha256 = hashlib.sha256(self.config.read_bytes()).hexdigest()
            self._runtime_sha256 = hashlib.sha256(self._runtime_path.read_bytes()).hexdigest()
            if self._python_path is None or not self._python_path.is_file():
                raise RuntimeError('actual supervisor interpreter image path unavailable')
            self._python_sha256 = hashlib.sha256(self._python_path.read_bytes()).hexdigest()
        except Exception as exc:
            self._self_identity = None
            self._identity_error = f'{type(exc).__name__}: {exc}'
            self._state, self._fault = 'paused', 'supervisor self-identity unknown; refusing child launch'
            self._publish()
            return
        guard = self.database.with_suffix(self.database.suffix + '.supervisor')
        try:
            self._lock = workflow_runtime.gate_lock(guard, nonblocking=True)
        except (TimeoutError, BlockingIOError):
            self._state, self._fault = 'paused', 'supervisor guard already held'
            self._publish()
            return
        try:
            failures = 0
            while not self._stop.is_set():
                try:
                    scope = None
                    launch_command = list(self.command)
                    ready_path = token = None
                    if self._handshake:
                        if os.name != 'nt':
                            raise RuntimeError('supervisor containment requires Windows Job Objects')
                        import workflow_process_scope
                        scope = workflow_process_scope.OwnedProcessScope.create('workflow-coordinator')
                        self._scope = scope
                        ready_path = Path(tempfile.mkdtemp(prefix='ms14-coordinator-ready-'))/'ready.json'
                        token = secrets.token_urlsafe(32)
                        launch_command += ['--supervisor-ready-path', str(ready_path), f'--supervisor-token={token}']
                    kwargs = {'close_fds': True}
                    if os.name == 'nt':
                        kwargs['creationflags'] = getattr(subprocess, 'CREATE_NEW_PROCESS_GROUP', 0)
                    else:
                        kwargs['start_new_session'] = True
                    proc = self._popen(launch_command, **kwargs)
                    self._child = proc
                    if scope is not None:
                        try:
                            identity = self._await_ready(proc, ready_path, token)
                            if not self._valid_raw_identity(identity):
                                raise RuntimeError('coordinator readiness identity unavailable or malformed')
                            import gate_execution
                            raw = gate_execution.process_identity(identity['pid'])
                            raw = self._verified_ready_identity(identity, raw)
                            scope.attach(raw)
                            if not scope.contains(raw) or scope.snapshot()['active_processes'] < 1:
                                raise RuntimeError('coordinator process scope attachment unverified')
                            self._scope, self._scope_identity = scope, raw
                            ready_path.with_suffix(ready_path.suffix+'.go').write_text(json.dumps({'token':token}),encoding='utf-8')
                        except Exception:
                            raise
                except (OSError, ImportError, ValueError, RuntimeError) as exc:
                    self._state, self._fault = 'paused', f'{type(exc).__name__}: {exc}'
                    self._publish()
                    return
                self._child = proc
                self._state, self._fault, self._next_retry = 'running', None, None
                published=self._publish()
                if self.on_running is not None and not self._running_callback_done:
                    self._running_callback_done=True
                    try:
                        if not published or not self._scope or not self._scope_identity:
                            raise RuntimeError('running health or owned coordinator identity proof unavailable')
                        receipt={'supervisor_identity':dict(self._self_identity),
                            'coordinator_identity':dict(self._scope_identity),
                            'scope_name':getattr(self._scope,'name',None),
                            'scope_member':True,'ready_go':bool(ready_path and ready_path.with_suffix(ready_path.suffix+'.go').is_file()),
                            'activation_nonce':self.activation_nonce,
                            'config_path':str(self.config),'config_sha256':self._config_sha256,
                            'runtime_entry_path':str(self._runtime_path),'runtime_entry_sha256':self._runtime_sha256,
                            'python_path':str(self._python_path),'python_sha256':self._python_sha256}
                        if not receipt['ready_go'] or not self._scope.contains(self._scope_identity):
                            raise RuntimeError('coordinator readiness/Job membership proof unavailable')
                        self.on_running(receipt)
                    except Exception as exc:
                        self._running_callback_failed=True
                        self._state='paused'; self._fault=f'CUTOVER_RUNNING_PROOF_FAILED: {type(exc).__name__}: {exc}'
                        self._publish()
                        return
                while proc.poll() is None and not self._stop.wait(.1):
                    pass
                code = proc.poll()
                if code is None:
                    try:
                        self._stop_child(proc)
                        code = proc.wait(timeout=5)
                    except Exception as exc:
                        self._state, self._fault = 'paused', f'owned child cleanup unknown: {exc}'
                        self._publish()
                        return  # retain guard while ownership is uncertain
                self._last_exit = code
                self._child = None
                if self._scope is not None:
                    try:
                        proof = self._scope.terminate_and_wait(5)
                        if proof['status'] != 'CONFIRMED': raise RuntimeError('coordinator job remains active')
                        self._scope.close(); self._scope = None; self._scope_identity = None
                    except Exception as exc:
                        self._state, self._fault = 'paused', f'owned child cleanup unknown: {exc}'
                        self._publish(); return
                try:
                    summary = self._reconcile_review_loss()
                    if summary is not None: self._review_quiescence = summary
                except Exception as exc:
                    self._fault = f'review loss reconciliation unresolved: {type(exc).__name__}: {exc}'
                    self._review_quiescence = dict(self._review_quiescence,
                        fault=f'{type(exc).__name__}: {exc}')
                self._publish()
                if self._stop.is_set():
                    self._state = 'paused'; self._publish(); return
                failures += 1
                if failures >= MAX_FAILURES:
                    self._state, self._fault, self._next_retry = 'paused', 'restart burst exhausted', None
                    self._publish(); return
                delay = min(MAX_BACKOFF, self._backoff[min(failures-1, len(self._backoff)-1)])
                self._state, self._next_retry = 'restarting', self._clock() + delay
                self._publish()
                if self._stop.wait(0): return
                self._sleep_interruptibly(delay)
            self._state = 'paused'; self._publish()
        except KeyboardInterrupt:
            self._stop.set()
            child = self._child
            if child is not None and child.poll() is None:
                try: self._stop_child(child)
                except Exception as exc:
                    self._state, self._fault = 'paused', f'owned child cleanup unknown: {exc}'
                    self._publish(); return
            self._state = 'paused'; self._publish()
        finally:
            cleaned = self._cleanup_owned()
            if cleaned:
                # Covers failed replacement launch and graceful/exhausted exits too;
                # only run once owned child/job cleanup is proven.
                if not self._running_callback_failed:
                    try:
                        summary = self._reconcile_review_loss()
                        if summary is not None: self._review_quiescence = summary
                    except Exception as exc:
                        self._fault = f'review loss reconciliation unresolved: {type(exc).__name__}: {exc}'
                        self._review_quiescence = dict(self._review_quiescence,
                            fault=f'{type(exc).__name__}: {exc}')
                self._publish()
                if self._lock is not None:
                    self._lock.close(); self._lock = None
            else:
                self._state='paused'
                if self._running_callback_failed and 'owned child cleanup unknown' not in str(self._fault):
                    self._fault=f'{self._fault}; owned child cleanup unknown (ownership proof unavailable)'
                self._publish()

    def _cleanup_owned(self):
        proc, scope = self._child, self._scope
        try:
            if scope is not None:
                proof=scope.terminate_and_wait(5)
                if proof['status'] != 'CONFIRMED':
                    self._fault='owned child cleanup unknown: coordinator scope remains active or unverified'
                    return False
                scope.close(); self._scope=None; self._scope_identity=None
                if proc is not None and proc.poll() is None: proc.wait(timeout=5)
            elif proc is not None and proc.poll() is None:
                workflow_runtime.stop_owned_tree(proc,timeout=5)
                proc.wait(timeout=5)
            self._child=None
            return True
        except Exception as exc:
            self._fault=f'owned child cleanup unknown: {exc}'
            if self._running_callback_failed:
                self._fault='CUTOVER_RUNNING_PROOF_FAILED; owned child cleanup unknown: '+str(exc)
            return False

    def _reconcile_review_loss(self):
        if self._loss_reconciler is not None:
            return self._loss_reconciler()
        guard = workflow_runtime.gate_lock(
            self.database.with_suffix(self.database.suffix + '.coordinator'), nonblocking=True)
        coordinator = None
        try:
            try: import workflow_coordinator
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_coordinator
            coordinator = workflow_coordinator.load_coordinator(self.config)
            owner = coordinator.review_quiescence
            if owner is not None:
                return owner.reconcile()
        finally:
            if coordinator is not None:
                for handler in coordinator.handlers.values():
                    close = getattr(handler, 'close', None)
                    if callable(close): close()
            guard.close()

    def _sleep_interruptibly(self, seconds):
        end = time.monotonic() + seconds
        while not self._stop.is_set() and time.monotonic() < end:
            self._sleep(min(.05, end-time.monotonic()))

    def _await_ready(self, proc, path, token):
        deadline=time.monotonic()+8
        expected=hashlib.sha256(token.encode()).hexdigest()
        while time.monotonic()<deadline and proc.poll() is None:
            try:
                value=json.loads(path.read_text(encoding='utf-8'))
                if value.get('token_digest') != expected or not isinstance(value.get('identity'),dict):
                    raise RuntimeError('coordinator readiness proof invalid')
                return value['identity']
            except FileNotFoundError:
                time.sleep(.025)
        raise RuntimeError('coordinator readiness proof unavailable')

    def _stop_child(self, proc):
        if self._scope is not None:
            proof=self._scope.terminate_and_wait(5)
            if proof['status'] != 'CONFIRMED': raise RuntimeError('coordinator job remains active')
            return
        workflow_runtime.stop_owned_tree(proc, timeout=5)


def _creation_identity(proc):
    try:
        import gate_execution
        return gate_execution.process_identity(proc.pid, handle=getattr(proc, '_handle', None))
    except Exception:
        return None


def main(argv=None):
    parser = argparse.ArgumentParser(description='Explicit process-only workflow coordinator supervisor')
    sub = parser.add_subparsers(dest='command', required=True)
    serve = sub.add_parser('serve')
    serve.add_argument('--config', required=True)
    serve.add_argument('--port', required=True, type=int)
    serve.add_argument('--health-file', required=True)
    serve.add_argument('--activation-nonce')
    args = parser.parse_args(argv)
    try:
        database = WorkflowSupervisor._config_database(Path(args.config).resolve())
        if not 1 <= args.port <= 65535:
            parser.error('--port must be between 1 and 65535')
        WorkflowSupervisor(database, args.config, args.health_file, port=args.port,
                           activation_nonce=args.activation_nonce).run()
    except ValueError as exc:
        parser.error(str(exc))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
