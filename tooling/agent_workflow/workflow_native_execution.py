"""Coordinator adapter for durable native task execution."""
import os
import secrets
import subprocess
import sys
import time
import hashlib
import threading
from pathlib import Path

try:
    import gate_execution
    import workflow_runtime
    import workflow_native_worker
    import workflow_task_artifacts
    import workflow_native_cancellation
    import workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_execution, workflow_runtime, workflow_native_worker, workflow_task_artifacts, workflow_native_cancellation, workflow_store


class NativeExecutionBackend:
    def __init__(self, store, artifact_dir, lock_dir, notify_committed=None):
        self.store=store; self.artifact_dir=Path(artifact_dir).resolve(); self.lock_dir=Path(lock_dir).resolve()
        self.notify_committed=notify_committed; self.workers={}; self._watchers=[]; self.scopes={}; self.scope_identities={}; self._scope_lock=threading.RLock()

    def _task_id(self,payload):
        return payload.get('task_id') or payload.get('aggregate_id')

    def observe(self,event_id,payload):
        task_id=self._task_id(payload)
        if not task_id: return 'UNKNOWN'
        task=self.store.get_task(task_id)
        if task is None: return 'ABSENT'
        if task.get('state') == 'CANCELLED':
            saved=self.store.get_event(event_id)
            cancellation=self.store.get_task_cancellation(task_id)
            if workflow_native_cancellation.cancelled_native_readiness_settled(
                    task, saved, cancellation, self.store.get_task_resources(task_id),
                    self.store.get_native_claim(task_id), self.store.get_native_scope(task_id)):
                return 'SETTLED'
            return 'UNKNOWN'
        if task['state']=='COMPLETED': return 'APPLIED'
        claim=self.store.get_native_claim(task_id)
        if not claim: return 'ABSENT' if task['state']=='QUEUED' else 'UNKNOWN'
        state=workflow_native_worker.native_identity_state(claim['process_identity'])
        return 'APPLIED' if state=='alive' else 'UNKNOWN'

    def dispatch(self,event_id,payload):
        task_id=self._task_id(payload)
        task=self.store.get_task(task_id) if task_id else None
        if task is None: raise RuntimeError('native task is missing')
        if self.observe(event_id,payload)=='APPLIED': return
        if task['state']!='QUEUED': raise RuntimeError('native task is not queued')
        run_state=self.store.get_run(task['run_id'])
        if run_state is None: raise RuntimeError('native task run is missing')
        attempt=run_state['attempt']
        if attempt['phase']=='QUEUED':
            try:
                self.store.transition_attempt(task['run_id'],task['evaluation_generation'],
                    expected_sequence=run_state['run']['sequence'],phase='CHECKING',
                    command_id=f"native-checking:{task['run_id']}:{task['evaluation_generation']}")
            except workflow_store.SubmissionLaneBusy:
                return 'PENDING'
        elif attempt['phase']!='CHECKING':
            raise RuntimeError('native task attempt is not in CHECKING phase')
        # Older persisted CHECKING attempts also pass through the transactional
        # lane check. This prevents a resumed/legacy active attempt from
        # launching alongside another current active submission.
        try:
            self.store.assert_submission_lane(task['run_id'],task['evaluation_generation'])
        except workflow_store.SubmissionLaneBusy:
            return 'PENDING'
        token=secrets.token_urlsafe(32)
        script=Path(__file__).resolve().with_name('workflow_native_worker.py')
        ready_dir=self.artifact_dir/'launches'; ready_dir.mkdir(parents=True,exist_ok=True)
        ready_path=ready_dir/(secrets.token_hex(24)+'.ready.json')
        log_path=self.artifact_dir/task_id/'worker.log'; log_path.parent.mkdir(parents=True,exist_ok=True)
        log=log_path.open('ab')
        creationflags=(getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0)|getattr(subprocess,'CREATE_NO_WINDOW',0)) if os.name=='nt' else 0
        kwargs={'creationflags':creationflags} if os.name=='nt' else {'start_new_session':True}
        try: proc=subprocess.Popen([sys.executable,'-u',str(script),'run','--database',str(Path(self.store.path).resolve()),
            '--task-id',task_id,'--claim-token='+token,'--artifact-dir',str(self.artifact_dir),'--lock-dir',str(self.lock_dir),'--ready-path',str(ready_path)],
            stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,close_fds=True,**kwargs)
        finally: log.close()
        self.workers[task_id]=proc
        deadline=time.monotonic()+5
        identity=None
        while time.monotonic()<deadline and proc.poll() is None:
            try:
                data=ready_path.read_bytes()
                if len(data)>1024: raise RuntimeError('native startup acknowledgement exceeds limit')
                ack=workflow_native_worker.workflow_store.strict_loads(data.decode('utf-8'))
                if (not isinstance(ack,dict) or set(ack)!={'identity','token_digest'} or
                    ack['token_digest']!=hashlib.sha256(token.encode()).hexdigest()):
                    raise RuntimeError('native startup acknowledgement is invalid')
                if workflow_native_worker.native_identity_state(ack['identity'])!='alive':
                    raise RuntimeError('native worker identity is not verifiably alive')
                identity=ack['identity']; break
            except FileNotFoundError:
                time.sleep(.025)
            except Exception:
                workflow_runtime.stop_owned_tree(proc)
                raise
        if identity is None:
            # A launcher that has exited cannot safely be used to identify or
            # terminate descendants.  The worker never got far enough to claim
            # the task, so retain its log and report the concrete startup cause.
            return_code=proc.poll()
            if return_code is not None:
                try: tail=log_path.read_text(encoding='utf-8',errors='replace')[-2000:]
                except OSError: tail='(worker log unavailable)'
                ready_state='present' if ready_path.exists() else 'missing'
                raise RuntimeError(f'native worker startup failed exit {return_code}; log {log_path}; ready {ready_state}; last stage: {tail}')
            workflow_runtime.stop_owned_tree(proc)
            raise RuntimeError('native worker startup acknowledgement unavailable')
        # The ready identity is the interpreter, not the venv launcher. Fence
        # that exact process (and its future descendants) before SQL admission.
        scope=None
        try:
            try: import workflow_process_scope
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_process_scope
            scope=workflow_process_scope.OwnedProcessScope.create(task_id)
            raw_identity=gate_execution.process_identity(identity['pid'])
            if (raw_identity is None or raw_identity.get('pid')!=identity['pid'] or
                    workflow_native_worker.native_process_identity(identity['pid'])!=identity):
                raise RuntimeError('native ready identity does not match current raw process identity')
            scope.attach(raw_identity)
            snapshot=scope.snapshot()
            if raw_identity not in snapshot['identities'] or snapshot['active_processes']<1:
                raise RuntimeError('worker was not verified inside its owned process scope')
            self.scopes[task_id]=scope
            self.scope_identities[task_id]=raw_identity
            proof={'scope_name':scope.name,'task_id':task_id,'run_id':task['run_id'],
                'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                'input_digest':task['input_digest'],'worker_identity':raw_identity,
                'kill_on_close':True,'non_inherited_handle':True}
            proof_bytes=workflow_native_worker.workflow_store.canonical_json(proof).encode('utf-8')
            proof_ref,_=workflow_task_artifacts.register_bytes(self.store,task,self.artifact_dir,'PROVENANCE',proof_bytes)
            print('native-dispatch-identities:'+str({'launcher_pid':proc.pid,'worker_identity':identity}),flush=True)
            self.store.dispatch_task(task_id=task_id,event_id='event_native_dispatch_'+task_id,
                                     process_identity=identity,claim_token=token,
                                     native_scope={'scope_name':scope.name,'worker_identity':raw_identity,'creation_artifact_ref':proof_ref})
            self.workers[task_id]=proc
            watcher=threading.Thread(target=self._worker_finished,args=(task_id,proc),name='native-worker-completion',daemon=True)
            self._watchers.append(watcher); watcher.start()
            if self.notify_committed: self.notify_committed()
        except Exception:
            if scope is not None:
                try: scope.terminate_and_wait(5)
                except Exception: pass
                # Keep an unresolved scope open; closing it would lose the
                # only durable OS ownership handle.
                try:
                    if scope.snapshot()['active_processes']==0: scope.close()
                except Exception: pass
            workflow_runtime.stop_owned_tree(proc)
            raise

    def _worker_finished(self,task_id,proc):
        proc.wait()
        try:
            task=self.store.get_task(task_id)
            if task and task['state']=='COMPLETED':
                workflow_native_cancellation.NativeCancellation(self.store,self.artifact_dir).cleanup_scope(task_id)
            elif task and task['state'] not in ('CANCEL_REQUESTED','CANCELLED'):
                # Resolve an unknown held resource after root exit; never infer
                # descendant death from the worker Popen alone.
                workflow_native_cancellation.NativeCancellation(self.store,self.artifact_dir).cleanup_scope(task_id)
            scope=self.scopes.get(task_id)
            if scope is not None and self.store.get_native_scope(task_id)['state']=='CLEANED':
                with self._scope_lock:
                    if self.scopes.get(task_id) is scope:
                        scope.close(); self.scopes.pop(task_id,None)
        except Exception as exc:
            print('native-scope-cleanup-unresolved:'+task_id+':'+str(exc),flush=True)
            try:
                task=self.store.get_task(task_id)
                if task:
                    code='NATIVE_SCOPE_CLEANUP_UNKNOWN'; message=str(exc)[:400]
                    incident={'contract_version':1,'incident_id':'incident_scope_'+hashlib.sha256((task_id+code).encode()).hexdigest()[:20],
                        'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],'task_id':task_id,
                        'code':code,'message':message,'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]}
                    self.store.record_incident(incident)
            except Exception: pass
        if self.notify_committed: self.notify_committed()

    def close(self):
        for proc in list(self.workers.values()):
            task_id=next((k for k,v in self.workers.items() if v is proc),None)
            scope=self.scopes.get(task_id)
            if scope is not None:
                task=self.store.get_task(task_id)
                saved_scope=self.store.get_native_scope(task_id)
                if saved_scope is None:
                    result=scope.terminate_and_wait(5)
                    if result['status']!='CONFIRMED' or result['active_processes']!=0: raise RuntimeError('unregistered owned native scope cleanup unresolved')
                    with self._scope_lock:
                        if self.scopes.get(task_id) is scope: scope.close(); self.scopes.pop(task_id,None)
                elif task and task['state'] != 'CANCELLED':
                    workflow_native_cancellation.NativeCancellation(self.store,self.artifact_dir).cleanup_scope(task_id)
                elif task and task['state']=='CANCELLED':
                    if saved_scope and saved_scope['state']=='CLEANED': workflow_native_cancellation.NativeCancellation(self.store,self.artifact_dir)._verify_artifact(task,saved_scope,saved_scope['cleanup_artifact_ref'])
                with self._scope_lock:
                    saved_scope=self.store.get_native_scope(task_id)
                    if self.scopes.get(task_id) is scope and saved_scope is not None and saved_scope['state']=='CLEANED':
                        scope.close(); self.scopes.pop(task_id,None)
            elif proc.poll() is None: workflow_runtime.stop_owned_tree(proc)
        for thread in self._watchers: thread.join(timeout=2)
        if self.scopes or any(proc.poll() is None for proc in self.workers.values()):
            raise RuntimeError('native workers or owned scopes remain unresolved')
        self.workers.clear()
