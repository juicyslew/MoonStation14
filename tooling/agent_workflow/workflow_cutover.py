"""Read-only WR12 drain barrier; deliberately does not stop or start services."""
from __future__ import annotations

import hashlib
import json
import threading
from pathlib import Path

try:
    import workflow_runtime, gate_execution, admission, owner_attention, gate_dispatch
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_runtime, gate_execution, admission, owner_attention, gate_dispatch


class DrainBarrier:
    """Own existing admission/execution locks while checking a fresh drain snapshot."""
    def __init__(self, root, hold_receipt, *, workflow=None, request=None):
        self.root = Path(root).absolute()
        self.hold_receipt = hold_receipt
        self.workflow = workflow or workflow_runtime
        self.request = request or gate_dispatch._request
        self._locks = []
        self._hold = None
        self.initial = None
        self._owner_thread = None
        self._job_locks = {}
        self._head_locks = {}
        self._records = {}
        self._hold_path = None
        self._active = False
        self._ownership_uncertain = False

    def __enter__(self):
        try:
            # This call fences the lexical root/reparse chain before any resolve or probe.
            self._hold = admission.verified_hold_handle(self.root, self.hold_receipt)
            self._hold_path = self.root/'gate-maintenance.json'
            self.root = self.root.resolve()
            self._owner_thread = threading.get_ident()
            for path in (self.root/'workflow-cutover', self.root/'gate-execution-launch', self.root):
                self._locks.append(self.workflow.gate_lock(path, nonblocking=True))
            self._acquire_record_locks()
            if self._hold is None or len(self._locks) < 3 or any(lock is None for lock in self._locks):
                raise RuntimeError('required barrier ownership is incomplete')
            self._active = True
            self.initial = self.inspect()
            self._records_initial = dict(self._records)
            self._heads_initial = dict(self._heads)
            return self
        except BaseException:
            try: self._release()
            except BaseException as cleanup: raise RuntimeError('barrier startup failed and cleanup remains uncertain') from cleanup
            raise

    def __exit__(self, typ, value, tb):
        self._release()

    def _release(self):
        if self._owner_thread is not None and threading.get_ident() != self._owner_thread:
            raise RuntimeError('barrier release must run on guard-owning thread; all guards remain retained')
        self._active = False
        error = None
        retained=[]
        for lock in reversed(self._locks):
            try: lock.close()
            except BaseException as exc:
                error = error or exc; retained.append(lock)
        self._locks = list(reversed(retained))
        if retained: self._ownership_uncertain = True
        if self._hold is not None and not retained:
            try: admission.close_verified_hold(self._hold[0]); self._hold = None
            except BaseException as exc: error = error or exc; self._ownership_uncertain = True
        if not self._locks and self._hold is None:
            self._owner_thread = None
            self._ownership_uncertain = False
        if error: raise error

    def inspect(self):
        if (not self._active or self._owner_thread != threading.get_ident() or self._hold is None
                or self._ownership_uncertain or any(lock is None for lock in self._locks)):
            return {'status':'HELD','blockers':[{'code':'BARRIER_NOT_MAINTAINED'}],
                    'jobs':[],'owner_attention':{},'lead_inboxes':[],
                    'limitations':['inspection requires active owner-thread barrier context']}
        return self._inspect()

    def _inspect(self):
        blockers = []
        try:
            # No mutation: any existing blocker is a hard refusal.
            self.workflow.require_clear_gate(self.root)
        except Exception as exc:
            blockers.append({'code':'NATIVE_BLOCKER','detail':str(exc)[:240]})
        jobs = []; records = {}
        directory = self.root/'gate-execution'
        for path in sorted(directory.glob('*.json')) if directory.is_dir() else ():
            try:
                job = gate_execution.read(self.root, path.stem)
                if not isinstance(job, dict) or job.get('job_id') != path.stem:
                    raise ValueError('job record identity mismatch')
                state = job.get('state')
                row = {'job_id':path.stem,'state':state,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
                jobs.append(row)
                records[path.stem] = (row['sha256'], job)
                if state in ('requested','launching','running') or state not in ('completed','blocked','interrupted'):
                    blockers.append({'code':'JOB_NONTERMINAL_OR_UNKNOWN','job_id':path.stem,'state':state})
                identities=[job.get('worker_identity')]
                child=job.get('owned_child')
                if isinstance(child,dict): identities.extend((child.get('identity'),child.get('process_identity')))
                if isinstance(job.get('owned_children'),list):
                    identities.extend(x.get('identity') or x.get('process_identity') for x in job['owned_children'] if isinstance(x,dict))
                for identity in identities:
                    if identity is not None and gate_execution.identity_state(identity) != 'dead':
                        blockers.append({'code':'JOB_PROCESS_PRESENT_OR_UNKNOWN','job_id':path.stem})
                    if job.get('cleanup',{}).get('status') == 'no_child_recorded':
                        row['limitation'] = 'historical_no_child_recorded_not_cleanup_proof'
            except Exception as exc:
                blockers.append({'code':'JOB_RECORD_UNKNOWN','job_id':path.stem,'detail':str(exc)[:200]})
        try:
            current = owner_attention.current_work(self.root, workflow=self.workflow)
            attention = owner_attention.collect(self.root, workflow=self.workflow, refresh=False)
            if not isinstance(attention,dict) or set(attention) != {'awaiting_owner','agent_work','unconfirmed','completed'}:
                raise ValueError('owner attention response shape invalid')
            if not isinstance(current,list): raise ValueError('current owner-work response malformed')
        except Exception as exc:
            current=[]; attention={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}
            blockers.append({'code':'OWNER_ATTENTION_UNKNOWN','detail':str(exc)[:200]})
        if attention.get('awaiting_owner'):
            blockers.append({'code':'AWAITING_OWNER','count':len(attention['awaiting_owner'])})
        for group in current:
            head=group.get('head') if isinstance(group,dict) else None
            req=(head or {}).get('gate_request') or {}
            if req.get('dispatch_pending') is True or req.get('dispatch_state') in ('dispatch_pending','creation_unknown') or req.get('creation_unknown') is True:
                blockers.append({'code':'LEAD_DISPATCH_PENDING','candidate_id':(head or {}).get('id')})
            if req.get('eligible_task_inflight') is True:
                blockers.append({'code':'LEAD_TASK_INFLIGHT','candidate_id':(head or {}).get('id')})
        leads=sorted({(x.get('head') or {}).get('gate_request',{}).get('session_id') for x in current
                      if isinstance(x,dict) and isinstance((x.get('head') or {}).get('gate_request',{}).get('session_id'),str)})
        inbox=[]
        for sid in leads:
            try:
                response=self.request(None,'GET','/api/session/'+sid+'/inbox')
                if not isinstance(response,tuple) or len(response)!=2 or response[0] != 200:
                    raise ValueError('lead inbox request failed')
                payload=response[1]
                if isinstance(payload,dict):
                    if isinstance(payload.get('data'),list): payload=payload['data']
                    elif isinstance(payload.get('items'),list): payload=payload['items']
                    else: raise ValueError('lead inbox response lacks data array')
                if not isinstance(payload,list): raise ValueError('lead inbox schema invalid')
                queued=[]
                for item in payload:
                    if not isinstance(item,dict): raise ValueError('lead inbox item malformed')
                    metadata=item.get('metadata')
                    if metadata is None and isinstance(item.get('payload'),dict): metadata=item['payload'].get('metadata')
                    if metadata is not None and not isinstance(metadata,dict): raise ValueError('lead inbox metadata malformed')
                    # OpenCode's inbox has no read/ACK semantics; structured workflow notices remain pending.
                    if metadata and any(key.startswith('workflow_gate') and value is True for key,value in metadata.items()):
                        queued.append({'workflow_metadata':sorted(key for key,value in metadata.items() if key.startswith('workflow_gate') and value is True),
                                       'delivery':item.get('delivery')})
                inbox.append({'session_id':sid,'queued_workflow':queued})
                if queued: blockers.append({'code':'LEAD_INBOX_WORKFLOW_PENDING','session_id':sid,'count':len(queued)})
            except Exception as exc:
                inbox.append({'session_id':sid,'status':'unknown'})
                blockers.append({'code':'LEAD_INBOX_UNKNOWN','session_id':sid,'detail':str(exc)[:160]})
        self._records = records
        self._heads = {str((x.get('head') or {}).get('id')): hashlib.sha256(json.dumps(x.get('head'),sort_keys=True,default=str).encode()).hexdigest()
                       for x in current if isinstance(x,dict) and x.get('head')}
        # Legacy groups/head ambiguity and agent work are disclosed, not treated as owner ACK.
        return {'status':'HELD' if blockers else 'OBSERVED_ZERO', 'blockers':blockers,
                'jobs':jobs,'owner_attention':{k:len(v) for k,v in attention.items()},'lead_inboxes':inbox,
                'limitations':['process inventory is limited to recorded job identities; external owned-process inventory and observer identity are separate required proof','historical no_child_recorded is not cleanup proof']}

    def _acquire_record_locks(self):
        # Lock known job and current-head IDs in stable order, including admission IDs without records.
        ids=set((self.root/'gate-execution').glob('*.json')) if (self.root/'gate-execution').is_dir() else set()
        ids={p.stem for p in ids}
        candidates=self.root/'candidates'
        if candidates.is_dir(): ids.update(p.stem for p in candidates.glob('*.json'))
        for item in owner_attention.current_work(self.root,workflow=self.workflow):
            head=item.get('head') if isinstance(item,dict) else None
            if isinstance(head,dict) and isinstance(head.get('id'),str): ids.add(head['id'])
        for ident in sorted(ids):
            lock=self.workflow.gate_lock(self.root/'gate-execution-locks'/ident,nonblocking=True)
            self._locks.append(lock); self._job_locks[ident]=lock
            worker_lock=self.workflow.gate_lock(self.root/'gate-execution-locks'/(ident+'-worker'),nonblocking=True)
            self._locks.append(worker_lock)
            if (self.root/'candidates'/f'{ident}.json').is_file():
                candidate=self.workflow.readjson(self.root/'candidates'/f'{ident}.json')
                worktree=candidate.get('worktree',{}).get('worktree'); sprint=candidate.get('sprint_id')
                if isinstance(worktree,str) and isinstance(sprint,str):
                    key=(str(Path(worktree).resolve()).casefold(),sprint)
                    if key not in self._head_locks:
                        guard=self.workflow.try_sprint_lock(self.root,worktree,sprint)
                        if guard is None:
                            raise BlockingIOError(f'sprint mutation guard busy for known candidate {ident}')
                        self._head_locks[key]=guard
                        self._locks.append(guard)

    def recheck(self):
        if (not self._active or self._owner_thread != threading.get_ident() or self._hold is None
                or self._ownership_uncertain or any(lock is None for lock in self._locks)):
            return {'status':'HELD','reason':'maintained barrier ownership is unavailable or uncertain'}
        current = self.inspect()
        if self.initial is None or current['status'] != 'OBSERVED_ZERO' or self.initial['status'] != 'OBSERVED_ZERO':
            return {'status':'HELD','reason':'initial or fresh drain snapshot is not clear','initial':self.initial,'current':current}
        if [(x['job_id'],x['sha256']) for x in self.initial['jobs']] != [(x['job_id'],x['sha256']) for x in current['jobs']]:
            return {'status':'HELD','reason':'execution records changed since initial snapshot','current':current}
        if self._records != getattr(self,'_records_initial',self._records):
            return {'status':'HELD','reason':'execution record set changed since initial snapshot','current':current}
        if self._heads != getattr(self,'_heads_initial',self._heads):
            return {'status':'HELD','reason':'owner head set changed since initial snapshot','current':current}
        return current


def inspect_cli(root, receipt):
    """Bounded read-only inspection. A caller must supply an existing private receipt."""
    with DrainBarrier(root, receipt) as barrier:
        return barrier.recheck()
