"""Inactive, explicit-path SQLite authority for the workflow protocol.

This module owns durable records only. It never dispatches work or touches the
legacy JSON lifecycle files; callers perform external effects after committing.
"""
from __future__ import annotations

import hashlib
import ipaddress
import json
import sqlite3
import time
import urllib.parse
from pathlib import Path

REVIEW_CLAIM_WINDOW_MS = 1_200_000

from jsonschema import Draft202012Validator

SCHEMA_VERSION = 1
APPLICATION_ID = 0x4D533134
_PROTOCOL = Path(__file__).parents[2] / "docs" / "development-workflow-redesign" / "contracts" / "protocol.schema.json"
_SCHEMA = json.loads(_PROTOCOL.read_text(encoding="utf-8"))
Draft202012Validator.check_schema(_SCHEMA)
_VALIDATOR = Draft202012Validator(_SCHEMA)


class StoreError(Exception):
    """Base class for safe, non-mutating protocol/storage rejection."""


class ProtocolError(StoreError):
    def __init__(self, message, *, field=None, recovery=None):
        if field is not None: self.field = field
        if recovery is not None: self.recovery = recovery
        super().__init__(message)


class IdempotencyConflict(StoreError):
    pass


class SubmissionHeadConflict(StoreError):
    """A submission cannot replace the current feature head through this API."""
    REPLACEMENT_REQUIRED = "REPLACEMENT_REQUIRED"
    ACTIVE_OR_UNSETTLED = "ACTIVE_OR_UNSETTLED"
    INVALID_TARGET = "INVALID_TARGET"

    def __init__(self, reason):
        if reason not in (self.REPLACEMENT_REQUIRED, self.ACTIVE_OR_UNSETTLED, self.INVALID_TARGET):
            raise ValueError("invalid feature-head conflict reason")
        self.reason = reason
        self.field = "feature_head"
        super().__init__("existing feature head requires a sanctioned settled-run replacement")


class ArtifactIdentityConflict(IdempotencyConflict):
    """An immutable artifact ID was rebound to different metadata."""
    def __init__(self, artifact_id, field):
        self.artifact_id = artifact_id
        self.field = field
        super().__init__("artifact ID reused with changed content")


class StaleCommand(StoreError):
    pass


class SubmissionLaneBusy(StoreError):
    """Another current submission owns the active CHECKING/REVIEWING/STOPPING lane."""


def strict_loads(text: str, *, allow_float=False):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ProtocolError(f"duplicate JSON key: {key}")
            result[key] = value
        return result

    def bad_constant(value):
        raise ProtocolError(f"non-finite JSON number: {value}")

    def parse_float(value):
        raise ProtocolError(f"protocol numbers must be integers: {value}")

    try:
        return json.loads(text, object_pairs_hook=pairs, parse_constant=bad_constant,
                          parse_float=(float if allow_float else parse_float))
    except (ValueError, TypeError) as exc:
        raise ProtocolError(str(exc)) from exc


def canonical_json(value) -> str:
    def check(node):
        if node is None or isinstance(node, (str, bool, int)):
            return
        if isinstance(node, list):
            for item in node: check(item)
            return
        if isinstance(node, dict):
            if any(not isinstance(key, str) for key in node):
                raise ProtocolError("JSON object keys must be strings")
            for item in node.values(): check(item)
            return
        raise ProtocolError(f"not a JSON value: {type(node).__name__}")
    try:
        check(value)
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False)
    except (TypeError, ValueError) as exc:
        raise ProtocolError(f"not strict JSON: {exc}") from exc


def digest(value) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def schema_error_details(error, kind=None):
    """Format safe recovery metadata from a validator error and trusted schema constraints."""
    parts=list(getattr(error,"absolute_path",()) or ())
    field=".".join(str(part) for part in parts) or (kind or "request")
    field=field[:256]
    keyword=getattr(error,"validator",None)
    constraint=getattr(error,"validator_value",None)
    if keyword == "required":
        instance=getattr(error,"instance",None)
        missing=sorted(set(constraint or ())-set(instance or {})) if isinstance(instance,dict) else []
        names=", ".join(str(name)[:80] for name in missing[:6])
        recovery=f"Add required field(s): {names}." if names else "Add the required field indicated by this path."
    elif keyword == "additionalProperties":
        schema=getattr(error,"schema",None)
        props=schema.get("properties",{}) if isinstance(schema,dict) else {}
        allowed=", ".join(sorted(str(name)[:80] for name in props)[:6])
        recovery=f"Remove unsupported field(s); allowed fields include: {allowed}." if allowed else "Remove fields not defined by this protocol object."
    elif keyword == "type":
        recovery=f"Provide this field as the required {constraint} type."
    elif keyword == "enum":
        choices=", ".join(repr(item)[:60] for item in (constraint or ())[:8])
        recovery=f"Use one of the allowed values: {choices}."
    elif keyword in ("minLength", "maxLength", "minimum", "maximum", "minItems", "maxItems"):
        recovery=f"Adjust this field to satisfy the protocol {keyword} constraint ({constraint})."
    else:
        recovery=f"Follow the version-1 {kind or 'request'} contract for this field."
    return field,recovery[:500]


def _is_loopback_url(value):
    if not isinstance(value,str): return False
    parsed=urllib.parse.urlparse(value)
    try: address=ipaddress.ip_address(parsed.hostname)
    except (ValueError,TypeError): return False
    return (parsed.scheme in ('http','https') and address.is_loopback and not parsed.username and
        not parsed.password and parsed.path in ('','/') and not parsed.query and not parsed.fragment)


def _internal_task_event(event_id, task):
    """Private storage wake-up; unlike dispatch_envelope it has no AI recipient."""
    if not isinstance(event_id, str) or not event_id.startswith("event_"):
        raise ProtocolError("stable internal event_id required")
    return {"contract_version": 1, "event_id": event_id, "kind": "TASK_READY",
            "run_id": task["run_id"], "evaluation_generation": task["evaluation_generation"],
            "task_id": task["task_id"], "assignment_generation": task["assignment_generation"],
            "input_digest": task["input_digest"]}


def _validate_internal_task_event(event, task):
    expected = _internal_task_event(event.get("event_id") if isinstance(event, dict) else None, task)
    if event != expected:
        raise ProtocolError("initial outbox event must be the exact internally constructed TASK_READY wake-up")
    return expected


def validate(value, kind=None):
    if isinstance(value, str):
        value = strict_loads(value)
    # Schema validators consider integral-valued floats valid integers. Enforce
    # the wire format before schema validation (and before any caller mutates).
    canonical_json(value)
    errors = list(_VALIDATOR.iter_errors(value))
    if kind is not None:
        definition = _SCHEMA["$defs"].get(kind)
        if definition is None:
            raise ValueError(f"unknown protocol kind {kind}")
        sub = {"$schema": _SCHEMA["$schema"], "$defs": _SCHEMA["$defs"],
               "allOf": [{"$ref": f"#/$defs/{kind}"}]}
        errors = list(Draft202012Validator(sub).iter_errors(value))
    if errors:
        priority={"required":0,"type":1,"enum":2,"additionalProperties":3,"const":4}
        error=sorted(errors,key=lambda e:(priority.get(e.validator,10),len(e.absolute_path),tuple(map(str,e.absolute_path)),e.validator or ""))[0]
        field,recovery=schema_error_details(error,kind)
        raise ProtocolError("; ".join(sorted("/".join(map(str, e.absolute_path)) + ": " + e.message for e in errors)),field=field,recovery=recovery)
    return value


def _validate_rejection_incident(response, incident):
    if response.get("result") == "ERROR":
        if incident is None:
            raise ProtocolError("ERROR rejection requires its bound incident")
        incident = validate(incident, "incident")
        if response.get("incident_ref") != incident["incident_id"]:
            raise ProtocolError("rejection incident_ref does not match incident")
    elif incident is not None:
        raise ProtocolError("only ERROR rejection may record an incident")


def _schema_inventory():
    """Build the expected table/column/index inventory from the canonical DDL."""
    ref = sqlite3.connect(":memory:")
    try:
        ref.executescript(_DDL)
        names={row[0] for row in ref.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
        tables={name:tuple(row[1] for row in ref.execute(f'PRAGMA table_info("{name}")')) for name in names}
        indexes={row[0] for row in ref.execute("SELECT name FROM sqlite_master WHERE type='index' AND sql IS NOT NULL")}
        return tables,indexes
    finally:
        ref.close()


def _readonly_run_record(db, run_id, worktree, sprint):
    row=db.execute("SELECT run_json,current_generation,sequence FROM runs WHERE id=?",(run_id,)).fetchone()
    if row is None: raise StoreError("feature head references a missing run")
    run=json.loads(row['run_json']); run.update(current_attempt=row['current_generation'],sequence=row['sequence'])
    attempt_row=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",
        (run_id,row['current_generation'])).fetchone()
    if attempt_row is None: raise StoreError("run current attempt is missing")
    terminal_row=db.execute("SELECT e.payload FROM history h JOIN events e ON e.id=h.event_id WHERE h.run_id=? AND h.generation=? AND json_extract(e.payload,'$.result_id') IS NOT NULL ORDER BY h.sequence DESC LIMIT 1",
        (run_id,row['current_generation'])).fetchone()
    tasks=[]
    for taskrow in db.execute("SELECT task_json,logical_key FROM tasks WHERE run_id=? ORDER BY generation,id",(run_id,)):
        task=json.loads(taskrow['task_json']); task['logical_key']=taskrow['logical_key']
        tasks.append(task)
    incidents=[{'incident':json.loads(r['incident_json']),'seen_ms':r['seen_ms'],
        'resolved_ms':r['resolved_ms'],'resolution_proof_ref':r['resolution_proof_ref'],
        'linked_delivery_id':r['linked_delivery_id']} for r in db.execute(
        "SELECT incident_json,seen_ms,resolved_ms,resolution_proof_ref,linked_delivery_id FROM incidents WHERE run_id=? ORDER BY id",(run_id,))]
    resources=[{'claim_id':r['claim_id'],'resource_key':r['resource_key'],'run_id':r['run_id'],
        'generation':r['generation'],'task_id':r['task_id'],'assignment_generation':r['assignment_generation'],
        'identity_proof':r['identity_proof'],'state':r['state'],'cleanup_proof':r['cleanup_proof'],
        'request_digest':r['request_digest']} for r in db.execute(
        "SELECT * FROM resources WHERE run_id=? ORDER BY generation,resource_key,claim_id",(run_id,))]
    submissions=db.execute("SELECT id,request_json,response_json,caller,idem_key FROM submissions ORDER BY id").fetchall()
    submission=None
    for s in submissions:
        response=json.loads(s['response_json'])
        if response.get('run_id')==run_id:
            submission={'submission_id':s['id'],'request':json.loads(s['request_json']),
                'response':response,'caller':s['caller'],'idempotency_key':s['idem_key']}
            break
    request=submission['request'] if submission else None
    decisions=[{'decision':json.loads(r['decision_json']),'response':json.loads(r['response_json'])}
        for r in db.execute("SELECT decision_json,response_json FROM owner_decisions WHERE run_id=? ORDER BY generation,id",(run_id,))]
    acceptance_ids=[]; acceptances=[]
    for r in db.execute("SELECT id,run_id,generation,input_digest,decision_id FROM acceptances WHERE run_id=? ORDER BY generation,id",(run_id,)):
        acceptance_ids.append(r['id']); acceptances.append({'acceptance_id':r['id'],'run_id':r['run_id'],
            'evaluation_generation':r['generation'],'input_digest':r['input_digest'],'decision_id':r['decision_id']})
    if run.get('owner_acceptance_id') and run['owner_acceptance_id'] not in acceptance_ids:
        acceptance_ids.append(run['owner_acceptance_id'])
    regression_jobs=[]
    for acceptance_id in acceptance_ids:
        for r in db.execute("SELECT id,acceptance_id,job_json,inputs_json FROM regression_jobs WHERE acceptance_id=? ORDER BY id",(acceptance_id,)):
            regression_jobs.append({'regression_id':r['id'],'acceptance_id':r['acceptance_id'],
                'job':json.loads(r['job_json']),'inputs':json.loads(r['inputs_json'])})
    event_rows=[]
    for r in db.execute("SELECT e.id,e.run_id,e.generation,e.kind,e.payload,e.payload_digest,e.created_ms,h.sequence FROM events e LEFT JOIN history h ON h.event_id=e.id WHERE e.run_id=? ORDER BY COALESCE(h.sequence,2147483647),e.created_ms,e.id",(run_id,)):
        event_rows.append({'event_id':r['id'],'run_id':r['run_id'],'generation':r['generation'],
            'kind':r['kind'],'payload':json.loads(r['payload']),'payload_digest':r['payload_digest'],
            'created_ms':r['created_ms'],'sequence':r['sequence']})
    deliveries=[]
    for r in db.execute("SELECT d.*,e.run_id,e.generation,e.kind,e.payload FROM deliveries d JOIN events e ON e.id=d.event_id WHERE e.run_id=? ORDER BY e.created_ms,d.id",(run_id,)):
        deliveries.append({'delivery_id':r['id'],'event_id':r['event_id'],'run_id':r['run_id'],
            'generation':r['generation'],'event_kind':r['kind'],'recipient':json.loads(r['recipient_json']),
            'target_kind':r['target_kind'],'state':r['state'],'payload_digest':r['payload_digest'],
            'transport_receipt':None if r['transport_receipt'] is None else json.loads(r['transport_receipt']),
            'protocol_ack':None if r['protocol_ack'] is None else json.loads(r['protocol_ack']),
            'payload':json.loads(r['payload']),
            'attempts':r['attempts'],'claim_generation':r['claim_generation'],
            'lease_token':r['lease_token'],'lease_until_ms':r['lease_until_ms']})
    artifacts=[]
    for r in db.execute("SELECT artifact_json FROM artifacts WHERE run_id=? ORDER BY id",(run_id,)):
        artifacts.append(json.loads(r['artifact_json']))
    if submission:
        for r in db.execute("SELECT artifact_json FROM artifacts WHERE run_id IS NULL ORDER BY id"):
            artifact=json.loads(r['artifact_json'])
            if artifact.get('artifact',{}).get('submission_id')==submission['submission_id']:
                artifacts.append(artifact)
    pauses=[]
    for r in db.execute("SELECT p.task_id,p.dependency_key,p.paused_since_ms,p.accumulated_paused_ms,p.context_json FROM task_dependency_pauses p JOIN tasks t ON t.id=p.task_id WHERE t.run_id=? ORDER BY p.task_id",(run_id,)):
        pause_ms=r['paused_since_ms']; accumulated=r['accumulated_paused_ms']
        pauses.append({'task_id':r['task_id'],'dependency_key':r['dependency_key'],
            'paused_since_ms':max(0,pause_ms),'active':pause_ms>=0,
            'last_resumed_ms':abs(pause_ms) if pause_ms<0 else None,
            'accumulated_paused_ms':abs(accumulated),'context':json.loads(r['context_json'])})
    return {'worktree':worktree,'sprint':sprint,'run':run,'attempt':json.loads(attempt_row['attempt_json']),
        'terminal_result':None if terminal_row is None else json.loads(terminal_row['payload']),
        'tasks':tasks,'request':request,'owner_decisions':decisions,'acceptances':acceptances,
        'regression_jobs':regression_jobs,'incidents':incidents,'resources':resources,
        'dependency_pauses':pauses,'deliveries':deliveries,'events':event_rows,'artifacts':artifacts}


_DDL = """
CREATE TABLE IF NOT EXISTS submissions(
 id TEXT PRIMARY KEY, caller TEXT NOT NULL, operation TEXT NOT NULL, idem_key TEXT NOT NULL,
 request_digest TEXT NOT NULL, request_json TEXT NOT NULL, response_json TEXT NOT NULL,
 UNIQUE(caller,operation,idem_key));
CREATE TABLE IF NOT EXISTS runs(
 id TEXT PRIMARY KEY, sprint TEXT NOT NULL, worktree TEXT NOT NULL, run_json TEXT NOT NULL,
 current_generation INTEGER NOT NULL, sequence INTEGER NOT NULL DEFAULT 0);
DROP INDEX IF EXISTS one_feature_head;
CREATE TABLE IF NOT EXISTS feature_heads(worktree TEXT NOT NULL,sprint TEXT NOT NULL,run_id TEXT NOT NULL REFERENCES runs(id),PRIMARY KEY(worktree,sprint));
CREATE TABLE IF NOT EXISTS attempts(
 run_id TEXT NOT NULL REFERENCES runs(id), generation INTEGER NOT NULL, attempt_json TEXT NOT NULL,
 PRIMARY KEY(run_id,generation));
CREATE TABLE IF NOT EXISTS tasks(
 id TEXT PRIMARY KEY, run_id TEXT NOT NULL REFERENCES runs(id), generation INTEGER NOT NULL,
 logical_key TEXT NOT NULL, task_json TEXT NOT NULL, result_digest TEXT, result_json TEXT,
 UNIQUE(id,run_id,generation), FOREIGN KEY(run_id,generation) REFERENCES attempts(run_id,generation));
CREATE TABLE IF NOT EXISTS native_claims(task_id TEXT PRIMARY KEY REFERENCES tasks(id), process_identity TEXT NOT NULL, claim_token TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS native_scopes(task_id TEXT PRIMARY KEY REFERENCES tasks(id), assignment_generation INTEGER NOT NULL, scope_name TEXT NOT NULL UNIQUE, worker_identity_json TEXT NOT NULL, creation_artifact_ref TEXT NOT NULL, cleanup_artifact_ref TEXT, state TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS native_check_records(task_id TEXT NOT NULL REFERENCES tasks(id), check_index INTEGER NOT NULL, assignment_generation INTEGER NOT NULL, artifact_ref TEXT NOT NULL UNIQUE REFERENCES artifacts(id), PRIMARY KEY(task_id,check_index));
CREATE UNIQUE INDEX IF NOT EXISTS one_logical_task_per_attempt ON tasks(run_id,generation,logical_key);
CREATE TABLE IF NOT EXISTS task_deps(
 task_id TEXT NOT NULL REFERENCES tasks(id), dependency_id TEXT NOT NULL REFERENCES tasks(id),
 PRIMARY KEY(task_id,dependency_id), CHECK(task_id<>dependency_id));
CREATE TABLE IF NOT EXISTS artifacts(id TEXT PRIMARY KEY, run_id TEXT REFERENCES runs(id), artifact_json TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY, run_id TEXT REFERENCES runs(id), generation INTEGER,
 kind TEXT NOT NULL, payload TEXT NOT NULL, payload_digest TEXT NOT NULL, created_ms INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS outbox(event_id TEXT PRIMARY KEY REFERENCES events(id), state TEXT NOT NULL DEFAULT 'PENDING',
 claim_generation INTEGER NOT NULL DEFAULT 0, lease_until_ms INTEGER, attempts INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS deliveries(id TEXT PRIMARY KEY, event_id TEXT NOT NULL REFERENCES events(id),
  recipient_json TEXT NOT NULL, target_kind TEXT NOT NULL, state TEXT NOT NULL, payload_digest TEXT NOT NULL, transport_receipt TEXT,
  protocol_ack TEXT, attempts INTEGER NOT NULL DEFAULT 0, claim_generation INTEGER NOT NULL DEFAULT 0,
  lease_token TEXT, lease_until_ms INTEGER,
 UNIQUE(event_id,recipient_json));
CREATE TABLE IF NOT EXISTS incidents(id TEXT PRIMARY KEY, run_id TEXT REFERENCES runs(id), generation INTEGER,
 incident_json TEXT NOT NULL, seen_ms INTEGER, resolved_ms INTEGER, resolution_proof_ref TEXT,
 linked_delivery_id TEXT REFERENCES deliveries(id));
CREATE TABLE IF NOT EXISTS resources(claim_id TEXT PRIMARY KEY, resource_key TEXT NOT NULL, run_id TEXT NOT NULL REFERENCES runs(id),
  generation INTEGER NOT NULL, task_id TEXT NOT NULL REFERENCES tasks(id), assignment_generation INTEGER NOT NULL,
  identity_proof TEXT NOT NULL, state TEXT NOT NULL, cleanup_proof TEXT, request_digest TEXT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS one_active_resource_claim ON resources(resource_key) WHERE state IN ('HELD','UNKNOWN');
CREATE TABLE IF NOT EXISTS resource_proofs(artifact_id TEXT PRIMARY KEY, claim_id TEXT NOT NULL UNIQUE REFERENCES resources(claim_id));
CREATE TABLE IF NOT EXISTS task_cancellations(task_id TEXT PRIMARY KEY REFERENCES tasks(id), proof_json TEXT NOT NULL, confirmed INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS command_receipts(operation TEXT NOT NULL, caller TEXT NOT NULL, command_key TEXT NOT NULL, request_digest TEXT NOT NULL, response_json TEXT NOT NULL, PRIMARY KEY(operation,caller,command_key));
CREATE TABLE IF NOT EXISTS author_scopes(scope_id TEXT PRIMARY KEY, caller TEXT NOT NULL, submission_key TEXT NOT NULL, payload_digest TEXT NOT NULL, scope_json TEXT NOT NULL, UNIQUE(caller,submission_key));
CREATE TABLE IF NOT EXISTS history(id INTEGER PRIMARY KEY AUTOINCREMENT, run_id TEXT NOT NULL REFERENCES runs(id),
 generation INTEGER NOT NULL, sequence INTEGER NOT NULL, event_id TEXT NOT NULL REFERENCES events(id));
CREATE TABLE IF NOT EXISTS recovery_metadata(run_id TEXT NOT NULL, generation INTEGER NOT NULL,
 fresh_input_proof TEXT NOT NULL, boundary TEXT NOT NULL, reusable_refs_json TEXT NOT NULL,
 PRIMARY KEY(run_id,generation), FOREIGN KEY(run_id,generation) REFERENCES attempts(run_id,generation));
CREATE TABLE IF NOT EXISTS reused_checks(run_id TEXT NOT NULL, target_generation INTEGER NOT NULL,
 logical_check_key TEXT NOT NULL, source_task_id TEXT NOT NULL REFERENCES tasks(id), receipt_refs_json TEXT NOT NULL,
 PRIMARY KEY(run_id,target_generation,logical_check_key),
 FOREIGN KEY(run_id,target_generation) REFERENCES attempts(run_id,generation));
CREATE TABLE IF NOT EXISTS recovery_check_obligations(run_id TEXT NOT NULL, generation INTEGER NOT NULL,
  logical_check_key TEXT NOT NULL, PRIMARY KEY(run_id,generation,logical_check_key),
  FOREIGN KEY(run_id,generation) REFERENCES attempts(run_id,generation));
CREATE TABLE IF NOT EXISTS owner_decisions(
 id TEXT PRIMARY KEY, run_id TEXT NOT NULL REFERENCES runs(id), generation INTEGER NOT NULL,
 input_digest TEXT NOT NULL, request_digest TEXT NOT NULL, decision_json TEXT NOT NULL,
 response_json TEXT NOT NULL, owner_binding_json TEXT NOT NULL, result_id TEXT NOT NULL,
 result_digest TEXT NOT NULL, source_artifact_ref TEXT NOT NULL, build_artifact_refs_json TEXT NOT NULL,
 regression_inputs_json TEXT,
 FOREIGN KEY(run_id,generation) REFERENCES attempts(run_id,generation));
CREATE TABLE IF NOT EXISTS acceptances(
 id TEXT PRIMARY KEY, run_id TEXT NOT NULL REFERENCES runs(id), generation INTEGER NOT NULL,
 input_digest TEXT NOT NULL, decision_id TEXT NOT NULL UNIQUE REFERENCES owner_decisions(id),
 UNIQUE(run_id,generation,input_digest));
CREATE TABLE IF NOT EXISTS regression_jobs(
 id TEXT PRIMARY KEY, acceptance_id TEXT NOT NULL UNIQUE REFERENCES acceptances(id),
 job_json TEXT NOT NULL, inputs_json TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS dependency_circuits(
 dependency_key TEXT PRIMARY KEY, generation INTEGER NOT NULL, state TEXT NOT NULL,
 failures INTEGER NOT NULL DEFAULT 0, opened_ms INTEGER, next_probe_ms INTEGER,
 probes INTEGER NOT NULL DEFAULT 0, last_failure_id TEXT, details_json TEXT NOT NULL DEFAULT '{}');
CREATE TABLE IF NOT EXISTS dependency_failures(
 dependency_key TEXT NOT NULL REFERENCES dependency_circuits(dependency_key), failure_id TEXT NOT NULL,
 payload_json TEXT NOT NULL, PRIMARY KEY(dependency_key,failure_id));
CREATE TABLE IF NOT EXISTS task_dependency_pauses(
 task_id TEXT PRIMARY KEY REFERENCES tasks(id), dependency_key TEXT NOT NULL, paused_since_ms INTEGER NOT NULL,
 accumulated_paused_ms INTEGER NOT NULL DEFAULT 0, context_json TEXT NOT NULL);
PRAGMA user_version=1;
"""


class WorkflowStore:
    """A connection-per-operation SQLite store; path is mandatory, never inferred."""
    def __init__(self, database_path, *, timeout=5.0):
        if database_path is None:
            raise ValueError("an explicit database path is required")
        self.path = str(Path(database_path).expanduser().resolve())
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self.timeout = timeout
        self._initialize()

    def _connect(self):
        db = sqlite3.connect(self.path, timeout=self.timeout, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        db.execute(f"PRAGMA busy_timeout={max(0, int(self.timeout * 1000))}")
        db.execute("PRAGMA synchronous=FULL")
        if db.execute("PRAGMA foreign_keys").fetchone()[0] != 1 or db.execute("PRAGMA synchronous").fetchone()[0] != 2:
            db.close()
            raise StoreError("SQLite safety pragmas unavailable")
        return db

    @staticmethod
    def readonly_snapshot(database_path, *, worktree=None, timeout=5.0):
        """Read an existing store as one consistent, strictly read-only snapshot.

        Unlike WorkflowStore construction this never creates directories, opens a
        writable connection, migrates schema, or initializes an empty database.
        """
        path = Path(database_path).expanduser().resolve()
        if not path.is_file():
            return {"available": False, "reason": "database_missing", "captured_at_ms":int(time.time()*1000),"records": [],"history":[]}
        captured_at_ms=int(time.time()*1000)
        uri = path.as_uri() + "?mode=ro"
        try:
            db = sqlite3.connect(uri, uri=True, timeout=timeout, isolation_level=None)
            db.row_factory = sqlite3.Row
            db.execute("PRAGMA query_only=ON")
            db.execute(f"PRAGMA busy_timeout={max(0, int(timeout * 1000))}")
            db.execute("BEGIN")
            if db.execute("PRAGMA application_id").fetchone()[0] != APPLICATION_ID or db.execute("PRAGMA user_version").fetchone()[0] != SCHEMA_VERSION:
                raise StoreError("incompatible workflow database")
            required = {"feature_heads", "runs", "attempts", "tasks"}
            present = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
            if not required <= present:
                raise StoreError("incompatible workflow database")
            expected_tables,expected_indexes=_schema_inventory()
            actual_tables={name:tuple(r[1] for r in db.execute(f'PRAGMA table_info("{name}")')) for name in present}
            actual_indexes={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='index' AND sql IS NOT NULL")}
            if actual_tables!=expected_tables or actual_indexes!=expected_indexes:
                raise StoreError("incompatible workflow database")
            head_sql="SELECT worktree,sprint,run_id FROM feature_heads"; head_args=()
            if worktree is not None: head_sql+=" WHERE worktree=?"; head_args=(worktree,)
            head_sql+=" ORDER BY worktree,sprint"
            head_rows=db.execute(head_sql,head_args).fetchall()
            head_ids={row['run_id'] for row in head_rows}
            head_records=[_readonly_run_record(db,row['run_id'],row['worktree'],row['sprint']) for row in head_rows]
            history_sql="SELECT id,sprint,worktree,run_json FROM runs"; history_args=()
            if worktree is not None: history_sql+=" WHERE worktree=?"; history_args=(worktree,)
            history_sql+=" ORDER BY worktree,sprint,id"
            history=[]
            for runrow in db.execute(history_sql,history_args):
                if runrow['id'] not in head_ids:
                    history.append(_readonly_run_record(db,runrow['id'],runrow['worktree'],runrow['sprint']))
            unscoped_incidents={'open':0,'recovering':0,'total':0}
            for row in db.execute("SELECT incident_json FROM incidents WHERE run_id IS NULL AND resolved_ms IS NULL"):
                incident=strict_loads(row['incident_json'])
                state=incident.get('state') if isinstance(incident,dict) else None
                if state in ('OPEN','RECOVERING'):
                    unscoped_incidents[state.lower()]+=1
                    unscoped_incidents['total']+=1
            # Global native-capacity visibility is intentionally aggregate-only:
            # foreign task/run/worktree identities and proofs never leave SQLite.
            native_capacity={row['state'].lower():row['count'] for row in db.execute(
                "SELECT state,COUNT(*) AS count FROM resources WHERE resource_key='native_validation' AND state IN ('HELD','UNKNOWN') GROUP BY state")}
            native_capacity={'held':native_capacity.get('held',0),'unknown':native_capacity.get('unknown',0)}
            db.rollback()
            return {"available":True,"captured_at_ms":captured_at_ms,"records":head_records,
                    "history":history,"unscoped_incidents":unscoped_incidents,
                    "native_capacity":native_capacity}
        except (sqlite3.Error, StoreError, ValueError, TypeError, KeyError) as exc:
            return {"available":False,"reason":"database_unavailable","detail":str(exc),
                    "captured_at_ms":int(time.time()*1000),"records":[],"history":[]}
        finally:
            if 'db' in locals(): db.close()

    def _initialize(self):
        db = self._connect()
        try:
            app_id = db.execute("PRAGMA application_id").fetchone()[0]
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if app_id not in (0, APPLICATION_ID) or version not in (0, SCHEMA_VERSION):
                raise StoreError(f"incompatible schema version {version}")
            tables = db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
            if version == SCHEMA_VERSION:
                if app_id != APPLICATION_ID:
                    raise StoreError("versioned database has no workflow application ID")
                # Additive dependency pause records are safe for existing v1 stores.
                db.executescript("BEGIN IMMEDIATE; CREATE TABLE IF NOT EXISTS dependency_circuits(dependency_key TEXT PRIMARY KEY,generation INTEGER NOT NULL,state TEXT NOT NULL,failures INTEGER NOT NULL DEFAULT 0,opened_ms INTEGER,next_probe_ms INTEGER,probes INTEGER NOT NULL DEFAULT 0,last_failure_id TEXT,details_json TEXT NOT NULL DEFAULT '{}'); CREATE TABLE IF NOT EXISTS dependency_failures(dependency_key TEXT NOT NULL REFERENCES dependency_circuits(dependency_key),failure_id TEXT NOT NULL,payload_json TEXT NOT NULL,PRIMARY KEY(dependency_key,failure_id)); CREATE TABLE IF NOT EXISTS task_dependency_pauses(task_id TEXT PRIMARY KEY REFERENCES tasks(id),dependency_key TEXT NOT NULL,paused_since_ms INTEGER NOT NULL,accumulated_paused_ms INTEGER NOT NULL DEFAULT 0,context_json TEXT NOT NULL); CREATE TABLE IF NOT EXISTS author_scopes(scope_id TEXT PRIMARY KEY,caller TEXT NOT NULL,submission_key TEXT NOT NULL,payload_digest TEXT NOT NULL,scope_json TEXT NOT NULL,UNIQUE(caller,submission_key)); COMMIT;")
                # Build the expected inventory from the live DDL so additive
                # artifact columns/indexes remain owned by that DDL.
                expected_tables,expected_indexes=_schema_inventory()
                actual_tables={r[0]:tuple(row[1] for row in db.execute(f'PRAGMA table_info("{r[0]}")')) for r in tables}
                actual_indexes={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='index' AND sql IS NOT NULL")}
                if actual_tables!=expected_tables or actual_indexes!=expected_indexes:
                    raise StoreError("workflow schema inventory is incompatible")
                return
            if app_id != 0 or version != 0 or tables:
                raise StoreError("only an empty unidentified database can be initialized")
            mode = db.execute("PRAGMA journal_mode=WAL").fetchone()[0]
            if str(mode).lower() != "wal":
                raise StoreError("SQLite WAL mode unavailable")
            db.executescript("BEGIN IMMEDIATE;\n" + _DDL.replace("PRAGMA user_version=1;", "PRAGMA user_version=1;\nPRAGMA application_id="+str(APPLICATION_ID)+";") + "\nCOMMIT;")
            if db.execute("PRAGMA foreign_keys").fetchone()[0] != 1 or db.execute("PRAGMA synchronous").fetchone()[0] != 2:
                raise StoreError("SQLite safety pragmas unavailable")
        except BaseException:
            if db.in_transaction:
                db.rollback()
            raise
        finally:
            db.close()

    def close(self):
        """Connections are short-lived; included as an explicit lifecycle marker."""

    def submit(self, *, caller, request, run, first_task, outbox_event, logical_task_key=None, rejected_response=None, incident=None, replace_head_run_id=None):
        """Atomically record exact receipt; accepted response/run/task/outbox commit together."""
        request = validate(request, "submission_request")
        if not isinstance(caller, str) or not caller.strip():
            raise ProtocolError("caller binding is required")
        request_digest = digest(request)
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            existing = db.execute("SELECT request_digest,response_json FROM submissions WHERE caller=? AND operation='submit' AND idem_key=?", (caller,request["idempotency_key"])).fetchone()
            if existing:
                if existing["request_digest"] != request_digest:
                    raise IdempotencyConflict("idempotency key reused with different request")
                db.commit()
                return json.loads(existing["response_json"])
            if rejected_response is not None:
                response = validate(rejected_response, "submission_response")
                if response["status"] != "REJECTED":
                    raise ProtocolError("rejected response must be REJECTED")
                _validate_rejection_incident(response, incident)
                db.execute("INSERT INTO submissions VALUES(?,?,?,?,?,?,?)", ("submit_"+hashlib.sha256((caller+"\0submit\0"+request["idempotency_key"]).encode()).hexdigest()[:24],caller,"submit",request["idempotency_key"],request_digest,canonical_json(request),canonical_json(response)))
                if incident is not None: self._record_incident_tx(db, incident)
                db.commit()
                return response
            run = validate(run, "run")
            first_task = validate(first_task, "task")
            if not isinstance(logical_task_key, str) or not logical_task_key.strip():
                raise ProtocolError("logical_task_key is required for accepted work")
            outbox_event = _validate_internal_task_event(outbox_event, first_task)
            response = {"contract_version":1,"status":"ACCEPTED","run_id":run["run_id"],"generation":1,"next_action":"WAIT_FOR_RESULT"}
            validate(response, "submission_response")
            if (run["run_kind"] != "FEATURE_GATE" or run["sprint_id"] != request["sprint_id"] or
                run["input_digest"] != request["input_digest"] or run["plan_digest"] != request["plan_digest"] or
                run["policy_digest"] != request["policy_digest"] or run["author"] != request["author"] or
                run["current_attempt"] != 1 or run["sequence"] != 0 or first_task["run_id"] != run["run_id"] or
                first_task["evaluation_generation"] != 1 or first_task["kind"] != "CHECK" or
                first_task["state"] != "QUEUED" or first_task["result"] is not None or first_task["assignment_generation"] != 1 or
                first_task["assigned_session_id"] is not None or
                first_task["dependency_ids"] or first_task["input_digest"] != request["input_digest"] or
                outbox_event["run_id"] != run["run_id"] or outbox_event["evaluation_generation"] != 1 or
                outbox_event["task_id"] != first_task["task_id"] or outbox_event["assignment_generation"] != 1 or
                outbox_event["input_digest"] != request["input_digest"]):
                raise ProtocolError("submission/run/task binding mismatch")
            explicit_target=request.get("replaces_run_id")
            if replace_head_run_id is not None and explicit_target is not None and replace_head_run_id != explicit_target:
                raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
            author_request_target=explicit_target is not None
            target=explicit_target if author_request_target else replace_head_run_id
            head=db.execute("SELECT run_id FROM feature_heads WHERE worktree=? AND sprint=?",(request["source_worktree"],request["sprint_id"])).fetchone()
            if head:
                if target is None: raise SubmissionHeadConflict(SubmissionHeadConflict.REPLACEMENT_REQUIRED)
                if target!=head[0]: raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
                priorrow=db.execute("SELECT current_generation,run_json,worktree,sprint FROM runs WHERE id=?",(head[0],)).fetchone()
                if priorrow is None or priorrow[2]!=request["source_worktree"] or priorrow[3]!=request["sprint_id"]:
                    raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
                prior_generation=priorrow[0]
                prior_run=json.loads(priorrow[1])
                if author_request_target and prior_run.get("run_kind")!="FEATURE_GATE":
                    raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
                if author_request_target and prior_run.get("author")!=request["author"]:
                    grant=self._valid_author_successor_grant_tx(db,run_id=head[0],worktree=request['source_worktree'],
                        predecessor_session_id=prior_run.get('author',{}).get('session_id'),
                        successor_session_id=request.get('author',{}).get('session_id'),generation=prior_generation)
                    if grant is None:
                        raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
                oldattempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(head[0],prior_generation)).fetchone()
                oldjson=None if oldattempt is None else json.loads(oldattempt[0])
                if author_request_target and oldjson is not None and oldjson.get("phase") in ("DECIDED","CLOSED") and (
                    oldjson.get("phase")!="DECIDED" or oldjson.get("result")!="FAIL"):
                    raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
                unsettled=db.execute("SELECT 1 FROM tasks WHERE run_id=? AND json_extract(task_json,'$.state') NOT IN ('COMPLETED','CANCELLED')",(head[0],)).fetchone() if oldattempt else True
                held=db.execute("SELECT 1 FROM resources WHERE run_id=? AND state IN ('HELD','UNKNOWN')",(head[0],)).fetchone()
                dirty_scope=db.execute("SELECT 1 FROM native_scopes n JOIN tasks t ON t.id=n.task_id WHERE t.run_id=? AND n.state!='CLEANED'",(head[0],)).fetchone()
                allowed_phase=("DECIDED",) if author_request_target else ("DECIDED","CLOSED")
                if oldjson is None or oldjson["phase"] not in allowed_phase or unsettled or held or dirty_scope:
                    raise SubmissionHeadConflict(SubmissionHeadConflict.ACTIVE_OR_UNSETTLED)
                if author_request_target:
                    if oldjson.get("result")!="FAIL" or oldjson.get("cleanup_state") not in ("NOT_REQUIRED","CONFIRMED"):
                        raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
            elif target is not None:
                raise SubmissionHeadConflict(SubmissionHeadConflict.INVALID_TARGET)
            db.execute("INSERT INTO submissions VALUES(?,?,?,?,?,?,?)", ("submit_"+hashlib.sha256((caller+"\0submit\0"+request["idempotency_key"]).encode()).hexdigest()[:24],caller,"submit",request["idempotency_key"],request_digest,canonical_json(request),canonical_json(response)))
            db.execute("INSERT INTO runs VALUES(?,?,?,?,?,0)", (run["run_id"],run["sprint_id"],request["source_worktree"],canonical_json(run),1))
            db.execute("INSERT INTO feature_heads(worktree,sprint,run_id) VALUES(?,?,?) ON CONFLICT(worktree,sprint) DO UPDATE SET run_id=excluded.run_id",(request["source_worktree"],request["sprint_id"],run["run_id"]))
            attempt = {"contract_version":1,"run_id":run["run_id"],"generation":1,"phase":"QUEUED","result":None,"closure":None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None}
            validate(attempt,"attempt")
            db.execute("INSERT INTO attempts VALUES(?,?,?)", (run["run_id"],1,canonical_json(attempt)))
            db.execute("INSERT INTO tasks(id,run_id,generation,logical_key,task_json) VALUES(?,?,?,?,?)", (first_task["task_id"],run["run_id"],1,logical_task_key,canonical_json(first_task)))
            self._event(db, outbox_event, run["run_id"], 1)
            db.execute("INSERT INTO outbox(event_id) VALUES(?)", (outbox_event["event_id"],))
            db.commit()
            return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally:
            db.close()

    def record_rejection(self, *, caller, idempotency_key, raw_request, response, incident=None):
        """Persist rejection for malformed input without inventing an admitted run."""
        if not isinstance(caller,str) or not caller.strip() or not isinstance(idempotency_key,str) or not idempotency_key.strip():
            raise ProtocolError("caller and explicit idempotency key are required")
        raw=strict_loads(raw_request) if isinstance(raw_request,str) else raw_request
        if not isinstance(raw,dict): raise ProtocolError("rejected request must be a JSON object")
        if raw.get("idempotency_key") != idempotency_key: raise ProtocolError("rejection idempotency key binding mismatch")
        response=validate(response,"submission_response")
        if response["status"]!="REJECTED": raise ProtocolError("rejection response must be REJECTED")
        _validate_rejection_incident(response, incident)
        raw_digest=digest(raw); db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            old=db.execute("SELECT request_digest,response_json FROM submissions WHERE caller=? AND operation='submit' AND idem_key=?",(caller,idempotency_key)).fetchone()
            if old:
                if old[0]!=raw_digest: raise IdempotencyConflict("idempotency key reused with changed request")
                if json.loads(old[1])!=response: raise IdempotencyConflict("rejection receipt changed")
                db.commit(); return response
            sid=hashlib.sha256((caller+"\0submit\0"+idempotency_key).encode()).hexdigest()[:24]
            db.execute("INSERT INTO submissions VALUES(?,?,?,?,?,?,?)",("submit_"+sid,caller,"submit",idempotency_key,raw_digest,canonical_json(raw),canonical_json(response)))
            if incident is not None: self._record_incident_tx(db, incident)
            db.commit(); return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    @staticmethod
    def _event(db, event, run_id=None, generation=None):
        payload=canonical_json(event)
        db.execute("INSERT INTO events VALUES(?,?,?,?,?,?,?)", (event["event_id"],run_id,generation,event.get("kind","DISPATCH"),payload,hashlib.sha256(payload.encode()).hexdigest(),int(time.time()*1000)))

    @staticmethod
    def _native_resource_board_hint_tx(db, resource_key, claim_id, state, run_id, generation):
        """Queue a durable internal board wake in the resource mutation transaction."""
        if resource_key != "native_validation":
            return
        event_id="event_board_native_"+hashlib.sha256(f"{claim_id}\0{state}\0{generation}".encode()).hexdigest()[:32]
        event={"contract_version":1,"event_id":event_id,"kind":"BOARD_STATE_CHANGED",
               "run_id":run_id,"evaluation_generation":generation}
        WorkflowStore._event(db,event,run_id,generation)
        db.execute("INSERT INTO outbox(event_id) VALUES(?)",(event_id,))

    def get_submission(self, caller, key):
        db=self._connect()
        try:
            row=db.execute("SELECT response_json FROM submissions WHERE caller=? AND operation='submit' AND idem_key=?",(caller,key)).fetchone()
            return None if row is None else json.loads(row[0])
        finally: db.close()

    def lookup_submission_receipt(self, *, caller, idempotency_key, request_digest):
        """Read the saved submit receipt only when its caller/key/input all match."""
        db=self._connect()
        try:
            row=db.execute("SELECT request_digest,response_json FROM submissions WHERE caller=? AND operation='submit' AND idem_key=?",(caller,idempotency_key)).fetchone()
            if row is None: return None
            if row[0] != request_digest: raise IdempotencyConflict("idempotency key reused with different request")
            return json.loads(row[1])
        finally: db.close()

    def submission_artifacts(self, *, caller, idempotency_key):
        """Return registered evidence for this caller's actually accepted submission."""
        db=self._connect()
        try:
            row=db.execute("SELECT id,response_json FROM submissions WHERE caller=? AND operation='submit' AND idem_key=?",(caller,idempotency_key)).fetchone()
            if row is None: return []
            response=json.loads(row[1])
            if response.get('status') != 'ACCEPTED': return []
            rows=db.execute("SELECT artifact_json FROM artifacts WHERE run_id IS NULL ORDER BY id").fetchall()
            result=[]
            for item, in rows:
                binding=json.loads(item); artifact=binding['artifact']
                if artifact.get('producer_kind')=='SUBMISSION' and artifact.get('submission_id')==row[0] and artifact.get('producer_id')==row[0]:
                    result.append(binding)
            return result
        finally: db.close()

    def get_run(self, run_id):
        db=self._connect()
        try:
            row=db.execute("SELECT run_json,current_generation,sequence FROM runs WHERE id=?",(run_id,)).fetchone()
            if row is None: return None
            run=json.loads(row[0]); attempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(run_id,row[1])).fetchone()
            run["current_attempt"] = row[1]
            run["sequence"]=row[2]
            return {"run":run,"attempt":json.loads(attempt[0])}
        finally: db.close()

    def list_runs_by_phase(self, phases):
        """Return saved run IDs whose current attempt is in one of these phases."""
        phases=tuple(phases)
        if not phases: return []
        db=self._connect()
        try:
            return [row[0] for row in db.execute(
                "SELECT a.run_id FROM attempts a JOIN runs r ON r.id=a.run_id AND r.current_generation=a.generation WHERE json_extract(a.attempt_json,'$.phase') IN (%s) ORDER BY a.run_id" % ','.join('?' for _ in phases), phases).fetchall()]
        finally: db.close()

    def get_task(self, task_id):
        """Read one saved task snapshot for an adapter; no lifecycle mutation."""
        db=self._connect()
        try:
            row=db.execute("SELECT task_json,logical_key FROM tasks WHERE id=?",(task_id,)).fetchone()
            return None if row is None else dict(json.loads(row[0]),logical_key=row[1])
        finally: db.close()

    def list_tasks(self, run_id, generation=None):
        """Read saved tasks for a run, optionally narrowed to an attempt."""
        db=self._connect()
        try:
            if generation is None:
                rows=db.execute("SELECT task_json,logical_key FROM tasks WHERE run_id=? ORDER BY generation,id",(run_id,)).fetchall()
            else:
                rows=db.execute("SELECT task_json,logical_key FROM tasks WHERE run_id=? AND generation=? ORDER BY id",(run_id,generation)).fetchall()
            result=[dict(json.loads(r[0]),logical_key=r[1]) for r in rows]
            if generation is not None:
                for item in self._effective_check_tasks(db,run_id,generation):
                    if item.get('reused'):
                        result.append(item)
            return result
        finally: db.close()

    def effective_checks_complete(self, run_id, generation):
        db=self._connect()
        try: return self._effective_checks_complete(db,run_id,generation)
        finally: db.close()

    def get_native_claim(self, task_id):
        """Read the immutable native dispatch identity/token for adapter replay fencing."""
        db=self._connect()
        try:
            row=db.execute("SELECT process_identity,claim_token FROM native_claims WHERE task_id=?",(task_id,)).fetchone()
            return None if row is None else {'process_identity':json.loads(row[0]),'claim_token':row[1]}
        finally: db.close()

    def get_run_submission(self, run_id):
        """Read accepted request and its immutable SUBMISSION artifacts for a run."""
        db=self._connect()
        try:
            row=db.execute("SELECT run_json FROM runs WHERE id=?",(run_id,)).fetchone()
            if row is None: return None
            run=json.loads(row[0])
            submission=db.execute("SELECT id,request_json,response_json,caller,idem_key FROM submissions WHERE response_json LIKE ?",('%"run_id":"'+run_id+'"%',)).fetchone()
            if submission is None: return None
            submission_id=submission[0]
            artifacts=[]
            for (raw,) in db.execute("SELECT artifact_json FROM artifacts WHERE run_id IS NULL"):
                item=json.loads(raw)
                if item.get('artifact',{}).get('submission_id')==submission_id: artifacts.append(item)
            return {'submission_id':submission_id,'request':json.loads(submission[1]),'response':json.loads(submission[2]),
                    'caller':submission[3],'idempotency_key':submission[4],'artifacts':artifacts}
        finally: db.close()

    def outbox_summary(self):
        """Return bounded aggregate outbox state counts (never event payloads)."""
        db=self._connect()
        try:
            return {row[0]:row[1] for row in db.execute("SELECT state,COUNT(*) FROM outbox GROUP BY state")}
        finally: db.close()

    def get_event(self, event_id):
        """Read immutable event identity, payload, and creation timestamp."""
        db=self._connect()
        try:
            row=db.execute("SELECT id,run_id,generation,kind,payload,payload_digest,created_ms FROM events WHERE id=?",(event_id,)).fetchone()
            return None if row is None else {"event_id":row[0],"run_id":row[1],"generation":row[2],"kind":row[3],"payload":json.loads(row[4]),"payload_digest":row[5],"created_ms":row[6]}
        finally: db.close()

    def record_native_check_completed(self, task_id, check_index, artifact_ref):
        """Record an immutable successful inner-check link to task provenance."""
        if type(check_index) is not int or check_index < 0:
            raise ProtocolError("check_index must be a nonnegative integer")
        if not isinstance(task_id, str) or not task_id or not isinstance(artifact_ref, str) or not artifact_ref:
            raise ProtocolError("task and artifact references are required")
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT task_json FROM tasks WHERE id=?", (task_id,)).fetchone()
            if row is None: raise StaleCommand("unknown task")
            task = json.loads(row[0])
            current = db.execute("SELECT current_generation FROM runs WHERE id=?", (task["run_id"],)).fetchone()
            if (task["kind"] != "CHECK" or task["state"] not in ("CLAIMED", "RUNNING") or
                    current is None or current[0] != task["evaluation_generation"]):
                raise StaleCommand("native check task is not active")
            artifact_row = db.execute("SELECT run_id,artifact_json FROM artifacts WHERE id=?", (artifact_ref,)).fetchone()
            if artifact_row is None: raise ProtocolError("unknown artifact reference")
            binding = json.loads(artifact_row[1]); artifact = binding["artifact"]
            if (artifact_row[0] != task["run_id"] or binding.get("input_digest") != task["input_digest"] or
                    artifact.get("type") != "PROVENANCE" or artifact.get("producer_kind") != "TASK" or
                    artifact.get("producer_task_id") != task_id or artifact.get("producer_id") != task_id or
                    artifact.get("run_id") != task["run_id"]):
                raise ProtocolError("native check artifact must be task-owned input-bound PROVENANCE")
            prior = db.execute("SELECT artifact_ref FROM native_check_records WHERE task_id=? AND check_index=?", (task_id, check_index)).fetchone()
            if prior:
                if prior[0] != artifact_ref: raise IdempotencyConflict("native check record is immutable")
                db.commit(); return {"disposition":"REPLAYED","artifact_ref":artifact_ref}
            db.execute("INSERT INTO native_check_records VALUES(?,?,?,?)", (task_id, check_index, task["assignment_generation"], artifact_ref))
            db.commit(); return {"disposition":"ACCEPTED","artifact_ref":artifact_ref}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def list_native_check_records(self, task_id):
        db = self._connect()
        try:
            return [{"task_id": row[0], "check_index": row[1], "assignment_generation": row[2], "artifact_ref": row[3]}
                    for row in db.execute("SELECT task_id,check_index,assignment_generation,artifact_ref FROM native_check_records WHERE task_id=? ORDER BY check_index", (task_id,))]
        finally: db.close()

    def get_terminal_result(self, run_id, generation):
        """Read the immutable terminal result event for an attempt, if present."""
        db=self._connect()
        try:
            row=db.execute("SELECT e.payload FROM history h JOIN events e ON e.id=h.event_id WHERE h.run_id=? AND h.generation=? AND json_extract(e.payload,'$.result_id') IS NOT NULL ORDER BY h.sequence DESC LIMIT 1",(run_id,generation)).fetchone()
            return None if row is None else json.loads(row[0])
        finally: db.close()

    @staticmethod
    def _effective_check_tasks(db, run_id, generation):
        """Own CHECK tasks plus explicit immutable source links for a recovery attempt."""
        own=[dict(json.loads(r[0]),logical_key=r[1],reused=False) for r in db.execute("SELECT task_json,logical_key FROM tasks WHERE run_id=? AND generation=? AND json_extract(task_json,'$.kind')='CHECK'",(run_id,generation))]
        for key,source_id in db.execute("SELECT logical_check_key,source_task_id FROM reused_checks WHERE run_id=? AND target_generation=? ORDER BY logical_check_key",(run_id,generation)):
            row=db.execute("SELECT task_json FROM tasks WHERE id=?",(source_id,)).fetchone()
            if row is not None: own.append(dict(json.loads(row[0]),logical_key=key,reused=True))
        return own

    @staticmethod
    def _effective_checks_complete(db,run_id,generation):
        tasks=WorkflowStore._effective_check_tasks(db,run_id,generation)
        obligations={r[0] for r in db.execute("SELECT logical_check_key FROM recovery_check_obligations WHERE run_id=? AND generation=?",(run_id,generation))}
        if not tasks: return False
        for t in tasks:
            if t["state"]!="COMPLETED" or t.get("result",{}).get("decision")!="PASS": return False
            if t.get("reused"):
                links=db.execute("SELECT receipt_refs_json FROM reused_checks WHERE run_id=? AND target_generation=? AND logical_check_key=?",(run_id,generation,t["logical_key"])).fetchone()
                if links is None or not json.loads(links[0]): return False
        return not obligations or obligations.issubset({t["logical_key"] for t in tasks if t["state"]=="COMPLETED" and t.get("result",{}).get("decision")=="PASS"})

    def register_artifact(self, artifact, *, input_digest=None):
        """Register immutable external artifact metadata; byte verification is external."""
        artifact = validate(artifact, "artifact")
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            run_id = artifact.get("run_id")
            if artifact["producer_kind"] == "SUBMISSION":
                receipt = db.execute("SELECT id,request_json FROM submissions WHERE id=?", (artifact["submission_id"],)).fetchone()
                if artifact["producer_id"] != artifact["submission_id"] or run_id is not None or not input_digest:
                    raise ProtocolError("submission artifact requires its immutable submission identity and input digest")
                if receipt is not None:
                    request = json.loads(receipt[1])
                    if artifact["producer_id"] != receipt[0] or input_digest != request.get("input_digest"):
                        raise ProtocolError("submission artifact provenance mismatch")
            elif artifact["producer_kind"] == "TASK":
                producer = db.execute("SELECT run_id,generation,task_json FROM tasks WHERE id=?", (artifact["producer_task_id"],)).fetchone()
                if producer is None or producer[0] != run_id or artifact["producer_id"] != artifact["producer_task_id"]:
                    raise ProtocolError("artifact task producer mismatch")
                task = json.loads(producer[2])
                if input_digest is not None and input_digest != task["input_digest"]:
                    raise ProtocolError("task artifact input binding mismatch")
                input_digest = task["input_digest"]
                if artifact["type"] == "CHECK_RECEIPT":
                    if task["kind"] != "CHECK": raise ProtocolError("CHECK_RECEIPT must name a CHECK producer")
                    input_digest = input_digest or task["input_digest"]
                    if input_digest != task["input_digest"]: raise ProtocolError("CHECK_RECEIPT input binding mismatch")
                    claim = db.execute("SELECT 1 FROM native_claims WHERE task_id=?", (artifact["producer_task_id"],)).fetchone()
                    if not claim: raise ProtocolError("CHECK_RECEIPT requires a saved native producer claim")
            elif artifact["producer_kind"] == "OWNER":
                raise ProtocolError("OWNER artifact provenance is unsupported until acceptance storage is implemented")
            binding = {"artifact": artifact, "input_digest": input_digest}
            existing = db.execute("SELECT artifact_json FROM artifacts WHERE id=?", (artifact["artifact_id"],)).fetchone()
            body = canonical_json(binding)
            if existing:
                if existing[0] != body:
                    old = json.loads(existing[0])
                    changed = next((key for key in ("artifact", "input_digest")
                                    if old.get(key) != binding.get(key)), "artifact")
                    field = "artifacts.input_digest" if changed == "input_digest" else "artifacts.artifact_id"
                    raise ArtifactIdentityConflict(artifact["artifact_id"], field)
                db.commit(); return {"disposition":"REPLAYED","artifact_id":artifact["artifact_id"]}
            db.execute("INSERT INTO artifacts(id,run_id,artifact_json) VALUES(?,?,?)", (artifact["artifact_id"], run_id, body))
            db.commit(); return {"disposition":"ACCEPTED","artifact_id":artifact["artifact_id"]}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_artifact(self, artifact_id):
        db = self._connect()
        try:
            row = db.execute("SELECT artifact_json FROM artifacts WHERE id=?", (artifact_id,)).fetchone()
            return None if row is None else json.loads(row[0])
        finally:
            db.close()

    def register_author_scope(self, *, caller, command_key, payload_digest, scope):
        """Durably freeze one validated author scope; exact retries return the same receipt."""
        if not all(isinstance(x,str) and x.strip() for x in (caller,command_key,payload_digest)):
            raise ProtocolError('caller, command key, and payload digest are required')
        scope_body=canonical_json(scope); submission_key=scope['submission_idempotency_key']
        scope_id='scope_'+digest(scope)[:32]
        response={'status':'REGISTERED','scope_id':scope_id,'payload_digest':payload_digest,
                  'policy_digest':scope['policy_digest'],'submission_id':scope['submission_id']}
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            command=db.execute('SELECT request_digest,response_json FROM command_receipts WHERE operation=? AND caller=? AND command_key=?',('register_author_scope',caller,command_key)).fetchone()
            if command:
                if command[0]!=payload_digest: raise IdempotencyConflict('registration key reused with different payload')
                db.commit(); return json.loads(command[1])
            previous=db.execute('SELECT scope_id,payload_digest,scope_json FROM author_scopes WHERE caller=? AND submission_key=?',(caller,submission_key)).fetchone()
            if previous:
                if previous['payload_digest']!=payload_digest or previous['scope_json']!=scope_body:
                    raise IdempotencyConflict('submission idempotency key is already bound to another author scope')
                response={'status':'REGISTERED','scope_id':previous['scope_id'],'payload_digest':payload_digest,
                          'policy_digest':scope['policy_digest'],'submission_id':scope['submission_id']}
            else:
                db.execute('INSERT INTO author_scopes VALUES(?,?,?,?,?)',(scope_id,caller,submission_key,payload_digest,scope_body))
            db.execute('INSERT INTO command_receipts VALUES(?,?,?,?,?)',('register_author_scope',caller,command_key,payload_digest,canonical_json(response)))
            db.commit(); return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_author_scope(self, *, caller, submission_key):
        db=self._connect()
        try:
            row=db.execute('SELECT scope_json FROM author_scopes WHERE caller=? AND submission_key=?',(caller,submission_key)).fetchone()
            return None if row is None else json.loads(row[0])
        finally: db.close()

    def get_author_scope_registration_receipt(self, *, caller, command_key, payload_digest=None):
        """Read one caller-bound registration command receipt, optionally exact-input bound."""
        db=self._connect()
        try:
            row=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='register_author_scope' AND caller=? AND command_key=?",(caller,command_key)).fetchone()
            if row is None:return None
            if payload_digest is not None and row['request_digest']!=payload_digest:
                raise IdempotencyConflict('registration key reused with different payload')
            return json.loads(row['response_json'])
        finally:db.close()

    def record_author_scope_rejection(self, *, caller, command_key, payload_digest, response):
        """Persist a bounded validation FAIL in existing command-receipt storage."""
        if not all(isinstance(x,str) and x.strip() for x in (caller,command_key,payload_digest)):
            raise ProtocolError('caller, command key, and payload digest are required')
        body=canonical_json(response)
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='register_author_scope' AND caller=? AND command_key=?",(caller,command_key)).fetchone()
            if row:
                if row['request_digest']!=payload_digest:raise IdempotencyConflict('registration key reused with different payload')
                db.commit();return json.loads(row['response_json'])
            db.execute('INSERT INTO command_receipts VALUES(?,?,?,?,?)',('register_author_scope',caller,command_key,payload_digest,body))
            db.commit();return json.loads(body)
        except BaseException:
            if db.in_transaction:db.rollback()
            raise
        finally:db.close()

    def get_task_result_digest(self, task_id):
        """Read the persisted digest for an exact completed task result."""
        db = self._connect()
        try:
            row = db.execute("SELECT result_digest FROM tasks WHERE id=?", (task_id,)).fetchone()
            return None if row is None else row[0]
        finally:
            db.close()

    def get_recovery_metadata(self, run_id, generation):
        """Read the immutable recovery proof, boundary, and reusable links."""
        db=self._connect()
        try:
            row=db.execute("SELECT fresh_input_proof,boundary,reusable_refs_json FROM recovery_metadata WHERE run_id=? AND generation=?",(run_id,generation)).fetchone()
            if row is None: return None
            return {"run_id":run_id,"generation":generation,"fresh_input_proof":row[0],
                    "boundary":row[1],"reusable_artifact_refs":json.loads(row[2])}
        finally: db.close()

    def get_owner_decisions(self, run_id):
        db = self._connect()
        try:
            return [json.loads(row[0]) for row in db.execute(
                "SELECT response_json FROM owner_decisions WHERE run_id=? ORDER BY rowid", (run_id,))]
        finally:
            db.close()

    def get_owner_decision_receipt(self, owner, command_id):
        """Read the immutable command binding and receipt for replay verification."""
        decision_id = "owner_" + hashlib.sha256(canonical_json({"owner": owner, "command_id": command_id}).encode()).hexdigest()[:24]
        db = self._connect()
        try:
            row = db.execute("SELECT request_digest,decision_json,response_json,owner_binding_json,result_id,result_digest,source_artifact_ref,build_artifact_refs_json,regression_inputs_json FROM owner_decisions WHERE id=?", (decision_id,)).fetchone()
            if row is None:
                return None
            return {"request_digest": row[0], "decision": json.loads(row[1]), "response": json.loads(row[2]),
                    "owner": json.loads(row[3]), "result_id": row[4], "result_digest": row[5],
                    "source_artifact_ref": row[6], "build_artifact_refs": json.loads(row[7]),
                    "regression_inputs": None if row[8] is None else json.loads(row[8])}
        finally:
            db.close()

    def get_acceptance(self, acceptance_id):
        db = self._connect()
        try:
            row = db.execute("SELECT id,run_id,generation,input_digest,decision_id FROM acceptances WHERE id=?", (acceptance_id,)).fetchone()
            return None if row is None else {"acceptance_id": row[0], "run_id": row[1], "evaluation_generation": row[2], "input_digest": row[3], "decision_id": row[4]}
        finally:
            db.close()

    def get_regression_job(self, regression_id):
        db = self._connect()
        try:
            row = db.execute("SELECT job_json FROM regression_jobs WHERE id=?", (regression_id,)).fetchone()
            return None if row is None else json.loads(row[0])
        finally:
            db.close()

    def get_regression_inputs(self, regression_id):
        db = self._connect()
        try:
            row = db.execute("SELECT inputs_json FROM regression_jobs WHERE id=?", (regression_id,)).fetchone()
            return None if row is None else json.loads(row[0])
        finally:
            db.close()

    def admit_regression_job(self, regression_id):
        """Admit a trusted, fully prepared owner regression as native CHECK work."""
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT acceptance_id,job_json,inputs_json FROM regression_jobs WHERE id=?", (regression_id,)).fetchone()
            if row is None: raise StaleCommand("unknown regression job")
            acceptance_id, job_text, inputs_text = row
            job, inputs = json.loads(job_text), json.loads(inputs_text)
            acceptance_row = db.execute("SELECT run_id,generation,input_digest FROM acceptances WHERE id=?", (acceptance_id,)).fetchone()
            if acceptance_row is None: raise StaleCommand("regression job has no acceptance")
            feature_id, generation, accepted_digest = acceptance_row
            feature_row = db.execute("SELECT run_json FROM runs WHERE id=?", (feature_id,)).fetchone()
            feature = None if feature_row is None else json.loads(feature_row[0])
            decision_row = db.execute("SELECT decision_json FROM owner_decisions WHERE id=(SELECT decision_id FROM acceptances WHERE id=?)", (acceptance_id,)).fetchone()
            decision = None if decision_row is None else json.loads(decision_row[0])
            if not feature or feature.get("run_kind") != "FEATURE_GATE" or not decision or decision.get("decision") != "ACCEPT":
                raise StaleCommand("regression requires a stored acceptance of a feature run")
            if (feature["input_digest"] != accepted_digest or decision["input_digest"] != accepted_digest or
                    job.get("acceptance_id") != acceptance_id or job.get("source_input_digest") != accepted_digest or
                    inputs.get("accepted_feature_run_id") != feature_id or inputs.get("acceptance_id") != acceptance_id or
                    inputs.get("source_input_digest", accepted_digest) != accepted_digest):
                raise ProtocolError("regression inputs are not bound to the accepted feature snapshot")
            required = ("snapshot_root", "source_manifest", "source_manifest_ref", "plan", "plan_digest", "policy", "policy_digest", "author", "sprint_id")
            if any(key not in inputs for key in required): raise ProtocolError("trusted regression inputs are incomplete")
            manifest = inputs["source_manifest"]
            manifest_row=db.execute("SELECT run_id,artifact_json FROM artifacts WHERE id=?",(inputs["source_manifest_ref"],)).fetchone()
            manifest_binding=None if manifest_row is None else json.loads(manifest_row[1])
            if (not isinstance(manifest, dict) or manifest_row is None or
                    manifest_row[0] not in (None, feature_id) or manifest_binding["artifact"].get("type") != "SOURCE_MANIFEST"):
                raise ProtocolError("source manifest is not registered to the accepted input")
            if digest(inputs["plan"]) != inputs["plan_digest"] or digest(inputs["policy"]) != inputs["policy_digest"]:
                raise ProtocolError("regression plan or policy digest mismatch")
            if inputs["author"] != feature["author"] or inputs["sprint_id"] != feature["sprint_id"]:
                raise ProtocolError("regression author/sprint differs from accepted feature")
            run_id = job["run_id"]
            prior = db.execute("SELECT current_generation,sequence FROM runs WHERE id=?", (run_id,)).fetchone()
            if prior:
                saved = db.execute("SELECT task_json FROM tasks WHERE id=?", (job["task_id"],)).fetchone()
                if saved is None: raise ProtocolError("conflicting regression run exists")
                db.commit()
                event_id="event_regression_task_" + hashlib.sha256(regression_id.encode()).hexdigest()[:24]
                return {"run_id":run_id,"task_id":job["task_id"],"event_id":event_id,"replayed":True}
            run = validate({"contract_version":1,"run_id":run_id,"sprint_id":inputs["sprint_id"],"run_kind":"POST_ACCEPTANCE_REGRESSION",
                "owner_acceptance_id":acceptance_id,"input_digest":accepted_digest,"plan_digest":inputs["plan_digest"],
                "policy_digest":inputs["policy_digest"],"author":inputs["author"],"current_attempt":1,"sequence":0}, "run")
            task = validate({"contract_version":1,"task_id":job["task_id"],"run_id":run_id,"evaluation_generation":1,
                "kind":"CHECK","dependency_ids":[],"input_digest":accepted_digest,"state":"QUEUED",
                "assignment_generation":1,"assigned_session_id":None,"deadline_ms":int(time.time()*1000)+3600000,"result":None}, "task")
            event_id = "event_regression_task_" + hashlib.sha256(regression_id.encode()).hexdigest()[:24]
            event = _internal_task_event(event_id, task)
            db.execute("INSERT INTO runs VALUES(?,?,?,?,?,0)", (run_id,run["sprint_id"],feature_id,canonical_json(run),1))
            attempt={"contract_version":1,"run_id":run_id,"generation":1,"phase":"QUEUED","result":None,"closure":None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None}
            validate(attempt,"attempt")
            db.execute("INSERT INTO attempts VALUES(?,?,?)",(run_id,1,canonical_json(attempt)))
            db.execute("INSERT INTO tasks(id,run_id,generation,logical_key,task_json) VALUES(?,?,?,?,?)",(job["task_id"],run_id,1,"post_acceptance_full_regression",canonical_json(task)))
            self._event(db,event,run_id,1); db.execute("INSERT INTO outbox(event_id) VALUES(?)",(event_id,))
            db.commit()
            return {"run_id":run_id,"task_id":job["task_id"],"event_id":event_id,"replayed":False}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_regression_for_run(self, run_id):
        db=self._connect()
        try:
            row=db.execute("SELECT j.id,j.acceptance_id,j.job_json,j.inputs_json,a.run_id,a.generation,a.input_digest FROM regression_jobs j JOIN acceptances a ON a.id=j.acceptance_id JOIN runs r ON r.id=? AND json_extract(r.run_json,'$.owner_acceptance_id')=a.id",(run_id,)).fetchone()
            if row is None: return None
            return {"regression_id":row[0],"acceptance_id":row[1],"job":json.loads(row[2]),"inputs":json.loads(row[3]),"acceptance":{"run_id":row[4],"generation":row[5],"input_digest":row[6]}}
        finally: db.close()

    def record_owner_decision(self, decision, *, authenticated_owner, displayed_result_id,
                              displayed_result_digest, source_artifact_ref,
                              build_artifact_refs, regression_inputs=None):
        """Persist an authenticated acceptance and its queued regression obligation.

        This records storage metadata only; artifact bytes and regression execution
        remain the responsibility of later adapters.
        """
        decision = validate(decision, "owner_decision")
        if authenticated_owner != decision["owner"] or authenticated_owner.get("role") != "owner":
            raise ProtocolError("authenticated owner binding mismatch")
        action = decision["decision"]
        if not isinstance(build_artifact_refs, list) or not build_artifact_refs:
            raise ProtocolError("at least one BUILD artifact reference is required")
        if action == "ACCEPT" and not isinstance(regression_inputs, dict):
            raise ProtocolError("trusted regression inputs are required")
        if regression_inputs is not None:
            if not isinstance(regression_inputs, dict): raise ProtocolError("regression inputs must be an object")
            canonical_json(regression_inputs)
        elif action == "ACCEPT":
            raise ProtocolError("trusted regression inputs are required")
        command_digest = digest({"decision": decision, "displayed_result_id": displayed_result_id,
            "displayed_result_digest": displayed_result_digest, "source_artifact_ref": source_artifact_ref,
            "build_artifact_refs": build_artifact_refs, "regression_inputs": regression_inputs})
        decision_id = "owner_" + hashlib.sha256(canonical_json({"owner": decision["owner"], "command_id": decision["command_id"]}).encode()).hexdigest()[:24]
        acceptance_id = decision.get("acceptance_id")
        regression_id = "task_regression_" + acceptance_id.removeprefix("accept_") if acceptance_id else None
        regression_run_id = "run_regression_" + acceptance_id.removeprefix("accept_") if acceptance_id else None
        response = ({"decision_id": decision_id, "acceptance_id": acceptance_id,
                    "regression_id": regression_id, "regression_run_id": regression_run_id} if action == "ACCEPT" else
                    {"decision_id": decision_id, "decision": action, "run_id": decision["run_id"],
                     "evaluation_generation": decision["evaluation_generation"], "input_digest": decision["input_digest"]})
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            run_row=db.execute("SELECT run_json FROM runs WHERE id=?",(decision["run_id"],)).fetchone()
            if run_row is None:
                raise StaleCommand("run not found")
            persisted_run=json.loads(run_row[0])
            if persisted_run.get("author",{}).get("worktree") != authenticated_owner.get("worktree"):
                raise PermissionError("run is outside configured owner worktree")
            old = db.execute("SELECT request_digest,response_json FROM owner_decisions WHERE id=?", (decision_id,)).fetchone()
            if old:
                if old[0] != command_digest:
                    raise IdempotencyConflict("owner command ID reused with changed content")
                db.commit()
                return json.loads(old[1])
            row = db.execute("SELECT r.run_json,r.current_generation,r.sequence,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id AND a.generation=r.current_generation WHERE r.id=?", (decision["run_id"],)).fetchone()
            if row is None or row[1] != decision["evaluation_generation"] or row[2] != decision["expected_sequence"]:
                raise StaleCommand("owner decision run generation or sequence is stale")
            run = json.loads(row[0]); attempt = json.loads(row[3])
            if run.get("run_kind") != "FEATURE_GATE":
                raise ProtocolError("owner decision is only valid for a FEATURE_GATE run")
            if run["input_digest"] != decision["input_digest"] or attempt.get("phase") != "DECIDED" or attempt.get("result") != "PASS":
                raise StaleCommand("owner decision requires current terminal PASS")
            hist = db.execute("SELECT e.payload FROM history h JOIN events e ON e.id=h.event_id WHERE h.run_id=? AND h.generation=? ORDER BY h.sequence DESC LIMIT 1", (decision["run_id"], decision["evaluation_generation"])).fetchone()
            event = None if hist is None else json.loads(hist[0])
            if not event or event.get("result") != "PASS" or event.get("result_id") != displayed_result_id or event.get("result_digest") != displayed_result_digest:
                raise StaleCommand("displayed result does not match current saved PASS")
            self._require_artifacts(db, [source_artifact_ref], decision["run_id"], input_digest=decision["input_digest"])
            source_binding = json.loads(db.execute("SELECT artifact_json FROM artifacts WHERE id=?", (source_artifact_ref,)).fetchone()[0])
            if source_binding["artifact"].get("type") != "SOURCE_MANIFEST":
                raise ProtocolError("source reference must be a SOURCE_MANIFEST")
            self._require_artifacts(db, build_artifact_refs, decision["run_id"], input_digest=decision["input_digest"])
            for ref in build_artifact_refs:
                binding = json.loads(db.execute("SELECT artifact_json FROM artifacts WHERE id=?", (ref,)).fetchone()[0])
                artifact = binding["artifact"]
                producer = db.execute("SELECT task_json FROM tasks WHERE id=?", (artifact.get("producer_task_id"),)).fetchone()
                task = None if producer is None else json.loads(producer[0])
                effective_ids = {t["task_id"] for t in self._effective_check_tasks(db, decision["run_id"], decision["evaluation_generation"])}
                if (artifact.get("type") != "BUILD" or artifact.get("producer_kind") != "TASK" or
                    artifact.get("producer_task_id") not in effective_ids or not task or task.get("kind") != "CHECK" or
                    task.get("state") != "COMPLETED" or task.get("result", {}).get("decision") != "PASS"):
                    raise ProtocolError("BUILD reference must be produced by a completed PASS task")
            if action == "ACCEPT":
                inputs = dict(regression_inputs)
                inputs["source_input_digest"] = decision["input_digest"]
                inputs["source_artifact_ref"] = source_artifact_ref
                prior = db.execute("SELECT id,decision_id FROM acceptances WHERE run_id=? AND generation=? AND input_digest=?", (decision["run_id"], decision["evaluation_generation"], decision["input_digest"])).fetchone()
                if prior:
                    if prior[0] != acceptance_id: raise ProtocolError("snapshot already has a different acceptance identity")
                    saved = db.execute("SELECT response_json,decision_json,owner_binding_json,result_id,result_digest,source_artifact_ref,build_artifact_refs_json,regression_inputs_json FROM owner_decisions WHERE id=?", (prior[1],)).fetchone()
                    old_decision=json.loads(saved[1]); old_decision.pop("command_id",None)
                    new_decision=dict(decision); new_decision.pop("command_id",None)
                    old_inputs=json.loads(saved[7] or "null")
                    new_inputs=inputs
                    for value in (old_inputs,new_inputs):
                        if isinstance(value,dict):
                            value.pop("source_input_digest",None); value.pop("source_artifact_ref",None)
                    if (old_decision != new_decision or json.loads(saved[2]) != authenticated_owner or
                        saved[3] != displayed_result_id or saved[4] != displayed_result_digest or
                        saved[5] != source_artifact_ref or json.loads(saved[6]) != build_artifact_refs or
                        old_inputs != new_inputs):
                        raise IdempotencyConflict("acceptance identity reused with changed owner decision semantics")
                    db.commit()
                    return json.loads(saved[0])
            feedback = {"contract_version": 1, "event_id": "event_owner_feedback_" + decision_id[6:],
                "kind": "OWNER_FEEDBACK", "decision_id": decision_id, "run_id": decision["run_id"],
                "evaluation_generation": decision["evaluation_generation"], "input_digest": decision["input_digest"],
                "author": run["author"], "findings": decision["findings"],
                "next_action": {"ACCEPT": "REGRESSION_QUEUED", "REQUEST_CHANGES": "FIX_FINDINGS", "CANCEL": "CLOSED"}[action]}
            if action != "ACCEPT":
                feedback.update(result_id=feedback["event_id"], control_kind="OWNER_DECISION",
                    source_result_id=displayed_result_id, source_result_digest=displayed_result_digest)
                feedback["result_digest"]=digest({k:v for k,v in feedback.items() if k!="event_id"})
                self._event(db, feedback, decision["run_id"], decision["evaluation_generation"])
                db.execute("INSERT INTO outbox(event_id) VALUES(?)", (feedback["event_id"],))
                db.execute("INSERT INTO owner_decisions VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", (decision_id, decision["run_id"], decision["evaluation_generation"], decision["input_digest"], command_digest, canonical_json(decision), canonical_json(response), canonical_json(authenticated_owner), displayed_result_id, displayed_result_digest, source_artifact_ref, canonical_json(build_artifact_refs), None))
                self._enqueue_control_feedback_tx(db, feedback, run, source_result_id=displayed_result_id,
                                                  source_result_digest=displayed_result_digest)
                db.commit()
                return response
            inputs = dict(regression_inputs)
            inputs["source_input_digest"] = decision["input_digest"]
            inputs["source_artifact_ref"] = source_artifact_ref
            job = validate({"contract_version": 1, "regression_id": regression_id, "acceptance_id": acceptance_id,
                "run_id": regression_run_id, "source_input_digest": decision["input_digest"],
                "task_id": regression_id, "status": "QUEUED", "result": None}, "regression_job")
            self._event(db, feedback, decision["run_id"], decision["evaluation_generation"])
            db.execute("INSERT INTO outbox(event_id) VALUES(?)", (feedback["event_id"],))
            self._event(db, {"contract_version": 1, "event_id": "event_regression_ready_" + acceptance_id[8:],
                "kind": "REGRESSION_READY", "acceptance_id": acceptance_id, "regression_id": regression_id,
                "run_id": regression_run_id})
            event_id = "event_regression_ready_" + acceptance_id[8:]
            db.execute("INSERT INTO outbox(event_id) VALUES(?)", (event_id,))
            db.execute("INSERT INTO owner_decisions VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", (decision_id, decision["run_id"], decision["evaluation_generation"], decision["input_digest"], command_digest, canonical_json(decision), canonical_json(response), canonical_json(authenticated_owner), displayed_result_id, displayed_result_digest, source_artifact_ref, canonical_json(build_artifact_refs), canonical_json(inputs)))
            db.execute("INSERT INTO acceptances VALUES(?,?,?,?,?)", (acceptance_id, decision["run_id"], decision["evaluation_generation"], decision["input_digest"], decision_id))
            db.execute("INSERT INTO regression_jobs VALUES(?,?,?,?)", (regression_id, acceptance_id, canonical_json(job), canonical_json(inputs)))
            db.commit()
            return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally:
            db.close()

    @staticmethod
    def submission_id_for(caller, idempotency_key):
        """Return deterministic receipt identity for pre-admission artifact metadata."""
        return "submit_" + hashlib.sha256((caller + "\0submit\0" + idempotency_key).encode()).hexdigest()[:24]

    @staticmethod
    def _require_artifacts(db, refs, run_id, *, input_digest=None, allow_current_check_task_id=None):
        for ref in refs:
            row = db.execute("SELECT run_id,artifact_json FROM artifacts WHERE id=?", (ref,)).fetchone()
            if row is None: raise ProtocolError("unknown artifact reference")
            info = json.loads(row[1]); artifact = info["artifact"]
            if row[0] not in (None, run_id) or (input_digest is not None and info.get("input_digest") != input_digest):
                raise ProtocolError("artifact reference outside run/input scope")
            if artifact["producer_kind"] == "SUBMISSION" and run_id is not None:
                submission = db.execute("SELECT request_json,response_json FROM submissions WHERE id=?", (artifact["submission_id"],)).fetchone()
                run = db.execute("SELECT run_json FROM runs WHERE id=?", (run_id,)).fetchone()
                if submission is None or run is None:
                    raise ProtocolError("submission artifact has no accepted receipt for this run")
                request, response, saved_run = json.loads(submission[0]), json.loads(submission[1]), json.loads(run[0])
                if (artifact["producer_id"] != artifact["submission_id"] or response.get("status") != "ACCEPTED" or
                    response.get("run_id") != run_id or request.get("input_digest") != info.get("input_digest") or
                    saved_run["input_digest"] != info.get("input_digest")):
                    raise ProtocolError("submission artifact receipt/run/input identity mismatch")
            if artifact["producer_kind"] == "TASK":
                producer = db.execute("SELECT task_json FROM tasks WHERE id=?", (artifact["producer_task_id"],)).fetchone()
                task = None if producer is None else json.loads(producer[0])
                if task is None or info.get("input_digest") != task["input_digest"]:
                    raise ProtocolError("task artifact lacks exact frozen input binding")
                if artifact["type"] == "CHECK_RECEIPT" and (task["state"] != "COMPLETED" or not task.get("result") or task["result"].get("decision") != "PASS"):
                    if artifact["producer_task_id"] != allow_current_check_task_id:
                        raise ProtocolError("CHECK_RECEIPT requires an accepted PASS producer result")

    def claim_outbox(self, *, now_ms=None, lease_ms=30000):
        """Claim pending/expired rows; same event body survives retry, token fences ACKs."""
        now_ms = int(time.time()*1000) if now_ms is None else now_ms
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT o.event_id,o.claim_generation,e.payload FROM outbox o JOIN events e ON e.id=o.event_id WHERE o.state='PENDING' OR (o.state='CLAIMED' AND o.lease_until_ms<=?) ORDER BY e.created_ms LIMIT 1",(now_ms,)).fetchone()
            if row is None: db.commit(); return None
            gen=row[1]+1
            db.execute("UPDATE outbox SET state='CLAIMED',claim_generation=?,lease_until_ms=?,attempts=attempts+1 WHERE event_id=?",(gen,now_ms+lease_ms,row[0]))
            db.commit()
            return {"event_id":row[0],"claim_generation":gen,"payload":json.loads(row[2])}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def add_task(self, task, *, logical_task_key, ready_event=None):
        """Insert one unique logical slot; opaque IDs cannot bypass slot uniqueness."""
        task = validate(task, "task")
        if ready_event is not None:
            ready_event = _validate_internal_task_event(ready_event, task)
        if not isinstance(logical_task_key, str) or not logical_task_key.strip():
            raise ProtocolError("logical_task_key is required")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            run=db.execute("SELECT current_generation FROM runs WHERE id=?",(task["run_id"],)).fetchone()
            if run is None or run[0] != task["evaluation_generation"]:
                raise StaleCommand("task attempt is not current")
            attempt_row=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(task["run_id"],task["evaluation_generation"])).fetchone()
            phase=json.loads(attempt_row[0])["phase"]
            if phase in ("STOPPING","DECIDED","CLOSED"):
                raise StaleCommand("task admission is closed")
            if task["state"] != "QUEUED" or task["result"] is not None:
                raise ProtocolError("new task must be queued without a result")
            if task["kind"] == "CHECK" and phase not in ("QUEUED","CHECKING"):
                raise StaleCommand("check task is outside check phase")
            if task["kind"] == "REVIEW":
                if phase != "REVIEWING": raise StaleCommand("review task requires REVIEWING phase")
                if not self._effective_checks_complete(db,task["run_id"],task["evaluation_generation"]): raise StaleCommand("all required checks must pass before review admission")
            if task["kind"]=="CHECK" and db.execute("SELECT 1 FROM reused_checks WHERE run_id=? AND target_generation=? AND logical_check_key=?",(task["run_id"],task["evaluation_generation"],logical_task_key)).fetchone(): raise IdempotencyConflict("logical CHECK slot is already fulfilled by retained evidence")
            deps=task["dependency_ids"]
            if len(set(deps)) != len(deps): raise ProtocolError("duplicate task dependency")
            for dep in deps:
                parent=db.execute("SELECT run_id,generation FROM tasks WHERE id=?",(dep,)).fetchone()
                if parent is None or parent[0]!=task["run_id"] or (parent[1]!=task["evaluation_generation"] and not db.execute("SELECT 1 FROM reused_checks WHERE run_id=? AND target_generation=? AND source_task_id=?",(task["run_id"],task["evaluation_generation"],dep)).fetchone()):
                    raise ProtocolError("dependency must exist in same attempt")
            try:
                db.execute("INSERT INTO tasks(id,run_id,generation,logical_key,task_json) VALUES(?,?,?,?,?)",(task["task_id"],task["run_id"],task["evaluation_generation"],logical_task_key,canonical_json(task)))
                for dep in deps: db.execute("INSERT INTO task_deps VALUES(?,?)",(task["task_id"],dep))
            except sqlite3.IntegrityError as exc:
                existing=db.execute("SELECT task_json FROM tasks WHERE run_id=? AND generation=? AND logical_key=?",(task["run_id"],task["evaluation_generation"],logical_task_key)).fetchone()
                if existing:
                    if json.loads(existing[0]) == task:
                        db.commit()
                        return {"disposition":"REPLAYED","task_id":task["task_id"]}
                    raise IdempotencyConflict("logical task key reused with changed content") from exc
                raise IdempotencyConflict("task ID already identifies different work") from exc
            if ready_event is not None:
                self._event(db, ready_event, task["run_id"], task["evaluation_generation"])
                db.execute("INSERT INTO outbox(event_id) VALUES(?)", (ready_event["event_id"],))
            db.commit()
            return {"disposition":"ACCEPTED","task_id":task["task_id"]}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def acknowledge_outbox(self, event_id, claim_generation):
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            cur=db.execute("UPDATE outbox SET state='ACKNOWLEDGED' WHERE event_id=? AND state='CLAIMED' AND claim_generation=?",(event_id,claim_generation))
            if cur.rowcount != 1: raise StaleCommand("stale outbox delivery acknowledgement")
            db.commit()
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def complete_task(self, result, *, session_id):
        """Accept immutable, correctly fenced callback; exact late duplicate replays."""
        result = validate(result, "review_result")
        body = canonical_json(result)
        result_hash = hashlib.sha256(body.encode()).hexdigest()
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT task_json,result_digest,result_json FROM tasks WHERE id=?", (result["task_id"],)).fetchone()
            if row is None: raise StaleCommand("unknown task")
            task = json.loads(row[0])
            if task["kind"] != "REVIEW" or task["assigned_session_id"] != session_id:
                raise StaleCommand("callback session does not own task")
            if row[1] is not None:
                if row[1] == result_hash:
                    db.commit()
                    return {"disposition":"REPLAYED","result_digest":result_hash}
                raise IdempotencyConflict("task result is immutable")
            if db.execute("SELECT 1 FROM task_dependency_pauses WHERE task_id=? AND paused_since_ms>=0", (result["task_id"],)).fetchone():
                raise StaleCommand("review task is dependency-paused")
            runrow = db.execute("SELECT current_generation FROM runs WHERE id=?", (result["run_id"],)).fetchone()
            if (task["run_id"] != result["run_id"] or task["evaluation_generation"] != result["evaluation_generation"] or
                task["assignment_generation"] != result["assignment_generation"] or task["input_digest"] != result["input_digest"] or
                task["assigned_session_id"] != session_id or task["state"] not in ("CLAIMED","RUNNING") or
                runrow is None or runrow[0] != result["evaluation_generation"]):
                raise StaleCommand("stale task generation, assignment, session, input or admission")
            self._require_artifacts(db, result["inspected_refs"] + [ref for finding in result["findings"] for ref in finding["evidence_refs"]], result["run_id"], input_digest=result["input_digest"])
            self._commit_task_result(db, task, {"decision":result["decision"],"findings":result["findings"],"artifact_refs":result["inspected_refs"]}, body, result_hash)
            db.commit()
            return {"disposition":"ACCEPTED","result_digest":result_hash,"result_id":self._task_result_event_id(result["task_id"],result_hash)}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def report_task_error(self, report, *, session_id):
        """Commit a bound REVIEW fault, its task result and incident atomically."""
        report = validate(report, "task_error")
        body = canonical_json(report)
        digest = hashlib.sha256(body.encode()).hexdigest()
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT task_json,result_digest FROM tasks WHERE id=?", (report["task_id"],)).fetchone()
            if row is None: raise StaleCommand("unknown task")
            task = json.loads(row[0])
            if task["kind"] != "REVIEW" or task["assigned_session_id"] != session_id:
                raise StaleCommand("callback session does not own task")
            if row[1] is not None:
                if row[1] == digest:
                    db.commit(); return {"disposition":"REPLAYED","result_digest":digest}
                raise IdempotencyConflict("task result is immutable")
            if db.execute("SELECT 1 FROM task_dependency_pauses WHERE task_id=? AND paused_since_ms>=0", (report["task_id"],)).fetchone():
                raise StaleCommand("review task is dependency-paused")
            current = db.execute("SELECT current_generation FROM runs WHERE id=?", (report["run_id"],)).fetchone()
            if (task["run_id"], task["evaluation_generation"], task["assignment_generation"], task["input_digest"], task["assigned_session_id"]) != (report["run_id"], report["evaluation_generation"], report["assignment_generation"], report["input_digest"], session_id) or task["state"] not in ("CLAIMED", "RUNNING") or current is None or current[0] != report["evaluation_generation"]:
                raise StaleCommand("stale task generation, assignment, session, input or admission")
            self._require_artifacts(db, report["artifact_refs"], report["run_id"], input_digest=report["input_digest"])
            self._commit_task_result(db, task, {"decision":"ERROR","findings":[],"artifact_refs":report["artifact_refs"]}, body, digest)
            incident_id = "incident_" + hashlib.sha256((report["task_id"] + digest).encode()).hexdigest()[:24]
            self._record_incident_tx(db, {"contract_version":1,"incident_id":incident_id,"run_id":report["run_id"],"evaluation_generation":report["evaluation_generation"],"task_id":report["task_id"],"code":report["error_code"],"message":report["message"],"recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":report["artifact_refs"]})
            db.commit()
            return {"disposition":"ACCEPTED","result_digest":digest,"result_id":self._task_result_event_id(report["task_id"],digest),"incident_id":incident_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    @staticmethod
    def _task_result_event_id(task_id, result_hash):
        return "event_task_completed_" + hashlib.sha256((task_id + result_hash).encode()).hexdigest()[:24]

    def _commit_task_result(self, db, task, saved_result, body, result_hash):
        task["state"] = "COMPLETED"
        task["result"] = saved_result
        validate(task, "task")
        db.execute("UPDATE tasks SET task_json=?,result_digest=?,result_json=? WHERE id=?",
                   (canonical_json(task), result_hash, body, task["task_id"]))
        event_id = self._task_result_event_id(task["task_id"], result_hash)
        event = {"contract_version":1,"event_id":event_id,"kind":"TASK_COMPLETED",
                 "run_id":task["run_id"],"evaluation_generation":task["evaluation_generation"],
                 "task_id":task["task_id"],"assignment_generation":task["assignment_generation"],
                 "input_digest":task["input_digest"],"result_digest":result_hash}
        self._event(db, event, task["run_id"], task["evaluation_generation"])
        db.execute("INSERT INTO outbox(event_id) VALUES(?)", (event_id,))

    def complete_native_task(self, *, task_id, run_id, evaluation_generation,
                             assignment_generation, input_digest, process_identity,
                             claim_token, outcome, error_report=None):
        """Accept one immutable CHECK producer result under its saved program claim."""
        if not isinstance(outcome, dict) or set(outcome) != {"decision", "findings", "artifact_refs"}:
            raise ProtocolError("native outcome must contain decision, findings and artifact_refs")
        decision = outcome["decision"]
        if decision not in ("PASS", "FAIL", "ERROR") or not isinstance(outcome["findings"], list) or not isinstance(outcome["artifact_refs"], list):
            raise ProtocolError("invalid native outcome shape")
        if decision == "FAIL" and (not outcome["findings"] or any(not isinstance(finding, dict) or not isinstance(finding.get("required_change"), str) or not finding["required_change"].strip() for finding in outcome["findings"])):
            raise ProtocolError("FAIL requires actionable findings with required_change")
        if decision == "PASS" and any(not isinstance(finding, dict) or "required_change" in finding for finding in outcome["findings"]):
            raise ProtocolError("PASS findings cannot prescribe required changes")
        if decision == "ERROR":
            if error_report is None:
                raise ProtocolError("ERROR requires a bound WR01 task_error report")
            error_report = validate(error_report, "task_error")
            if (error_report["task_id"], error_report["run_id"], error_report["evaluation_generation"],
                error_report["assignment_generation"], error_report["input_digest"]) != (task_id, run_id, evaluation_generation, assignment_generation, input_digest):
                raise ProtocolError("native error report binding mismatch")
        elif error_report is not None:
            raise ProtocolError("error_report is only valid for ERROR outcomes")
        refs = list(outcome["artifact_refs"])
        refs += [ref for finding in outcome["findings"] for ref in finding.get("evidence_refs", [])]
        candidate = {"contract_version":1,"task_id":task_id,"run_id":run_id,
                     "evaluation_generation":evaluation_generation,"assignment_generation":assignment_generation,
                     "input_digest":input_digest,"decision":decision,"findings":outcome["findings"],
                     "artifact_refs":outcome["artifact_refs"]}
        # The task schema is the WR01 authority for result decision/findings shape.
        candidate_task = {"contract_version":1,"task_id":task_id,"run_id":run_id,
                          "evaluation_generation":evaluation_generation,"kind":"CHECK","dependency_ids":[],
                          "input_digest":input_digest,"state":"COMPLETED","assignment_generation":assignment_generation,
                          "assigned_session_id":None,"deadline_ms":0,
                          "result":{"decision":decision,"findings":outcome["findings"],"artifact_refs":outcome["artifact_refs"]}}
        validate(candidate_task, "task")
        body = canonical_json({"binding":candidate,"process_identity":process_identity,"claim_token":claim_token,
                               "error_report":error_report})
        result_hash = hashlib.sha256(body.encode()).hexdigest()
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT task_json,result_digest FROM tasks WHERE id=?", (task_id,)).fetchone()
            claim = db.execute("SELECT process_identity,claim_token FROM native_claims WHERE task_id=?", (task_id,)).fetchone()
            if row is None or claim is None: raise StaleCommand("unknown native task/claim")
            task = json.loads(row[0])
            if task["kind"] != "CHECK": raise ProtocolError("native producer completion is CHECK-only")
            if (task["run_id"],task["evaluation_generation"],task["assignment_generation"],task["input_digest"],task.get("process_identity")) != (run_id,evaluation_generation,assignment_generation,input_digest,process_identity) or json.loads(claim[0]) != process_identity or claim[1] != claim_token:
                raise StaleCommand("native result does not match saved task claim")
            if row[1] is not None:
                if row[1] == result_hash:
                    db.commit(); return {"disposition":"REPLAYED","result_digest":result_hash,"result_id":self._task_result_event_id(task_id,result_hash)}
                raise IdempotencyConflict("task result is immutable")
            current = db.execute("SELECT current_generation FROM runs WHERE id=?", (run_id,)).fetchone()
            attempt = db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?", (run_id,evaluation_generation)).fetchone()
            if current is None or current[0] != evaluation_generation or attempt is None or json.loads(attempt[0])["phase"] not in ("QUEUED","CHECKING","REVIEWING") or task["state"] not in ("CLAIMED","RUNNING"):
                raise StaleCommand("new native result is outside active task/attempt")
            self._require_artifacts(db, refs, run_id, input_digest=input_digest, allow_current_check_task_id=task_id)
            for ref in outcome["artifact_refs"]:
                row = db.execute("SELECT artifact_json FROM artifacts WHERE id=?", (ref,)).fetchone()
                art = {} if row is None else json.loads(row[0])["artifact"]
                if art.get("type") != "CHECK_RECEIPT": continue
                if art["producer_task_id"] != task_id:
                    raise ProtocolError("native PASS receipt must belong to this CHECK task")
            if error_report is not None:
                self._require_artifacts(db, error_report["artifact_refs"], run_id, input_digest=input_digest)
            saved_result={"decision":decision,"findings":outcome["findings"],"artifact_refs":outcome["artifact_refs"]}
            self._commit_task_result(db, task, saved_result, body, result_hash)
            db.commit()
            return {"disposition":"ACCEPTED","result_digest":result_hash,"result_id":self._task_result_event_id(task_id,result_hash)}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally:
            db.close()

    def claim_task(self, *, task_id, run_id, evaluation_generation, assignment_generation, input_digest, session_id):
        """Bind claim to current live assignment; duplicate exact claim replays."""
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()
            current=db.execute("SELECT current_generation FROM runs WHERE id=?",(run_id,)).fetchone()
            if row is None or current is None: raise StaleCommand("unknown task/run")
            task=json.loads(row[0])
            if (task["run_id"]!=run_id or task["evaluation_generation"]!=evaluation_generation or
                task["assignment_generation"]!=assignment_generation or task["input_digest"]!=input_digest or
                current[0]!=evaluation_generation or not session_id):
                raise StaleCommand("claim does not match queued current assignment")
            if task["kind"] != "REVIEW": raise ProtocolError("AI claim is restricted to REVIEW tasks")
            if db.execute("SELECT 1 FROM task_dependency_pauses WHERE task_id=? AND paused_since_ms>=0", (task_id,)).fetchone():
                raise StaleCommand("review task is dependency-paused")
            attempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(run_id,evaluation_generation)).fetchone()
            if attempt is None or json.loads(attempt[0])["phase"] not in ("QUEUED","CHECKING","REVIEWING"):
                raise StaleCommand("review claim is outside active attempt phase")
            if task["state"]=="CLAIMED" and task["assigned_session_id"]==session_id:
                db.commit(); return {"disposition":"REPLAYED","task_id":task_id}
            if task["state"]!="DISPATCHING" or task["assigned_session_id"]!=session_id:
                raise StaleCommand("claim session is not the saved dispatched recipient")
            self._require_pass_dependencies(db, task)
            task["state"]="CLAIMED"
            db.execute("UPDATE tasks SET task_json=? WHERE id=?",(canonical_json(task),task_id))
            db.commit()
            return {"disposition":"ACCEPTED","task_id":task_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    @staticmethod
    def _require_pass_dependencies(db, task):
        for dep in task["dependency_ids"]:
            row=db.execute("SELECT task_json FROM tasks WHERE id=?",(dep,)).fetchone()
            if row is None: raise StaleCommand("dependency is missing")
            parent=json.loads(row[0])
            retained=db.execute("SELECT 1 FROM reused_checks WHERE run_id=? AND target_generation=? AND source_task_id=?",(task["run_id"],task["evaluation_generation"],dep)).fetchone()
            if parent["run_id"]!=task["run_id"] or (parent["evaluation_generation"]!=task["evaluation_generation"] and not retained) or parent["state"]!="COMPLETED" or parent.get("result",{}).get("decision")!="PASS":
                raise StaleCommand("dependencies are not completed PASS")

    def dispatch_task(self, *, task_id, event_id, assigned_session_id=None,
                      process_identity=None, claim_token=None, dispatch_envelope=None, native_scope=None,
                      review_session_claim=None):
        """Persist assignment and stable internal wake intent before any claim."""
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()
            if row is None: raise StaleCommand("unknown task")
            task=json.loads(row[0]); current=db.execute("SELECT current_generation FROM runs WHERE id=?",(task["run_id"],)).fetchone()
            attempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(task["run_id"],task["evaluation_generation"])).fetchone()
            if current is None or current[0]!=task["evaluation_generation"] or attempt is None or json.loads(attempt[0])["phase"] in ("STOPPING","DECIDED","CLOSED"):
                raise StaleCommand("task attempt is not active")
            if task["state"]!="QUEUED": raise StaleCommand("only queued tasks can be dispatched")
            self._require_pass_dependencies(db,task)
            if task["kind"]=="REVIEW":
                if not isinstance(assigned_session_id,str) or not assigned_session_id or process_identity is not None or claim_token is not None or native_scope is not None:
                    raise ProtocolError("review dispatch requires its assigned session")
                envelope=validate(dispatch_envelope,"dispatch_envelope")
                if (envelope["event_id"]!=event_id or envelope["run_id"]!=task["run_id"] or
                    envelope["evaluation_generation"]!=task["evaluation_generation"] or envelope["task_id"]!=task_id or
                    envelope["assignment_generation"]!=task["assignment_generation"] or envelope["input_digest"]!=task["input_digest"] or
                    envelope["recipient_binding"].get("session_id")!=assigned_session_id):
                    raise ProtocolError("review envelope is not bound to saved task assignment")
                if review_session_claim is not None:
                    if (not isinstance(review_session_claim,dict) or
                        set(review_session_claim)!={'server','session_id','message_id','body_sha256'} or
                        not _is_loopback_url(review_session_claim.get('server')) or
                        review_session_claim.get('session_id')!=assigned_session_id or
                        not all(isinstance(review_session_claim.get(k),str) and review_session_claim[k] for k in ('message_id','body_sha256'))):
                        raise ProtocolError('review session claim proof is invalid')
            elif task["kind"]=="CHECK":
                if assigned_session_id is not None or dispatch_envelope is not None or not process_identity or not claim_token:
                    raise ProtocolError("CHECK dispatch requires program-owned process identity and claim token, not a session")
                if native_scope is not None:
                    if set(native_scope)!={'scope_name','worker_identity','creation_artifact_ref'} or not isinstance(native_scope['worker_identity'],dict) or set(native_scope['worker_identity'])!={'pid','created'} or native_scope['worker_identity'].get('pid')!=process_identity.get('pid'):
                        raise ProtocolError('native process scope binding is invalid')
                    self._require_artifacts(db,[native_scope['creation_artifact_ref']],task['run_id'],input_digest=task['input_digest'])
                    artifact=json.loads(db.execute('SELECT artifact_json FROM artifacts WHERE id=?',(native_scope['creation_artifact_ref'],)).fetchone()[0])['artifact']
                    if artifact.get('producer_task_id')!=task_id or artifact.get('type')!='PROVENANCE': raise ProtocolError('native scope proof must be task-produced provenance')
            else: raise ProtocolError("unsupported task kind")
            if task['kind']=='CHECK' and review_session_claim is not None:
                raise ProtocolError('review session claim is REVIEW-only')
            if review_session_claim is not None:
                proof={**review_session_claim,'task_id':task_id,'run_id':task['run_id'],
                    'evaluation_generation':task['evaluation_generation'],
                    'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
                self._insert_resource_tx(db, resource_key='review-session:'+digest({'server':review_session_claim['server'],'session_id':review_session_claim['session_id']}),
                    run_id=task['run_id'],generation=task['evaluation_generation'],task_id=task_id,
                    assignment_generation=task['assignment_generation'],identity_proof=proof)
            saved=dict(task,state="DISPATCHING",assigned_session_id=assigned_session_id)
            if task["kind"]=="CHECK":
                saved["process_identity"] = process_identity
                validate(saved, "task")
                db.execute("INSERT INTO native_claims(task_id,process_identity,claim_token) VALUES(?,?,?)", (task_id,canonical_json(process_identity),claim_token))
                if native_scope is not None:
                    db.execute("INSERT INTO native_scopes(task_id,assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,state) VALUES(?,?,?,?,?,'OWNED')",(task_id,task['assignment_generation'],native_scope['scope_name'],canonical_json(native_scope['worker_identity']),native_scope['creation_artifact_ref']))
            event=envelope if task["kind"]=="REVIEW" else _internal_task_event(event_id,saved)
            db.execute("UPDATE tasks SET task_json=? WHERE id=?",(canonical_json(saved),task_id))
            self._event(db,event,task["run_id"],task["evaluation_generation"])
            db.execute("INSERT INTO outbox(event_id) VALUES(?)",(event_id,))
            db.commit(); return {"disposition":"DISPATCHED","event_id":event_id,"task_id":task_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def report_native_interruption(self, report, *, cleanup_artifact_ref):
        """Record a native worker interruption only after saved scope cleanup."""
        report = validate(report, "task_error")
        if not isinstance(cleanup_artifact_ref, str) or not cleanup_artifact_ref:
            raise ProtocolError("cleanup artifact reference is required")
        body = canonical_json(report)
        result_digest = hashlib.sha256(body.encode()).hexdigest()
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT task_json,result_digest FROM tasks WHERE id=?", (report["task_id"],)).fetchone()
            if row is None: raise StaleCommand("unknown task")
            task = json.loads(row[0])
            binding = (task["run_id"], task["evaluation_generation"], task["assignment_generation"], task["input_digest"])
            reported = (report["run_id"], report["evaluation_generation"], report["assignment_generation"], report["input_digest"])
            if binding != reported or task["kind"] != "CHECK": raise StaleCommand("native interruption task binding mismatch")
            if row[1] is not None:
                if row[1] == result_digest:
                    db.commit(); return {"disposition":"REPLAYED", "result_digest":result_digest}
                raise IdempotencyConflict("task result is immutable")
            current = db.execute("SELECT current_generation FROM runs WHERE id=?", (report["run_id"],)).fetchone()
            if task["state"] not in ("DISPATCHING", "CLAIMED", "RUNNING") or current is None or current[0] != report["evaluation_generation"]:
                raise StaleCommand("native task is not eligible for interruption completion")
            scope = db.execute("SELECT assignment_generation,state,cleanup_artifact_ref FROM native_scopes WHERE task_id=?", (task["task_id"],)).fetchone()
            if scope is None or scope[0] != task["assignment_generation"] or scope[1] != "CLEANED" or scope[2] != cleanup_artifact_ref:
                raise StaleCommand("native scope cleanup is not confirmed")
            if db.execute("SELECT 1 FROM resources WHERE task_id=? AND state IN ('HELD','UNKNOWN')", (task["task_id"],)).fetchone():
                raise StaleCommand("native resources remain unsettled")
            refs = list(report["artifact_refs"]) + [cleanup_artifact_ref]
            self._require_artifacts(db, refs, report["run_id"], input_digest=report["input_digest"])
            saved = {"decision":"ERROR", "findings":[], "artifact_refs":report["artifact_refs"]}
            self._commit_task_result(db, task, saved, body, result_digest)
            incident_id = "incident_" + hashlib.sha256((task["task_id"] + result_digest).encode()).hexdigest()[:24]
            self._record_incident_tx(db, {"contract_version":1,"incident_id":incident_id,"run_id":report["run_id"],"evaluation_generation":report["evaluation_generation"],"task_id":task["task_id"],"code":report["error_code"],"message":report["message"],"recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":refs})
            db.commit()
            return {"disposition":"ACCEPTED", "result_digest":result_digest, "result_id":self._task_result_event_id(task["task_id"],result_digest), "incident_id":incident_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_native_scope(self, task_id):
        db=self._connect()
        try:
            row=db.execute('SELECT assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,cleanup_artifact_ref,state FROM native_scopes WHERE task_id=?',(task_id,)).fetchone()
            if row is None:return None
            return {'task_id':task_id,'assignment_generation':row[0],'scope_name':row[1],'worker_identity':json.loads(row[2]),'creation_artifact_ref':row[3],'cleanup_artifact_ref':row[4],'state':row[5]}
        finally:db.close()

    def list_native_scopes(self):
        """Return persisted native scopes joined to their current task records."""
        db=self._connect()
        try:
            rows=db.execute('SELECT n.task_id,n.assignment_generation,n.scope_name,n.worker_identity_json,n.creation_artifact_ref,n.cleanup_artifact_ref,n.state,t.task_json,EXISTS(SELECT 1 FROM resources r WHERE r.task_id=n.task_id AND r.state IN (\'HELD\',\'UNKNOWN\')) FROM native_scopes n JOIN tasks t ON t.id=n.task_id ORDER BY n.task_id').fetchall()
            result=[]
            for r in rows:
                task=json.loads(r[7])
                terminal=task['state'] in ('COMPLETED','ERROR','CANCELLED','REJECTED')
                if not terminal or r[6]=='OWNED' or r[8]:
                    result.append({'task_id':r[0],'assignment_generation':r[1],'scope_name':r[2],'worker_identity':json.loads(r[3]),'creation_artifact_ref':r[4],'cleanup_artifact_ref':r[5],'state':r[6],'task':task})
            return result
        finally:db.close()

    def find_task_cancel_event(self, task_id):
        """Find the exact persisted cancellation command for the current task binding."""
        task=self.get_task(task_id)
        if task is None:return None
        db=self._connect()
        try:
            rows=db.execute("SELECT id,payload FROM events WHERE kind='CANCEL_TASK' ORDER BY created_ms DESC,id",()).fetchall()
            for event_id,raw in rows:
                payload=json.loads(raw)
                if all(payload.get(k)==task.get(k) for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')):
                    return {'event_id':event_id,'payload':payload}
            return None
        finally:db.close()

    def get_task_cancellation(self, task_id):
        """Read the persisted cancellation proof and confirmation bit without side effects."""
        db=self._connect()
        try:
            row=db.execute('SELECT proof_json,confirmed FROM task_cancellations WHERE task_id=?',(task_id,)).fetchone()
            return {'proof':json.loads(row[0]),'confirmed':row[1]} if row else None
        finally:db.close()

    def record_native_scope_cleanup(self, *, task_id, assignment_generation, artifact_ref):
        """Bind verified zero-active-process scope proof to its exact native assignment."""
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT task_json FROM tasks WHERE id=?',(task_id,)).fetchone()
            scope=db.execute('SELECT assignment_generation,state,cleanup_artifact_ref FROM native_scopes WHERE task_id=?',(task_id,)).fetchone()
            if row is None or scope is None: raise StaleCommand('native task scope is missing')
            task=json.loads(row[0])
            if task['assignment_generation']!=assignment_generation or scope[0]!=assignment_generation:
                raise StaleCommand('native scope assignment mismatch')
            if scope[1]=='CLEANED':
                if scope[2]!=artifact_ref: raise IdempotencyConflict('native cleanup proof is immutable')
                db.commit(); return {'disposition':'REPLAYED','artifact_id':artifact_ref}
            self._require_artifacts(db,[artifact_ref],task['run_id'],input_digest=task['input_digest'])
            binding=json.loads(db.execute('SELECT artifact_json FROM artifacts WHERE id=?',(artifact_ref,)).fetchone()[0])
            art=binding['artifact']
            if art.get('producer_kind')!='TASK' or art.get('producer_task_id')!=task_id or art.get('producer_id')!=task_id or art.get('type')!='PROVENANCE':
                raise ProtocolError('native cleanup must be owned TASK PROVENANCE')
            db.execute("UPDATE native_scopes SET state='CLEANED',cleanup_artifact_ref=? WHERE task_id=?",(artifact_ref,task_id))
            db.commit(); return {'disposition':'ACCEPTED','artifact_id':artifact_ref}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def record_review_quiescence(self, task_id, *, claim_id, identity_proof, artifact_ref):
        """Persist bound proof that an active REVIEW's admission is quiescent, retaining its resource."""
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT task_json FROM tasks WHERE id=?',(task_id,)).fetchone()
            if row is None: raise StaleCommand('unknown review task')
            task=json.loads(row[0])
            if task.get('kind')!='REVIEW' or task.get('state') not in ('DISPATCHING','CLAIMED','RUNNING'):
                raise StaleCommand('review task is not active')
            current=db.execute('SELECT current_generation FROM runs WHERE id=?',(task['run_id'],)).fetchone()
            if current is None or current[0]!=task['evaluation_generation']:
                raise StaleCommand('stale review generation')
            res=db.execute('SELECT resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof FROM resources WHERE claim_id=?',(claim_id,)).fetchone()
            if res is None or tuple(res[1:5])!=(task['run_id'],task['evaluation_generation'],task_id,task['assignment_generation']) or json.loads(res[5])!=identity_proof or res[6] not in ('HELD','UNKNOWN'):
                raise StaleCommand('review resource claim binding mismatch')
            self._require_artifacts(db,[artifact_ref],task['run_id'],input_digest=task['input_digest'])
            saved=db.execute('SELECT artifact_json FROM artifacts WHERE id=?',(artifact_ref,)).fetchone()
            art=json.loads(saved[0])['artifact']
            if art.get('producer_kind')!='TASK' or art.get('producer_task_id')!=task_id or art.get('producer_id')!=task_id or art.get('type')!='PROVENANCE':
                raise ProtocolError('review quiescence artifact binding mismatch')
            raw=Path(art['path']).read_bytes()
            if len(raw)!=art['size_bytes'] or hashlib.sha256(raw).hexdigest()!=art['sha256']:
                raise ProtocolError('review quiescence artifact bytes do not match registration')
            body=strict_loads(raw.decode('utf-8'))
            expected={'purpose':'REVIEW_QUIESCENCE','inactive_confirmed':True,'inbox_empty':True,'admission_proven':True,'task_id':task_id,'run_id':task['run_id'],'generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'session_id':identity_proof.get('session_id'),'server':identity_proof.get('server'),'body_sha256':identity_proof.get('body_sha256')}
            if not isinstance(body,dict) or any(body.get(k)!=v for k,v in expected.items()): raise ProtocolError('invalid review quiescence proof body')
            baseline=db.execute('SELECT artifact_json FROM artifacts WHERE id=?',(body.get('baseline_ref'),)).fetchone()
            if not baseline: raise ProtocolError('quiescence baseline is not registered')
            baseline_art=json.loads(baseline[0])['artifact']
            baseline_wrapper=json.loads(baseline[0])
            if (baseline_art.get('type')!='PROVENANCE' or baseline_art.get('producer_task_id')!=task_id or
                baseline_wrapper.get('input_digest')!=task['input_digest']):
                raise ProtocolError('quiescence baseline binding mismatch')
            bound=db.execute('SELECT claim_id FROM resource_proofs WHERE artifact_id=?',(artifact_ref,)).fetchone()
            if bound and bound[0]!=claim_id: raise ProtocolError('proof is bound to another resource claim')
            if res[7]==artifact_ref:
                db.commit(); return {'disposition':'REPLAYED','claim_id':claim_id,'state':'HELD'}
            db.execute('INSERT OR IGNORE INTO resource_proofs(artifact_id,claim_id) VALUES(?,?)',(artifact_ref,claim_id))
            db.execute("UPDATE resources SET state='HELD',cleanup_proof=? WHERE claim_id=?",(artifact_ref,claim_id))
            db.commit(); return {'disposition':'ACCEPTED' if res[7] is None else 'REPLAYED','claim_id':claim_id,'state':'HELD'}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_task_resources(self, task_id):
        db=self._connect()
        try:
            return [dict(claim_id=r[0],resource_key=r[1],run_id=r[2],generation=r[3],task_id=r[4],assignment_generation=r[5],identity_proof=json.loads(r[6]),state=r[7],cleanup_proof=r[8]) for r in db.execute('SELECT claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof FROM resources WHERE task_id=? ORDER BY claim_id',(task_id,))]
        finally: db.close()

    def begin_review_retry(self, *, task_id, caller, command_id, binding, proof, mode='PATCH', controls_snapshot=None):
        """Durably fence one explicit provider-retry command before its API effect."""
        if not all(isinstance(x,str) and x for x in (task_id,caller,command_id)):
            raise ProtocolError('retry command identity required')
        request={'task_id':task_id,'binding':binding,'proof':proof,'mode':mode,'controls_snapshot':controls_snapshot}
        request_digest=digest(request); db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            prior=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='review_retry' AND caller=? AND command_key=?",(caller,command_id)).fetchone()
            if prior:
                if prior[0]!=request_digest: raise IdempotencyConflict('retry command ID reused with different binding')
                db.commit(); return {'disposition':'REPLAYED','response':json.loads(prior[1])}
            unresolved=db.execute("SELECT 1 FROM command_receipts WHERE operation='review_retry' AND json_extract(response_json,'$.task_id')=? AND json_extract(response_json,'$.status')='INTENT'",(task_id,)).fetchone()
            if unresolved: raise StaleCommand('task already has an unresolved review retry intent')
            row=db.execute('SELECT task_json FROM tasks WHERE id=?',(task_id,)).fetchone()
            pause=db.execute('SELECT dependency_key,paused_since_ms,context_json FROM task_dependency_pauses WHERE task_id=?',(task_id,)).fetchone()
            if not row or not pause: raise StaleCommand('review retry task is not paused')
            task=json.loads(row[0]); current=db.execute('SELECT current_generation FROM runs WHERE id=?',(task['run_id'],)).fetchone()
            actual={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            held=db.execute("SELECT identity_proof FROM resources WHERE task_id=? AND state='HELD'",(task_id,)).fetchall()
            if (task.get('kind')!='REVIEW' or task.get('state') not in ('DISPATCHING','CLAIMED','RUNNING') or
                current is None or current[0]!=task['evaluation_generation'] or actual!=binding or
                pause[1]<0 or json.loads(pause[2])!=binding or len(held)!=1 or json.loads(held[0][0])!=proof):
                raise StaleCommand('review retry task binding or exclusive claim is stale')
            response={'status':'INTENT','task_id':task_id,'command_id':command_id,'binding':binding,'proof':proof,'mode':mode,'controls_snapshot':controls_snapshot,'intent_ms':int(time.time()*1000)}
            db.execute("INSERT INTO command_receipts VALUES('review_retry',?,?,?,?)",(caller,command_id,request_digest,canonical_json(response)))
            db.commit(); return {'disposition':'ACCEPTED','response':response}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_review_retry(self, *, task_id, caller, command_id):
        db=self._connect()
        try:
            row=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='review_retry' AND caller=? AND command_key=?",(caller,command_id)).fetchone()
            if not row: return None
            response=json.loads(row[1])
            return response if response.get('task_id')==task_id else None
        finally: db.close()

    def list_review_retry_receipts(self, task_id):
        """List retained retry intent and completion context for task-owned cleanup."""
        db=self._connect()
        try:
            rows=db.execute("SELECT caller,response_json FROM command_receipts WHERE operation='review_retry' AND json_extract(response_json,'$.task_id')=? ORDER BY rowid",(task_id,)).fetchall()
            return [{**json.loads(r[1]),'caller':r[0]} for r in rows]
        finally: db.close()

    def complete_review_retry(self, *, task_id, caller, command_id, response):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute("SELECT response_json FROM command_receipts WHERE operation='review_retry' AND caller=? AND command_key=?",(caller,command_id)).fetchone()
            if not row: raise StaleCommand('retry intent is missing')
            prior=json.loads(row[0])
            if prior.get('task_id')!=task_id: raise StaleCommand('retry intent task mismatch')
            if prior.get('status')!='INTENT':
                if prior.get('status')==response.get('status') and prior.get('final_receipt')==response:
                    db.commit(); return {'disposition':'REPLAYED','response':prior}
                raise IdempotencyConflict('retry receipt is immutable')
            if response.get('task_id')!=task_id or response.get('command_id')!=command_id or response.get('status') not in ('RESUMED','UNKNOWN','STOPPING'):
                raise ProtocolError('invalid retry completion receipt')
            merged=dict(prior); merged.update(response); merged['final_receipt']=response
            db.execute("UPDATE command_receipts SET response_json=? WHERE operation='review_retry' AND caller=? AND command_key=?",(canonical_json(merged),caller,command_id))
            db.commit(); return {'disposition':'COMPLETED','response':merged}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def record_review_retry_effect(self, *, task_id, caller, command_id):
        """Persist an exact external-effect acknowledgement without claiming task resume."""
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute("SELECT response_json FROM command_receipts WHERE operation='review_retry' AND caller=? AND command_key=?",(caller,command_id)).fetchone()
            if not row: raise StaleCommand('retry intent is missing')
            receipt=json.loads(row[0])
            if receipt.get('task_id')!=task_id: raise StaleCommand('retry intent task mismatch')
            if receipt.get('effect_status')=='APPLIED':
                db.commit(); return receipt
            if receipt.get('status')!='INTENT': raise StaleCommand('retry intent is already terminal')
            receipt['effect_status']='APPLIED'
            db.execute("UPDATE command_receipts SET response_json=? WHERE operation='review_retry' AND caller=? AND command_key=?",(canonical_json(receipt),caller,command_id))
            db.commit(); return receipt
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_task_dependency_pause(self, task_id):
        db=self._connect()
        try:
            r=db.execute("SELECT dependency_key,paused_since_ms,accumulated_paused_ms,context_json FROM task_dependency_pauses WHERE task_id=?",(task_id,)).fetchone()
            if not r: return None
            return dict(dependency_key=r[0],paused_since_ms=max(0,r[1]),active=r[1]>=0,last_resumed_ms=abs(r[1]) if r[1]<0 else None,accumulated_paused_ms=abs(r[2]),context=json.loads(r[3]))
        finally: db.close()

    def task_paused_duration_ms(self, task_id, now_ms):
        p=self.get_task_dependency_pause(task_id)
        return 0 if p is None else p['accumulated_paused_ms'] + (max(0, int(now_ms)-p['paused_since_ms']) if p['active'] else 0)

    def task_is_dependency_paused(self, task_id):
        p=self.get_task_dependency_pause(task_id)
        return p is not None and p['active']

    # Local API circuits share the existing dependency tables, but use their own
    # URL-namespaced keys and never touch task pause records.
    def local_api_failure(self, *, key, failure_id, now_ms, details):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            old=db.execute('SELECT generation FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
            if old is None:
                generation=1; failures=1; probes=0
                db.execute('INSERT INTO dependency_circuits(dependency_key,generation,state,failures,opened_ms,next_probe_ms,probes,last_failure_id,details_json) VALUES(?,? ,\'BACKOFF\',?,?,?,0,?,?)',(key,generation,failures,now_ms,now_ms+5000,failure_id,canonical_json(details)))
            else:
                generation=old[0]
                if db.execute('SELECT 1 FROM dependency_failures WHERE dependency_key=? AND failure_id=?',(key,failure_id)).fetchone():
                    db.commit(); return self.local_api_snapshot(key)
                row=db.execute('SELECT failures,probes,state FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
                failures=row[0]+1; probes=row[1]
                state='OPEN' if row[2]=='OPEN' or failures>=3 else 'BACKOFF'
                delay=(5,15,60)[min(probes,2)]*1000
                db.execute('UPDATE dependency_circuits SET state=?,failures=?,opened_ms=?,next_probe_ms=?,last_failure_id=?,details_json=? WHERE dependency_key=?',(state,failures,now_ms,None if state=='OPEN' else now_ms+delay,failure_id,canonical_json(details),key))
            db.execute('INSERT INTO dependency_failures VALUES(?,?,?)',(key,failure_id,canonical_json({'at_ms':now_ms,'details':details})))
            db.commit(); return self.local_api_snapshot(key)
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def local_api_snapshot(self,key):
        db=self._connect()
        try:
            r=db.execute('SELECT generation,state,failures,opened_ms,next_probe_ms,probes,last_failure_id,details_json FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
            return None if r is None else dict(generation=r[0],state=r[1],failures=r[2],opened_ms=r[3],next_probe_ms=r[4],probes=r[5],last_failure_id=r[6],details=json.loads(r[7]))
        finally: db.close()

    def local_api_open(self, *, key, details):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            db.execute("UPDATE dependency_circuits SET state='OPEN',next_probe_ms=NULL,details_json=? WHERE dependency_key=?",(canonical_json(details),key))
            db.commit()
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def local_api_permit(self, *, key, now_ms, probe_id):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE'); r=db.execute('SELECT generation,state,next_probe_ms,probes FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
            if r is None or r[1]!='BACKOFF' or r[2] is None or now_ms<r[2] or r[3]>=3:
                db.commit(); return None
            # Persist before network access. Restart or concurrent stores cannot replay a permit.
            number=r[3]+1; delay=(5,15,60)[min(number-1,2)]*1000
            db.execute('UPDATE dependency_circuits SET probes=?,next_probe_ms=?,details_json=? WHERE dependency_key=? AND generation=?',(number,now_ms+delay,canonical_json({'probe_id':probe_id,'probe_incomplete':True}),key,r[0]))
            db.commit(); return {'generation':r[0],'probe':number,'probe_id':probe_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def local_api_result(self, *, key, generation, probe_id, healthy, details):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE'); r=db.execute('SELECT generation,probes,details_json FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
            if r is None or r[0]!=generation or json.loads(r[2]).get('probe_id')!=probe_id: db.commit(); return False
            state='CLOSED' if healthy else ('OPEN' if r[1]>=3 or details.get('kind')=='AUTH_REPAIR_REQUIRED' else 'BACKOFF')
            db.execute('UPDATE dependency_circuits SET state=?,failures=?,next_probe_ms=CASE WHEN ? IN (\'CLOSED\',\'OPEN\') THEN NULL ELSE next_probe_ms END,last_failure_id=NULL,details_json=? WHERE dependency_key=?',(state,0 if healthy else (2 if state=='OPEN' else 1),state,canonical_json(details),key))
            db.commit(); return True
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def local_api_retry(self, *, key, command_id, caller='SYSTEM'):
        if not isinstance(key,str) or not key or not isinstance(command_id,str) or not command_id or not isinstance(caller,str) or not caller:
            raise ProtocolError('local API retry key, caller, and command_id are required')
        request_digest=digest({'dependency_key':key,'command_id':command_id,'caller':caller})
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            prior=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='local_api_retry' AND caller=? AND command_key=?",(caller,command_id)).fetchone()
            if prior:
                if prior[0]!=request_digest: raise IdempotencyConflict('local API retry command ID reused with different dependency')
                response=json.loads(prior[1]); db.commit(); return response
            r=db.execute('SELECT generation FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
            generation=1 if r is None else r[0]+1
            details={'retry_command_id':command_id}
            db.execute("INSERT INTO dependency_circuits(dependency_key,generation,state,failures,next_probe_ms,probes,details_json) VALUES(?,?,'BACKOFF',0,0,0,?) ON CONFLICT(dependency_key) DO UPDATE SET generation=excluded.generation,state='BACKOFF',failures=0,probes=0,opened_ms=NULL,next_probe_ms=0,last_failure_id=NULL,details_json=excluded.details_json",(key,generation,canonical_json(details)))
            row=db.execute('SELECT generation,state,failures,opened_ms,next_probe_ms,probes,last_failure_id,details_json FROM dependency_circuits WHERE dependency_key=?',(key,)).fetchone()
            response=dict(generation=row[0],state=row[1],failures=row[2],opened_ms=row[3],next_probe_ms=row[4],probes=row[5],last_failure_id=row[6],details=json.loads(row[7]))
            db.execute("INSERT INTO command_receipts(operation,caller,command_key,request_digest,response_json) VALUES('local_api_retry',?,?,?,?)",(caller,command_id,request_digest,canonical_json(response)))
            db.commit(); return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def pause_dependency_task(self, *, task_id, dependency_key, failure_id, binding, now_ms, evidence_refs=()):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT task_json FROM tasks WHERE id=?',(task_id,)).fetchone()
            if not row: raise StaleCommand('unknown task')
            task=json.loads(row[0])
            run=db.execute('SELECT current_generation FROM runs WHERE id=?',(task['run_id'],)).fetchone()
            if task.get('kind')!='REVIEW' or task.get('state') in ('COMPLETED','CANCELLED') or run is None or run[0]!=task['evaluation_generation']:
                raise StaleCommand('task is not a current active review')
            actual={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            if actual!=binding: raise StaleCommand('task binding mismatch')
            held=db.execute("SELECT resource_key FROM resources WHERE task_id=? AND state IN ('HELD','UNKNOWN')",(task_id,)).fetchall()
            if any(not r[0].startswith('review-session:') for r in held):
                raise StoreError('task holds native resources')
            existing=db.execute('SELECT 1 FROM dependency_failures WHERE dependency_key=? AND failure_id=?',(dependency_key,failure_id)).fetchone()
            if existing:
                db.commit(); return {'disposition':'DUPLICATE'}
            prior=db.execute('SELECT dependency_key,paused_since_ms,accumulated_paused_ms,context_json FROM task_dependency_pauses WHERE task_id=?',(task_id,)).fetchone()
            if prior and prior[1]>=0 and prior[0]!=dependency_key:
                raise StaleCommand('task is already paused for another dependency')
            db.execute("INSERT INTO dependency_circuits(dependency_key,generation,state,opened_ms,details_json) VALUES(?,?,'OPEN',?,'{}') ON CONFLICT(dependency_key) DO UPDATE SET state='OPEN',opened_ms=excluded.opened_ms",(dependency_key,task['evaluation_generation'],now_ms))
            failure_payload = binding if not evidence_refs else {'binding':binding,'evidence_refs':list(evidence_refs)}
            db.execute('INSERT INTO dependency_failures VALUES(?,?,?)',(dependency_key,failure_id,json.dumps(failure_payload,sort_keys=True)))
            if prior:
                db.execute('UPDATE task_dependency_pauses SET dependency_key=?,paused_since_ms=?,context_json=? WHERE task_id=?',(dependency_key,now_ms,json.dumps(binding,sort_keys=True),task_id))
            else:
                db.execute('INSERT INTO task_dependency_pauses VALUES(?,?,?,?,?)',(task_id,dependency_key,now_ms,0,json.dumps(binding,sort_keys=True)))
            db.commit(); return {'disposition':'PAUSED'}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def resume_dependency_task(self, *, task_id, dependency_key, binding, now_ms):
        db=self._connect()
        try:
            db.execute('BEGIN IMMEDIATE')
            row=db.execute('SELECT task_json FROM tasks WHERE id=?',(task_id,)).fetchone()
            pause=db.execute('SELECT dependency_key,paused_since_ms,accumulated_paused_ms,context_json FROM task_dependency_pauses WHERE task_id=?',(task_id,)).fetchone()
            if not row or not pause: raise StaleCommand('task is not paused')
            task=json.loads(row[0]); run=db.execute('SELECT current_generation FROM runs WHERE id=?',(task['run_id'],)).fetchone()
            actual={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            if task.get('kind')!='REVIEW' or task.get('state') in ('COMPLETED','CANCELLED','CANCEL_REQUESTED') or run is None or run[0]!=task['evaluation_generation'] or actual!=binding or pause[0]!=dependency_key or json.loads(pause[3])!=binding:
                raise StaleCommand('stale task/session binding')
            if pause[1]<0:
                db.commit(); return {'disposition':'REPLAYED','paused_ms':pause[2]}
            elapsed=max(0,int(now_ms)-pause[1]); total=pause[2]+elapsed
            db.execute('UPDATE task_dependency_pauses SET paused_since_ms=?,accumulated_paused_ms=? WHERE task_id=?',(-now_ms,total,task_id))
            db.execute("UPDATE dependency_circuits SET state='CLOSED' WHERE dependency_key=?",(dependency_key,))
            db.commit(); return {'disposition':'RESUMED','paused_ms':total}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def claim_native_task(self, *, task_id, run_id, evaluation_generation,
                          assignment_generation, input_digest, process_identity, claim_token):
        """Claim a CHECK only for its previously saved program identity/token."""
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()
            current=db.execute("SELECT current_generation FROM runs WHERE id=?",(run_id,)).fetchone()
            if row is None or current is None: raise StaleCommand("unknown task/run")
            t=json.loads(row[0]); claim=db.execute("SELECT process_identity,claim_token FROM native_claims WHERE task_id=?",(task_id,)).fetchone()
            if (t["run_id"]!=run_id or t["evaluation_generation"]!=evaluation_generation or current[0]!=evaluation_generation or
                t["assignment_generation"]!=assignment_generation or t["input_digest"]!=input_digest or t["kind"]!="CHECK" or
                claim is None or json.loads(claim[0])!=process_identity or claim[1]!=claim_token):
                raise StaleCommand("native claim identity or assignment mismatch")
            attempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(run_id,evaluation_generation)).fetchone()
            if attempt is None or json.loads(attempt[0])["phase"] not in ("QUEUED","CHECKING","REVIEWING"):
                raise StaleCommand("native claim is outside active attempt phase")
            if t["state"]=="CLAIMED": db.commit(); return {"disposition":"REPLAYED","task_id":task_id}
            if t["state"]!="DISPATCHING":
                raise StaleCommand("native task is not dispatchable")
            self._require_pass_dependencies(db,t)
            db.execute("UPDATE tasks SET task_json=? WHERE id=?",(canonical_json(dict(t,state="CLAIMED")),task_id))
            db.commit(); return {"disposition":"ACCEPTED","task_id":task_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def add_dependencies(self, task_id, dependency_ids):
        """Add same-attempt dependencies only; reject missing, cross-attempt or cycles."""
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            task=db.execute("SELECT run_id,generation FROM tasks WHERE id=?",(task_id,)).fetchone()
            if task is None: raise StaleCommand("unknown task")
            saved_task=db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()
            saved=json.loads(saved_task[0])
            attempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(task[0],task[1])).fetchone()
            if saved["state"]!="QUEUED" or json.loads(attempt[0])["phase"] not in ("QUEUED","CHECKING","REVIEWING"):
                raise StaleCommand("dependencies are frozen after dispatch begins")
            deps=list(saved["dependency_ids"])
            if len(set(dependency_ids))!=len(dependency_ids): raise ProtocolError("duplicate task dependency")
            for dep in dependency_ids:
                parent=db.execute("SELECT run_id,generation FROM tasks WHERE id=?",(dep,)).fetchone()
                if parent is None or tuple(parent)!=(task[0],task[1]): raise ProtocolError("dependency must exist in same attempt")
                if dep not in deps: deps.append(dep)
            cycle = task_id in deps
            if cycle:
                raise ProtocolError("task dependency cycle")
            for dep in deps: db.execute("INSERT OR IGNORE INTO task_deps VALUES(?,?)",(task_id,dep))
            cycle=db.execute("WITH RECURSIVE reach(id) AS (SELECT dependency_id FROM task_deps WHERE task_id=? UNION SELECT d.dependency_id FROM task_deps d JOIN reach r ON d.task_id=r.id) SELECT 1 FROM reach WHERE id=? LIMIT 1",(task_id,task_id)).fetchone()
            if cycle: raise ProtocolError("task dependency cycle")
            saved["dependency_ids"]=deps; validate(saved,"task")
            db.execute("UPDATE tasks SET task_json=? WHERE id=?",(canonical_json(saved),task_id))
            db.commit()
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    @staticmethod
    def _assert_global_submission_lane(db, run_id, generation, *, allow_queued_target=False):
        """Permit only one current run in a work-owning phase at a time.

        Attempt rows are already the durable admission authority. Keeping this
        check in the same SQLite write transaction as QUEUED->CHECKING makes
        competing authors/worktrees serialize without a separate lane record.
        """
        target=db.execute("SELECT r.current_generation,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id AND a.generation=r.current_generation WHERE r.id=?",
                          (run_id,)).fetchone()
        if target is None or target[0]!=generation:
            raise StaleCommand("submission lane requires the current attempt generation")
        target_attempt=json.loads(target[1]); target_phase=target_attempt["phase"]
        target_active=(target_phase in ("CHECKING","REVIEWING","STOPPING") or
                       (target_phase=="DECIDED" and target_attempt.get("cleanup_state")=="UNRESOLVED"))
        if allow_queued_target and target_phase=="QUEUED": target_active=True
        if not target_active:
            raise StaleCommand("submission lane target is not active")
        rows=db.execute("SELECT a.run_id,a.generation,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id AND a.generation=r.current_generation").fetchall()
        for other_run,other_generation,attempt_json in rows:
            other=json.loads(attempt_json); phase=other["phase"]
            active=phase in ("CHECKING","REVIEWING","STOPPING")
            if (active and
                    (other_run,other_generation)!=(run_id,generation)):
                raise SubmissionLaneBusy("another current submission owns the active lane")
        # A terminal ERROR may be durably reported while cleanup remains
        # unresolved. Keep the lane occupied only while actual resource/task
        # ownership remains unsettled; do not treat the saved cleanup label as
        # permanent ownership after the existing cleanup APIs prove cessation.
        held=db.execute("SELECT run_id,generation FROM resources WHERE state IN ('HELD','UNKNOWN')").fetchall()
        if any((owner_run,owner_generation)!=(run_id,generation) for owner_run,owner_generation in held):
            raise SubmissionLaneBusy("another attempt retains a HELD or UNKNOWN resource")
        unresolved=db.execute("SELECT t.run_id,t.generation FROM tasks t JOIN attempts a ON a.run_id=t.run_id AND a.generation=t.generation WHERE json_extract(a.attempt_json,'$.phase')='DECIDED' AND json_extract(a.attempt_json,'$.result')='ERROR' AND json_extract(t.task_json,'$.state') IN ('DISPATCHING','CLAIMED','RUNNING','CANCEL_REQUESTED')").fetchall()
        if any((owner_run,owner_generation)!=(run_id,generation) for owner_run,owner_generation in unresolved):
            raise SubmissionLaneBusy("another terminal ERROR attempt retains an unsettled dispatched task")
        scopes=db.execute("SELECT t.run_id,t.generation FROM native_scopes s JOIN tasks t ON t.id=s.task_id JOIN attempts a ON a.run_id=t.run_id AND a.generation=t.generation WHERE json_extract(a.attempt_json,'$.phase')='DECIDED' AND json_extract(a.attempt_json,'$.result')='ERROR' AND s.state!='CLEANED'").fetchall()
        if any((owner_run,owner_generation)!=(run_id,generation) for owner_run,owner_generation in scopes):
            raise SubmissionLaneBusy("another terminal ERROR attempt retains an uncleaned native scope")

    def assert_submission_lane(self, run_id, generation):
        """Revalidate an already-CHECKING attempt before native launch side effects."""
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            self._assert_global_submission_lane(db,run_id,generation)
            db.commit()
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def transition_attempt(self, run_id, generation, *, expected_sequence, phase,
                           proposed_result=None, closure=None, cleanup_state=None,
                           terminal_result=None, result_event=None, incident=None,
                           command_id=None, caller="SYSTEM"):
        """Guarded phase transition; terminal event and delivery intent share commit."""
        allowed={"QUEUED":{"CHECKING","STOPPING"},"CHECKING":{"REVIEWING","STOPPING"},
                 "REVIEWING":{"STOPPING"},"STOPPING":{"DECIDED","CLOSED"}}
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            request={"run_id":run_id,"generation":generation,"expected_sequence":expected_sequence,"phase":phase,
                     "proposed_result":proposed_result,"closure":closure,"cleanup_state":cleanup_state,
                     "terminal_result":terminal_result,"result_event":result_event,"incident":incident}
            command_key=command_id or f"{run_id}:{generation}:{expected_sequence}"
            if not isinstance(caller,str) or not caller.strip() or not isinstance(command_key,str) or not command_key.strip():
                raise ProtocolError("transition caller and command key must be nonempty")
            command_digest=digest(request)
            prior=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='transition_attempt' AND caller=? AND command_key=?",(caller,command_key)).fetchone()
            if prior:
                if prior[0]!=command_digest: raise IdempotencyConflict("transition command key reused with changed request")
                db.commit(); return json.loads(prior[1])
            row=db.execute("SELECT current_generation,sequence FROM runs WHERE id=?",(run_id,)).fetchone()
            if row is None or row[0]!=generation or row[1]!=expected_sequence: raise StaleCommand("attempt generation/sequence is stale")
            ar=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(run_id,generation)).fetchone()
            attempt=json.loads(ar[0]); old=attempt["phase"]
            if phase not in allowed.get(old,set()): raise StoreError(f"illegal attempt transition {old}->{phase}")
            tasks=[json.loads(r[0]) for r in db.execute("SELECT task_json FROM tasks WHERE run_id=? AND generation=?",(run_id,generation))]
            run_value=json.loads(db.execute("SELECT run_json FROM runs WHERE id=?",(run_id,)).fetchone()[0])
            if phase=="CHECKING":
                if old=="QUEUED":
                    self._assert_global_submission_lane(db,run_id,generation,allow_queued_target=True)
                checks=[t for t in tasks if t["kind"]=="CHECK"]
                if not checks or any(t["state"]!="QUEUED" or t["input_digest"]!=run_value["input_digest"] for t in checks):
                    raise StaleCommand("CHECKING requires initial pending checks bound to current input")
                if run_value.get("run_kind") == "POST_ACCEPTANCE_REGRESSION":
                    jobrow=db.execute("SELECT id,job_json FROM regression_jobs WHERE acceptance_id=?",(run_value["owner_acceptance_id"],)).fetchone()
                    if jobrow is None: raise StaleCommand("regression run has no stored job")
                    job=json.loads(jobrow[1])
                    if job.get("run_id")!=run_id or job.get("task_id") not in {t["task_id"] for t in checks}: raise ProtocolError("regression job/task binding mismatch")
                    job["status"]="RUNNING"
                    db.execute("UPDATE regression_jobs SET job_json=? WHERE id=?",(canonical_json(validate(job,"regression_job")),jobrow[0]))
            if phase=="REVIEWING":
                checks=self._effective_check_tasks(db,run_id,generation)
                if not self._effective_checks_complete(db,run_id,generation): raise StaleCommand("REVIEWING requires every required CHECK obligation to pass")
                for t in checks:
                    receipts=t["result"].get("artifact_refs",[])
                    valid=False
                    for ref in receipts:
                        ar=db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(ref,)).fetchone()
                        if ar:
                            art=json.loads(ar[0])["artifact"]
                            valid |= art.get("type")=="CHECK_RECEIPT" and art.get("producer_task_id")==t["task_id"]
                    if not valid: raise StaleCommand("CHECK task lacks registered producer-bound receipt")
            if phase=="STOPPING":
                if (proposed_result is None)==(closure is None): raise ProtocolError("STOPPING requires exactly one proposed result or closure")
                decisions=[t.get("result",{}).get("decision") for t in tasks if t["state"]=="COMPLETED"]
                if proposed_result=="PASS" and (not self._effective_checks_complete(db,run_id,generation) or not tasks or any(t["state"]!="COMPLETED" or t.get("result",{}).get("decision")!="PASS" for t in tasks)):
                    raise StaleCommand("PASS proposal requires all saved tasks complete PASS")
                if proposed_result=="FAIL" and "FAIL" not in decisions: raise StaleCommand("FAIL proposal requires a validated task FAIL")
                if proposed_result=="ERROR" and not any((t.get("result") or {}).get("decision")=="ERROR" for t in tasks) and incident is None:
                    raise StaleCommand("ERROR proposal requires saved task fault or incident context")
                # Close admission and revoke every unfinished assignment in this transaction.
                for t in tasks:
                    if t["state"] in ("COMPLETED","CANCELLED"): continue
                    if t["state"]=="QUEUED":
                        t.update(state="CANCELLED",result=None)
                        proof={"kind":"DB_NEVER_STARTED","task_id":t["task_id"],"run_id":run_id,"generation":generation,"assignment_generation":t["assignment_generation"],"input_digest":t["input_digest"]}
                        db.execute("INSERT OR IGNORE INTO task_cancellations(task_id,proof_json,confirmed) VALUES(?,?,1)",(t["task_id"],canonical_json(proof)))
                    else:
                        t["state"]="CANCEL_REQUESTED"
                        eid="event_cancel_task_"+hashlib.sha256((run_id+"\0"+str(generation)+"\0"+t["task_id"]+"\0"+str(t["assignment_generation"])).encode()).hexdigest()[:24]
                        event={"contract_version":1,"event_id":eid,"kind":"CANCEL_TASK","run_id":run_id,"evaluation_generation":generation,"task_id":t["task_id"],"assignment_generation":t["assignment_generation"],"input_digest":t["input_digest"]}
                        self._event(db,event,run_id,generation); db.execute("INSERT OR IGNORE INTO outbox(event_id) VALUES(?)",(eid,))
                    validate(t,"task")
                    db.execute("UPDATE tasks SET task_json=? WHERE id=?",(canonical_json(t),t["task_id"]))
                attempt.update(phase=phase,proposed_result=proposed_result,proposed_closure=closure,cleanup_state=cleanup_state or "PENDING")
                validate(attempt,"attempt")
            else:
                attempt["phase"]=phase
            if phase in ("DECIDED","CLOSED"):
                if old!="STOPPING": raise StoreError("terminalization requires STOPPING")
                taskrows=db.execute("SELECT task_json FROM tasks WHERE run_id=? AND generation=?",(run_id,generation)).fetchall()
                saved_tasks=[json.loads(r[0]) for r in taskrows]
                active=[t for t in saved_tasks if t["state"] not in ("COMPLETED","CANCELLED")]
                held=db.execute("SELECT claim_id,task_id,state FROM resources WHERE run_id=? AND generation=? AND state IN ('HELD','UNKNOWN')",(run_id,generation)).fetchall()
                for task in saved_tasks:
                    if task["state"]=="CANCELLED":
                        proofrow=db.execute("SELECT proof_json,confirmed FROM task_cancellations WHERE task_id=?",(task["task_id"],)).fetchone()
                        if proofrow is None or not proofrow[1]: raise StaleCommand("CANCELLED child lacks stored confirmation proof")
                        proof=json.loads(proofrow[0])
                        if (proof.get("run_id"),proof.get("generation"),proof.get("assignment_generation"),proof.get("input_digest"))!=(run_id,generation,task["assignment_generation"],task["input_digest"]):
                            raise ProtocolError("child cancellation proof assignment binding mismatch")
                proposed=attempt.get("proposed_result"); control=attempt.get("proposed_closure")
                cleanup=cleanup_state or attempt.get("cleanup_state")
                if phase=="CLOSED":
                    if control not in ("CANCELLED","SUPERSEDED") or proposed is not None:
                        raise ProtocolError("CLOSED requires control closure and null verdict")
                    if active or held or cleanup not in ("CONFIRMED","NOT_REQUIRED"):
                        raise StaleCommand("control closure requires all children/resources settled")
                    attempt.update(phase="CLOSED",closure=control,proposed_closure=None,result=None,cleanup_state=cleanup)
                    control_event={"contract_version":1,"event_id":"event_control_"+hashlib.sha256(f"{run_id}:{generation}:{row[1]+1}:{control}".encode()).hexdigest()[:24],"kind":"ATTEMPT_CLOSED","run_id":run_id,"evaluation_generation":generation,"closure":control,"child_ids":[t["task_id"] for t in saved_tasks]}
                    self._event(db,control_event,run_id,generation)
                    db.execute("INSERT INTO outbox(event_id) VALUES(?)",(control_event["event_id"],))
                    db.execute("INSERT INTO history(run_id,generation,sequence,event_id) VALUES(?,?,?,?)",(run_id,generation,row[1]+1,control_event["event_id"]))
                    # Cancellation is a terminal admission boundary. Release only
                    # this exact feature head, and only after physical ownership
                    # is proven settled in the same transaction as ATTEMPT_CLOSED.
                    # In particular, a STOPPING attempt never reaches this path.
                    if control == "CANCELLED" and run_value.get("run_kind") == "FEATURE_GATE":
                        self._verify_settled_feature_cleanup_tx(db,run_id,"CANCELLED",require_dispatch_settled=False)
                        db.execute("DELETE FROM feature_heads WHERE worktree=? AND sprint=? AND run_id=?",
                            (run_value["author"]["worktree"],run_value["sprint_id"],run_id))
                    result_event=control_event
                else:
                    promotion=(terminal_result=="ERROR" and cleanup=="UNRESOLVED" and (proposed in ("PASS","FAIL") or control in ("CANCELLED","SUPERSEDED")))
                    if terminal_result not in ("PASS","FAIL","ERROR") or (terminal_result!=proposed and not promotion):
                        raise ProtocolError("terminal verdict must match saved proposal")
                    if terminal_result in ("PASS","FAIL") and (active or held):
                        raise StaleCommand("PASS/FAIL requires settled child tasks and resources")
                    if terminal_result=="PASS":
                        checks=self._effective_check_tasks(db,run_id,generation)
                        if not self._effective_checks_complete(db,run_id,generation) or any(t["state"]!="COMPLETED" or (t.get("result") or {}).get("decision")!="PASS" for t in saved_tasks):
                            raise StaleCommand("PASS requires every saved task to complete PASS")
                        for check in checks:
                            valid=False
                            for ref in check["result"].get("artifact_refs",[]):
                                self._require_artifacts(db,[ref],run_id,input_digest=run_value["input_digest"])
                                ar=db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(ref,)).fetchone()
                                art=json.loads(ar[0])["artifact"]
                                valid |= art.get("type")=="CHECK_RECEIPT" and art.get("producer_task_id")==check["task_id"] and art.get("producer_id")==check["task_id"]
                            if not valid: raise StaleCommand("PASS check lacks registered producer-bound CHECK_RECEIPT")
                    if terminal_result=="FAIL":
                        failures=[t for t in saved_tasks if t["state"]=="COMPLETED" and (t.get("result") or {}).get("decision")=="FAIL"]
                        findings=[f for t in failures for f in (t["result"].get("findings") or []) if f.get("required_change")]
                        if not findings: raise StaleCommand("FAIL requires saved actionable findings")
                    if terminal_result=="ERROR":
                        if incident is None: raise ProtocolError("ERROR requires incident context")
                        if incident.get("run_id")!=run_id or incident.get("evaluation_generation")!=generation:
                            raise ProtocolError("terminal incident must bind exact run and generation")
                        if cleanup in ("CONFIRMED","NOT_REQUIRED"):
                            if active or held: raise StaleCommand("settled ERROR requires settled children/resources")
                        elif cleanup=="UNRESOLVED":
                            if not (active or held): raise StaleCommand("UNRESOLVED requires an unconfirmed child or held resource")
                            if not any((incident.get(k)==v for k,v in (("run_id",run_id),("evaluation_generation",generation)))):
                                raise ProtocolError("unresolved cleanup incident must be scoped to this attempt")
                        else: raise ProtocolError("ERROR cleanup state must be settled or UNRESOLVED")
                    elif cleanup not in ("CONFIRMED","NOT_REQUIRED","UNRESOLVED"):
                        raise ProtocolError("terminal cleanup state invalid")
                    elif cleanup=="UNRESOLVED":
                        # A cleanup fault promotes the proposal; the requested PASS/FAIL remains only historical proposal.
                        if not (active or held): raise StaleCommand("cleanup promotion requires concrete unresolved ownership")
                        if incident is None: raise ProtocolError("cleanup promotion requires a scoped incident")
                        terminal_result="ERROR"
                    attempt.update(result=terminal_result,phase="DECIDED",proposed_result=None,proposed_closure=None,cleanup_state=cleanup)
                    validate(attempt,"attempt")
                    db.execute("UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=?",(canonical_json(attempt),run_id,generation))
                    findings=[f for t in saved_tasks if t["state"]=="COMPLETED" and (t.get("result") or {}).get("decision")=="FAIL" for f in (t.get("result") or {}).get("findings",[])] if terminal_result=="FAIL" else [f for t in saved_tasks if t["state"]=="COMPLETED" for f in (t.get("result") or {}).get("findings",[])]
                    refs=sorted({ref for t in saved_tasks if t["state"]=="COMPLETED" for ref in ((t.get("result") or {}).get("artifact_refs",[])+[r for f in (t.get("result") or {}).get("findings",[]) for r in f.get("evidence_refs",[])])})
                    for ref in refs: self._require_artifacts(db,[ref],run_id,input_digest=run_value["input_digest"])
                    if run_value.get("run_kind") == "POST_ACCEPTANCE_REGRESSION":
                        jobrow=db.execute("SELECT id,acceptance_id,job_json FROM regression_jobs WHERE acceptance_id=?",(run_value["owner_acceptance_id"],)).fetchone()
                        if jobrow is None: raise StaleCommand("regression run has no stored job")
                        job=json.loads(jobrow[2])
                        if job.get("run_id")!=run_id or job.get("source_input_digest")!=run_value["input_digest"] or job.get("task_id") not in {t["task_id"] for t in saved_tasks}:
                            raise ProtocolError("regression finality binding mismatch")
                        event_id="event_regression_decided_"+hashlib.sha256(canonical_json({"regression_id":jobrow[0],"source_input_digest":run_value["input_digest"],"result":terminal_result,"artifact_refs":refs}).encode()).hexdigest()[:24]
                        event={"contract_version":1,"event_id":event_id,"kind":"REGRESSION_DECIDED","regression_id":jobrow[0],"acceptance_id":jobrow[1],"run_id":run_id,"evaluation_generation":generation,"input_digest":run_value["input_digest"],"result":terminal_result,"artifact_refs":refs}
                        if terminal_result in ("FAIL","ERROR"):
                            event.update(result_id=event_id,control_kind="POST_ACCEPTANCE_REGRESSION",
                                next_action="FIX_FINDINGS" if terminal_result=="FAIL" else "WAIT_FOR_RECOVERY",
                                findings=findings,author=run_value["author"])
                            event["result_digest"]=digest({k:v for k,v in event.items() if k!="event_id"})
                        if result_event is not None and result_event!=event: raise ProtocolError("regression event must be internally derived from saved terminal facts")
                        self._event(db,event,run_id,generation)
                        db.execute("INSERT INTO outbox(event_id) VALUES(?)",(event_id,))
                        db.execute("INSERT INTO history(run_id,generation,sequence,event_id) VALUES(?,?,?,?)",(run_id,generation,row[1]+1,event_id))
                        job.update(status="DECIDED",result=terminal_result)
                        db.execute("UPDATE regression_jobs SET job_json=? WHERE id=?",(canonical_json(validate(job,"regression_job")),jobrow[0]))
                        if terminal_result in ("FAIL", "ERROR"):
                            self._enqueue_control_feedback_tx(db, event, run_value, regression=True)
                        result_event=event
                    else:
                        # Public result_digest = SHA-256(canonical_json of exactly this
                        # semantic object); result_id/event_id derives from that digest.
                        # IDs are deliberately excluded to avoid a circular digest.
                        sem={"run_id":run_id,"evaluation_generation":generation,"input_digest":run_value["input_digest"],"result":terminal_result,"findings":findings,"artifact_refs":refs}
                        result_digest=digest(sem); result_id="event_result_"+hashlib.sha256(result_digest.encode()).hexdigest()[:24]
                        action={"PASS":"WAIT_FOR_OWNER_TEST","FAIL":"FIX_FINDINGS","ERROR":"WAIT_FOR_RECOVERY"}[terminal_result]
                        built={"contract_version":1,"event_id":result_id,"run_id":run_id,"evaluation_generation":generation,"input_digest":run_value["input_digest"],"result":terminal_result,"result_id":result_id,"result_digest":result_digest,"findings":findings,"artifact_refs":refs,"next_action":action}
                        if terminal_result=="ERROR":
                            if incident is None: raise ProtocolError("ERROR requires incident")
                            built.update(error_code=incident["code"],error_message=incident["message"],incident_ref=incident["incident_id"])
                        validate(built,"result_available")
                        if result_event is not None and result_event!=built: raise ProtocolError("result event must be internally derived from saved terminal facts")
                        result_event=built
                        self._event(db,result_event,run_id,generation)
                        db.execute("INSERT INTO history(run_id,generation,sequence,event_id) VALUES(?,?,?,?)",(run_id,generation,row[1]+1,result_event["event_id"]))
                        author=run_value["author"]
                        for target,recipient in (("AUTHOR",{"session_id":author["session_id"],"worktree":author["worktree"],"role":"author"}),("WEBPAGE",{"session_id":None,"worktree":author["worktree"],"role":"owner"})):
                            did="delivery_"+hashlib.sha256((result_event["event_id"]+target+canonical_json(recipient)).encode()).hexdigest()[:24]
                            self._enqueue_delivery_tx(db,event=result_event,delivery_id=did,recipient=recipient,target_kind=target)
                if phase=="DECIDED" and terminal_result=="ERROR" and incident is not None:
                    self._record_incident_tx(db,incident)
            seq=row[1]+1
            if phase not in ("DECIDED","CLOSED"):
                eid="event_attempt_"+hashlib.sha256(canonical_json({"run_id":run_id,"generation":generation,"sequence":seq,"phase":phase}).encode()).hexdigest()[:24]
                event={"contract_version":1,"event_id":eid,"kind":"ATTEMPT_"+phase,"run_id":run_id,"evaluation_generation":generation,"sequence":seq}
                self._event(db,event,run_id,generation); db.execute("INSERT INTO outbox(event_id) VALUES(?)",(eid,))
                result_event=event
            db.execute("UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=?",(canonical_json(attempt),run_id,generation))
            db.execute("UPDATE runs SET sequence=? WHERE id=?",(seq,run_id))
            validate(attempt,"attempt")
            if result_event and phase not in ("DECIDED","CLOSED"):
                db.execute("INSERT INTO history(run_id,generation,sequence,event_id) VALUES(?,?,?,?)",(run_id,generation,seq,result_event["event_id"]))
            if incident is not None and phase not in ("DECIDED","CLOSED"):
                incident=validate(incident,"incident")
                self._record_incident_tx(db,incident)
            response={"sequence":seq,"attempt":attempt,"event_id":None if result_event is None else result_event["event_id"]}
            db.execute("INSERT INTO command_receipts(operation,caller,command_key,request_digest,response_json) VALUES('transition_attempt',?,?,?,?)",(caller,command_key,command_digest,canonical_json(response)))
            db.commit()
            return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def confirm_task_cancelled(self, *, task_id, run_id, evaluation_generation,
                               assignment_generation, input_digest, cleanup_artifact_ref):
        """Persist trusted adapter cessation proof for an owned cancellation."""
        if not isinstance(cleanup_artifact_ref,str) or not cleanup_artifact_ref.startswith("artifact_"):
            raise ProtocolError("registered cleanup artifact reference required")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()
            if row is None: raise StaleCommand("unknown cancellation task")
            task=json.loads(row[0])
            if (task["run_id"],task["evaluation_generation"],task["assignment_generation"],task["input_digest"])!=(run_id,evaluation_generation,assignment_generation,input_digest):
                raise StaleCommand("cancellation proof assignment fence mismatch")
            req={"task_id":task_id,"run_id":run_id,"generation":evaluation_generation,"assignment_generation":assignment_generation,"input_digest":input_digest,"artifact_ref":cleanup_artifact_ref}
            old=db.execute("SELECT proof_json,confirmed FROM task_cancellations WHERE task_id=?",(task_id,)).fetchone()
            if old and old[1]:
                saved=json.loads(old[0])
                if saved.get("adapter_confirmation")!=req: raise IdempotencyConflict("cancellation confirmation proof is immutable")
                db.commit(); return {"disposition":"REPLAYED","task_id":task_id,"state":"CANCELLED"}
            if task["state"]!="CANCEL_REQUESTED": raise StaleCommand("task has no pending cancellation")
            if db.execute("SELECT 1 FROM resources WHERE task_id=? AND state IN ('HELD','UNKNOWN') LIMIT 1",(task_id,)).fetchone():
                raise StaleCommand("owned resources remain HELD or UNKNOWN")
            scope=db.execute('SELECT assignment_generation,state,cleanup_artifact_ref FROM native_scopes WHERE task_id=?',(task_id,)).fetchone()
            if scope is not None and (scope[0]!=assignment_generation or scope[1]!='CLEANED' or scope[2]!=cleanup_artifact_ref):
                raise StaleCommand('native process scope cleanup is not confirmed')
            self._require_artifacts(db,[cleanup_artifact_ref],run_id,input_digest=input_digest)
            artrow=db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(cleanup_artifact_ref,)).fetchone()
            artifact=json.loads(artrow[0])["artifact"]
            if artifact.get("producer_kind")!="TASK" or artifact.get("producer_id")!=task_id or artifact.get("producer_task_id")!=task_id or artifact.get("type") not in ("LOG","CHECK_RECEIPT","PROVENANCE"):
                raise ProtocolError("cleanup proof must be an owned TASK LOG or CHECK_RECEIPT")
            proof={"kind":"ADAPTER_CONFIRMED","task_id":task_id,"run_id":run_id,"generation":evaluation_generation,"assignment_generation":assignment_generation,"input_digest":input_digest,"artifact_ref":cleanup_artifact_ref}
            validate(dict(task,state="CANCELLED",result=None),"task")
            db.execute("UPDATE tasks SET task_json=? WHERE id=?",(canonical_json(dict(task,state="CANCELLED",result=None)),task_id))
            db.execute("INSERT INTO task_cancellations(task_id,proof_json,confirmed) VALUES(?,?,1) ON CONFLICT(task_id) DO UPDATE SET proof_json=excluded.proof_json,confirmed=1",(task_id,canonical_json(dict(proof,adapter_confirmation=req))))
            eid="event_cancelled_"+hashlib.sha256((task_id+"\0"+str(assignment_generation)+"\0"+cleanup_artifact_ref).encode()).hexdigest()[:24]
            event={"contract_version":1,"event_id":eid,"kind":"TASK_CANCELLED","run_id":run_id,"evaluation_generation":evaluation_generation,"task_id":task_id,"assignment_generation":assignment_generation,"input_digest":input_digest,"cleanup_artifact_ref":cleanup_artifact_ref}
            self._event(db,event,run_id,evaluation_generation); db.execute("INSERT INTO outbox(event_id) VALUES(?)",(eid,))
            db.commit(); return {"disposition":"CONFIRMED","task_id":task_id,"state":"CANCELLED","event_id":eid}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def recover_error(self, run_id, *, expected_sequence, authorization, fresh_input_proof,
                      boundary, recovery_event, reusable_artifact_refs=(), command_id=None, caller="SYSTEM"):
        """Only linked ERROR can advance current generation; proof is an adapter attestation."""
        if not isinstance(fresh_input_proof,str) or not fresh_input_proof.strip() or not boundary:
            raise ProtocolError("fresh input proof and failed boundary are required")
        if not isinstance(authorization,dict) or authorization.get("kind") not in ("POLICY","OWNER") or not authorization.get("id"):
            raise ProtocolError("recovery authorization required")
        if not isinstance(boundary,str) or not boundary.strip():
            raise ProtocolError("failed boundary must be nonempty")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            refs=list(reusable_artifact_refs)
            event_id=recovery_event.get("event_id") if isinstance(recovery_event,dict) else None
            request={"run_id":run_id,"expected_sequence":expected_sequence,"authorization":authorization,"fresh_input_proof":fresh_input_proof,"boundary":boundary,"event_id":event_id,"reusable_artifact_refs":refs}
            command_key=command_id or f"{run_id}:{expected_sequence}"
            if not isinstance(caller,str) or not caller.strip() or not isinstance(command_key,str) or not command_key.strip(): raise ProtocolError("recovery caller and command key required")
            command_digest=digest(request)
            prior=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='recover_error' AND caller=? AND command_key=?",(caller,command_key)).fetchone()
            if prior:
                if prior[0]!=command_digest: raise IdempotencyConflict("recovery command key reused with changed request")
                db.commit(); return json.loads(prior[1])
            row=db.execute("SELECT current_generation,sequence,run_json FROM runs WHERE id=?",(run_id,)).fetchone()
            if row is None or row[1]!=expected_sequence: raise StaleCommand("recovery sequence stale")
            oldgen=row[0]
            old=json.loads(db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(run_id,oldgen)).fetchone()[0])
            if old["phase"]!="DECIDED" or old["result"]!="ERROR": raise StoreError("only terminal ERROR can recover")
            if db.execute("SELECT 1 FROM tasks WHERE run_id=? AND generation=? AND json_extract(task_json,'$.state') NOT IN ('COMPLETED','CANCELLED') LIMIT 1",(run_id,oldgen)).fetchone(): raise StoreError("old attempt has unsettled child tasks")
            if db.execute("SELECT 1 FROM resources WHERE run_id=? AND state IN ('HELD','UNKNOWN')",(run_id,)).fetchone():
                raise StoreError("uncertain resource ownership blocks recovery")
            oldtasks=[dict(json.loads(r[0]),logical_key=r[1]) for r in db.execute("SELECT task_json,logical_key FROM tasks WHERE run_id=? AND generation=?",(run_id,oldgen))]
            if any(t["state"] not in ("COMPLETED","CANCELLED") for t in oldtasks): raise StoreError("old attempt has unsettled child tasks")
            for task in oldtasks:
                if task["state"]=="CANCELLED":
                    proof=db.execute("SELECT proof_json,confirmed FROM task_cancellations WHERE task_id=?",(task["task_id"],)).fetchone()
                    if proof is None or not proof[1]: raise StoreError("cancelled child lacks confirmed cessation")
                    p=json.loads(proof[0])
                    if (p.get("run_id"),p.get("generation"),p.get("assignment_generation"),p.get("input_digest"))!=(run_id,oldgen,task["assignment_generation"],task["input_digest"]): raise ProtocolError("child cancellation proof assignment mismatch")
            savedrun=json.loads(row[2])
            self._require_artifacts(db,[fresh_input_proof],run_id,input_digest=savedrun["input_digest"])
            proofinfo=json.loads(db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(fresh_input_proof,)).fetchone()[0])
            if proofinfo["artifact"]["type"] not in ("PROVENANCE","SOURCE_MANIFEST"): raise ProtocolError("fresh input proof must be provenance or source manifest")
            gen=oldgen+1
            if set(authorization)!={"kind","id"} or not isinstance(authorization["id"],str) or not authorization["id"].strip(): raise ProtocolError("recovery authorization has unsupported fields or empty id")
            attempt={"contract_version":1,"run_id":run_id,"generation":gen,"phase":"QUEUED","result":None,"closure":None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None,"recovery_from_generation":oldgen,"authorization":authorization}
            validate(attempt,"attempt")
            if not isinstance(event_id,str) or not event_id.startswith("event_"): raise ProtocolError("stable recovery event_id required")
            oldresult=db.execute("SELECT payload FROM events WHERE run_id=? AND generation=? AND json_extract(payload,'$.result')='ERROR' ORDER BY created_ms DESC LIMIT 1",(run_id,oldgen)).fetchone()
            oldresult_id=None if oldresult is None else json.loads(oldresult[0]).get("result_id")
            author=savedrun["author"]
            event={"contract_version":1,"event_id":event_id,"kind":"RECOVERY_STARTED","run_id":run_id,"evaluation_generation":gen,"from_generation":oldgen,"previous_error_result_id":oldresult_id,"input_digest":savedrun["input_digest"],"fresh_input_proof":fresh_input_proof,"failed_boundary":boundary,"authorization":authorization,"recipient_bindings":[author,{"session_id":None,"worktree":author["worktree"],"role":"owner"}],"next_action":"WAIT_FOR_RESULT","status_intent":"RECOVERY_STARTED"}
            if isinstance(recovery_event,dict) and recovery_event not in ({"event_id":event_id},{"contract_version":1,"event_id":event_id,"kind":"RECOVERY_STARTED","run_id":run_id,"evaluation_generation":gen}): raise ProtocolError("recovery event is generated from saved recovery facts")
            self._event(db,event,run_id,gen)
            db.execute("INSERT INTO attempts VALUES(?,?,?)",(run_id,gen,canonical_json(attempt)))
            if any(not isinstance(ref,str) or not ref.startswith("artifact_") for ref in refs):
                raise ProtocolError("invalid reusable artifact reference")
            effective_old=self._effective_check_tasks(db,run_id,oldgen)
            required={t["logical_key"] for t in effective_old}
            required.update(r[0] for r in db.execute("SELECT logical_check_key FROM recovery_check_obligations WHERE run_id=? AND generation=?",(run_id,oldgen)))
            by_key={t["logical_key"]:t for t in effective_old if t["state"]=="COMPLETED" and (t.get("result") or {}).get("decision")=="PASS"}
            receipt_by_task={}
            for t in by_key.values():
                receipt_by_task[t["task_id"]]=[]
                for r in t["result"].get("artifact_refs",[]):
                    self._require_artifacts(db,[r],run_id,input_digest=savedrun["input_digest"])
                    ar=db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(r,)).fetchone()
                    if ar:
                        art=json.loads(ar[0])["artifact"]
                        if art.get("type")=="CHECK_RECEIPT" and art.get("producer_task_id")==t["task_id"] and art.get("producer_id")==t["task_id"]: receipt_by_task[t["task_id"]].append(r)
            selected={}
            for ref in refs:
                match=None
                for key,t in by_key.items():
                    if ref in receipt_by_task[t["task_id"]]: match=(key,t); break
                if match is None: raise ProtocolError("reusable receipt must belong to a saved PASS CHECK in this ERROR attempt")
                key,t=match
                source=db.execute("SELECT run_id,generation FROM tasks WHERE id=?",(t["task_id"],)).fetchone()
                if source is None or source[0]!=run_id or source[1]>=gen: raise ProtocolError("reused source CHECK must be an earlier attempt of this run")
                selected.setdefault(key,{"task":t,"refs":[]})["refs"].append(ref)
            for key,entry in selected.items():
                db.execute("INSERT INTO reused_checks VALUES(?,?,?,?,?)",(run_id,gen,key,entry["task"]["task_id"],canonical_json(entry["refs"])))
            for key in required: db.execute("INSERT INTO recovery_check_obligations VALUES(?,?,?)",(run_id,gen,key))
            if required and required.issubset(selected):
                attempt["phase"]="REVIEWING"
                db.execute("UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=?",(canonical_json(attempt),run_id,gen))
            for ref in refs:
                ar=db.execute("SELECT run_id,artifact_json FROM artifacts WHERE id=?",(ref,)).fetchone()
                if ar is None or ar[0]!=run_id: raise ProtocolError("reusable artifact must be registered in this run")
                artifact=json.loads(ar[1])["artifact"]
                producer=db.execute("SELECT task_json FROM tasks WHERE id=?",(artifact.get("producer_task_id"),)).fetchone()
                if artifact.get("type")!="CHECK_RECEIPT" or producer is None:
                    raise ProtocolError("recovery reuse requires a registered CHECK_RECEIPT")
                source=json.loads(producer[0])
                if source["state"]!="COMPLETED" or source.get("result",{}).get("decision")!="PASS" or ref not in source.get("result",{}).get("artifact_refs",[]):
                    raise ProtocolError("reused CHECK receipt requires its saved PASS producer")
            db.execute("INSERT INTO recovery_metadata VALUES(?,?,?,?,?)",(run_id,gen,fresh_input_proof,boundary,canonical_json(refs)))
            db.execute("UPDATE runs SET current_generation=?,sequence=sequence+1 WHERE id=?",(gen,run_id))
            if attempt["phase"]=="REVIEWING":
                self._assert_global_submission_lane(db,run_id,gen)
            db.execute("INSERT INTO outbox(event_id) VALUES(?)",(recovery_event["event_id"],))
            db.execute("INSERT INTO history(run_id,generation,sequence,event_id) VALUES(?,?,?,?)",(run_id,gen,row[1]+1,recovery_event["event_id"]))
            response={"generation":gen,"recovery_from_generation":oldgen,"reused_artifacts":refs}
            db.execute("INSERT INTO command_receipts(operation,caller,command_key,request_digest,response_json) VALUES('recover_error',?,?,?,?)",(caller,command_key,command_digest,canonical_json(response)))
            # Evidence links refer to immutable accepted source task results; no copied result or execution is made.
            db.commit()
            return response
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def acquire_resource(self, *, resource_key, run_id, generation, task_id, assignment_generation, identity_proof):
        if not isinstance(identity_proof,dict) or not identity_proof: raise ProtocolError("structured ownership identity required")
        request={"resource_key":resource_key,"run_id":run_id,"generation":generation,"task_id":task_id,"assignment_generation":assignment_generation,"identity_proof":identity_proof}
        request_digest=digest(request)
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            current=db.execute("SELECT current_generation,run_json FROM runs WHERE id=?",(run_id,)).fetchone()
            task=db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()
            if current is None or current[0]!=generation or task is None: raise StaleCommand("resource owner stale")
            t=json.loads(task[0])
            if t["run_id"]!=run_id or t["input_digest"]!=json.loads(current[1])["input_digest"] or t["evaluation_generation"]!=generation or t["assignment_generation"]!=assignment_generation: raise StaleCommand("assignment fencing mismatch")
            attempt=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(run_id,generation)).fetchone()
            if attempt is None or json.loads(attempt[0])["phase"] in ("STOPPING","DECIDED","CLOSED") or t["state"] not in ("DISPATCHING","CLAIMED","RUNNING"): raise StaleCommand("resource admission is closed or task not active")
            old=db.execute("SELECT claim_id,request_digest FROM resources WHERE resource_key=? AND task_id=? AND assignment_generation=? ORDER BY rowid DESC LIMIT 1",(resource_key,task_id,assignment_generation)).fetchone()
            if old and old[1]==request_digest:
                db.commit(); return {"disposition":"REPLAYED","claim_id":old[0]}
            prior=db.execute("SELECT claim_id,request_digest FROM resources WHERE resource_key=? AND request_digest=?",(resource_key,request_digest)).fetchone()
            if prior:
                db.commit(); return {"disposition":"REPLAYED","claim_id":prior[0]}
            native=db.execute("SELECT process_identity FROM native_claims WHERE task_id=?",(task_id,)).fetchone()
            if native is None or json.loads(native[0])!=identity_proof: raise StaleCommand("resource identity is not the saved native process identity")
            claim_id=self._insert_resource_tx(db,resource_key=resource_key,run_id=run_id,generation=generation,
                task_id=task_id,assignment_generation=assignment_generation,identity_proof=identity_proof)
            self._native_resource_board_hint_tx(db,resource_key,claim_id,"HELD",run_id,generation)
            db.commit()
            return {"disposition":"ACCEPTED","claim_id":claim_id}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def mark_resource_unknown(self, claim_id, *, identity_proof):
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT state,identity_proof,resource_key,run_id,generation FROM resources WHERE claim_id=?",(claim_id,)).fetchone()
            if row is None or row[1]!=canonical_json(identity_proof): raise StaleCommand("resource claim identity mismatch")
            if row[0]=="UNKNOWN": db.commit(); return
            cur=db.execute("UPDATE resources SET state='UNKNOWN' WHERE claim_id=? AND state='HELD'",(claim_id,))
            if cur.rowcount!=1: raise StaleCommand("resource not held")
            self._native_resource_board_hint_tx(db,row[2],claim_id,"UNKNOWN",row[3],row[4])
            db.commit()
        finally: db.close()

    def release_resource(self, resource_key, *, claim_id, run_id, generation, task_id, assignment_generation, identity_proof, cleanup_proof):
        if not isinstance(cleanup_proof,str) or not cleanup_proof.startswith("artifact_"): raise ProtocolError("registered cleanup artifact reference required")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT run_id,generation,task_id,assignment_generation,identity_proof,state FROM resources WHERE claim_id=? AND resource_key=?",(claim_id,resource_key)).fetchone()
            if row is None or tuple(row[:4])!=(run_id,generation,task_id,assignment_generation) or json.loads(row[4])!=identity_proof: raise StaleCommand("resource claim fence mismatch")
            if row[5]=='RELEASED':
                oldproof=db.execute("SELECT cleanup_proof FROM resources WHERE claim_id=?",(claim_id,)).fetchone()[0]
                if oldproof!=cleanup_proof: raise IdempotencyConflict("released cleanup proof is immutable")
                db.commit(); return {"disposition":"REPLAYED","claim_id":claim_id,"state":"RELEASED"}
            if row[5] not in ('HELD','UNKNOWN'): raise StaleCommand("resource claim unavailable")
            task=json.loads(db.execute("SELECT task_json FROM tasks WHERE id=?",(task_id,)).fetchone()[0])
            self._require_artifacts(db,[cleanup_proof],run_id,input_digest=task["input_digest"])
            art=json.loads(db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(cleanup_proof,)).fetchone()[0])["artifact"]
            if art.get("producer_kind")!="TASK" or art.get("producer_task_id")!=task_id: raise ProtocolError("cleanup artifact must be produced by owning task")
            bound=db.execute("SELECT claim_id FROM resource_proofs WHERE artifact_id=?",(cleanup_proof,)).fetchone()
            if bound and bound[0]!=claim_id: raise ProtocolError("cleanup proof already bound to another resource claim")
            db.execute("INSERT OR IGNORE INTO resource_proofs(artifact_id,claim_id) VALUES(?,?)",(cleanup_proof,claim_id))
            cur=db.execute("UPDATE resources SET state='RELEASED',cleanup_proof=? WHERE claim_id=? AND state IN ('HELD','UNKNOWN')",(cleanup_proof,claim_id))
            if cur.rowcount!=1: raise StaleCommand("resource unavailable")
            self._native_resource_board_hint_tx(db,resource_key,claim_id,"RELEASED",run_id,generation)
            db.commit()
            return {"disposition":"RELEASED","claim_id":claim_id,"state":"RELEASED"}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_resource(self, claim_id):
        db=self._connect()
        try:
            row=db.execute("SELECT claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof FROM resources WHERE claim_id=?",(claim_id,)).fetchone()
            return None if row is None else dict(zip(("claim_id","resource_key","run_id","generation","task_id","assignment_generation","identity_proof","state","cleanup_proof"),row))
        finally: db.close()

    def get_resource_history(self, resource_key):
        db=self._connect()
        try:
            return [dict(zip(("claim_id","resource_key","run_id","generation","task_id","assignment_generation","identity_proof","state","cleanup_proof"),r)) for r in db.execute("SELECT claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof FROM resources WHERE resource_key=? ORDER BY rowid",(resource_key,))]
        finally: db.close()

    def _enqueue_delivery_tx(self, db, *, event, delivery_id, recipient, target_kind):
        """Insert one recipient delivery in the caller's existing transaction.

        Terminalization callers may invoke this after writing the DECIDED attempt,
        keeping verdict, immutable event, recipient job and wake intent atomic.
        The outbox is a code wake for durable delivery rows, not the job itself.
        """
        if target_kind not in ("AUTHOR","WEBPAGE"): raise ProtocolError("invalid delivery target")
        if not isinstance(event,dict) or not isinstance(event.get("event_id"),str) or not event["event_id"].startswith("event_"):
            raise ProtocolError("delivery event identity required")
        recipient=validate(recipient,"binding")
        event=validate(event,"result_available")
        runrow=db.execute("SELECT run_json,current_generation FROM runs WHERE id=?",(event["run_id"],)).fetchone()
        attemptrow=db.execute("SELECT attempt_json FROM attempts WHERE run_id=? AND generation=?",(event["run_id"],event["evaluation_generation"])).fetchone()
        if runrow is None or attemptrow is None: raise StaleCommand("delivery result has no saved run and attempt")
        run=json.loads(runrow[0]); attempt=json.loads(attemptrow[0])
        validate(run,"run"); validate(attempt,"attempt")
        if (attempt.get("phase")!="DECIDED" or attempt.get("result")!=event["result"] or
            event["input_digest"]!=run.get("input_digest")):
            raise StaleCommand("delivery result is not bound to the saved terminal attempt")
        author=run.get("author")
        expected=author if target_kind=="AUTHOR" else {"session_id":None,"worktree":author["worktree"],"role":"owner"}
        if recipient!=expected: raise StaleCommand("delivery binding is not the saved author or local owner")
        return self._insert_delivery_tx(db,event=event,delivery_id=delivery_id,recipient=recipient,target_kind=target_kind)

    def _insert_delivery_tx(self, db, *, event, delivery_id, recipient, target_kind):
        """Canonical immutable event/delivery/outbox insertion shared by typed intents."""
        payload=canonical_json(event); pd=hashlib.sha256(payload.encode()).hexdigest(); recipient_json=canonical_json(recipient)
        old_event=db.execute("SELECT payload,payload_digest FROM events WHERE id=?",(event["event_id"],)).fetchone()
        if old_event and (old_event[0]!=payload or old_event[1]!=pd): raise IdempotencyConflict("event ID reused with changed payload")
        if not old_event: self._event(db,event,event["run_id"],event["evaluation_generation"])
        old=db.execute("SELECT id,payload_digest FROM deliveries WHERE event_id=? AND recipient_json=?",(event["event_id"],recipient_json)).fetchone()
        if old:
            if old[0]!=delivery_id or old[1]!=pd: raise IdempotencyConflict("delivery identity reused with changed content")
            return {"delivery_id":delivery_id,"event_id":event["event_id"],"payload_digest":pd,"disposition":"REPLAYED"}
        current=db.execute("SELECT current_generation FROM runs WHERE id=?",(event["run_id"],)).fetchone()
        if current is None or event["evaluation_generation"]!=current[0]: raise StaleCommand("new delivery must name the current evaluation generation")
        prior=db.execute("SELECT event_id FROM deliveries WHERE id=?",(delivery_id,)).fetchone()
        if prior: raise IdempotencyConflict("delivery ID reused for another recipient or event")
        db.execute("INSERT INTO deliveries(id,event_id,recipient_json,target_kind,state,payload_digest) VALUES(?,?,?,?,'PENDING',?)",(delivery_id,event["event_id"],recipient_json,target_kind,pd))
        wake=db.execute("SELECT state,claim_generation FROM outbox WHERE event_id=?",(event["event_id"],)).fetchone()
        if wake is None:
            db.execute("INSERT INTO outbox(event_id) VALUES(?)",(event["event_id"],))
        elif wake[0]=="ACKNOWLEDGED":
            db.execute("UPDATE outbox SET state='PENDING',claim_generation=claim_generation+1,lease_until_ms=NULL WHERE event_id=?",(event["event_id"],))
        return {"delivery_id":delivery_id,"event_id":event["event_id"],"payload_digest":pd,"disposition":"ACCEPTED"}

    def _enqueue_control_feedback_tx(self, db, event, source_run, *, regression=False,
                                     source_result_id=None, source_result_digest=None):
        """Create author-only delivery for a saved owner decision or regression finality."""
        if regression:
            jobrow=db.execute("SELECT job_json,inputs_json FROM regression_jobs WHERE id=?",(event["regression_id"],)).fetchone()
            if jobrow is None: raise StaleCommand("regression feedback has no saved job")
            job,inputs=json.loads(jobrow[0]),json.loads(jobrow[1])
            if job.get("run_id")!=event["run_id"] or job.get("status")!="DECIDED" or job.get("result") not in ("FAIL","ERROR") or job.get("result")!=event.get("result"):
                raise StaleCommand("regression feedback is not bound to saved finality")
            runrow=db.execute("SELECT run_json FROM runs WHERE id=?",(event["run_id"],)).fetchone()
            author=json.loads(runrow[0])["author"]
            payload=event
            # Regression verdict is explicitly scoped to the accepted snapshot.
            payload["control_kind"]="POST_ACCEPTANCE_REGRESSION"
        else:
            row=db.execute("SELECT decision_json FROM owner_decisions WHERE id=?",(event["decision_id"],)).fetchone()
            if row is None: raise StaleCommand("owner feedback has no saved decision")
            decision=json.loads(row[0])
            if decision.get("decision") not in ("REQUEST_CHANGES","CANCEL") or decision.get("run_id")!=event["run_id"]:
                raise StaleCommand("owner feedback is not a saved author decision")
            payload=event
        author=source_run["author"]
        recipient={"session_id":author["session_id"],"worktree":author["worktree"],"role":"author"}
        did="delivery_"+hashlib.sha256((payload["event_id"]+"AUTHOR"+canonical_json(recipient)).encode()).hexdigest()[:24]
        self._insert_delivery_tx(db,event=payload,delivery_id=did,recipient=recipient,target_kind="AUTHOR")

    def enqueue_delivery(self, *, event, delivery_id, recipient, target_kind):
        """Persist exact payload and recipient before any transport is attempted."""
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            result=self._enqueue_delivery_tx(db,event=event,delivery_id=delivery_id,recipient=recipient,target_kind=target_kind)
            db.commit()
            return result
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def claim_delivery(self, *, now_ms=None, lease_ms=30000, delivery_id=None):
        now_ms=int(time.time()*1000) if now_ms is None else now_ms
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            query="SELECT d.id,d.event_id,d.recipient_json,d.target_kind,d.payload_digest,d.claim_generation,e.payload FROM deliveries d JOIN events e ON e.id=d.event_id WHERE d.state IN ('PENDING','TRANSPORT_ACCEPTED') AND d.protocol_ack IS NULL AND (d.lease_until_ms IS NULL OR d.lease_until_ms<=?)"
            params=[now_ms]
            if delivery_id is not None:
                query += " AND d.id=?"; params.append(delivery_id)
            query += " ORDER BY e.created_ms,d.id LIMIT 1"
            row=db.execute(query,params).fetchone()
            if not row: db.commit(); return None
            gen=row[5]+1; token=hashlib.sha256(f"{row[0]}:{gen}".encode()).hexdigest()
            db.execute("UPDATE deliveries SET claim_generation=?,lease_token=?,lease_until_ms=?,attempts=attempts+1 WHERE id=?",(gen,token,now_ms+lease_ms,row[0]))
            db.commit(); return {"delivery_id":row[0],"message_id":row[0],"event_id":row[1],"recipient":json.loads(row[2]),"target_kind":row[3],"payload_digest":row[4],"claim_generation":gen,"lease_token":token,"lease_until_ms":now_ms+lease_ms,"payload":json.loads(row[6])}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_delivery(self, delivery_id):
        db=self._connect()
        try:
            row=db.execute("SELECT d.id,d.event_id,d.recipient_json,d.target_kind,d.state,d.payload_digest,d.transport_receipt,d.protocol_ack,d.attempts,d.claim_generation,d.lease_until_ms,e.payload FROM deliveries d JOIN events e ON e.id=d.event_id WHERE d.id=?",(delivery_id,)).fetchone()
            if not row:return None
            result=dict(zip(("delivery_id","event_id","recipient","target_kind","state","payload_digest","transport_receipt","protocol_ack","attempts","claim_generation","lease_until_ms","payload"),row))
            for key in ("recipient","transport_receipt","protocol_ack","payload"):
                result[key]=None if result[key] is None else json.loads(result[key])
            result["recipient"]=validate(result["recipient"],"binding")
            expected_role="author" if result["target_kind"]=="AUTHOR" else "owner"
            if result["recipient"]["role"]!=expected_role:
                raise StoreError("stored delivery recipient does not match target kind")
            return result
        finally: db.close()

    def record_delivery_transport(self, delivery_id, *, claim_generation, lease_token, receipt):
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT d.claim_generation,d.state,d.transport_receipt,d.protocol_ack,d.lease_until_ms,d.lease_token,d.event_id,d.recipient_json,d.payload_digest FROM deliveries d WHERE d.id=?",(delivery_id,)).fetchone()
            if row is None: raise StaleCommand("unknown delivery")
            if row[3] is not None and (row[0]!=claim_generation or row[5]!=lease_token):
                db.commit(); return {"disposition":"HISTORICAL_STALE"}
            if row[0]!=claim_generation or row[5]!=lease_token: raise StaleCommand("stale delivery transport observation")
            if not isinstance(receipt,dict) or set(receipt)!={"message_id","event_id","recipient","payload_digest","accepted"}:
                raise ProtocolError("transport receipt must bind message, event, recipient and body digest")
            expected={"message_id":delivery_id,"event_id":row[6],"recipient":json.loads(row[7]),"payload_digest":row[8],"accepted":True}
            if receipt!=expected: raise StaleCommand("transport receipt does not match claimed delivery")
            saved=canonical_json(receipt)
            if row[2] is not None and row[2]!=saved: raise IdempotencyConflict("transport receipt changed")
            if row[3] is not None:
                if row[2] is None: db.execute("UPDATE deliveries SET transport_receipt=? WHERE id=?",(saved,delivery_id))
                db.commit(); return {"disposition":"HISTORICAL"}
            if row[4] is None: raise StaleCommand("delivery has no active lease")
            if row[2] is not None:
                db.commit(); return {"disposition":"REPLAYED"}
            db.execute("UPDATE deliveries SET state='TRANSPORT_ACCEPTED',transport_receipt=? WHERE id=?",(saved,delivery_id))
            db.commit()
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def acknowledge_delivery(self, ack, *, authenticated_session):
        ack=validate(ack,"feedback_ack")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT d.id,d.event_id,d.payload_digest,d.state,d.recipient_json,d.target_kind,e.run_id,e.generation,e.payload,e.payload_digest,d.protocol_ack FROM deliveries d JOIN events e ON e.id=d.event_id WHERE d.event_id=? AND d.target_kind='AUTHOR'",(ack["event_id"],)).fetchone()
            if row is None: raise StaleCommand("unknown delivery event")
            recipient=json.loads(row[4]); payload=strict_loads(row[8]); body=canonical_json(payload)
            if (row[2]!=hashlib.sha256(body.encode()).hexdigest() or row[9]!=row[2] or
                recipient.get("session_id")!=authenticated_session or recipient.get("session_id")!=ack["author_session_id"] or
                row[6]!=ack["run_id"] or row[7]!=ack["evaluation_generation"] or
                payload.get("result_id")!=ack["result_id"] or payload.get("result_digest")!=ack["result_digest"] or
                payload.get("event_id")!=ack["event_id"]):
                raise StaleCommand("protocol ACK event/result/recipient binding mismatch")
            if row[3]=="ACKNOWLEDGED":
                if row[10] != canonical_json(ack): raise IdempotencyConflict("delivery ACK changed content")
                db.commit(); return {"disposition":"REPLAYED","next_action":payload.get("next_action")}
            db.execute("UPDATE deliveries SET state='ACKNOWLEDGED',protocol_ack=?,claim_generation=claim_generation+1,lease_token=NULL,lease_until_ms=NULL WHERE id=?",(canonical_json(ack),row[0]))
            db.commit()
            return {"disposition":"ACKNOWLEDGED","next_action":payload.get("next_action")}
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def _record_incident_tx(self, db, incident, *, delivery_id=None):
        incident = validate(incident, "incident")
        iid = incident["incident_id"]
        run_id = incident.get("run_id")
        generation = incident.get("evaluation_generation")
        if (run_id is None) != (generation is None):
            raise ProtocolError("incident run and evaluation generation must be supplied together")
        if run_id is not None:
            run = db.execute("SELECT current_generation FROM runs WHERE id=?", (run_id,)).fetchone()
            if run is None or generation > run[0]:
                raise StaleCommand("incident run/generation is not registered")
            task_id = incident.get("task_id")
            if task_id is not None:
                task = db.execute("SELECT run_id,generation FROM tasks WHERE id=?", (task_id,)).fetchone()
                if task is None or tuple(task) != (run_id, generation):
                    raise ProtocolError("incident task context mismatch")
            self._require_artifacts(db, incident["artifact_refs"], run_id)
        elif incident["artifact_refs"]:
            raise ProtocolError("global incidents cannot reference run artifacts")
        if delivery_id is not None:
            row = db.execute("SELECT e.run_id,e.generation FROM deliveries d JOIN events e ON e.id=d.event_id WHERE d.id=?", (delivery_id,)).fetchone()
            if row is None: raise StaleCommand("unknown delivery")
            if row is None or (run_id, generation) != tuple(row):
                raise ProtocolError("incident context does not match linked delivery")
        body = canonical_json(incident)
        old = db.execute("SELECT incident_json,linked_delivery_id FROM incidents WHERE id=?", (iid,)).fetchone()
        if old:
            if old[0] != body or old[1] != delivery_id:
                raise IdempotencyConflict("incident ID reused with changed content")
            return {"disposition":"REPLAYED", "incident_id":iid}
        db.execute("INSERT INTO incidents(id,run_id,generation,incident_json,linked_delivery_id) VALUES(?,?,?,?,?)",
                   (iid,run_id,generation,body,delivery_id))
        self._incident_page_wake_tx(db, iid, "OPEN", run_id, generation)
        return {"disposition":"RECORDED", "incident_id":iid}

    @staticmethod
    def _incident_page_wake_tx(db, incident_id, action, run_id, generation):
        event_id="event_incident_"+hashlib.sha256(f"{incident_id}:{action}".encode()).hexdigest()[:32]
        event={"contract_version":1,"event_id":event_id,"kind":"INCIDENT_PAGE_PROJECTION",
               "incident_id":incident_id,"action":action}
        WorkflowStore._event(db,event,run_id,generation)
        db.execute("INSERT INTO outbox(event_id) VALUES(?)",(event_id,))

    def record_incident(self, incident):
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            result=self._record_incident_tx(db,incident)
            db.commit(); return result
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def record_delivery_incident(self, *, incident_id, delivery_id, incident):
        incident=validate(incident,"incident")
        if incident_id != incident["incident_id"]: raise ProtocolError("incident_id does not match incident body")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            result=self._record_incident_tx(db,incident,delivery_id=delivery_id)
            db.commit(); return result
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def mark_incident_seen(self, incident_id, *, now_ms=None):
        now_ms=int(time.time()*1000) if now_ms is None else now_ms
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            cur=db.execute("UPDATE incidents SET seen_ms=COALESCE(seen_ms,?) WHERE id=?",(now_ms,incident_id))
            if cur.rowcount!=1: raise StaleCommand("unknown incident")
            row=db.execute("SELECT run_id,generation,seen_ms FROM incidents WHERE id=?",(incident_id,)).fetchone()
            if row[2] == now_ms:
                # A first observation creates one durable page projection; retries
                # preserve the original timestamp and do not create another wake.
                existing=db.execute("SELECT 1 FROM events WHERE id=?",("event_incident_"+hashlib.sha256(f"{incident_id}:SEEN".encode()).hexdigest()[:32],)).fetchone()
                if not existing: self._incident_page_wake_tx(db,incident_id,"SEEN",row[0],row[1])
            db.commit()
            return self.get_incident(incident_id)
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def resolve_incident(self, incident_id, *, proof_ref, now_ms=None):
        if not proof_ref: raise ProtocolError("resolution proof required")
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row=db.execute("SELECT run_id,incident_json,resolved_ms,resolution_proof_ref FROM incidents WHERE id=?",(incident_id,)).fetchone()
            if row is None: raise StaleCommand("unknown incident")
            run_id, body, resolved_ms, old_proof=row
            if run_id is None: raise ProtocolError("global incident resolution proof is unsupported")
            if resolved_ms is not None:
                if old_proof != proof_ref: raise IdempotencyConflict("incident resolution proof changed")
                db.commit(); return self.get_incident(incident_id)
            self._require_artifacts(db,[proof_ref],run_id)
            incident=json.loads(body)
            if incident["state"] not in ("OPEN","RECOVERING"):
                raise StaleCommand("incident cannot be resolved from its current state")
            incident["state"]="RESOLVED"
            db.execute("UPDATE incidents SET incident_json=?,resolved_ms=?,resolution_proof_ref=? WHERE id=?",
                       (canonical_json(incident),int(time.time()*1000) if now_ms is None else now_ms,proof_ref,incident_id))
            self._incident_page_wake_tx(db,incident_id,"RESOLVED",run_id,json.loads(body).get("evaluation_generation"))
            db.commit(); return self.get_incident(incident_id)
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_incident(self, incident_id):
        db=self._connect()
        try:
            row=db.execute("SELECT incident_json,seen_ms,resolved_ms,resolution_proof_ref,linked_delivery_id FROM incidents WHERE id=?",(incident_id,)).fetchone()
            if row is None: return None
            return {"incident":json.loads(row[0]),"seen_ms":row[1],"resolved_ms":row[2],
                    "resolution_proof_ref":row[3],"delivery_id":row[4]}
        finally: db.close()

    def _verify_settled_feature_head_tx(self, db, run_id, generation, expected_sequence, worktree, terminal_kind):
        row=db.execute("SELECT r.current_generation,r.sequence,r.run_json,r.worktree,r.sprint,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id AND a.generation=r.current_generation WHERE r.id=?",(run_id,)).fetchone()
        if row is None or row[0]!=generation or row[1]!=expected_sequence: raise StaleCommand("attempt generation/sequence is stale")
        run=json.loads(row[2]); attempt=json.loads(row[5])
        head=db.execute("SELECT run_id FROM feature_heads WHERE worktree=? AND sprint=?",(worktree,run["sprint_id"])).fetchone()
        if row[3]!=worktree or run.get("run_kind")!="FEATURE_GATE" or head is None or head[0]!=run_id:
            raise StaleCommand("run is not the exact current feature head in this owner worktree")
        if terminal_kind=="ERROR":
            valid=attempt.get("phase")=="DECIDED" and attempt.get("result")=="ERROR" and attempt.get("cleanup_state") in ("CONFIRMED","NOT_REQUIRED","UNRESOLVED")
        else:
            valid=attempt.get("phase")=="CLOSED" and attempt.get("closure")=="CANCELLED" and attempt.get("result") is None and attempt.get("cleanup_state") in ("CONFIRMED","NOT_REQUIRED")
        if not valid: raise StaleCommand(f"only a settled terminal {terminal_kind} feature head can be retired")
        self._verify_settled_feature_cleanup_tx(db,run_id,terminal_kind)
        return run

    def _verify_settled_feature_cleanup_tx(self, db, run_id, terminal_kind, require_dispatch_settled=True):
        if db.execute("SELECT 1 FROM tasks WHERE run_id=? AND json_extract(task_json,'$.state') NOT IN ('COMPLETED','CANCELLED')",(run_id,)).fetchone():
            raise StaleCommand("run has unsettled child tasks")
        if db.execute("SELECT 1 FROM resources WHERE run_id=? AND state IN ('HELD','UNKNOWN')",(run_id,)).fetchone():
            raise StaleCommand("run retains a HELD or UNKNOWN resource")
        if db.execute("SELECT 1 FROM native_scopes n JOIN tasks t ON t.id=n.task_id WHERE t.run_id=? AND n.state!='CLEANED'",(run_id,)).fetchone():
            raise StaleCommand("run retains an uncleaned native scope")
        # A cancelled REVIEW wake must have the existing exact never-started
        # readiness proof; completed reviews must have released all resources.
        tasks=[dict(json.loads(x[0]),task_id=x[1]) for x in db.execute("SELECT task_json,id FROM tasks WHERE run_id=?",(run_id,)).fetchall()]
        for task in tasks:
            if terminal_kind=="CANCELLED" and task.get('kind')=='CHECK' and task.get('state')=='CANCELLED':
                cancel=db.execute("SELECT proof_json,confirmed FROM task_cancellations WHERE task_id=?",(task['task_id'],)).fetchone()
                scope=db.execute("SELECT assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,cleanup_artifact_ref,state FROM native_scopes WHERE task_id=?",(task['task_id'],)).fetchone()
                if cancel is None or type(cancel[1]) is not int or cancel[1]!=1:
                    raise StaleCommand("cancelled check lacks confirmed native cancellation proof")
                proof=json.loads(cancel[0]); expected={'task_id':task['task_id'],'run_id':task['run_id'],'generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
                if not isinstance(proof,dict) or any(proof.get(k)!=v for k,v in expected.items()):
                    raise StaleCommand("cancelled check proof does not bind its assignment")
                if proof.get('kind')=='DB_NEVER_STARTED':
                    evrow=db.execute("SELECT id,kind,payload,payload_digest,run_id,generation FROM events WHERE json_extract(payload,'$.task_id')=? AND kind='TASK_READY' ORDER BY created_ms DESC LIMIT 1",(task['task_id'],)).fetchone()
                    claim=db.execute("SELECT process_identity,claim_token FROM native_claims WHERE task_id=?",(task['task_id'],)).fetchone()
                    resources=[dict(state=x[0],resource_key=x[1]) for x in db.execute("SELECT state,resource_key FROM resources WHERE task_id=?",(task['task_id'],)).fetchall()]
                    if evrow is None: raise StaleCommand("never-started check has no saved readiness event")
                    import workflow_native_cancellation
                    event={'event_id':evrow[0],'kind':evrow[1],'payload':json.loads(evrow[2]),'payload_digest':evrow[3],'run_id':evrow[4],'generation':evrow[5]}
                    cancellation={'proof':proof,'confirmed':cancel[1]}
                    if not workflow_native_cancellation.cancelled_native_readiness_settled(task,event,cancellation,resources,claim,None if scope is None else {'state':scope[5]}):
                        raise StaleCommand("never-started native readiness is not settled")
                elif proof.get('kind')=='ADAPTER_CONFIRMED':
                    if not isinstance(proof.get('artifact_ref'),str) or proof.get('adapter_confirmation')!=dict(expected,artifact_ref=proof.get('artifact_ref')):
                        raise StaleCommand("native cleanup proof is not bound to its assignment")
                    if scope is None or scope[5]!='CLEANED' or scope[4]!=proof['artifact_ref']:
                        raise StaleCommand("cancelled check has no matching cleaned native scope")
                    import workflow_native_cancellation
                    scope_value={'assignment_generation':scope[0],'scope_name':scope[1],'worker_identity':json.loads(scope[2]),'creation_artifact_ref':scope[3],'cleanup_artifact_ref':scope[4],'state':scope[5]}
                    try: workflow_native_cancellation.NativeCancellation(self,Path('.'))._verify_artifact(task,scope_value,proof['artifact_ref'])
                    except (ProtocolError, OSError, ValueError, UnicodeDecodeError) as exc: raise StaleCommand("cancelled check native cleanup proof is invalid") from exc
                else: raise StaleCommand("cancelled check lacks recognized native cancellation proof")
            if task.get('kind')!='REVIEW': continue
            resources=[dict(state=x[0],resource_key=x[1]) for x in db.execute("SELECT state,resource_key FROM resources WHERE task_id=?",(task['task_id'],)).fetchall()]
            if any(r['state']!='RELEASED' for r in resources): raise StaleCommand("review resource cleanup is unresolved")
            if task.get('state')=='CANCELLED':
                evrow=db.execute("SELECT id,kind,payload,payload_digest,run_id,generation FROM events WHERE json_extract(payload,'$.task_id')=? AND kind='TASK_READY' ORDER BY created_ms DESC LIMIT 1",(task['task_id'],)).fetchone()
                cancel=db.execute("SELECT proof_json,confirmed FROM task_cancellations WHERE task_id=?",(task['task_id'],)).fetchone()
                if evrow is None or cancel is None: raise StaleCommand("cancelled review readiness is unproven")
                import workflow_review_cancellation
                ev={'event_id':evrow[0],'kind':evrow[1],'payload':json.loads(evrow[2]),'payload_digest':evrow[3],'run_id':evrow[4],'generation':evrow[5]}
                cancellation={'proof':json.loads(cancel[0]),'confirmed':cancel[1]}
                ready=workflow_review_cancellation.cancelled_review_readiness_settled(task,ev,cancellation,resources)
                proof=cancellation['proof']
                expected={'task_id':task['task_id'],'run_id':task['run_id'],'generation':task['evaluation_generation'],
                    'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
                adapter_ok=(type(cancel[1]) is int and cancel[1]==1 and isinstance(proof,dict) and
                    proof.get('kind')=='ADAPTER_CONFIRMED' and all(proof.get(k)==v for k,v in expected.items()) and
                    isinstance(proof.get('artifact_ref'),str) and
                    proof.get('adapter_confirmation')==dict(expected,artifact_ref=proof.get('artifact_ref')))
                if adapter_ok:
                    try:
                        self._require_artifacts(db,[proof['artifact_ref']],run_id,input_digest=task['input_digest'])
                        artrow=db.execute("SELECT artifact_json FROM artifacts WHERE id=?",(proof['artifact_ref'],)).fetchone()
                        artifact=json.loads(artrow[0])['artifact']
                        adapter_ok=(artifact.get('producer_kind')=='TASK' and artifact.get('producer_id')==task['task_id'] and
                            artifact.get('producer_task_id')==task['task_id'] and artifact.get('run_id')==run_id and
                            artifact.get('type')=='PROVENANCE' and artifact.get('immutable') is True and
                            type(artifact.get('size_bytes')) is int and artifact['size_bytes']>=0 and
                            isinstance(artifact.get('sha256'),str) and len(artifact['sha256'])==64)
                        if adapter_ok:
                            raw=Path(artifact['path']).read_bytes()
                            evidence=json.loads(raw.decode('utf-8'))
                            adapter_ok=(len(raw)==artifact['size_bytes'] and hashlib.sha256(raw).hexdigest()==artifact['sha256'] and
                                isinstance(evidence,dict) and
                                evidence.get('purpose')=='REVIEW_CLEANUP' and evidence.get('task_id')==task['task_id'] and
                                evidence.get('run_id')==task['run_id'] and evidence.get('generation')==task['evaluation_generation'] and
                                evidence.get('assignment_generation')==task['assignment_generation'] and evidence.get('input_digest')==task['input_digest'] and
                                isinstance(task.get('assigned_session_id'),str) and evidence.get('session_id')==task['assigned_session_id'] and
                                evidence.get('inactive_confirmed') is True and evidence.get('inbox_empty') is True and
                                evidence.get('message_absent') is True and isinstance(evidence.get('history_guard'),dict) and
                                evidence['history_guard'].get('current_identity_matches') is True and
                                evidence['history_guard'].get('no_foreign_postbaseline_user') is True and
                                evidence['history_guard'].get('session_id')==task['assigned_session_id'])
                    except (ProtocolError, TypeError, KeyError, ValueError, OSError, UnicodeDecodeError):
                        adapter_ok=False
                if not ready and not adapter_ok: raise StaleCommand("cancelled review readiness is unproven")
        if require_dispatch_settled:
            outstanding=db.execute("SELECT 1 FROM events e LEFT JOIN outbox o ON o.event_id=e.id LEFT JOIN deliveries d ON d.event_id=e.id WHERE e.run_id=? AND e.kind IN ('TASK_READY','START_REVIEW','CANCEL_TASK') AND ((o.event_id IS NOT NULL AND o.state!='ACKNOWLEDGED') OR (d.id IS NOT NULL AND (d.state NOT IN ('ACKNOWLEDGED','COMPLETED') OR d.protocol_ack IS NULL))) LIMIT 1",(run_id,)).fetchone()
            if outstanding: raise StaleCommand("run has an unsettled task/review dispatch")

    def _verify_settled_error_head_tx(self, db, run_id, generation, expected_sequence, worktree):
        return self._verify_settled_feature_head_tx(db,run_id,generation,expected_sequence,worktree,"ERROR")

    def _verify_settled_cancelled_head_tx(self, db, run_id, generation, expected_sequence, worktree):
        return self._verify_settled_feature_head_tx(db,run_id,generation,expected_sequence,worktree,"CANCELLED")

    def verify_settled_error_head(self, *, run_id, generation, expected_sequence, worktree):
        """Read-only verification of the exact settled terminal ERROR feature head."""
        db=self._connect()
        try:
            db.execute("BEGIN")
            self._verify_settled_error_head_tx(db,run_id,generation,expected_sequence,worktree)
            return {"run_id":run_id,"generation":generation,"expected_sequence":expected_sequence,"worktree":worktree}
        finally: db.close()

    def retire_settled_error_head(self, *, run_id, generation, expected_sequence, caller, command_id, worktree):
        """Owner-only retirement of the exact settled terminal ERROR feature head.

        This is deliberately not an attempt transition: historical result, task
        records, and result deliveries remain immutable.
        """
        return self._retire_settled_head(run_id=run_id,generation=generation,expected_sequence=expected_sequence,
            caller=caller,command_id=command_id,worktree=worktree,terminal_kind="ERROR")

    def retire_settled_cancelled_head(self, *, run_id, generation, expected_sequence, caller, command_id, worktree):
        """Owner-only retirement of a historically closed, settled cancellation."""
        return self._retire_settled_head(run_id=run_id,generation=generation,expected_sequence=expected_sequence,
            caller=caller,command_id=command_id,worktree=worktree,terminal_kind="CANCELLED")

    def _retire_settled_head(self, *, run_id, generation, expected_sequence, caller, command_id, worktree, terminal_kind):
        request={"run_id":run_id,"generation":generation,"expected_sequence":expected_sequence,"worktree":worktree}
        request_digest=digest(request)
        operation="retire_error_head" if terminal_kind=="ERROR" else "retire_cancelled_head"
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            prior=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation=? AND caller=? AND command_key=?",(operation,caller,command_id)).fetchone()
            if prior:
                if prior[0]!=request_digest: raise IdempotencyConflict("retirement command key reused with changed request")
                db.commit(); return json.loads(prior[1])
            verify=self._verify_settled_error_head_tx if terminal_kind=="ERROR" else self._verify_settled_cancelled_head_tx
            run=verify(db,run_id,generation,expected_sequence,worktree)
            prefix="error" if terminal_kind=="ERROR" else "cancelled"
            event_id='event_retire_'+prefix+'_'+hashlib.sha256((caller+'\0'+command_id).encode()).hexdigest()[:32]
            event={'contract_version':1,'event_id':event_id,'kind':'OWNER_'+terminal_kind+'_HEAD_RETIRED','run_id':run_id,'evaluation_generation':generation}
            if terminal_kind=="ERROR": event.update(reason='CANCELLED',historical_result='ERROR')
            else: event['closure']='CANCELLED'
            self._event(db,event,run_id,generation)
            changed=db.execute("DELETE FROM feature_heads WHERE worktree=? AND sprint=? AND run_id=?",(worktree,run['sprint_id'],run_id)).rowcount
            if changed!=1: raise StaleCommand("feature head changed during retirement")
            receipt={'event_id':event_id,'state':'RETIRED_HEAD','run_id':run_id,'evaluation_generation':generation}
            receipt.update({'historical_result':'ERROR'} if terminal_kind=="ERROR" else {'historical_closure':'CANCELLED','historical_result':None})
            db.execute("INSERT INTO command_receipts(operation,caller,command_key,request_digest,response_json) VALUES(?,?,?,?,?)",(operation,caller,command_id,request_digest,canonical_json(receipt)))
            db.commit(); return receipt
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def authorize_author_successor(self, *, run_id, generation, expected_sequence, caller,
                                   command_id, worktree, predecessor_session_id,
                                   successor_session_id):
        """Persist an owner-issued, narrowly scoped successor continuity grant.

        The grant records authority to continue a run, not authorship of its
        immutable historical result.  Identity verification and owner
        authentication are intentionally the caller's responsibility.
        """
        request={"run_id":run_id,"evaluation_generation":generation,
                 "expected_sequence":expected_sequence,"worktree":worktree,
                 "predecessor_session_id":predecessor_session_id,
                 "successor_session_id":successor_session_id}
        request_digest=digest(request)
        db=self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            prior=db.execute("SELECT request_digest,response_json FROM command_receipts WHERE operation='authorize_author_successor' AND caller=? AND command_key=?",(caller,command_id)).fetchone()
            if prior:
                if prior[0]!=request_digest: raise IdempotencyConflict("successor command key reused with changed request")
                db.commit(); return json.loads(prior[1])
            row=db.execute("SELECT r.current_generation,r.sequence,r.run_json,r.worktree,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id AND a.generation=r.current_generation WHERE r.id=?",(run_id,)).fetchone()
            if row is None or row[0]!=generation or row[1]!=expected_sequence:
                raise StaleCommand("attempt generation/sequence is stale")
            run=json.loads(row[2]); attempt=json.loads(row[4])
            if row[3]!=worktree or run.get('run_kind')!='FEATURE_GATE':
                raise PermissionError("run is outside owner worktree")
            author=run.get('author') or {}
            if author.get('worktree')!=worktree or author.get('session_id')!=predecessor_session_id:
                raise StaleCommand("predecessor does not match original run author")
            if predecessor_session_id==successor_session_id:
                raise StaleCommand("successor must differ from predecessor")
            if attempt.get('phase') not in ('DECIDED','CLOSED'):
                raise StaleCommand("only terminal runs can authorize a successor")
            event_id='event_successor_'+hashlib.sha256((caller+'\\0'+command_id).encode()).hexdigest()[:32]
            event={'contract_version':1,'event_id':event_id,'kind':'OWNER_AUTHOR_SUCCESSOR_AUTHORIZED',
                   'run_id':run_id,'evaluation_generation':generation,
                   'predecessor_session_id':predecessor_session_id,
                   'successor_session_id':successor_session_id,'worktree':worktree}
            self._event(db,event,run_id,generation)
            receipt={'run_id':run_id,'evaluation_generation':generation,'worktree':worktree,'predecessor_session_id':predecessor_session_id,
                     'successor_session_id':successor_session_id,'event_id':event_id}
            db.execute("INSERT INTO command_receipts(operation,caller,command_key,request_digest,response_json) VALUES('authorize_author_successor',?,?,?,?)",(caller,command_id,request_digest,canonical_json(receipt)))
            db.commit(); return receipt
        except BaseException:
            if db.in_transaction: db.rollback()
            raise
        finally: db.close()

    def get_author_successor_grant(self, *, run_id, successor_session_id):
        """Look up only a grant still valid for the run's current terminal generation."""
        db=self._connect()
        try:
            rows=db.execute("SELECT response_json FROM command_receipts WHERE operation='authorize_author_successor' AND json_extract(response_json,'$.run_id')=? AND json_extract(response_json,'$.successor_session_id')=? ORDER BY rowid DESC",(run_id,successor_session_id)).fetchall()
            for row in rows:
                grant=json.loads(row[0])
                valid=self._valid_author_successor_grant_tx(db,run_id=run_id,worktree=grant.get('worktree'),predecessor_session_id=grant.get('predecessor_session_id'),successor_session_id=successor_session_id,generation=grant.get('evaluation_generation'))
                if valid: return valid
            return None
        finally: db.close()

    @staticmethod
    def _valid_author_successor_grant_tx(db, *, run_id, worktree, predecessor_session_id, successor_session_id, generation):
        if not isinstance(generation,int) or isinstance(generation,bool): return None
        rows=db.execute("SELECT response_json FROM command_receipts WHERE operation='authorize_author_successor' AND json_extract(response_json,'$.run_id')=? AND json_extract(response_json,'$.evaluation_generation')=? AND json_extract(response_json,'$.worktree')=? AND json_extract(response_json,'$.predecessor_session_id')=? AND json_extract(response_json,'$.successor_session_id')=? ORDER BY rowid DESC",(run_id,generation,worktree,predecessor_session_id,successor_session_id)).fetchall()
        if not rows: return None
        row=db.execute("SELECT r.current_generation,r.worktree,r.run_json,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id AND a.generation=r.current_generation WHERE r.id=?",(run_id,)).fetchone()
        if row is None or row[0]!=generation or row[1]!=worktree: return None
        run=json.loads(row[2]); attempt=json.loads(row[3]); author=run.get('author') or {}
        if author.get('session_id')!=predecessor_session_id or author.get('worktree')!=worktree: return None
        if attempt.get('phase') not in ('DECIDED','CLOSED'): return None
        return json.loads(rows[0][0])

    def list_author_successor_grants(self, *, successor_session_id, worktree):
        db=self._connect()
        try:
            candidates=db.execute("SELECT DISTINCT json_extract(response_json,'$.run_id') FROM command_receipts WHERE operation='authorize_author_successor' AND json_extract(response_json,'$.successor_session_id')=? AND json_extract(response_json,'$.worktree')=? ORDER BY 1",(successor_session_id,worktree)).fetchall()
            grants=[]
            for (run_id,) in candidates:
                grant=self.get_author_successor_grant(run_id=run_id,successor_session_id=successor_session_id)
                if grant and grant.get('worktree')==worktree: grants.append(grant)
            return grants
        finally: db.close()

    def is_current_feature_head(self, run_id):
        db=self._connect()
        try:
            return db.execute("SELECT 1 FROM runs r JOIN feature_heads h ON h.worktree=r.worktree AND h.sprint=r.sprint AND h.run_id=r.id WHERE r.id=?",(run_id,)).fetchone() is not None
        finally: db.close()

    def is_settled_error_head_retired(self, run_id):
        db=self._connect()
        try:
            return db.execute("SELECT 1 FROM events WHERE run_id=? AND kind='OWNER_ERROR_HEAD_RETIRED' LIMIT 1",(run_id,)).fetchone() is not None
        finally: db.close()

    @staticmethod
    def _insert_resource_tx(db, *, resource_key, run_id, generation, task_id, assignment_generation, identity_proof):
        request_digest=digest({'resource_key':resource_key,'run_id':run_id,'generation':generation,
            'task_id':task_id,'assignment_generation':assignment_generation,'identity_proof':identity_proof})
        claim_id='resource_'+hashlib.sha256((resource_key+'\0'+request_digest).encode()).hexdigest()[:32]
        try:
            db.execute("INSERT INTO resources(claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof,request_digest) VALUES(?,?,?,?,?,?,?,'HELD',NULL,?)",
                (claim_id,resource_key,run_id,generation,task_id,assignment_generation,canonical_json(identity_proof),request_digest))
        except sqlite3.IntegrityError as exc:
            raise StoreError('resource held; automatic stealing forbidden') from exc
        return claim_id

    def get_task_incident(self, task_id):
        """Return the first saved incident bound to this task's exact run generation."""
        task = self.get_task(task_id)
        if task is None: return None
        db=self._connect()
        try:
            row=db.execute("SELECT incident_json FROM incidents WHERE run_id=? AND generation=? AND json_extract(incident_json,'$.task_id')=? ORDER BY id LIMIT 1",
                (task['run_id'],task['evaluation_generation'],task_id)).fetchone()
            return None if row is None else json.loads(row[0])
        finally: db.close()
