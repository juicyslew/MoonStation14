"""Inactive, explicitly started local workflow coordinator and bounded ASGI API."""
from __future__ import annotations

import hashlib
import functools
import json
import threading
import time
from dataclasses import dataclass
from pathlib import Path
import os
import statistics
import argparse
import ipaddress
import urllib.parse
import secrets
import hmac

import anyio
from contextlib import asynccontextmanager
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse, HTMLResponse, StreamingResponse
from starlette.routing import Route

import workflow_runtime
import workflow_store
import workflow_submission
import workflow_scope
import workflow_board
import gate_progress
import workflow_task_artifacts


def _loopback_request(request):
    client=request.client
    try:
        if not client or not ipaddress.ip_address(client.host.split('%',1)[0]).is_loopback: return False
        host=request.headers.get('host','')
        parsed=urllib.parse.urlsplit('//'+host)
        return parsed.hostname is not None and (parsed.hostname.lower()=='localhost' or ipaddress.ip_address(parsed.hostname).is_loopback)
    except (ValueError, AttributeError):
        return False


def _request_origin(request):
    host=request.headers.get('host','')
    return f"{request.url.scheme}://{host}"


@dataclass(frozen=True)
class CoordinatorBinding:
    caller: str
    context: object


@dataclass(frozen=True)
class AuthorWorktreeBinding:
    caller: str
    token_file: str
    context_file: str
    repo: str
    floors: dict
    publication_receipt_path: str
    publication_receipt_sha256: str


@dataclass(frozen=True)
class ReviewerBinding:
    caller: str
    worktree: str
    review_role: str


@dataclass(frozen=True)
class OwnerBinding:
    caller: str
    worktree: str
    session_id: str | None = None


class OpenCodeIdentityVerifier:
    """Verify configured session identity using the existing OpenCode API helpers."""
    def __init__(self, server, *, session_lookup=None, role_lookup=None):
        self.server = server
        if session_lookup is None or role_lookup is None:
            import gate_dispatch
            session_lookup = session_lookup or gate_dispatch._session
            role_lookup = role_lookup or gate_dispatch._role
        self.session_lookup, self.role_lookup = session_lookup, role_lookup

    def __call__(self, context):
        author = context.authenticated_author
        session = self.session_lookup(self.server, author['session_id'])
        if not isinstance(session, dict) or session.get('id') != author['session_id']:
            return False
        location = session.get('location')
        directory = location.get('directory') if isinstance(location, dict) else None
        if not isinstance(directory, str) or not directory:
            return False
        if Path(directory).resolve() != Path(context.repo).resolve(): return False
        agent = session.get('agent')
        if not isinstance(agent, str) or not agent.startswith('orchestrate-'): return False
        role = self.role_lookup(self.server, agent, directory)
        return isinstance(role, dict) and role.get('mode') == 'primary'


_registry_guard = threading.Lock()
_registry = set()
_MAX_API_BODY = 1024 * 1024


class IntakeHeld(Exception):
    pass


async def _read_json_body(request, limit=_MAX_API_BODY, *, allow_float=False):
    chunks=[]; size=0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit: raise ValueError('request body exceeds the 1 MiB limit')
        chunks.append(chunk)
    return workflow_store.strict_loads(b''.join(chunks).decode('utf-8'),allow_float=allow_float)


def _scope_payload_digest(payload):
    try:return workflow_store.digest(payload)
    except workflow_store.ProtocolError:
        # Preserve a durable receipt for strict-schema failures such as version 1.0.
        data=json.dumps(payload,sort_keys=True,separators=(',',':'),ensure_ascii=True,allow_nan=False).encode('utf-8')
        return hashlib.sha256(data).hexdigest()


def _registry_key(path):
    value = str(Path(path).resolve())
    return value.casefold() if os.name == 'nt' else value


def _sanitize_exception(exc, request=None, coordinator=None):
    """Redact exception text against request and configured bearer values."""
    secrets=[]
    if request is not None:
        auth=request.headers.get("authorization", "")
        if auth.startswith("Bearer "): secrets.append(auth[7:])
    if coordinator is not None:
        for name in ("bindings", "author_worktrees", "review_bindings", "owner_bindings"):
            mapping=getattr(coordinator,name,{})
            if isinstance(mapping,dict): secrets.extend(str(key) for key in mapping)
    return workflow_task_artifacts.sanitize_text(str(exc),secrets)


_FAILED_RUN_REPLACEMENT_RECOVERY = "Keep the rejected receipt/key unchanged. Prepare fresh scope/submission keys and fresh artifact IDs, producer IDs, and refs; set replaces_run_id to your original owned, settled FAIL run ID. Never edit or reuse the rejected key."

_ERROR_ACTIONS = {
    "MALFORMED_REQUEST": "CORRECT_REQUEST", "INVALID_QUERY": "CORRECT_REQUEST",
    "INVALID_BINDING": "WAIT_FOR_CONFIGURATION", "IDENTITY_DENIED": "VERIFY_CURRENT_SESSION",
    "UNAUTHORIZED": "VERIFY_CURRENT_SESSION", "AUTHOR_SESSION_REQUIRED": "VERIFY_CURRENT_SESSION",
    "FORBIDDEN": "CHECK_STATUS", "NOT_FOUND": "CHECK_STATUS",
    "STALE_COMMAND": "REFRESH_STATUS", "FROZEN_INPUTS_UNAVAILABLE": "REPORT_ERROR",
    "FROZEN_INPUT_LOCATOR_TOO_LARGE": "REPORT_ERROR", "INTAKE_HELD": "WAIT_FOR_ADMISSION",
    "IDEMPOTENCY_CONFLICT": "CHECK_STATUS", "SCOPE_NOT_REGISTERED": "REGISTER_SCOPE_WITH_SAME_KEY",
    "SUBMISSION_UNAVAILABLE": "WAIT_FOR_RECOVERY", "IDENTITY_UNAVAILABLE": "WAIT_FOR_RECOVERY",
    "ACK_CONFLICT": "CHECK_RESULT_STATUS", "ACKNOWLEDGEMENT_UNAVAILABLE": "CHECK_RESULT_STATUS",
    "STALE_OWNER_VIEW": "REFRESH_OWNER_VIEW", "OWNER_UI_UNAVAILABLE": "WAIT_FOR_CONFIGURATION",
    "REVIEW_UNAVAILABLE": "WAIT_FOR_RECOVERY", "OWNER_UNAVAILABLE": "WAIT_FOR_CONFIGURATION",
    "CANCEL_UNAVAILABLE": "WAIT_FOR_CONFIGURATION", "INVALID_CANCELLATION": "CORRECT_REQUEST",
    "INVALID_SUCCESSOR_AUTHORIZATION": "WAIT_FOR_RECOVERY",
    "RETRY_UNAVAILABLE": "WAIT_FOR_CONFIGURATION", "INVALID_RETRY": "CORRECT_REQUEST",
    "SUBMISSION_REPLACEMENT_UNAVAILABLE": "PREPARE_FAILED_RUN_REPLACEMENT", "FEATURE_HEAD_UNSETTLED": "WAIT_FOR_RECOVERY",
}


def _error(code, message, action=None, *, field=None, recovery=None):
    """Return a bounded, actionable error without changing authority or receipt semantics."""
    action = action or _ERROR_ACTIONS.get(code, "WAIT_FOR_RECOVERY")
    if recovery is None:
        recovery = {
            "CORRECT_REQUEST": "Correct the request fields/parameters and retry this call.",
            "VERIFY_CURRENT_SESSION": "Use the currently configured session identity; do not change permissions or share tokens.",
            "CHECK_STATUS": "Query status for the identifier in this caller's scope first. Exact replay keeps the key; corrected payload requires fresh scope and command keys. Never replace an uncertain submission blindly.",
            "CHECK_RESULT_STATUS": "Inspect status for the original run to check run health, then recover only by replaying the exact delivered ACK with the same command_id. Do not invent or change event/result identifiers.",
            "REFRESH_STATUS": "Fetch current status/generation, then issue a fresh command using that generation.",
            "REFRESH_OWNER_VIEW": "Refresh the owner snapshot and displayed result identity before deciding whether a new command is justified.",
            "REPORT_ERROR": "Report an ERROR for this same assigned task; do not replace or edit frozen inputs.",
            "WAIT_FOR_ADMISSION": "Poll admission/status and retry only after the hold is released.",
            "WAIT_FOR_CONFIGURATION": "Wait for the configured workflow binding to be repaired; do not alter identity settings.",
            "WAIT_FOR_RECOVERY": "Retain identifiers and poll status; retry the same operation only when its outcome is known.",
            "CORRECT_SCOPE": "Correct the scope payload and register it with a fresh registration key.",
            "USE_NEW_REGISTRATION_KEY": "Preserve the existing receipt; use a fresh registration key for corrected scope input.",
            "CORRECT_SCOPE_KEY": "Supply exactly one registration idempotency_key.",
            "REGISTER_SCOPE_WITH_SAME_KEY": "Register the intended scope using the same key, then query scope_status.",
            "CHECK_SCOPE_STATUS": "Query scope_status with the same registration key before retrying.",
            "USE_NEW_COMMAND_ID": "Inspect the original command result; use a fresh command_id only for a corrected command.",
            "CORRECT_COMMAND": "Correct the command and provide a fresh command_id.",
            "PREPARE_NEW_SCOPE": "Preserve old evidence; use fresh artifact IDs, update criteria.evidence_refs, and register a fresh scope/submission with new keys.",
            "PREPARE_FAILED_RUN_REPLACEMENT": _FAILED_RUN_REPLACEMENT_RECOVERY,
            "CHECK_SUBMISSION_STATUS": "Query submission status using the same caller/key. If outcome is uncertain, replay only the identical request; known changed evidence requires fresh artifact IDs and fresh scope/submission keys.",
            "REPORT_WORKFLOW_BLOCKER": "Retain this request and receipt and report the workflow-capability blocker. Do not change sprint, overwrite evidence, or blindly resubmit.",
            "REPAIR_LOCAL_API": "Wait for the configured local API recovery path; do not bypass it.",
        }.get(action, "Retain current identifiers and poll status; do not reset, replace evidence, or change privileges.")
    result = {"error": {"code": code, "message": str(message)[:500], "next_action": action, "recovery": str(recovery)[:500]}}
    if field is not None:
        result["error"]["field"] = field
    return result


def _submission_guidance(receipt):
    """Project safe recovery guidance for a durable ERROR receipt without altering it."""
    if not isinstance(receipt,dict) or receipt.get("status") != "REJECTED" or receipt.get("result") != "ERROR":
        return None
    code=receipt.get("code")
    if code == "SUBMISSION_REPLACEMENT_UNAVAILABLE":
        return _error(code,"This submission receipt is a final rejection. Prepare a fresh request and scope to replace your settled failed run.",
            "PREPARE_FAILED_RUN_REPLACEMENT",field="replaces_run_id",recovery=_FAILED_RUN_REPLACEMENT_RECOVERY)["error"]
    if code == "FEATURE_HEAD_UNSETTLED":
        return _error(code,"The existing feature run is not settled for replacement.","WAIT_FOR_RECOVERY",field="replaces_run_id",
            recovery="Check status of the original run and wait until cleanup/recovery settles. Do not force-cancel it, create new keys to bypass it, or change sprint.")["error"]
    return _error("SYSTEM_ERROR","A durable submission system error is recorded.","WAIT_FOR_RECOVERY",
        recovery="Retain the same request and key, inspect this caller's submission status, and wait for the workflow recovery path. Do not assume admission or replace evidence.")["error"]


def event_kind(payload):
    """Resolve explicit event kinds, or strictly recognize the typed result wire shape."""
    if not isinstance(payload, dict):
        return None
    if "kind" in payload:
        return payload.get("kind")
    try:
        workflow_store.validate(payload, "result_available")
    except (workflow_store.ProtocolError, ValueError, TypeError):
        try:
            workflow_store.validate(payload, "dispatch_envelope")
        except (workflow_store.ProtocolError, ValueError, TypeError):
            return None
        return "TASK_ASSIGNMENT"
    return "RESULT_AVAILABLE"


class ExplicitCoordinator:
    """Owns an outbox polling thread only after explicit start()."""
    def __init__(self, store, *, bindings, artifact_dir, handlers=None,
                 review_bindings=None, review_results=None, review_pipeline=None,
                  identity_verifier=None, owner_bindings=None, owner_service=None,
                 reconcile_interval=1.0, lease_ms=30000, native_recovery=None,
                  native_retry=None, transport_health=None, clock_ms=None,
                     review_quiescence=None, admission_root=None, author_worktrees=None,
                  author_activity_observer=None, activity_refresh_interval=5.0, activity_ttl=30.0):
        self.store = store
        self.admission_root = Path(admission_root) if admission_root is not None else None
        self.bindings = dict(bindings)
        self.author_worktrees = dict(author_worktrees or {})
        self.author_activity_observer=author_activity_observer
        self.activity_refresh_interval=float(activity_refresh_interval)
        self.activity_ttl=float(activity_ttl)
        self._activity_lock=threading.Lock()
        self._activity_state_lock=threading.Lock()
        self._activity_clock=time.monotonic
        self._activity_checked=None
        self._activity_result={'checked_at':None,'complete':False,'agents':[],'errors':[]}
        self._activity_semantic=None
        self.review_bindings = dict(review_bindings or {})
        self.owner_bindings = dict(owner_bindings or {})
        self._owner_ui_cookie = secrets.token_urlsafe(32)
        if (set(self.bindings) & set(self.review_bindings) or set(self.bindings) & set(self.owner_bindings)
                or set(self.review_bindings) & set(self.owner_bindings)):
            raise ValueError("author, reviewer, and owner tokens must be distinct")
        callers=[x.caller for x in self.bindings.values() if isinstance(x,CoordinatorBinding)]
        callers += [x.caller for x in self.review_bindings.values()]
        callers += [x.caller for x in self.owner_bindings.values()]
        if len(callers)!=len(set(callers)): raise ValueError("configured callers must be distinct")
        self.owner_service = owner_service
        self.run_control = None
        self.review_results = review_results
        self.review_retry = None
        self.review_pipeline = review_pipeline
        if (not self.bindings and not self.author_worktrees) or any(not isinstance(k, str) or not k for k in self.bindings):
            raise ValueError("nonempty bearer bindings required")
        self.artifact_dir = Path(artifact_dir)
        self.handlers = dict(handlers or {})
        if not callable(identity_verifier): raise ValueError("identity_verifier is required")
        self.identity_verifier = identity_verifier
        self.reconcile_interval = min(30.0, max(.05, float(reconcile_interval)))
        self.lease_ms = lease_ms
        self.native_recovery = native_recovery
        self.native_retry = native_retry
        self.review_quiescence = review_quiescence
        self.transport_health=transport_health
        self.clock_ms=clock_ms or (lambda:int(time.time()*1000))
        self.native_recovery_summary = None
        self.native_retry_summary = None
        self.review_quiescence_summary = {'confirmed': [], 'unknown': [], 'pending': []}
        self._stop = threading.Event()
        self._wake = threading.Event()
        self._board_condition=threading.Condition()
        self._board_revision=0
        self._board_workflow_revision=0
        self._board_subscribers=0
        self._thread = None
        self._lock = None
        self._exchange_lock = None
        self._start_thread_id = None
        self.last_error = None
        self._error_lock = threading.Lock()
        self._pending = 0
        self._samples = []
        self._metrics_lock = threading.Lock()

    def start(self):
        if self._thread and self._thread.is_alive(): return
        path = Path(self.store.path).resolve()
        registry_key = _registry_key(path)
        exchange_key = _registry_key(self.admission_root / 'workflow-observer') if self.admission_root is not None else None
        with _registry_guard:
            if registry_key in _registry: raise BlockingIOError("coordinator already owns this database in this process")
            if exchange_key is not None and ('exchange', exchange_key) in _registry:
                raise BlockingIOError("coordinator already owns this admission exchange in this process")
            _registry.add(registry_key)
            if exchange_key is not None: _registry.add(('exchange', exchange_key))
        try:
            if exchange_key is not None:
                self._exchange_lock = workflow_runtime.gate_lock(self.admission_root / 'workflow-observer', nonblocking=True)
            self._lock = workflow_runtime.gate_lock(path.with_suffix(path.suffix + ".coordinator"), nonblocking=True)
        except BaseException:
            if self._exchange_lock:
                self._exchange_lock.close(); self._exchange_lock = None
            with _registry_guard: _registry.discard(registry_key)
            if exchange_key is not None:
                with _registry_guard: _registry.discard(('exchange', exchange_key))
            raise
        self._registry_key = registry_key
        self._exchange_registry_key = ('exchange', exchange_key) if exchange_key is not None else None
        self._start_thread_id = threading.get_ident()
        try:
            # A stalled-run repair hold is a maintenance barrier, not merely an
            # intake gate.  Check before startup recovery, which may write rows.
            paused = self._maintenance_paused()
            # Reconcile persisted native ownership while exclusive coordinator
            # ownership is held, before any outbox delivery can dispatch work.
            if not paused and self.native_recovery is not None:
                self.native_recovery_summary = self.native_recovery.reconcile()
                if self.native_recovery_summary.get('unknown'):
                    self._set_error('native startup recovery unresolved: ' + ', '.join(self.native_recovery_summary['unknown']))
            if not paused and self.review_quiescence is not None:
                self.review_quiescence_summary = self.review_quiescence.reconcile()
            self._stop.clear()
            self._thread = threading.Thread(target=self._run, name="workflow-coordinator", daemon=True)
            self._thread.start()
        except BaseException:
            self._thread = None
            if self._lock:
                self._lock.close(); self._lock = None
            if self._exchange_lock:
                self._exchange_lock.close(); self._exchange_lock = None
            with _registry_guard: _registry.discard(registry_key)
            if self._exchange_registry_key:
                with _registry_guard: _registry.discard(self._exchange_registry_key)
            raise

    def stop(self):
        if self._exchange_lock is not None and threading.get_ident() != self._start_thread_id:
            raise RuntimeError("coordinator stop must run on the guard-owning thread; guards remain owned")
        self._stop.set(); self._wake.set()
        with self._board_condition: self._board_condition.notify_all()
        if self._thread:
            self._thread.join(timeout=3)
        if self._thread and self._thread.is_alive(): raise RuntimeError("coordinator did not stop; guard remains owned")
        if self._lock and self.review_quiescence is not None:
            self.review_quiescence.reconcile()
        for handler in self.handlers.values():
            close=getattr(handler,'close',None)
            if callable(close): close()
        self._thread = None
        if self._lock:
            self._lock.close(); self._lock = None
        with _registry_guard: _registry.discard(getattr(self, '_registry_key', None))
        if self._exchange_lock:
            self._exchange_lock.close(); self._exchange_lock = None
        with _registry_guard:
            if getattr(self, '_exchange_registry_key', None): _registry.discard(self._exchange_registry_key)

    def notify_committed(self):
        with self._board_condition:
            self._board_revision+=1
            self._board_workflow_revision+=1
            self._board_condition.notify_all()
        self._wake.set()

    def _notify_activity_changed(self):
        """Wake board viewers without treating observer changes as workflow mutations."""
        with self._board_condition:
            self._board_revision+=1
            self._board_condition.notify_all()

    def _board_revision_subscribe(self):
        with self._board_condition:
            self._board_subscribers+=1
            return self._board_revision

    def _board_revision_unsubscribe(self):
        with self._board_condition: self._board_subscribers=max(0,self._board_subscribers-1)

    def _board_wait_revision(self, after, timeout=1.0):
        with self._board_condition:
            self._board_condition.wait_for(lambda:self._board_revision>after or self._stop.is_set(),timeout)
            return self._board_revision if self._board_revision>after else None

    async def _board_event_stream(self):
        revision=self._board_revision_subscribe()
        try:
            yield self._board_revision_frame(revision)
            while not self._stop.is_set():
                current=await anyio.to_thread.run_sync(self._board_wait_revision,revision,1.0)
                if self._stop.is_set(): break
                if current is None:
                    changed=await anyio.to_thread.run_sync(self.refresh_author_activity)
                    if changed:
                        with self._board_condition: revision=self._board_revision
                        yield self._board_revision_frame(revision)
                    else:
                        yield b': heartbeat\n\n'
                    continue
                revision=current
                yield self._board_revision_frame(revision)
        finally:
            self._board_revision_unsubscribe()

    def _board_revision_frame(self, revision):
        with self._board_condition:
            workflow_revision=self._board_workflow_revision
        return ('event: board-revision\ndata: '+json.dumps({'revision':revision,
                'workflow_revision':workflow_revision},separators=(',',':'))+'\n\n').encode('utf-8')

    def _board_snapshot_payload(self, worktree):
        raw=workflow_store.WorkflowStore.readonly_snapshot(self.store.path,worktree=worktree)
        result=workflow_board.project(raw,worktree=worktree,author_activity=self._cached_author_activity())
        result['coordinator']=self.summary()
        if self.admission_root is not None:
            try:
                if __package__:
                    from . import workflow_cutover_history
                else:
                    import workflow_cutover_history
                result['legacy_history']=workflow_cutover_history.read_projection(self.admission_root)
            except (ImportError, OSError, ValueError, TypeError):
                result['legacy_history']={'available':False,'reason':'Saved history metadata is unavailable.'}
        return result

    def _cached_author_activity(self):
        with self._activity_state_lock:
            value=self._activity_result
            checked=self._activity_checked
            if checked is None or self._activity_clock()-checked > self.activity_ttl:
                return [{'worktree':a.get('worktree'),'label':a.get('label'),'activity':'unknown',
                         'summary':a.get('summary','No active work'),'session_id':a.get('session_id','')}
                        for a in value.get('agents',[]) if isinstance(a,dict)]
            return list(value.get('agents',[])) if value.get('complete') else [
                {**a,'activity':'unknown'} for a in value.get('agents',[]) if isinstance(a,dict)]

    def refresh_author_activity(self):
        """Refresh one shared bounded observation; safe for concurrent SSE viewers."""
        observer=self.author_activity_observer
        if observer is None or not self._activity_lock.acquire(blocking=False): return False
        try:
            now=self._activity_clock()
            with self._activity_state_lock:
                if self._activity_checked is not None and now-self._activity_checked < self.activity_refresh_interval:
                    return False
                prior_agents=list(self._activity_result.get('agents',[]))
            try:
                observed=observer()
                if not isinstance(observed,dict): raise ValueError('invalid observation')
                agents=[]
                for item in observed.get('agents',[]):
                    if not isinstance(item,dict): continue
                    agents.append({k:item.get(k) for k in ('worktree','label','activity','summary','session_id')})
                complete=observed.get('complete') is True
                if not complete:
                    agents=[{**a,'activity':'unknown'} for a in agents]
                result={'checked_at':observed.get('checked_at'),'complete':complete,'agents':agents,
                        'errors':[str(e)[:160] for e in observed.get('errors',[]) if isinstance(e,str)]}
            except Exception:
                result={'checked_at':None,'complete':False,'agents':[
                    {k:a.get(k) for k in ('worktree','label','summary','session_id')} | {'activity':'unknown'}
                    for a in prior_agents if isinstance(a,dict)],'errors':['author activity unavailable']}
            semantic=(result['complete'],result['agents'],result['errors'])
            with self._activity_state_lock:
                changed=semantic != self._activity_semantic
                self._activity_result=result
                self._activity_checked=now
                self._activity_semantic=semantic
        finally:
            self._activity_lock.release()
        if changed: self._notify_activity_changed()
        return changed

    def _board_seen(self, incident_id, worktree):
        incident=self.store.get_incident(incident_id)
        if not incident: return 404,None
        run_id=incident.get('incident',{}).get('run_id')
        run=self.store.get_run(run_id) if run_id else None
        if not run or run['run'].get('author',{}).get('worktree')!=worktree:
            return 403,None
        result=self.store.mark_incident_seen(incident_id)
        self.notify_committed()
        return 200,result

    def _set_error(self, value):
        with self._error_lock: self.last_error = value

    def _run(self):
        while not self._stop.is_set():
            self._wake.clear()
            try:
                if self._maintenance_paused():
                    self._wake.wait(self.reconcile_interval)
                    continue
                if self.transport_health is not None:
                    self.transport_health.tick()
                unavailable=(self.transport_health is not None and self.transport_health.snapshot()['state'] in ('OPEN','BACKOFF'))
                if self.review_pipeline is not None and not unavailable:
                    self.review_pipeline.reconcile_pending()
                if self.native_retry is not None:
                    self.native_retry_summary = self.native_retry.reconcile()
                if self.native_recovery is not None:
                    self.native_recovery_summary = self.native_recovery.reconcile(unresolved_only=True)
                    if self.native_recovery_summary.get('unknown'):
                        self._set_error('native recovery remains unresolved: ' + ', '.join(self.native_recovery_summary['unknown']))
                    elif self.last_error and self.last_error.startswith((
                            'native startup recovery unresolved:', 'native recovery remains unresolved:')):
                        self._set_error(None)
                if self.review_quiescence is not None:
                    self.review_quiescence_summary = self.review_quiescence.reconcile(unresolved_only=True)
                # Keep a continuously replenished/slow outbox from starving the
                # health and reconciliation work above. A dispatch is not
                # interruptible, so bound the number of deliveries per pass.
                delivered = 0
                drain_started = time.monotonic()
                while not self._stop.is_set() and delivered < 64:
                    # Maintenance holds may be activated while a handler is
                    # running; do not start another delivery after the hold.
                    if self._maintenance_paused(): break
                    claim = self.store.claim_outbox(lease_ms=self.lease_ms)
                    if claim is None: break
                    self._pending += 1
                    try: self._deliver(claim)
                    finally: self._pending -= 1
                    delivered += 1
                    if time.monotonic() - drain_started >= self.reconcile_interval:
                        break
            except Exception as exc:
                self._set_error(f"{type(exc).__name__}: {exc}")
            self._wake.wait(self.reconcile_interval)

    def _maintenance_paused(self):
        if self.admission_root is None:
            return False
        try:
            import admission
        except ModuleNotFoundError:
            from tooling.agent_workflow import admission
        # Ordinary update holds intentionally remain intake-only.
        hold = admission.maintenance(self.admission_root)
        return bool(hold and (hold.get('owner') in ('workflow-stalled-repair', 'workflow-runtime-update') or hold.get('owner') is None))

    def _deliver(self, claim):
        payload, event_id = claim["payload"], claim["event_id"]
        kind = event_kind(payload)
        if kind in ("ATTEMPT_CHECKING", "ATTEMPT_REVIEWING", "ATTEMPT_STOPPING"):
            self.notify_committed()
            self.store.acknowledge_outbox(event_id, claim["claim_generation"]); return
        if kind == "BOARD_STATE_CHANGED":
            self.notify_committed()
            self.store.acknowledge_outbox(event_id, claim["claim_generation"]); return
        if kind == "INCIDENT_PAGE_PROJECTION":
            self.notify_committed()
            self.store.acknowledge_outbox(event_id, claim["claim_generation"]); return
        handler = self.handlers.get(kind)
        if handler is None:
            self._set_error(f"handler unavailable for {kind or 'unknown event'}")
            return
        try:
            if claim["claim_generation"] > 1:
                observation = handler.observe(event_id, payload)
                if observation == "APPLIED":
                    self.store.acknowledge_outbox(event_id, claim["claim_generation"]); return
                if observation == "SETTLED":
                    if kind != "TASK_READY":
                        self._incident(event_id, "DISPATCH_EFFECT_UNKNOWN", "invalid-local-settlement")
                        self._set_error(f"event {event_id}: local settlement is only valid for TASK_READY"); return
                    self.store.acknowledge_outbox(event_id, claim["claim_generation"])
                    self._set_error(None); return
                if observation != "ABSENT":
                    self._incident(event_id, "DISPATCH_EFFECT_UNKNOWN", "observe")
                    self._set_error(f"event {event_id}: external effect state unknown (observe)"); return
            started = time.monotonic()
            self._record_latency(event_id, started)
            disposition = handler.dispatch(event_id, payload)
            if disposition == "PENDING":
                return
            if disposition == "SETTLED":
                if kind != "TASK_READY":
                    self._incident(event_id, "DISPATCH_EFFECT_UNKNOWN", "invalid-local-settlement")
                    self._set_error(f"event {event_id}: local settlement is only valid for TASK_READY"); return
                self.store.acknowledge_outbox(event_id, claim["claim_generation"])
                self._set_error(None); return
            if disposition in {"UNKNOWN", "WAIT_FOR_RECOVERY"}:
                self._incident(event_id, "DISPATCH_EFFECT_UNKNOWN", "dispatch")
                self._set_error(f"event {event_id}: external effect state unknown (dispatch)"); return
            self.store.acknowledge_outbox(event_id, claim["claim_generation"])
            self._set_error(None)
        except Exception as exc:
            self._incident(event_id, "DISPATCH_ADAPTER_ERROR", "dispatch")
            self._set_error(f"event {event_id}: {type(exc).__name__}: {exc}")

    def _record_latency(self, event_id, started):
        event=self.store.get_event(event_id)
        if event:
            sample={"event_id":event_id,"event_created_to_handler_start_ms":max(0, int(time.time()*1000)-event['created_ms']), "post_admission_to_dispatch_ms":None}
            with self._metrics_lock:
                self._samples.append(sample); self._samples=self._samples[-256:]

    def _incident(self, event_id, code, operation):
        try:
            event=self.store.get_event(event_id)
            if event is None: return
            payload=event['payload']
            rid=payload.get('run_id') or event.get('run_id')
            gen=payload.get('evaluation_generation') or event.get('generation')
            incident_id='incident_dispatch_'+hashlib.sha256((event_id+'\0'+code).encode()).hexdigest()[:24]
            incident={"contract_version":1,"incident_id":incident_id,"code":code,"message":f"Outbox event {event_id} {operation} could not be safely completed.","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[],"run_id":rid,"evaluation_generation":gen}
            self.store.record_incident(incident)
        except Exception as exc:
            self._set_error(f"event {event_id}: incident persistence failed ({type(exc).__name__})")

    def _author_diagnostics(self, run, log_artifact_id=None, log_offset=0):
        generation=run.get('current_attempt')
        tasks=self.store.list_tasks(run['run_id'],generation)
        checks=[t for t in tasks if t.get('kind')=='CHECK']
        completed=[t for t in checks if t.get('state')=='COMPLETED']
        findings=[]; logs=[]; candidates=[]; omitted=0; skipped_checks=max(0,len(completed)-4)
        secrets=[v for mapping in (self.bindings,self.author_worktrees,self.review_bindings,self.owner_bindings)
                 for v in mapping if isinstance(v,str)]
        secrets += [os.environ.get('OPENCODE_PASSWORD','')]
        for task in completed[:4]:
            result_record=task.get('result') or {}
            bounded_findings=[]
            for finding in (result_record.get('findings') or [])[:4]:
                if isinstance(finding,dict):
                    bounded_findings.append({k:workflow_task_artifacts.sanitize_text(str(finding[k]),secrets)[:128]
                        for k in ('code','message') if k in finding})
                elif isinstance(finding,str): bounded_findings.append(workflow_task_artifacts.sanitize_text(finding,secrets)[:128])
            error=None
            if result_record.get('decision')=='ERROR':
                incident=self.store.get_task_incident(task.get('task_id'))
                if (isinstance(incident,dict) and incident.get('task_id')==task.get('task_id')
                        and incident.get('run_id')==run['run_id']
                        and incident.get('evaluation_generation')==generation):
                    error=incident
            findings.append({'task_id':str(task.get('task_id',''))[:128],'decision':str(result_record.get('decision',''))[:32],
                'findings':bounded_findings,'error_report':{k:workflow_task_artifacts.sanitize_text(str(error[k]),secrets)[:256]
                    for k in ('code','message') if k in error} if isinstance(error,dict) else None})
            task_logs=[]
            for ref in result_record.get('artifact_refs',[]):
                stored=self.store.get_artifact(ref)
                artifact=(stored or {}).get('artifact')
                if artifact and artifact.get('artifact_id')==ref and artifact.get('type')=='LOG':
                    artifact=dict(artifact,input_digest=(stored or {}).get('input_digest'))
                    task_logs.append(artifact)
            if len(task_logs)>4: omitted+=len(task_logs)-4; task_logs=task_logs[-4:]
            candidates.extend((task,artifact) for artifact in task_logs)
        if log_artifact_id is not None:
            selected=[(task,artifact) for task,artifact in candidates if artifact.get('artifact_id')==log_artifact_id]
            if not selected: return {'state':'available' if completed else 'pending','checks':findings,'skipped_checks':skipped_checks,'logs':[], 'skipped_logs':0,'log_error':'artifact_not_available'}
            candidates=selected[:1]
        elif len(candidates)>4:
            omitted+=len(candidates)-4; candidates=candidates[-4:]
        for task,artifact in candidates:
            logs.append({'task_id':task.get('task_id'),**workflow_task_artifacts.read_log_excerpt(
                artifact,artifact_dir=self.artifact_dir,task_id=task.get('task_id'),run_id=run['run_id'],
                input_digest=run.get('input_digest'),secret_values=secrets,
                **({'offset':log_offset} if log_artifact_id is not None else {}))})
        result={'state':'available' if completed else 'pending','checks':findings,'skipped_checks':skipped_checks,'logs':logs,
            'skipped_logs':omitted,'redaction_note':'Limited credential redaction; not a universal secret detector.'}
        # Leave room for the enclosing run/status response and use the same UTF-8
        # JSON encoding policy as Starlette's response renderer.
        while len(json.dumps(result,ensure_ascii=False,separators=(',',':')).encode('utf-8'))>48*1024 and result['logs']:
            result['logs'].pop(0); result['skipped_logs']+=1
        return result

    def app(self):
        try:
            import workflow_coordinator_http_mcp
        except ModuleNotFoundError:
            from tooling.agent_workflow import workflow_coordinator_http_mcp
        http_mcp = workflow_coordinator_http_mcp.CoordinatorHTTPMCP(self, None)
        @asynccontextmanager
        async def lifespan(app):
            self.start()
            try:
                await http_mcp.start()
                yield
            finally:
                await http_mcp.stop()
                self.stop()

        async def submit(request: Request):
            binding = self._binding(request)
            if isinstance(binding, JSONResponse): return binding
            try:
                try: body = await _read_json_body(request)
                except Exception: return JSONResponse(_error("MALFORMED_REQUEST", "Request body must be strict UTF-8 JSON."), 400)
                if not isinstance(body, dict) or set(body) != {"idempotency_key", "request"} or not isinstance(body["idempotency_key"], str):
                    return JSONResponse(_error("MALFORMED_REQUEST", "Expected idempotency_key and request."), 400)
                key, raw = body["idempotency_key"], body["request"]
                if not isinstance(binding, CoordinatorBinding): return JSONResponse(_error("INVALID_BINDING", "Configured identity binding is invalid."), 503)
                context = binding.context
                def identity_guard():
                    if isinstance(context,workflow_scope.AuthorWorktreeContext) and context.authenticated_author['session_id'] in getattr(self,'configured_reviewer_sessions',set()):
                        raise PermissionError('configured reviewer sessions cannot act as authors')
                    verified=self.identity_verifier(context)
                    if not verified: raise RuntimeError('configured session identity could not be verified')
                caller = binding.caller
                admission_start=time.monotonic()
                def intake_guard():
                    if self.admission_root is not None:
                        try: import admission
                        except ModuleNotFoundError: from tooling.agent_workflow import admission
                        held=admission.maintenance(self.admission_root)
                        if held: raise IntakeHeld(held['note'])
                def resolve_context(req):
                    try:
                        return workflow_scope.effective_context(self.store,context,caller=caller,
                            submission_idempotency_key=key,sprint_id=req.get('sprint_id'),policy_digest=req.get('policy_digest'))
                    except workflow_scope.ScopeError as exc:
                        raise workflow_submission.workflow_preflight.PreflightFailure('FAIL',exc.code,exc.field,str(exc),
                            'Register this exact feature scope and retry with its submission key.') from exc
                receipt = await anyio.to_thread.run_sync(lambda: workflow_submission.submit_submission(self.store, caller=caller, idempotency_key=key, request=raw, context=context, artifact_dir=self.artifact_dir, context_resolver=resolve_context, admission_guard=intake_guard,identity_guard=identity_guard))
                admission_ms=(time.monotonic()-admission_start)*1000
                with self._metrics_lock:
                    self._samples.append({"metric":"preflight_and_admission_ms","duration_ms":admission_ms}); self._samples=self._samples[-256:]
                self.notify_committed()
                response={"response":receipt}
                guidance=_submission_guidance(receipt)
                if guidance is not None: response['submission_guidance']=guidance
                return JSONResponse(response)
            except workflow_store.ArtifactIdentityConflict as exc:
                return JSONResponse(_error("ARTIFACT_ID_CONFLICT", "An artifact ID is already bound to different evidence.", "PREPARE_NEW_SCOPE", field=exc.field,
                    recovery="Preserve the existing evidence. Assign fresh artifact IDs to changed content/producers, update criteria.evidence_refs, then register a fresh scope and submit using fresh keys; scope inputs already frozen cannot be edited."), 409)
            except workflow_store.IdempotencyConflict:
                return JSONResponse(_error("IDEMPOTENCY_CONFLICT", "Key was already used with different input."), 409)
            except PermissionError:
                return JSONResponse(_error('IDENTITY_DENIED','Configured session identity is not authorized.'),403)
            except RuntimeError as exc:
                if str(exc)=='configured session identity could not be verified':
                    return JSONResponse(_error('IDENTITY_UNAVAILABLE','Configured session identity could not be verified.'),503)
                return JSONResponse(_error("SUBMISSION_UNAVAILABLE", _sanitize_exception(exc,request,self)), 503)
            except IntakeHeld as exc:
                return JSONResponse(_error("INTAKE_HELD", _sanitize_exception(exc,request,self)),503)
            except Exception as exc:
                return JSONResponse(_error("SUBMISSION_UNAVAILABLE", _sanitize_exception(exc,request,self)), 503)

        def registration_receipt(caller,key,request_digest=None):
            return self.store.get_author_scope_registration_receipt(caller=caller,command_key=key,payload_digest=request_digest)

        async def verify_author(binding):
            if isinstance(binding.context, workflow_scope.AuthorWorktreeContext) and binding.context.authenticated_author['session_id'] in getattr(self,'configured_reviewer_sessions',set()):
                return JSONResponse(_error('IDENTITY_DENIED','Configured reviewer sessions cannot act as authors.'),403)
            try: verified=await anyio.to_thread.run_sync(self.identity_verifier,binding.context)
            except PermissionError: return JSONResponse(_error('IDENTITY_DENIED','Configured session identity is not authorized.'),403)
            except Exception: return JSONResponse(_error('IDENTITY_UNAVAILABLE','Configured session identity could not be verified.'),503)
            return None if verified else JSONResponse(_error('IDENTITY_UNAVAILABLE','Configured session identity could not be verified.'),503)

        async def scope_register(request):
            binding=self._binding(request)
            if isinstance(binding,JSONResponse):return binding
            if not isinstance(binding,CoordinatorBinding):return JSONResponse(_error('INVALID_BINDING','Configured identity binding is invalid.'),503)
            try: body=await _read_json_body(request,allow_float=True)
            except Exception:return JSONResponse(_error('MALFORMED_REQUEST','Request body must be bounded strict UTF-8 JSON.','CORRECT_SCOPE'),400)
            if not isinstance(body,dict) or set(body)!={'idempotency_key','scope'} or not isinstance(body.get('idempotency_key'),str) or not body['idempotency_key'].strip() or not isinstance(body.get('scope'),dict):
                return JSONResponse(_error('MALFORMED_REQUEST','Expected nonempty registration idempotency_key and strict scope object.','CORRECT_SCOPE'),400)
            if len(body['idempotency_key'])>2000:return JSONResponse(_error('MALFORMED_REQUEST','Registration key exceeds limit.','CORRECT_SCOPE'),400)
            try:
                request_digest=_scope_payload_digest(body['scope'])
                prior=registration_receipt(binding.caller,body['idempotency_key'],request_digest)
                if prior is not None:
                    denied=await verify_author(binding)
                    if denied:return denied
                    return JSONResponse({'response':prior})
            except workflow_store.IdempotencyConflict:
                denied=await verify_author(binding)
                if denied:return denied
                return JSONResponse(_error('IDEMPOTENCY_CONFLICT','Registration key was used with a different scope.','USE_NEW_REGISTRATION_KEY'),409)
            except Exception:return JSONResponse(_error('SCOPE_STATUS_UNAVAILABLE','Could not verify prior registration receipt.','CHECK_SCOPE_STATUS'),503)
            if self.admission_root is not None:
                try:
                    try: import admission
                    except ModuleNotFoundError: from tooling.agent_workflow import admission
                    held=admission.maintenance(self.admission_root)
                except Exception:return JSONResponse(_error('ADMISSION_UNAVAILABLE','Admission guard could not be checked.','WAIT_FOR_RECOVERY'),503)
                if held:return JSONResponse(_error('INTAKE_HELD',held['note'],'WAIT_FOR_ADMISSION'),503)
            denied=await verify_author(binding)
            if denied:return denied
            try:
                result=await anyio.to_thread.run_sync(lambda:workflow_scope.register_author_scope(self.store,binding.context,binding.caller,body['scope'],body['idempotency_key'],artifact_dir=self.artifact_dir))
                return JSONResponse({'response':result})
            except workflow_scope.ScopeError as exc:
                result={'status':'REJECTED','result':'FAIL','code':exc.code,'field':exc.field,'message':'Scope input does not satisfy the bound version-1 feature contract.',
                        'next_action':'CORRECT_SCOPE','recovery':str(getattr(exc,'recovery',None) or 'Correct the indicated scope field using current bound status and fresh keys.')[:500],'payload_digest':request_digest}
                try:result=await anyio.to_thread.run_sync(lambda:self.store.record_author_scope_rejection(caller=binding.caller,
                    command_key=body['idempotency_key'],payload_digest=request_digest,response=result))
                except workflow_store.IdempotencyConflict:return JSONResponse(_error('IDEMPOTENCY_CONFLICT','Registration key was used with different scope input.','USE_NEW_REGISTRATION_KEY'),409)
                except Exception:return JSONResponse(_error('SCOPE_REGISTRATION_UNAVAILABLE','Validation failure receipt could not be persisted; recover with scope_status using the same key.','CHECK_SCOPE_STATUS'),503)
                return JSONResponse({'response':result,'recovery':'Correct scope fields and register with a fresh registration key; if a scope is already frozen, preserve it rather than editing it.'})
            except workflow_store.IdempotencyConflict as exc:
                result={'status':'REJECTED','result':'FAIL','code':'SUBMISSION_SCOPE_CONFLICT','field':'submission_idempotency_key',
                        'message':'Submission key is already bound to a different frozen scope.','next_action':'USE_NEW_SUBMISSION_KEY',
                        'recovery':'Preserve the frozen scope. Use a fresh registration key and a fresh submission key for corrected scope inputs.','payload_digest':request_digest}
                try:result=await anyio.to_thread.run_sync(lambda:self.store.record_author_scope_rejection(caller=binding.caller,
                    command_key=body['idempotency_key'],payload_digest=request_digest,response=result))
                except workflow_store.IdempotencyConflict:return JSONResponse(_error('IDEMPOTENCY_CONFLICT','Registration key was used with different scope input.','USE_NEW_REGISTRATION_KEY'),409)
                except Exception:return JSONResponse(_error('SCOPE_REGISTRATION_UNAVAILABLE','Conflict receipt could not be persisted; recover with scope_status using the same key.','CHECK_SCOPE_STATUS'),503)
                return JSONResponse({'response':result,'recovery':'The scope already binds the submission identity. Preserve it; prepare a fresh registration and submission with new keys rather than editing frozen inputs.'})
            except Exception:return JSONResponse(_error('SCOPE_REGISTRATION_UNAVAILABLE','Scope registration outcome may be uncertain. Use scope_status with the same registration key.','CHECK_SCOPE_STATUS'),503)

        async def scope_status(request):
            binding=self._binding(request)
            if isinstance(binding,JSONResponse):return binding
            if not isinstance(binding,CoordinatorBinding):return JSONResponse(_error('INVALID_BINDING','Configured identity binding is invalid.'),503)
            denied=await verify_author(binding)
            if denied:return denied
            if set(request.query_params)!={'idempotency_key'} or not request.query_params.get('idempotency_key') or len(request.query_params.get('idempotency_key',''))>2000:
                return JSONResponse(_error('INVALID_QUERY','Exactly one bounded idempotency_key is required.','CORRECT_SCOPE_KEY'),400)
            try: receipt=registration_receipt(binding.caller,request.query_params['idempotency_key'])
            except Exception:return JSONResponse(_error('SCOPE_STATUS_UNAVAILABLE','Scope receipt lookup failed. Retry this same status lookup.','CHECK_SCOPE_STATUS'),503)
            if receipt is None:return JSONResponse(_error('SCOPE_NOT_REGISTERED','No registration receipt exists for this caller and key.','REGISTER_SCOPE_WITH_SAME_KEY'),404)
            return JSONResponse({'response':receipt})

        async def status(request: Request):
            binding = self._binding(request)
            if isinstance(binding, JSONResponse): return binding
            if isinstance(binding,CoordinatorBinding) and isinstance(binding.context,workflow_scope.AuthorWorktreeContext):
                denied=await verify_author(binding)
                if denied:return denied
            if len(request.query_params.multi_items()) != len(set(k for k,_ in request.query_params.multi_items())):
                return JSONResponse(_error("INVALID_QUERY", "Duplicate status query parameter."), 400)
            if set(request.query_params) - {"run_id", "idempotency_key", "include_diagnostics", "log_artifact_id", "log_offset"}:
                return JSONResponse(_error("INVALID_QUERY", "Unknown status query parameter."), 400)
            diagnostic_raw=request.query_params.get('include_diagnostics','false')
            if diagnostic_raw not in ('true','false'):
                return JSONResponse(_error('INVALID_QUERY','include_diagnostics must be true or false.'),400)
            include_diagnostics=diagnostic_raw=='true'
            artifact_id=request.query_params.get('log_artifact_id'); offset_raw=request.query_params.get('log_offset')
            if artifact_id is not None and (not artifact_id or len(artifact_id)>256):
                return JSONResponse(_error('INVALID_QUERY','log_artifact_id must be a nonempty artifact ID of at most 256 characters.'),400)
            if offset_raw is not None and artifact_id is None:
                return JSONResponse(_error('INVALID_QUERY','log_offset requires log_artifact_id.'),400)
            if (artifact_id is not None or offset_raw is not None) and not include_diagnostics:
                return JSONResponse(_error('INVALID_QUERY','Log selection requires include_diagnostics=true.'),400)
            if offset_raw is not None and (len(offset_raw)>8 or not offset_raw.isascii() or not offset_raw.isdigit()):
                return JSONResponse(_error('INVALID_QUERY','log_offset must be a nonnegative byte offset.'),400)
            log_offset=int(offset_raw or '0')
            if log_offset>16*1024*1024: return JSONResponse(_error('INVALID_QUERY','log_offset exceeds the log size limit.'),400)
            if include_diagnostics and not (request.query_params.get('run_id') or request.query_params.get('idempotency_key')):
                return JSONResponse(_error('INVALID_QUERY','Diagnostics require run_id or idempotency_key.'),400)
            if artifact_id is not None and not (request.query_params.get('run_id') or request.query_params.get('idempotency_key')):
                return JSONResponse(_error('INVALID_QUERY','Log selection requires run_id or idempotency_key.'),400)
            key = request.query_params.get('idempotency_key')
            run_id = request.query_params.get('run_id')
            receipt = self.store.get_submission(binding.caller, key) if key else None
            run_id = run_id or (receipt.get('run_id') if receipt and receipt.get('status') == 'ACCEPTED' else None)
            result = self.store.get_run(run_id) if run_id else None
            if result:
                run = result['run']
                owner = run.get('author') or {}
                same_author=owner==binding.context.authenticated_author
                grant=None
                if not same_author and run_id and isinstance(binding.context,workflow_scope.AuthorWorktreeContext):
                    grant=self.store.get_author_successor_grant(run_id=run_id,successor_session_id=binding.context.authenticated_author['session_id'])
                if not same_author and (not grant or Path(grant.get('worktree','')).resolve()!=Path(binding.context.repo).resolve()):
                    return JSONResponse(_error('FORBIDDEN','Run is outside this configured author/worktree.'),403)
                if Path(owner.get('worktree', '')).resolve() != Path(binding.context.repo).resolve():
                    return JSONResponse(_error('FORBIDDEN', 'Run is outside this configured author/worktree.'), 403)
            if key and receipt is None and result is None:
                return JSONResponse({"run":None,"attempt":None,"submission":None,"freshness":"not_checked","coordinator":self.summary(),
                    "next_action":"CHECK_SUBMISSION_STATUS","recovery":"No receipt is visible for this caller/key. This does not prove artifact IDs are unused; replay the same request/key only if the outcome is uncertain, otherwise prepare corrected evidence with fresh artifact IDs and fresh scope/submission keys."})
            if receipt and receipt.get('status') == 'REJECTED' and result is None:
                response={"run":None,"attempt":None,"submission":receipt,"freshness":"not_checked","coordinator":self.summary(),
                    "next_action":receipt.get('next_action') or 'REPORT_ERROR',"recovery":"The REJECTED receipt is immutable. Follow its finding; do not infer artifact IDs or keys are unused or overwrite this receipt."}
                guidance=_submission_guidance(receipt)
                if guidance is not None: response['submission_guidance']=guidance
                return JSONResponse(response)
            response={"run": result['run'] if result else None, "attempt": result['attempt'] if result else None, "submission": receipt, "freshness": "not_checked", "coordinator": self.summary()}
            if result and result['attempt'].get('result')=='ERROR':
                retired=await anyio.to_thread.run_sync(lambda:self.store.is_settled_error_head_retired(run_id))
                head=await anyio.to_thread.run_sync(lambda:self.store.is_current_feature_head(run_id))
                response['continuation']={'retired_head':retired,
                    'next_action':'PREPARE_FRESH_SUBMISSION' if retired and not head else 'WAIT_FOR_RECOVERY'}
            elif result and result['attempt'].get('result')=='FAIL':
                head=await anyio.to_thread.run_sync(lambda:self.store.is_current_feature_head(run_id))
                response['continuation']={'retired_head':False,'next_action':'FIX_FINDINGS_AND_REPLACE' if head else 'HISTORY_ONLY'}
            if isinstance(binding.context,workflow_scope.AuthorWorktreeContext):
                author=binding.context.authenticated_author
                def project_successions():
                    projected=[]
                    for grant in self.store.list_author_successor_grants(successor_session_id=author['session_id'],worktree=binding.context.repo):
                        saved=self.store.get_run(grant['run_id'])
                        if not saved: continue
                        result_name=saved['attempt'].get('result')
                        head=self.store.is_current_feature_head(grant['run_id'])
                        retired=self.store.is_settled_error_head_retired(grant['run_id'])
                        action=('PREPARE_FRESH_SUBMISSION' if result_name=='ERROR' and retired and not head else
                                'WAIT_FOR_RECOVERY' if result_name=='ERROR' else
                                'FIX_FINDINGS_AND_REPLACE' if result_name=='FAIL' and head else 'HISTORY_ONLY')
                        projected.append({'run_id':grant['run_id'],'evaluation_generation':grant['evaluation_generation'],
                            'predecessor_session_id':grant['predecessor_session_id'],'worktree':grant['worktree'],
                            'continuation':action,'retired_head':retired and not head})
                    return projected
                response['author_successions']=await anyio.to_thread.run_sync(project_successions)
            if not include_diagnostics:
                response['diagnostics_hint']='To inspect your validation log excerpts, call status(include_diagnostics=true, run_id=...).'
            elif result:
                # The opt-in query may hash/redact large files. Keep that work off
                # the ASGI event loop; ownership was verified above before dispatch.
                run=result['run']
                response['diagnostics']=await anyio.to_thread.run_sync(
                    lambda: self._author_diagnostics(run,artifact_id,log_offset))
            if isinstance(binding.context, workflow_scope.AuthorWorktreeContext) and not key and not request.query_params.get('run_id'):
                floors=binding.context.floors
                response['author_context']={'derived_caller':binding.caller,'authenticated_author':binding.context.authenticated_author,
                    'repo':binding.context.repo,'typed_floors':{'required_packs':sorted(floors['required_packs']),
                    'selectors':{k:sorted(v) for k,v in floors['selectors'].items()},'review_roles':sorted(floors['roles']),
                    'owner_exceptions':floors['exceptions'],'config_overrides':floors['overrides'],
                    'review_envelope_limit':floors['review_limit']},'publication_receipt_path':binding.context.publication_receipt_path,
                    'publication_receipt_sha256':binding.context.publication_receipt_sha256}
            return JSONResponse(response)

        async def acknowledge_result(request: Request):
            binding = self._binding(request)
            if isinstance(binding, JSONResponse): return binding
            if not isinstance(binding, CoordinatorBinding):
                return JSONResponse(_error("INVALID_BINDING", "Configured author binding is invalid."), 503)
            if isinstance(binding.context, workflow_scope.AuthorWorktreeContext):
                denied=await verify_author(binding)
                if denied:return denied
            try:
                body = workflow_store.strict_loads((await request.body()).decode("utf-8"))
                if not isinstance(body, dict): raise ValueError("acknowledgement must be an object")
                if "author_session_id" in body:
                    raise ValueError("author_session_id is server-bound")
                ack = dict(body, author_session_id=binding.context.authenticated_author["session_id"])
                result = await anyio.to_thread.run_sync(lambda: self.store.acknowledge_delivery(
                    ack, authenticated_session=binding.context.authenticated_author["session_id"]))
                self.notify_committed()
                response={"accepted": True, "disposition": result["disposition"]}
                saved_event=self.store.get_event(ack["event_id"])
                if saved_event and (saved_event["payload"].get("control_kind") or saved_event["payload"].get("result_id")):
                    response["next_action"]=saved_event["payload"].get("next_action") or result.get("next_action")
                return JSONResponse(response)
            except (workflow_store.ProtocolError, ValueError, TypeError) as exc:
                forged_session=isinstance(locals().get("body"),dict) and "author_session_id" in body
                return JSONResponse(_error("INVALID_RESULT_ACK", "Acknowledgement does not match the version-1 result contract.", "CORRECT_ACK",
                    field=("author_session_id" if forged_session else getattr(exc, "field", None) or "acknowledgement"),
                    recovery=("Remove caller-supplied author_session_id; it is server-bound. Use the current chat session and original delivered result/event identity." if forged_session else getattr(exc,"recovery",None) or "Use the acknowledgement fields and original result identity delivered for this event; do not invent or alter the result.")), 400)
            except workflow_store.IdempotencyConflict:
                return JSONResponse(_error("ACK_CONFLICT", "This acknowledgement identity is already bound to a different result.", "CHECK_RESULT_STATUS",
                    recovery="The ACK is immutable. Inspect run status for run health; recover only by replaying the exact delivered ACK with the same command_id. Do not change event/result identities."), 409)
            except workflow_store.StaleCommand as exc:
                return JSONResponse(_error("INVALID_RESULT_ACK", "Acknowledgement does not match the delivered event, result, or current author binding.", "CORRECT_ACK",
                    field="result_digest", recovery="Remove any caller-supplied author_session_id. Use only the original delivered event/result fields and current chat session; do not invent or alter the ACK."),400)
            except Exception:
                return JSONResponse(_error("ACKNOWLEDGEMENT_UNAVAILABLE", "Acknowledgement outcome could not be confirmed.", "CHECK_RESULT_STATUS",
                    recovery="Inspect run status for run health. The ACK may have committed; recover only by replaying the exact delivered ACK with the same command_id. Do not change event/result identities."), 503)

        async def health(request: Request):
            auth = self._binding(request)
            if isinstance(auth, JSONResponse):
                token=request.headers.get("authorization", "")
                if token.startswith("Bearer ") and token[7:] in self.owner_bindings:
                    auth=self.owner_bindings[token[7:]]
                elif token.startswith('Bearer ') and token[7:] in self.author_worktrees:
                    # Administrative health is bearer-bound, not an author admission route.
                    auth=True
            return auth if isinstance(auth, JSONResponse) else JSONResponse(self.summary())

        async def integration_status_page(request: Request):
            if not _loopback_request(request):
                return JSONResponse(_error('FORBIDDEN','Local loopback access required.'),403)
            if len(self.owner_bindings) != 1:
                return JSONResponse(_error('OWNER_UI_UNAVAILABLE','Exactly one configured local owner is required.'),503)
            empty=workflow_board.project({'available':False,'reason':'not_connected','records':[],'history':[]},worktree='')
            response=HTMLResponse(gate_progress.workflow_page(empty))
            response.set_cookie('ms14_owner_ui',self._owner_ui_cookie,path='/v1',httponly=True,samesite='strict',secure=False)
            response.headers['Cache-Control']='no-store'
            response.headers['Referrer-Policy']='no-referrer'
            response.headers['Content-Security-Policy']="frame-ancestors 'none'"
            return response

        async def board_events(request: Request):
            binding=owner_binding(request)
            if binding is None: return JSONResponse(_error('UNAUTHORIZED','Configured owner bearer token required.'),403)
            return StreamingResponse(self._board_event_stream(),media_type='text/event-stream',headers={
                'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Accel-Buffering':'no'})

        async def authorize_review(request, binding, task_id, task=None):
            sid=request.headers.get('x-ms14-reviewer-session','')
            if not isinstance(sid,str) or not sid or len(sid)>2000:
                return JSONResponse(_error('FORBIDDEN','Reviewer session assignment required.'),403)
            task=task or self.store.get_task(task_id)
            if task is None: return JSONResponse(_error('FORBIDDEN','Task is outside this reviewer assignment.'),403)
            planner=self.review_results.planner
            configured=getattr(planner,'reviewers',{}).get(binding.review_role)
            transport=getattr(self.review_pipeline,'transport',None)
            if (not isinstance(configured,dict) or configured.get('worktree')!=binding.worktree or
                    task.get('kind')!='REVIEW' or task.get('assigned_session_id')!=sid or
                    task.get('logical_key')!='review:'+binding.review_role):
                return JSONResponse(_error('FORBIDDEN','Task is outside this reviewer assignment.'),403)
            try:
                run=self.store.get_run(task['run_id'])
                run=run.get('run',run)
                if task.get('evaluation_generation')!=run.get('current_attempt'):
                    return JSONResponse(_error('FORBIDDEN','Review task is stale.','REFRESH_TASK_STATUS',recovery="Refresh status for this assigned task and use its current generation; do not claim another task."),403)
                assignment=planner.build_assignment(task_id)
                if transport is None: raise RuntimeError('review transport unavailable')
                status,reply=await anyio.to_thread.run_sync(lambda:transport.request(transport.server,'GET','/api/session/'+urllib.parse.quote(sid)))
                if status>=500:
                    return JSONResponse(_error('REVIEW_UNAVAILABLE','Reviewer session identity is unavailable.'),503)
                if status!=200 or not transport.session_matches_assignment(task,assignment,reply):
                    return JSONResponse(_error('FORBIDDEN','Session identity does not match this assignment.'),403)
            except Exception:
                return JSONResponse(_error('REVIEW_UNAVAILABLE','Reviewer session identity is unavailable.'),503)
            return task,sid

        async def review(request: Request, operation_name):
            token = request.headers.get("authorization", "")
            binding = self.review_bindings.get(token[7:]) if token.startswith("Bearer ") else None
            if binding is None:
                return JSONResponse(_error("UNAUTHORIZED", "Configured reviewer bearer token required."), 403)
            if self.review_results is None:
                return JSONResponse(_error("REVIEW_UNAVAILABLE", "Review result service is not configured."), 503)
            try:
                payload = workflow_store.strict_loads((await request.body()).decode("utf-8"))
            except Exception:
                return JSONResponse(_error("MALFORMED_REQUEST", "Request body must be strict UTF-8 JSON."), 400)
            if not isinstance(payload, dict) or not isinstance(payload.get("task_id"), str):
                return JSONResponse(_error("MALFORMED_REQUEST", "Review payload must identify a task."), 400)
            if "session_id" in payload:
                return JSONResponse(_error("MALFORMED_REQUEST", "Remove session_id from result/error JSON; session identity is supplied separately by the verified SDK tool."), 400)
            authorized = await authorize_review(request,binding,payload["task_id"])
            if isinstance(authorized,JSONResponse): return authorized
            task,session_id=authorized
            operation = {"claim": self.review_results.claim, "complete": self.review_results.complete,
                         "error": self.review_results.error}[operation_name]
            if operation_name == "claim":
                payload["session_id"] = session_id
            try:
                result = await anyio.to_thread.run_sync(lambda: operation(payload, session_id=session_id))
                if result.get("accepted"):
                    self.notify_committed()
                    if operation_name == 'claim':
                        try:
                            guide = await anyio.to_thread.run_sync(self.review_results.planner.review_guide, payload['task_id'])
                        except workflow_store.ProtocolError as exc:
                            message=workflow_task_artifacts.sanitize_text(str(exc))[:300]
                            template=await anyio.to_thread.run_sync(lambda: self.review_results.planner.error_report_template(
                                payload['task_id'],error_code='FROZEN_INPUTS_UNAVAILABLE',message=message))
                            guide_error=_error('FROZEN_INPUTS_UNAVAILABLE',message,'REPORT_ERROR',field='task_id',
                                    recovery='The claim is accepted. Write this exact JSON to the assigned error path, then call report_error for this same task using the verified session_id tool argument; do not submit a review verdict.',
                                    )['error']
                            guide_error['error_report_template']=template
                            return JSONResponse({'accepted':True,'claim_accepted':True,'task_id':payload['task_id'],'guide_error':guide_error})
                        except Exception as exc:
                            message=f'reviewer guide generation failed ({type(exc).__name__})'
                            template=await anyio.to_thread.run_sync(lambda: self.review_results.planner.error_report_template(
                                payload['task_id'],error_code='FROZEN_INPUTS_UNAVAILABLE',message=message))
                            guide_error=_error('FROZEN_INPUTS_UNAVAILABLE',message,'REPORT_ERROR',field='task_id',
                                    recovery='The claim is accepted. Write this exact JSON to the assigned error path, then call report_error for this same task using the verified session_id tool argument; do not submit a review verdict.',
                                    )['error']
                            guide_error['error_report_template']=template
                            return JSONResponse({'accepted':True,'claim_accepted':True,'task_id':payload['task_id'],'guide_error':guide_error})
                        result = {**result, 'review_guide': guide,
                                  'review_instruction': 'Read the complete review guide artifact before submitting your canonical review_result.'}
                return JSONResponse(result)
            except Exception:
                return JSONResponse(_error("REVIEW_UNAVAILABLE", "Review result service could not persist the request."), 503)

        async def review_claim(request): return await review(request, "claim")
        async def review_complete(request): return await review(request, "complete")
        async def review_error(request): return await review(request, "error")

        async def review_status(request):
            header=request.headers.get("authorization", "")
            binding=self.review_bindings.get(header[7:]) if header.startswith("Bearer ") else None
            if binding is None:
                return JSONResponse(_error("UNAUTHORIZED", "Configured reviewer bearer token required."), 403)
            if set(request.query_params) != {"task_id"} or not request.query_params.get("task_id"):
                return JSONResponse(_error("INVALID_QUERY", "Exactly task_id is required."), 400)
            task=self.store.get_task(request.query_params["task_id"])
            if task is None:
                return JSONResponse(_error("NOT_FOUND", "Review task was not found."), 404)
            authorized=await authorize_review(request,binding,request.query_params["task_id"],task)
            if isinstance(authorized,JSONResponse): return authorized
            try:
                try:
                    import workflow_native_worker
                except ModuleNotFoundError:
                    from tooling.agent_workflow import workflow_native_worker
                frozen_request, frozen_bundle = workflow_native_worker.load_native_inputs(self.store, task["run_id"])
                if frozen_request.get("input_digest") != task.get("input_digest"):
                    raise ValueError("review task input digest differs from accepted submission")
                submission = self.store.get_run_submission(task["run_id"])
                manifest = workflow_native_worker.resolve_generated_manifest(submission, frozen_bundle)
                def artifact_view(artifact):
                    return {key: artifact.get(key) for key in ("artifact_id", "type", "path", "sha256", "size_bytes")}
                assigned_ids={artifact.get("artifact_id") for artifact in frozen_bundle.get("verified_artifacts",[])}
                submitted={binding["artifact"].get("artifact_id"):binding["artifact"] for binding in submission["artifacts"]}
                bound=[]
                for artifact in frozen_bundle.get("verified_artifacts",[]):
                    artifact_id=artifact.get("artifact_id")
                    saved_artifact=submitted.get(artifact_id)
                    if (saved_artifact is None or saved_artifact.get("submission_id")!=submission.get("submission_id") or
                        saved_artifact.get("producer_kind")!="SUBMISSION" or saved_artifact.get("producer_id")!=submission.get("submission_id") or
                        any(saved_artifact.get(key)!=artifact.get(key) for key in ("type","path","sha256","size_bytes"))):
                        raise ValueError("assigned artifact is not an exact accepted submission binding")
                    bound.append(artifact_view(saved_artifact))
                if {item["artifact_id"] for item in bound}!=assigned_ids:
                    raise ValueError("assigned frozen artifact binding is missing")
                inputs = {"source_worktree": frozen_request["source_worktree"],
                          "input_digest": frozen_request["input_digest"],
                          "bundle": artifact_view(workflow_native_worker.resolve_generated_bundle(submission)),
                          "source_manifest": artifact_view(manifest),"bound_artifacts":bound}
            except Exception as exc:
                safe_reason = str(exc).replace("\n", " ")[:240] or type(exc).__name__
                return JSONResponse(_error("FROZEN_INPUTS_UNAVAILABLE", f"Frozen review inputs unavailable: {safe_reason}. Report an ERROR for this task."), 409)
            refs=[]
            run_tasks=self.store.list_tasks(task["run_id"],task["evaluation_generation"])
            check_refs=[ref for candidate in run_tasks if candidate.get("kind")=="CHECK"
                        for ref in (candidate.get("result") or {}).get("artifact_refs",[])]
            for ref in check_refs:
                saved=self.store.get_artifact(ref)
                artifact=(saved or {}).get("artifact",{})
                if artifact.get("type")=="CHECK_RECEIPT":
                    refs.append({"artifact_id":artifact.get("artifact_id"),"type":artifact.get("type"),
                                 "path":artifact.get("path"),"sha256":artifact.get("sha256"),"size_bytes":artifact.get("size_bytes")})
            task_view={k:task.get(k) for k in ("task_id","run_id","state","kind","logical_key","decision","deadline_ms","assigned_session_id")}
            result={"task":task_view,"freshness":"not_checked","artifacts":refs,"inputs":inputs}
            encoded=json.dumps(result,separators=(",",":"),ensure_ascii=False).encode("utf-8")
            if len(encoded)>4096:
                # Never discard registered evidence locators. The immutable guide is
                # the canonical complete inventory and is already assignment-bound.
                try:
                    guide=await anyio.to_thread.run_sync(self.review_results.planner.review_guide,task["task_id"])
                except Exception as exc:
                    safe_reason=str(exc).replace("\n"," ")[:180] or type(exc).__name__
                    return JSONResponse(_error("FROZEN_INPUTS_UNAVAILABLE",f"Frozen review guide unavailable: {safe_reason}. Report an ERROR for this task."),409)
                result={"task":task_view,"freshness":"not_checked","artifacts":[],
                        "inputs":{"source_worktree":inputs["source_worktree"],
                                  "input_digest":inputs["input_digest"],"guide":guide},
                        "artifacts_externalized":True,
                        "review_instruction":"Read the complete review guide artifact for all assigned artifact bindings and readable evidence."}
                encoded=json.dumps(result,separators=(",",":"),ensure_ascii=False).encode("utf-8")
                if len(encoded)>4096:
                    return JSONResponse(_error("FROZEN_INPUTS_UNAVAILABLE", "Frozen review guide locator exceeds the safe status response size; report an ERROR for this task."), 409)
            return JSONResponse(result)

        def owner_binding(request):
            header=request.headers.get("authorization", "")
            if 'authorization' in request.headers:
                if not header.startswith('Bearer '): return None
                return self.owner_bindings.get(header[7:])
            if not _loopback_request(request): return None
            cookie=request.cookies.get('ms14_owner_ui','')
            try:
                if not cookie or len(cookie)>128 or not cookie.isascii() or not hmac.compare_digest(cookie,self._owner_ui_cookie): return None
            except (TypeError, ValueError):
                return None
            origin=request.headers.get('origin')
            if request.method not in ('GET','HEAD'):
                if not origin or origin != _request_origin(request): return None
            elif origin and origin != _request_origin(request): return None
            fetch_site=request.headers.get('sec-fetch-site')
            if fetch_site and fetch_site != 'same-origin': return None
            if len(self.owner_bindings)!=1: return None
            return next(iter(self.owner_bindings.values()))

        def owner_auth(request):
            """Return (binding, local-cookie-auth); bearer auth always wins."""
            binding=owner_binding(request)
            local=('authorization' not in request.headers and binding is not None
                   and _loopback_request(request) and bool(request.cookies.get('ms14_owner_ui')))
            return binding,local

        def configured_owner_roots():
            paths=[getattr(getattr(b,'context',None),'repo',None) for b in self.bindings.values()]
            paths.extend(b.repo for b in self.author_worktrees.values())
            return {str(Path(p).resolve()).casefold() if os.name=='nt' else str(Path(p).resolve()) for p in paths if isinstance(p,str) and p}

        def target_owner(request,binding,run_id):
            """Bind browser-local owner actions to a saved run's configured author root."""
            _,local=owner_auth(request)
            if not isinstance(run_id,str) or not run_id: return None if local else binding
            saved=self.store.get_run(run_id)
            if not saved: return None if local else binding
            target=saved['run'].get('author',{}).get('worktree')
            try: canonical=str(Path(target).resolve())
            except (TypeError,ValueError,OSError): return None
            key=canonical.casefold() if os.name=='nt' else canonical
            if local:
                if key not in configured_owner_roots(): return None
                return OwnerBinding(binding.caller,target,binding.session_id)
            bound=str(Path(binding.worktree).resolve()); bound=bound.casefold() if os.name=='nt' else bound
            return binding if key==bound else None

        def aggregate_board():
            roots=[]; seen_roots=set()
            paths=[getattr(getattr(b,'context',None),'repo',None) for b in self.bindings.values()]
            paths.extend(b.repo for b in self.author_worktrees.values())
            for path in paths:
                if not isinstance(path,str) or not path: continue
                canonical=str(Path(path).resolve()); key=canonical.casefold() if os.name=='nt' else canonical
                if key not in seen_roots: roots.append(canonical); seen_roots.add(key)
            snapshots=[self._board_snapshot_payload(root) for root in roots]
            if not snapshots:
                result=workflow_board.project({'available':False,'reason':'no_configured_author_roots','records':[],'history':[]},worktree='')
                result['coordinator']=self.summary()
                return result
            result=dict(snapshots[0])
            result['columns']={phase:[] for phase in workflow_board.PHASES}
            result['history']=[]; result['faults']=[]
            for snapshot in snapshots:
                for phase in workflow_board.PHASES: result['columns'][phase].extend(snapshot.get('columns',{}).get(phase,[]))
                result['history'].extend(snapshot.get('history',[])); result['faults'].extend(snapshot.get('faults',[]))
            result['system']=dict(snapshots[0].get('system',{}))
            queue_rows=[s.get('system',{}).get('queue',{}) for s in snapshots]
            if all(type(q.get('queued_tasks')) is int for q in queue_rows):
                result['system']['queue']={**queue_rows[0], 'queued_tasks':sum(q['queued_tasks'] for q in queue_rows),
                    'queued_native_tasks':sum(q.get('queued_native_tasks',0) for q in queue_rows),
                    'pending_native_tasks':sum(q.get('pending_native_tasks',0) for q in queue_rows),
                    'scope':'configured_owner_roots','worktree':'configured owner roots'}
            result['worktree']='configured owner roots'
            return result

        async def owner_snapshot(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error("UNAUTHORIZED", "Configured owner bearer token required."),403)
            if set(request.query_params)!={"run_id"} or not request.query_params.get("run_id"):
                return JSONResponse(_error("INVALID_QUERY", "Exactly run_id is required."),400)
            saved=self.store.get_run(request.query_params["run_id"])
            if saved is None: return JSONResponse(_error("NOT_FOUND", "Run was not found."),404)
            run,attempt=saved["run"],saved["attempt"]
            effective=target_owner(request,binding,request.query_params["run_id"])
            if effective is None or (run.get("author",{}).get("worktree") != effective.worktree
                    or run.get("run_kind") != "FEATURE_GATE"):
                return JSONResponse(_error("FORBIDDEN", "Run is outside this owner worktree."),403)
            run_id=request.query_params["run_id"]
            result=self.store.get_terminal_result(run_id,run["current_attempt"])
            submission=self.store.get_run_submission(run_id)
            builds=[]
            for task in self.store.list_tasks(run_id,run["current_attempt"]):
                if task.get("state")=="COMPLETED" and task.get("result",{}).get("decision")=="PASS":
                    for ref in task.get("result",{}).get("artifact_refs",[]):
                        item=self.store.get_artifact(ref)
                        if item and item["artifact"].get("type")=="BUILD": builds.append(item["artifact"])
            refs=[]
            if submission:
                refs=[x["artifact"] for x in submission["artifacts"] if x["artifact"].get("type") in ("SOURCE_MANIFEST","PROVENANCE")]
            public=lambda a:{k:a.get(k) for k in ("artifact_id","type","path","sha256","size_bytes")}
            data={"run":run,"attempt":attempt,"result_available":bool(result),"result":result,
                  "displayed_result_id":(result or {}).get("result_id"),"displayed_result_digest":(result or {}).get("result_digest"),
                  "expected_sequence":run.get("sequence"),"input_digest":run.get("input_digest"),"generation":run.get("current_attempt"),
                  "manual_steps":(submission or {}).get("request",{}).get("manual_steps",[]),"artifacts":[public(x) for x in refs+builds]}
            return JSONResponse(data)

        async def board_snapshot(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error("UNAUTHORIZED", "Configured owner bearer token required."),403)
            if request.query_params: return JSONResponse(_error("INVALID_QUERY", "Board scope comes from the configured owner binding."),400)
            result=await anyio.to_thread.run_sync(aggregate_board if local else lambda:self._board_snapshot_payload(binding.worktree))
            return JSONResponse(result)

        async def board_incident_seen(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error("UNAUTHORIZED", "Configured owner bearer token required."),403)
            incident_id=request.path_params["incident_id"]
            target=binding
            if local:
                incident=self.store.get_incident(incident_id)
                run_id=(incident or {}).get('incident',{}).get('run_id')
                target=target_owner(request,binding,run_id)
                if target is None: return JSONResponse(_error("FORBIDDEN", "Incident is outside configured owner roots."),403)
            status,result=await anyio.to_thread.run_sync(lambda:self._board_seen(incident_id,target.worktree))
            if status==404: return JSONResponse(_error("NOT_FOUND", "Incident was not found."),404)
            if status==403: return JSONResponse(_error("FORBIDDEN", "Incident is outside this owner worktree."),403)
            return JSONResponse({"seen":True,"incident":result})

        async def owner_decision(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error("UNAUTHORIZED", "Configured owner bearer token required."),403)
            if self.owner_service is None: return JSONResponse(_error("OWNER_UNAVAILABLE", "Owner service is not configured."),503)
            try:
                body=workflow_store.strict_loads((await request.body()).decode("utf-8"))
                if not isinstance(body,dict) or set(body)!={"decision","displayed_result_id","displayed_result_digest"} or not isinstance(body["decision"],dict): raise ValueError("Expected decision and displayed result identity.")
                decision=body["decision"]
                if "owner" in decision or "kind" in decision and decision["kind"]!="LOCAL_OWNER": raise ValueError("Owner identity and command kind are server-bound.")
                if local and not isinstance(decision.get('run_id'),str): raise ValueError('decision run_id is required')
                effective=target_owner(request,binding,decision.get('run_id'))
                if effective is None: return JSONResponse(_error("FORBIDDEN", "Run is outside configured owner roots."),403)
                owner={"session_id":binding.session_id,"worktree":effective.worktree,"role":"owner"}
                decision=dict(decision,owner=owner,kind="LOCAL_OWNER")
                response=await anyio.to_thread.run_sync(lambda:self.owner_service.decide(decision,authenticated_owner=owner,displayed_result_id=body["displayed_result_id"],displayed_result_digest=body["displayed_result_digest"]))
                self.notify_committed()
                return JSONResponse({"accepted":True,"response":response})
            except (workflow_store.StaleCommand,workflow_store.IdempotencyConflict):
                return JSONResponse(_error("STALE_OWNER_VIEW", "Owner result changed or command identity is already bound.", "REFRESH_OWNER_VIEW", recovery="Refresh the owner snapshot and run/result digest. Issue a new command ID only after confirming the displayed input changed; check an uncertain command first."),409)
            except PermissionError:
                return JSONResponse(_error("FORBIDDEN", "Run is outside this configured owner worktree."),403)
            except (workflow_store.ProtocolError,ValueError,TypeError):
                return JSONResponse(_error("INVALID_OWNER_DECISION", "Owner decision payload is invalid.", "CORRECT_OWNER_DECISION", recovery="Correct the decision using the current owner snapshot and exact displayed result identity; this does not constitute owner acceptance."),400)
            except Exception:
                return JSONResponse(_error("OWNER_DECISION_UNAVAILABLE", "Owner decision outcome could not be confirmed.", "WAIT_FOR_RECOVERY", recovery="Refresh the owner snapshot and inspect command/result status before retrying; do not infer acceptance or issue a replacement blindly."),503)

        async def authorize_successor(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error('UNAUTHORIZED','Configured owner bearer token required.'),403)
            if not isinstance(binding,OwnerBinding): return JSONResponse(_error('INVALID_BINDING','Owner binding is invalid.'),503)
            try:
                body=workflow_store.strict_loads((await request.body()).decode('utf-8'))
                fields={'run_id','evaluation_generation','expected_sequence','successor_session_id','command_id'}
                strings=('run_id','successor_session_id','command_id')
                if not isinstance(body,dict) or set(body)!=fields or any(not isinstance(body.get(k),str) or not body[k].strip() for k in strings) or type(body.get('evaluation_generation')) is not int or type(body.get('expected_sequence')) is not int:
                    raise ValueError('invalid authorization shape')
                target=target_owner(request,binding,body['run_id'])
                if target is None: return JSONResponse(_error('FORBIDDEN','Run is outside configured owner roots.'),403)
                saved=self.store.get_run(body['run_id'])
                if not saved: return JSONResponse(_error('NOT_FOUND','Run was not found.'),404)
                run=saved['run']; predecessor=(run.get('author') or {}).get('session_id')
                if (run.get('author') or {}).get('worktree')!=target.worktree or not predecessor or predecessor==body['successor_session_id']:
                    return JSONResponse(_error('FORBIDDEN','Successor must be a different author in the same worktree.'),403)
                if body['successor_session_id'] in getattr(self,'configured_reviewer_sessions',set()):
                    return JSONResponse(_error('IDENTITY_DENIED','Reviewer sessions cannot be successors.'),403)
                slot=next((x for x in self.author_worktrees.values() if Path(x.repo).resolve()==Path(target.worktree).resolve()),None)
                if slot is None: return JSONResponse(_error('IDENTITY_DENIED','No configured author context exists for this worktree.'),403)
                context=workflow_scope.worktree_author_context(repo=slot.repo,authenticated_author={'session_id':body['successor_session_id'],'worktree':slot.repo,'role':'author'},floors=slot.floors,publication_receipt_path=slot.publication_receipt_path,publication_receipt_sha256=slot.publication_receipt_sha256)
                if not await anyio.to_thread.run_sync(self.identity_verifier,context):
                    return JSONResponse(_error('IDENTITY_DENIED','Successor is not a verified primary orchestrator in this worktree.'),403)
                receipt=await anyio.to_thread.run_sync(lambda:self.store.authorize_author_successor(run_id=body['run_id'],generation=body['evaluation_generation'],expected_sequence=body['expected_sequence'],caller=target.caller,command_id=body['command_id'],worktree=target.worktree,predecessor_session_id=predecessor,successor_session_id=body['successor_session_id']))
                self.notify_committed()
                return JSONResponse({'grant':receipt})
            except workflow_store.IdempotencyConflict: return JSONResponse(_error('IDEMPOTENCY_CONFLICT','Command ID was reused with changed grant data.'),409)
            except workflow_store.StaleCommand: return JSONResponse(_error('STALE_COMMAND','Run generation, sequence, or terminal state changed.'),409)
            except PermissionError: return JSONResponse(_error('FORBIDDEN','Run is outside this configured owner worktree.'),403)
            except Exception: return JSONResponse(_error('INVALID_SUCCESSOR_AUTHORIZATION','Successor authorization is invalid or unavailable.'),400)

        async def owner_cancel(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error("UNAUTHORIZED", "Configured owner bearer token required."),403)
            if not isinstance(binding, OwnerBinding): return JSONResponse(_error("INVALID_BINDING", "Owner binding is invalid."),503)
            if self.run_control is None: return JSONResponse(_error("CANCEL_UNAVAILABLE", "Run cancellation is not configured."),503)
            try:
                body=workflow_store.strict_loads((await request.body()).decode("utf-8"))
                if not isinstance(body,dict) or body.get("actor") not in (None, "OWNER") or body.get("reason")!="CANCELLED":
                    raise ValueError("Expected owner cancellation command; actor is server-bound.")
                binding=target_owner(request,binding,body.get('run_id'))
                if binding is None: return JSONResponse(_error("FORBIDDEN", "Run is outside configured owner roots."),403)
                body=dict(body,actor="OWNER",contract_version=1)
                response=await anyio.to_thread.run_sync(lambda:self.run_control.cancel(body,binding))
                self.notify_committed()
                return JSONResponse({"accepted":True,"receipt":response})
            except PermissionError: return JSONResponse(_error("FORBIDDEN", "Run is outside this configured owner worktree."),403)
            except workflow_store.StaleCommand as exc:
                detail=" ".join(str(exc).split())[:300]
                message="Run sequence or generation is stale."
                if detail: message=f"{message} {detail}"
                return JSONResponse(_error("STALE_COMMAND", message),409)
            except Exception: return JSONResponse(_error("INVALID_CANCELLATION", "Cancellation command is invalid or unavailable."),400)

        async def owner_retry_review(request):
            binding,local=owner_auth(request)
            if binding is None: return JSONResponse(_error("UNAUTHORIZED","Configured owner bearer token required."),403)
            try:
                body=workflow_store.strict_loads((await request.body()).decode('utf-8'))
                if not isinstance(body,dict) or set(body)!={'task_id','command_id','expected_sequence'} or not isinstance(body.get('task_id'),str) or not isinstance(body.get('command_id'),str) or type(body.get('expected_sequence')) is not int:
                    raise ValueError('Expected task_id, command_id, expected_sequence.')
                task_id,command_id=body['task_id'],body['command_id']
                task_for_root=self.store.get_task(task_id)
                target=target_owner(request,binding,(task_for_root or {}).get('run_id'))
                if target is None: return JSONResponse(_error('FORBIDDEN','Review task is outside configured owner roots.'),403)
                binding=target
                saved=self.store.get_review_retry(task_id=task_id,caller=binding.caller,command_id=command_id)
                if saved and saved.get('status') in ('RESUMED','STOPPING'):
                    task=self.store.get_task(task_id)
                    run=self.store.get_run(task['run_id']) if task else None
                    if not task or not run or run['run']['author'].get('worktree')!=binding.worktree:
                        return JSONResponse(_error('FORBIDDEN','Review task is outside this owner worktree.'),403)
                    return JSONResponse(saved)
                task=self.store.get_task(task_id)
                run=self.store.get_run(task['run_id']) if task else None
                if not task or not run or run['run']['author'].get('worktree')!=binding.worktree:
                    return JSONResponse(_error('FORBIDDEN','Review task is outside this owner worktree.'),403)
                if run['run']['current_attempt']!=task['evaluation_generation'] or run['run']['sequence']!=body['expected_sequence']:
                    return JSONResponse(_error('STALE_COMMAND','Run sequence or task generation is stale.'),409)
                if self.review_retry is None: return JSONResponse(_error('RETRY_UNAVAILABLE','Review retry is not configured.'),503)
                result=await anyio.to_thread.run_sync(lambda:self.review_retry.retry(task_id,command_id,binding.caller))
                if result.get('status')=='RESUMED': self.notify_committed()
                return JSONResponse(result,200 if result.get('status') in ('RESUMED','STOPPING') else 202 if result.get('status')=='PENDING' else 409 if result.get('status')=='REJECTED' else 503)
            except Exception:
                return JSONResponse(_error('INVALID_RETRY','Review retry command is invalid.'),400)


        async def retry_local_api(request):
            binding=owner_binding(request)
            if binding is None: return JSONResponse(_error('UNAUTHORIZED','Configured owner bearer token required.','REPAIR_LOCAL_API'),403)
            if not isinstance(binding,OwnerBinding): return JSONResponse(_error('INVALID_BINDING','Owner binding is invalid.','REPAIR_LOCAL_API'),403)
            if self.transport_health is None: return JSONResponse(_error('LOCAL_API_RECOVERY_UNAVAILABLE','Local API circuit is not configured.','REPAIR_LOCAL_API'),503)
            try: body=workflow_store.strict_loads((await request.body()).decode('utf-8'))
            except Exception: return JSONResponse(_error('MALFORMED_REQUEST','Expected strict JSON command_id.','CORRECT_COMMAND'),400)
            if not isinstance(body,dict) or set(body)!={'command_id'} or not isinstance(body.get('command_id'),str) or not body['command_id'].strip():
                return JSONResponse(_error('INVALID_LOCAL_API_RETRY','Exactly one nonempty command_id is required.','CORRECT_COMMAND'),422)
            try:
                receipt=self.transport_health.retry(body['command_id'],caller='OWNER:'+binding.caller)
                return JSONResponse({'accepted':True,'receipt':receipt,'next_action':'WAIT_FOR_LOCAL_API_PROBE'})
            except workflow_store.IdempotencyConflict:
                return JSONResponse(_error('IDEMPOTENCY_CONFLICT','Command ID was already used with different input.','USE_NEW_COMMAND_ID'),409)
            except Exception:
                return JSONResponse(_error('LOCAL_API_RECOVERY_UNAVAILABLE','Local API retry could not be persisted.','REPAIR_LOCAL_API'),503)

        app = Starlette(routes=[Route("/integration-status",integration_status_page),Route("/v1/submit", submit, methods=["POST"]),Route('/v1/scope/register',scope_register,methods=['POST']),Route('/v1/scope/status',scope_status), Route("/v1/status", status), Route("/v1/acknowledge-result", acknowledge_result, methods=["POST"]), Route("/v1/health", health), Route("/v1/review/claim", review_claim, methods=["POST"]), Route("/v1/review/complete", review_complete, methods=["POST"]), Route("/v1/review/error", review_error, methods=["POST"]), Route("/v1/review/status",review_status),Route("/v1/owner/snapshot",owner_snapshot),Route("/v1/board/snapshot",board_snapshot),Route("/v1/board/events",board_events),Route("/v1/board/incidents/{incident_id}/seen",board_incident_seen,methods=["POST"]),Route("/v1/owner/decision",owner_decision,methods=["POST"]),Route('/v1/owner/authorize-successor',authorize_successor,methods=['POST']),Route("/v1/recovery/retry-review",owner_retry_review,methods=["POST"]),Route("/v1/recovery/retry-local-api",retry_local_api,methods=["POST"]),Route("/v1/cancel",owner_cancel,methods=["POST"])], lifespan=lifespan)
        http_mcp.rest_app = app
        for profile, path in workflow_coordinator_http_mcp.PROFILE_PATHS.items():
            async def mcp_endpoint(request, profile=profile): return await http_mcp.request(profile, request)
            app.routes.append(Route(path, mcp_endpoint, methods=["GET", "POST", "DELETE"]))
        return app

    def _binding(self, request):
        header = request.headers.get("authorization", "")
        if not header.startswith("Bearer ") or (header[7:] not in self.bindings and header[7:] not in self.author_worktrees):
            return JSONResponse(_error("UNAUTHORIZED", "Configured bearer token required."), 403)
        token=header[7:]
        binding=self.bindings.get(token)
        if binding is not None:return binding
        slot=self.author_worktrees.get(token)
        if slot is None:return JSONResponse(_error("UNAUTHORIZED", "Configured bearer token required."), 403)
        session=request.headers.get('X-MS14-Author-Session','')
        if not session or len(session)>2000 or any(ord(ch)<33 for ch in session):
            return JSONResponse(_error('AUTHOR_SESSION_REQUIRED','Provide the current coding chat session ID in X-MS14-Author-Session.','VERIFY_CURRENT_SESSION'),403)
        author={'session_id':session,'worktree':slot.repo,'role':'author'}
        context=workflow_scope.worktree_author_context(repo=slot.repo,authenticated_author=author,floors=slot.floors,
            publication_receipt_path=slot.publication_receipt_path,publication_receipt_sha256=slot.publication_receipt_sha256)
        return CoordinatorBinding(workflow_scope.author_caller(slot.caller,session), context)

    def summary(self):
        with self._error_lock: error = self.last_error
        counts = self.store.outbox_summary()
        result={"status": "running" if self._thread and self._thread.is_alive() else "stopped", "pending_events": counts.get('PENDING', 0) + counts.get('CLAIMED', 0), "last_error": error[:256] if error else None}
        if self.admission_root is not None:
            result['workflow_observer_guard']={'configured':True,'owned':self._exchange_lock is not None}
            try:
                result['orchestration_paused_for_maintenance'] = self._maintenance_paused()
            except Exception:
                result['orchestration_paused_for_maintenance'] = True
        if self.transport_health is not None: result['local_api']=self.transport_health.snapshot()
        if self.native_retry_summary is not None: result['native_recovery']=self.native_retry_summary
        result['review_quiescence']=self.review_quiescence_summary
        return result

    def latency_samples(self):
        with self._metrics_lock: return list(self._samples)


def _loopback_server(value):
    parsed=urllib.parse.urlparse(value)
    if (parsed.scheme not in ('http','https') or not parsed.hostname or parsed.username or parsed.password or
            parsed.path not in ('','/') or parsed.query or parsed.fragment):
        raise ValueError('opencode_server must be an explicit HTTP(S) URL')
    host=parsed.hostname
    if host.lower() == 'localhost':
        value=urllib.parse.urlunparse(parsed._replace(netloc='127.0.0.1'+((':'+str(parsed.port)) if parsed.port else '')))
    else:
        try:
            if not ipaddress.ip_address(host).is_loopback: raise ValueError
        except ValueError as exc: raise ValueError('opencode_server must use a loopback host') from exc
    return value.rstrip('/')


def _configured_activity_roots(paths):
    """Select only explicitly configured canonical Agent 01–04 repository roots."""
    roots=[]; seen=set()
    for value in paths:
        root=Path(value).resolve()
        if root.name not in {f'MoonStation14-agent-{index:02d}' for index in range(1,5)}:
            continue
        key=str(root).casefold() if os.name=='nt' else str(root)
        if key not in seen:
            roots.append(str(root)); seen.add(key)
    return roots


def load_coordinator(config_path):
    """Load a strict, explicit config; no service is activated by this function."""
    path=Path(config_path).resolve(); base=path.parent
    raw=workflow_store.strict_loads(path.read_text(encoding='utf-8'))
    expected={'database','artifact_dir','opencode_server','bindings'}
    if not isinstance(raw,dict) or set(raw)-expected-{'reviewers','owners','admission_root','author_worktrees'} or not expected.issubset(raw): raise ValueError('config requires database, artifact_dir, opencode_server, bindings; reviewers, owners, and author_worktrees are optional')
    def resolve(value):
        if not isinstance(value,str) or not value: raise ValueError('configured paths must be nonempty strings')
        result=Path(value); return (base/result).resolve() if not result.is_absolute() else result.resolve()
    database, artifacts=resolve(raw['database']),resolve(raw['artifact_dir'])
    contexts={}; bindings={}; callers={}
    worktree_rows=raw.get('author_worktrees',[])
    if not isinstance(raw['bindings'],list) or (not raw['bindings'] and not worktree_rows): raise ValueError('at least one static or worktree author binding is required')
    for item in raw['bindings']:
        if not isinstance(item,dict) or set(item)!={'token_file','caller','context_file'}: raise ValueError('binding fields must be token_file, caller, context_file')
        token_path, context_path=resolve(item['token_file']),resolve(item['context_file'])
        token=token_path.read_text(encoding='utf-8').strip()
        caller=item['caller']
        if not token or not isinstance(caller,str) or not caller.strip() or token in bindings or caller in callers:
            raise ValueError('token and caller bindings must be nonempty and unique')
        context_raw=workflow_store.strict_loads(context_path.read_text(encoding='utf-8'))
        if not isinstance(context_raw,dict): raise ValueError('context must be an object')
        for field in ('repo','publication_receipt_path'):
            context_raw[field]=str(resolve_context_path(context_raw[field],context_path.parent))
        if isinstance(context_raw.get('artifacts'),list):
            for artifact in context_raw['artifacts']:
                if isinstance(artifact,dict) and isinstance(artifact.get('path'),str):
                    artifact['path']=str(resolve_context_path(artifact['path'],context_path.parent))
        context=workflow_submission._context(context_raw)
        repo=Path(context.repo).resolve()
        if database == repo or repo in database.parents or artifacts == repo or repo in artifacts.parents:
            raise ValueError('database and artifact_dir must be external to every configured worktree')
        contexts[token]=CoordinatorBinding(caller,context); bindings[token]=contexts[token]; callers[caller]=token
    worktree_bindings={}
    if not isinstance(worktree_rows,list): raise ValueError('author_worktrees must be a list')
    for item in worktree_rows:
        if not isinstance(item,dict) or set(item)!={'token_file','caller','context_file'}: raise ValueError('author_worktrees entries require token_file, caller, context_file')
        token_path,context_path=resolve(item['token_file']),resolve(item['context_file'])
        caller=item['caller']
        if not isinstance(caller,str) or not caller.strip() or caller in callers:
            raise ValueError('author worktree caller slots must be nonempty and unique')
        context_raw=workflow_store.strict_loads(context_path.read_text(encoding='utf-8'))
        if not isinstance(context_raw,dict) or set(context_raw)!={'repo','floors','publication_receipt_path','publication_receipt_sha256'}:
            raise ValueError('worktree context requires repo, floors, publication_receipt_path, publication_receipt_sha256 only')
        repo=resolve(context_raw['repo'])
        workflow_scope.validate_worktree_floors(context_raw['floors'])
        receipt=resolve_context_path(context_raw['publication_receipt_path'],context_path.parent)
        if not receipt.is_file() or hashlib.sha256(receipt.read_bytes()).hexdigest()!=context_raw['publication_receipt_sha256']:
            raise ValueError('worktree publication receipt is missing or has changed')
        token=token_path.read_text(encoding='utf-8').strip()
        if not token or token in bindings or token in worktree_bindings:
            raise ValueError('author worktree tokens must be nonempty and unique')
        if database==repo or repo in database.parents or artifacts==repo or repo in artifacts.parents:
            raise ValueError('database and artifact_dir must be external to every configured worktree')
        worktree_bindings[token]=AuthorWorktreeBinding(caller, str(token_path),str(context_path),str(repo),context_raw['floors'],str(receipt),context_raw['publication_receipt_sha256'])
        callers[caller]=token
    server=_loopback_server(raw['opencode_server'])
    base_request=functools.partial(__import__('gate_dispatch')._request,accepted_http_statuses=(404,))
    # Observe only explicitly configured Agent 01–04 roots; never infer roots
    # from filesystem layout or include MAIN/05 repositories in coding activity.
    activity_roots=_configured_activity_roots(
        [b.context.repo for b in bindings.values()]+[b.repo for b in worktree_bindings.values()])
    activity_observer=None
    if activity_roots:
        try: import agent_status
        except ModuleNotFoundError: from tooling.agent_workflow import agent_status
        def activity_request(_unused, method, endpoint, **kwargs):
            return base_request(server,method,endpoint,**kwargs)
        activity_observer=lambda:agent_status.observe_coding_agents(activity_roots,request=activity_request,budget_seconds=5)
    try: import workflow_transport_health, transport_health
    except ModuleNotFoundError: from tooling.agent_workflow import workflow_transport_health, transport_health
    circuit=workflow_transport_health.WorkflowTransportHealth(workflow_store.WorkflowStore(database),server,request=base_request)
    health=transport_health.TransportHealth(circuit,base_request)
    admission_root=resolve(raw['admission_root']) if 'admission_root' in raw else None
    coordinator=ExplicitCoordinator(circuit.store,bindings=bindings,author_worktrees=worktree_bindings,artifact_dir=artifacts,handlers={},identity_verifier=OpenCodeIdentityVerifier(server),transport_health=health,admission_root=admission_root,author_activity_observer=activity_observer)
    try:
        import workflow_native_execution
    except ModuleNotFoundError:
        from tooling.agent_workflow import workflow_native_execution
    native=workflow_native_execution.NativeExecutionBackend(coordinator.store,artifacts,database.parent/'native-validation',coordinator.notify_committed)
    import workflow_native_recovery
    coordinator.native_recovery=workflow_native_recovery.NativeRecovery(coordinator.store,artifacts)
    coordinator.handlers['TASK_READY']=native
    import workflow_result_delivery
    coordinator.handlers['RESULT_AVAILABLE']=workflow_result_delivery.AuthorResultDelivery(coordinator.store, server=server,request=health.request)
    coordinator.handlers['OWNER_FEEDBACK']=coordinator.handlers['RESULT_AVAILABLE']
    coordinator.handlers['REGRESSION_DECIDED']=coordinator.handlers['RESULT_AVAILABLE']
    configured=raw.get('reviewers',{})
    roles={'implementation','parity','coverage','fidelity'}
    if not isinstance(configured,dict) or set(configured)-roles: raise ValueError('reviewers must map known review roles')
    reviewer_bindings={}; agents={}; review_tokens={}
    authors={ctx.context.authenticated_author['session_id'] for ctx in bindings.values()}
    for role,item in configured.items():
        if not isinstance(item,dict) or set(item)!={'worktree','agent','token_file','caller'}: raise ValueError(f'reviewer {role} requires worktree, agent, token_file, caller')
        agent,caller=item['agent'],item['caller']
        worktree=resolve(item['worktree'])
        token=resolve(item['token_file']).read_text(encoding='utf-8').strip()
        if any(not isinstance(v,str) or not v.strip() for v in (agent,caller,token)) or agent != 'workflow-reviewer-' + role or token in bindings or token in worktree_bindings or token in review_tokens or caller in callers:
            raise ValueError('reviewer sessions, callers, and tokens must be nonempty and disjoint from author bindings')
        callers[caller]=token
        reviewer_bindings[role]={'role':'reviewer','worktree':str(worktree)}
        agents[role]=agent
        review_tokens[token]=ReviewerBinding(caller,str(worktree),role)
    configure_review_pipeline(coordinator,reviewer_bindings,agents,review_tokens,transport_request=health.request)
    coordinator.configured_reviewer_sessions=set()
    configured_owners=raw.get('owners',[])
    if not isinstance(configured_owners,list): raise ValueError('owners must be a list')
    owner_bindings={}
    for item in configured_owners:
        if not isinstance(item,dict) or set(item)-{'caller','worktree','token_file','session_id'} or not {'caller','worktree','token_file'}.issubset(item): raise ValueError('owner requires caller, worktree, token_file, and optional session_id')
        caller=item['caller']; token=resolve(item['token_file']).read_text(encoding='utf-8').strip(); worktree=str(resolve(item['worktree']))
        sid=item.get('session_id')
        if not isinstance(caller,str) or not caller.strip() or not token or (sid is not None and (not isinstance(sid,str) or not sid.strip())) or token in bindings or token in worktree_bindings or token in review_tokens or token in owner_bindings or caller in callers:
            raise ValueError('owner caller and token must be nonempty and distinct')
        callers[caller]=token
        owner_bindings[token]=OwnerBinding(caller,worktree,sid)
    coordinator.owner_bindings=owner_bindings
    if owner_bindings:
        try: import workflow_owner
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_owner
        coordinator.owner_service=workflow_owner.OwnerService(coordinator.store,snapshot_dir=artifacts/'accepted-snapshots')
        try: import workflow_run_control
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_run_control
        coordinator.run_control=workflow_run_control.ServiceRunControl(coordinator.store,coordinator.notify_committed)
    return coordinator


def configure_review_pipeline(coordinator, reviewers, agents, review_bindings, transport_request=None):
    """Attach the actual review services to an already-created coordinator."""
    try:
        import workflow_native_execution, workflow_review, workflow_review_results, workflow_review_transport, workflow_review_pipeline, workflow_regression, workflow_review_dependency, workflow_review_retry, workflow_native_retry
    except ModuleNotFoundError:
        from tooling.agent_workflow import workflow_native_execution, workflow_review, workflow_review_results, workflow_review_transport, workflow_review_pipeline, workflow_regression, workflow_review_dependency, workflow_review_retry, workflow_native_retry
    native=coordinator.handlers.get('TASK_READY')
    if native is None: raise ValueError('native TASK_READY backend must be configured first')
    server=getattr(coordinator.identity_verifier,'server',None)
    if server is None: raise ValueError('explicit coordinator server is required for review transport')
    transport_request=transport_request or (coordinator.transport_health.request if coordinator.transport_health is not None else None)
    planner=workflow_review.ReviewCoordinator(coordinator.store,reviewers=reviewers,artifact_dir=coordinator.artifact_dir)
    results=workflow_review_results.ReviewResults(coordinator.store,planner)
    transport=workflow_review_transport.ReviewDispatchBackend(planner,server=server,agents=agents,request=transport_request)
    coordinator.review_retry=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=transport_request,agents=agents)
    pipeline=workflow_review_pipeline.ReviewPipeline(coordinator.store,planner,results,transport,native,
        lambda event=None: coordinator.notify_committed())
    retry=workflow_native_retry.NativeRetry(coordinator.store,coordinator.artifact_dir)
    coordinator.native_retry=retry
    pipeline.dependency_monitor=workflow_review_dependency.ReviewDependencyMonitor(planner,server=server,
        request=transport_request or __import__('gate_dispatch')._request)
    coordinator.handlers['REGRESSION_READY']=workflow_regression.RegressionAdmission(coordinator.store,coordinator.notify_committed)
    try: import workflow_native_cancellation
    except ModuleNotFoundError: from tooling.agent_workflow import workflow_native_cancellation
    native_cancel=workflow_native_cancellation.NativeCancellation(coordinator.store,coordinator.artifact_dir)
    try: import workflow_review_cancellation
    except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_cancellation
    review_cancel=workflow_review_cancellation.ReviewCancellation(coordinator.store,planner,transport,
        server=server,request=transport_request)
    try: import workflow_review_quiescence
    except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_quiescence
    coordinator.review_quiescence=workflow_review_quiescence.ReviewQuiescence(coordinator.store,review_cancel)
    class CancelAdapter:
        def observe(self,event_id,payload):
            task=coordinator.store.get_task(payload.get('task_id'))
            if task and task.get('kind')=='CHECK': return native_cancel.observe(event_id,payload)
            if task and task.get('kind')=='REVIEW':
                saved=coordinator.store.find_task_cancel_event(task['task_id'])
                return 'ABSENT' if saved and saved.get('event_id')==event_id and saved.get('payload')==payload else 'UNKNOWN'
            return 'UNKNOWN'
        def dispatch(self,event_id,payload):
            task=coordinator.store.get_task(payload.get('task_id'))
            if not task: return 'UNKNOWN'
            if task.get('kind')=='CHECK': return native_cancel.dispatch(event_id,payload)
            if task.get('kind')=='REVIEW': return review_cancel.cancel(event_id,payload)
            return 'UNKNOWN'
    coordinator.handlers['CANCEL_TASK']=CancelAdapter()
    class RecoveryStartedAdapter:
        def _matches(self,event_id,payload):
            event=coordinator.store.get_event(event_id)
            run_id=payload.get('run_id') if isinstance(payload,dict) else None
            generation=payload.get('evaluation_generation') if isinstance(payload,dict) else None
            current=coordinator.store.get_run(run_id) if run_id else None
            return bool(event and event.get('payload')==payload and payload.get('kind')=='RECOVERY_STARTED' and
                current and current['run']['current_attempt']==generation and
                current['run'].get('run_kind')=='FEATURE_GATE')
        def observe(self,event_id,payload):
            if not self._matches(event_id,payload): return 'UNKNOWN'
            if payload.get('failed_boundary','').startswith('review:'):
                metadata=coordinator.store.get_recovery_metadata(payload['run_id'],payload['evaluation_generation'])
                current=coordinator.store.get_run(payload['run_id'])
                generation=payload['evaluation_generation']
                tasks=coordinator.store.list_tasks(payload['run_id'],generation)
                checks=[task for task in tasks if task.get('kind')=='CHECK']
                reviews=[task for task in tasks if task.get('kind')=='REVIEW']
                valid=(metadata is not None and metadata.get('boundary')==payload.get('failed_boundary') and
                    current.get('attempt',{}).get('phase') in ('REVIEWING','DECIDED') and
                    coordinator.store.effective_checks_complete(payload['run_id'],generation) and
                    checks and all(task.get('reused') is True and task.get('state')=='COMPLETED' and
                                   task.get('result',{}).get('decision')=='PASS' for task in checks))
                if not valid: return 'UNKNOWN'
                if not reviews and current.get('attempt',{}).get('phase')!='REVIEWING': return 'UNKNOWN'
                return 'APPLIED' if reviews else 'ABSENT'
            checks=[task for task in coordinator.store.list_tasks(payload['run_id'],payload['evaluation_generation'])
                    if task.get('kind')=='CHECK']
            if len(checks)==1 and checks[0].get('logical_key')==payload.get('failed_boundary','').removeprefix('native:'):
                return 'APPLIED'
            return 'ABSENT' if not checks else 'UNKNOWN'
        def dispatch(self,event_id,payload):
            if not self._matches(event_id,payload): return 'UNKNOWN'
            metadata=coordinator.store.get_recovery_metadata(payload['run_id'],payload['evaluation_generation'])
            if metadata is None or metadata.get('boundary')!=payload.get('failed_boundary'):
                return 'UNKNOWN'
            if payload.get('failed_boundary','').startswith('review:'):
                observed=self.observe(event_id,payload)
                if observed=='APPLIED': return 'APPLIED'
                if observed!='ABSENT': return 'UNKNOWN'
                planner.plan_reviews(payload['run_id'])
                coordinator.notify_committed()
                return self.observe(event_id,payload)
            retry.ensure_task(payload['run_id'],payload['evaluation_generation'])
            coordinator.notify_committed()
            return 'APPLIED'
    coordinator.handlers['RECOVERY_STARTED']=RecoveryStartedAdapter()
    class AssignmentReceiptAdapter:
        def _applied(self,event_id,payload):
            saved=coordinator.store.get_event(event_id)
            if not saved or saved.get('payload')!=payload: return False
            task=coordinator.store.get_task(payload.get('task_id'))
            if (not task or task.get('kind')!='REVIEW' or
                    any(task.get(key)!=payload.get(key) for key in
                        ('task_id','run_id','evaluation_generation','assignment_generation','input_digest'))):
                return False
            try:
                assignment=planner.build_assignment(task['task_id'])
                return transport._receipt(task,assignment)
            except Exception:
                return False
        def observe(self,event_id,payload):
            return 'APPLIED' if self._applied(event_id,payload) else 'UNKNOWN'
        def dispatch(self,event_id,payload):
            return 'APPLIED' if self._applied(event_id,payload) else 'UNKNOWN'
    coordinator.handlers['TASK_ASSIGNMENT']=AssignmentReceiptAdapter()
    pipeline.review_cancellation=review_cancel
    coordinator.review_results=results; coordinator.review_bindings=dict(review_bindings); coordinator.review_pipeline=pipeline
    class CompletionAdapter:
        def _saved(self,event_id,payload):
            saved=coordinator.store.get_event(event_id)
            return saved if saved and saved.get('payload')==payload else None
        def _cancelled(self,event_id,payload):
            if not isinstance(payload,dict) or payload.get('kind')!='TASK_CANCELLED': return None
            if self._saved(event_id,payload) is None: return None
            task=coordinator.store.get_task(payload.get('task_id'))
            if (not task or task.get('state')!='CANCELLED' or
                any(task.get(k)!=payload.get(k) for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest'))): return None
            return task
        def _closed(self,event_id,payload):
            if not isinstance(payload,dict) or payload.get('kind')!='ATTEMPT_CLOSED' or self._saved(event_id,payload) is None: return False
            if not isinstance(payload.get('run_id'),str) or not isinstance(payload.get('evaluation_generation'),int): return False
            db=coordinator.store._connect()
            try:
                row=db.execute('SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?',
                    (payload['run_id'],payload['evaluation_generation'])).fetchone()
            finally: db.close()
            attempt=json.loads(row[0]) if row else None
            return bool(attempt and attempt.get('phase')=='CLOSED' and attempt.get('closure')==payload.get('closure'))
        def observe(self,event_id,payload):
            if isinstance(payload,dict) and payload.get('kind')=='TASK_CANCELLED':
                return 'APPLIED' if self._cancelled(event_id,payload) else 'UNKNOWN'
            if isinstance(payload,dict) and payload.get('kind')=='ATTEMPT_CLOSED':
                return 'APPLIED' if self._closed(event_id,payload) else 'UNKNOWN'
            return 'ABSENT' if self._saved(event_id,payload) else 'UNKNOWN'
        def dispatch(self,event_id,payload):
            if payload.get('kind')=='TASK_CANCELLED':
                task=self._cancelled(event_id,payload)
                if task is None: return 'UNKNOWN'
                saved=coordinator.store.get_run(task['run_id'])
                if saved and saved['attempt']['generation']==task['evaluation_generation'] and saved['attempt']['phase'] in ('REVIEWING','STOPPING'):
                    pipeline.reconcile_pending()
                return 'APPLIED'
            if payload.get('kind')=='ATTEMPT_CLOSED':
                return 'APPLIED' if self._closed(event_id,payload) else 'UNKNOWN'
            return 'APPLIED' if pipeline.completed(payload) is not None else 'UNKNOWN'
    coordinator.handlers['TASK_READY']=pipeline; coordinator.handlers['TASK_COMPLETED']=CompletionAdapter(); coordinator.handlers['TASK_CANCELLED']=coordinator.handlers['TASK_COMPLETED']
    coordinator.handlers['ATTEMPT_CLOSED']=CompletionAdapter()
    return pipeline


def resolve_context_path(value, base):
    path=Path(value)
    return (Path(base)/path).resolve() if not path.is_absolute() else path.resolve()


def main(argv=None):
    parser=argparse.ArgumentParser(description='Explicit local workflow coordinator (inactive unless serve is invoked)')
    sub=parser.add_subparsers(dest='command',required=True)
    serve=sub.add_parser('serve'); serve.add_argument('--config',required=True); serve.add_argument('--port',required=True,type=int)
    serve.add_argument('--supervisor-ready-path'); serve.add_argument('--supervisor-token')
    args=parser.parse_args(argv)
    if not 1 <= args.port <= 65535: parser.error('--port must be between 1 and 65535')
    if bool(args.supervisor_ready_path) != bool(args.supervisor_token):
        parser.error('--supervisor-ready-path and --supervisor-token must be supplied together')
    if args.supervisor_ready_path:
        # This internal gate runs before config loading, DB admission, or network setup.
        import hashlib, secrets
        from pathlib import Path
        try: import gate_execution
        except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
        ready=Path(args.supervisor_ready_path)
        identity=gate_execution.process_identity(os.getpid())
        if identity is None: raise RuntimeError('coordinator startup identity is unavailable')
        ready.parent.mkdir(parents=True,exist_ok=True)
        ready.write_text(json.dumps({'identity':identity,'token_digest':hashlib.sha256(args.supervisor_token.encode()).hexdigest()}),encoding='utf-8')
        go=ready.with_suffix(ready.suffix+'.go')
        deadline=time.monotonic()+10
        while time.monotonic()<deadline:
            try:
                value=json.loads(go.read_text(encoding='utf-8'))
                if value == {'token':args.supervisor_token}: break
            except (OSError,ValueError): pass
            time.sleep(.025)
        else: raise RuntimeError('supervisor containment acknowledgement timed out')
    import uvicorn
    coordinator=load_coordinator(args.config)
    uvicorn.run(coordinator.app(),host='127.0.0.1',port=args.port,workers=1,reload=False)
    return 0


if __name__ == '__main__': raise SystemExit(main())
