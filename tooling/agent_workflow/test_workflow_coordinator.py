"""Focused lifecycle tests for the inactive coordinator boundary."""
import tempfile
import unittest
import json
import subprocess
import sys
from unittest import mock
from pathlib import Path
from types import SimpleNamespace

import workflow_coordinator
import workflow_store
import workflow_scope
from preflight_test_support import make_preflight_case


class FakeStore:
    def __init__(self, path): self.path = path; self.claims = []; self.acks = []
    def claim_outbox(self, **kwargs): return self.claims.pop(0) if self.claims else None
    def acknowledge_outbox(self, event, generation): self.acks.append((event, generation))
    def get_event(self, event): return {"created_ms":0}


class Handler:
    def __init__(self): self.calls = []; self.seen = "ABSENT"
    def observe(self, event, payload): return self.seen
    def dispatch(self, event, payload): self.calls.append(event)


class CoordinatorTests(unittest.TestCase):
    def verifier(self, context): return True

    def test_continuous_outbox_returns_to_health_and_reconciliation(self):
        with tempfile.TemporaryDirectory() as d:
            store = FakeStore(Path(d) / "state.db")
            health = mock.Mock()
            recovery = mock.Mock()
            health.snapshot.return_value = {"state": "CLOSED"}
            recovery.reconcile.return_value = {"unknown": []}
            c = workflow_coordinator.ExplicitCoordinator(store, bindings={"secret": object()},
                artifact_dir=d, identity_verifier=self.verifier, transport_health=health,
                native_recovery=recovery, reconcile_interval=.05)
            calls = []
            clock = [0.0]
            store.claim_outbox = lambda **kwargs: {"event_id": "e", "claim_generation": 1,
                "payload": {"kind": "TASK_READY"}}
            def deliver(claim):
                calls.append(claim)
                clock[0] += .06  # deterministic handler duration; no real sleeping
            def wait(_timeout):
                if len(calls) >= 3:
                    c._stop.set()
            c._wake.wait = wait
            c._deliver = deliver
            with mock.patch.object(workflow_coordinator.time, "monotonic", side_effect=lambda: clock[0]):
                c._run()
            self.assertEqual(len(calls), 3)
            self.assertEqual(health.tick.call_count, 3)
            self.assertEqual(recovery.reconcile.call_count, 3)
            self.assertIsNone(c.last_error)

    def test_maintenance_hold_skips_startup_reconciliation_and_runtime_work(self):
        with tempfile.TemporaryDirectory() as d:
            store = FakeStore(Path(d) / "state.db")
            import admission
            hold = admission.begin_hold(Path(d) / "admission", owner="workflow-stalled-repair",
                control_id="fixture-control", note="temporary coordinator test hold")
            recovery = mock.Mock(); quiescence = mock.Mock()
            health = mock.Mock()
            c = workflow_coordinator.ExplicitCoordinator(store, bindings={"secret": object()},
                artifact_dir=d, identity_verifier=self.verifier, admission_root=Path(d) / "admission",
                native_recovery=recovery, review_quiescence=quiescence,
                transport_health=health, reconcile_interval=.01)
            try:
                c.start()
                import time
                time.sleep(.05)
                self.assertEqual(store.claims, [])
                recovery.reconcile.assert_not_called(); quiescence.reconcile.assert_not_called()
                health.tick.assert_not_called()
            finally:
                c.stop()
                admission.resume_hold(Path(d) / "admission", hold)
    def test_pending_handler_disposition_is_deferred_without_incident_or_error(self):
        with tempfile.TemporaryDirectory() as d:
            store=FakeStore(Path(d)/"state.db"); handler=Handler()
            handler.dispatch=lambda event,payload:"PENDING"
            c=workflow_coordinator.ExplicitCoordinator(store,bindings={"secret":object()},artifact_dir=d,handlers={"TASK_READY":handler},identity_verifier=self.verifier)
            c._deliver({"event_id":"e","claim_generation":1,"payload":{"kind":"TASK_READY"}})
            self.assertEqual(store.acks, [])
            self.assertIsNone(c.last_error)

    def test_constructor_is_inert_and_thread_dispatches_after_explicit_start(self):
        with tempfile.TemporaryDirectory() as d:
            store = FakeStore(Path(d) / "state.db"); handler = Handler()
            store.claims.append({"event_id":"event-1", "claim_generation":1, "payload":{"kind":"TASK_READY"}})
            c = workflow_coordinator.ExplicitCoordinator(store, bindings={"secret":object()}, artifact_dir=Path(d)/"artifacts", handlers={"TASK_READY":handler}, identity_verifier=self.verifier, reconcile_interval=.05)
            self.assertIsNone(c._thread)
            c.start()
            try:
                import time
                deadline=time.monotonic()+2
                while not store.acks and time.monotonic()<deadline: time.sleep(.01)
                self.assertEqual(handler.calls, ["event-1"])
                self.assertEqual(store.acks, [("event-1",1)])
            finally: c.stop()

    def test_reclaimed_claim_observes_applied_effect_before_ack(self):
        with tempfile.TemporaryDirectory() as d:
            store=FakeStore(Path(d)/"state.db"); h=Handler(); h.seen="APPLIED"
            c=workflow_coordinator.ExplicitCoordinator(store,bindings={"secret":object()},artifact_dir=d,handlers={"TASK_READY":h},identity_verifier=self.verifier)
            c._deliver({"event_id":"e","claim_generation":2,"payload":{"kind":"TASK_READY"}})
            self.assertEqual(h.calls, [])
            self.assertEqual(store.acks, [("e",2)])

    def test_unknown_reclaimed_effect_is_not_dispatched_or_acknowledged(self):
        with tempfile.TemporaryDirectory() as d:
            store=FakeStore(Path(d)/"state.db"); h=Handler(); h.seen="UNKNOWN"
            c=workflow_coordinator.ExplicitCoordinator(store,bindings={"secret":object()},artifact_dir=d,handlers={"TASK_READY":h},identity_verifier=self.verifier)
            c._deliver({"event_id":"e","claim_generation":2,"payload":{"kind":"TASK_READY"}})
            self.assertEqual(h.calls, [])
            self.assertEqual(store.acks, [])
            self.assertIn("unknown", c.last_error)

    def test_same_process_database_guard(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'db'; a=workflow_coordinator.ExplicitCoordinator(FakeStore(path),bindings={'a':object()},artifact_dir=d,identity_verifier=self.verifier)
            b=workflow_coordinator.ExplicitCoordinator(FakeStore(path),bindings={'b':object()},artifact_dir=d,identity_verifier=self.verifier)
            a.start()
            try:
                with self.assertRaises(BlockingIOError): b.start()
            finally: a.stop()

    def test_thread_start_failure_releases_database_guard(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'db'; c=workflow_coordinator.ExplicitCoordinator(FakeStore(path),bindings={'a':object()},artifact_dir=d,identity_verifier=self.verifier)
            with mock.patch('threading.Thread.start',side_effect=RuntimeError('thread startup')):
                with self.assertRaises(RuntimeError): c.start()
            replacement=workflow_coordinator.ExplicitCoordinator(FakeStore(path),bindings={'a':object()},artifact_dir=d,identity_verifier=self.verifier)
            replacement.start(); replacement.stop()

    def test_opencode_verifier_rejects_wrong_session_worktree_agent_and_mode(self):
        with tempfile.TemporaryDirectory() as d:
            context=SimpleNamespace(authenticated_author={'session_id':'s','worktree':d,'role':'author'},repo=Path(d))
            session={'id':'s','location':{'directory':d},'agent':'orchestrate-test'}
            role={'mode':'primary'}
            verifier=workflow_coordinator.OpenCodeIdentityVerifier('local',session_lookup=lambda server,sid:session,role_lookup=lambda server,agent,directory:role)
            self.assertTrue(verifier(context))
            for mutate in (lambda:session.update(id='wrong'),lambda:session.update(id='s',location={'directory':''}),lambda:session.update(location={'directory':str(Path(d).parent)},agent='orchestrate-test'),lambda:session.update(location=None),lambda:session.update(location={'directory':d},agent='integration-lead'),lambda:role.update(mode='secondary')):
                mutate(); self.assertFalse(verifier(context))
                session.update(id='s',location={'directory':d},agent='orchestrate-test'); role.update(mode='primary')

    def test_missing_handler_retains_claim(self):
        with tempfile.TemporaryDirectory() as d:
            store=FakeStore(Path(d)/"state.db")
            c=workflow_coordinator.ExplicitCoordinator(store,bindings={"secret":object()},artifact_dir=d,identity_verifier=self.verifier)
            c._deliver({"event_id":"e","claim_generation":1,"payload":{"kind":"TASK_READY"}})
            self.assertEqual(store.acks, [])
            self.assertIn("unavailable", c.last_error)


class CoordinatorHTTPTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)
        self.case=make_preflight_case(self.root, caller='caller', key='key')
        self.store=workflow_store.WorkflowStore(self.root/'state.sqlite')
        scope_payload={'contract_version':1,'policy':self.case.policy,'artifacts':self.case.context.artifacts,'submission_idempotency_key':'key'}
        workflow_scope.register_author_scope(self.store,self.case.context,'caller',scope_payload,'registration-key',artifact_dir=self.root/'archive')
        self.calls=[]; self.effects=set(); self.raise_after_effect=False
        class Dispatch:
            def __init__(inner, outer): inner.outer=outer
            def observe(inner, event, payload): return 'APPLIED' if event in inner.outer.effects else 'ABSENT'
            def dispatch(inner, event, payload):
                if event not in inner.outer.effects:
                    inner.outer.effects.add(event); inner.outer.calls.append(event)
                assert inner.outer.store.get_submission('caller','key')['status']=='ACCEPTED'
                if inner.outer.raise_after_effect:
                    inner.outer.raise_after_effect=False
                    raise RuntimeError('lost response after effect')
        binding=workflow_coordinator.CoordinatorBinding('caller', self.case.context)
        self.coordinator=workflow_coordinator.ExplicitCoordinator(self.store,bindings={'secret':binding},artifact_dir=self.root/'artifacts',handlers={'TASK_READY':Dispatch(self)},identity_verifier=lambda ctx: True,reconcile_interval=5)
        from starlette.testclient import TestClient
        self.client=TestClient(self.coordinator.app()); self.client.__enter__()
        self.config=self.root/'coordinator.json'; self.token=self.root/'token'; self.context_file=self.root/'context.json'
        self.token.write_text('secret')
        self.context_file.write_text(json.dumps({'repo':str(self.case.context.repo),'authenticated_author':self.case.context.authenticated_author,'policy':self.case.context.policy,'artifacts':[dict(self.case.artifact)],'publication_receipt_path':str(self.case.receipt),'publication_receipt_sha256':self.case.context.publication_receipt_sha256}))
        self.config.write_text(json.dumps({'database':str(self.root/'process.sqlite'),'artifact_dir':str(self.root/'process-artifacts'),'opencode_server':'http://127.0.0.1:4096','bindings':[{'token_file':str(self.token),'caller':'process-caller','context_file':str(self.context_file)}]}))
    def tearDown(self):
        self.client.__exit__(None,None,None); self.tmp.cleanup()
    def post(self, token='secret', key='key', request=None):
        return self.client.post('/v1/submit',headers={'Authorization':'Bearer '+token},json={'idempotency_key':key,'request':request or self.case.request})
    def test_real_preflight_admits_committed_before_woken_handler(self):
        response=self.post(); self.assertEqual(response.status_code,200); receipt=response.json()['response']; self.assertEqual(receipt['status'],'ACCEPTED')
        import time
        deadline=time.monotonic()+3
        while not self.calls and time.monotonic()<deadline: time.sleep(.01)
        self.assertEqual(len(self.calls),1)
        samples=[s for s in self.coordinator.latency_samples() if s.get('event_created_to_handler_start_ms') is not None]
        if samples:
            out=self.root/'coordinator-latency-sample.json'
            out.write_text(__import__('json').dumps({"sample_count":len(samples),"samples":samples[-32:],"scope":"local fake handler start; excludes model startup/native queue wait"},indent=2))
    def test_retry_and_status_replay_original_saved_run(self):
        first=self.post().json()['response']; second=self.post().json()['response']
        self.assertEqual(first,second); self.assertEqual(first['status'],'ACCEPTED')
        status=self.client.get('/v1/status?idempotency_key=key',headers={'Authorization':'Bearer secret'}).json()
        self.assertIn('submission', status, status)
        self.assertEqual(status['submission'],first); self.assertEqual(status['run']['run_id'],first['run_id'])
        import time
        deadline=time.monotonic()+3
        while not self.calls and time.monotonic()<deadline: time.sleep(.01)
        self.assertEqual(len(self.calls),1)
    def test_wrong_token_and_wrong_identity_do_not_admit(self):
        self.assertEqual(self.post(token='wrong').status_code,403)
        self.coordinator.identity_verifier=lambda ctx: False
        self.assertEqual(self.post(key='denied').status_code,503)
        self.assertIsNone(self.store.get_submission('caller','denied'))
    def test_status_works_when_identity_provider_unavailable(self):
        self.coordinator.identity_verifier=lambda ctx: (_ for _ in ()).throw(RuntimeError('offline'))
        response=self.client.get('/v1/status',headers={'Authorization':'Bearer secret'})
        self.assertEqual(response.status_code,200); self.assertEqual(response.json()['freshness'],'not_checked')

    def test_diagnostics_are_opt_in_owned_and_current_check_logs_only(self):
        import hashlib
        import workflow_task_artifacts
        submitted=self.post().json()['response']; run=self.store.get_run(submitted['run_id'])['run']
        raw=b'x'*9000+b'\nAuthorization: Bearer sentinel-secret\ncompiler error: useful failure\n'
        sha=hashlib.sha256(raw).hexdigest(); task_id='task_fixture'
        artifact={'artifact_id':'artifact-fixture','run_id':run['run_id'],'producer_task_id':task_id,
            'producer_id':task_id,'producer_kind':'TASK','type':'LOG','path':str(self.root/'artifacts'/'immutable'/task_id/('log-'+sha)),
            'sha256':sha,'size_bytes':len(raw),'immutable':True}
        path=Path(artifact['path']); path.parent.mkdir(parents=True); path.write_bytes(raw)
        task={'task_id':task_id,'kind':'CHECK','state':'COMPLETED','result':{'decision':'FAIL','findings':[{'code':'COMPILER_ERROR','message':'compiler token=sentinel-secret'}],'artifact_refs':['artifact-fixture']},'error_report':None}
        original_tasks=self.store.list_tasks; original_artifact=self.store.get_artifact
        self.store.list_tasks=lambda rid,generation: [task] if rid==run['run_id'] and generation==run['current_attempt'] else []
        self.store.get_artifact=lambda ref: {'artifact':artifact,'input_digest':run['input_digest']} if ref=='artifact-fixture' else None
        try:
            with mock.patch.object(workflow_task_artifacts,'read_log_excerpt',wraps=workflow_task_artifacts.read_log_excerpt) as reader:
                ordinary=self.client.get('/v1/status?run_id='+run['run_id'],headers={'Authorization':'Bearer secret'}).json()
                self.assertIn('diagnostics_hint',ordinary); self.assertNotIn('diagnostics',ordinary); reader.assert_not_called()
                denied=self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true',headers={'Authorization':'Bearer wrong'})
                self.assertEqual(denied.status_code,403); reader.assert_not_called()
                foreign=dict(run,author={'session_id':'other','worktree':str(self.root/'foreign')})
                with mock.patch.object(self.store,'get_run',return_value={'run':foreign,'attempt':{}}):
                    denied=self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true',headers={'Authorization':'Bearer secret'})
                self.assertEqual(denied.status_code,403); reader.assert_not_called()
            status=self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true',headers={'Authorization':'Bearer secret'}).json()
            log=status['diagnostics']['logs'][0]
            self.assertEqual(status['diagnostics']['checks'][0]['decision'],'FAIL')
            self.assertTrue(log['truncated']); self.assertNotIn('sentinel-secret',log['text'])
            self.assertIn('useful failure',log['text']); self.assertEqual(log['size_bytes'],len(raw))
            self.assertNotIn('sentinel-secret',json.dumps(status['diagnostics']))
            self.assertLess(len(json.dumps(status).encode('utf-8')),64*1024)
            artifact_id=log['artifact_id']
            middle=self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true&log_artifact_id='+artifact_id+'&log_offset=8192',headers={'Authorization':'Bearer secret'}).json()['diagnostics']['logs'][0]
            self.assertEqual(middle['offset'],8192); self.assertEqual(middle['next_offset'],None)
            self.assertIn('useful failure',middle['text']); self.assertNotIn('sentinel-secret',middle['text'])
            self.assertEqual(self.client.get('/v1/status?run_id='+run['run_id']+'&log_offset=0',headers={'Authorization':'Bearer secret'}).status_code,400)
            self.assertEqual(self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true&log_offset=0',headers={'Authorization':'Bearer secret'}).status_code,400)
            huge_offset=self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true&log_artifact_id='+artifact_id+'&log_offset='+'9'*5000,headers={'Authorization':'Bearer secret'})
            self.assertEqual(huge_offset.status_code,400); self.assertEqual(huge_offset.json()['error']['code'],'INVALID_QUERY')
            foreign_log=self.client.get('/v1/status?run_id='+run['run_id']+'&include_diagnostics=true&log_artifact_id=artifact-foreign',headers={'Authorization':'Bearer secret'}).json()['diagnostics']
            self.assertEqual(foreign_log['logs'],[]); self.assertEqual(foreign_log['log_error'],'artifact_not_available')
            self.assertNotIn('path',json.dumps(foreign_log))
            selected=self.client.get('/v1/status?idempotency_key=key&include_diagnostics=true',headers={'Authorization':'Bearer secret'}).json()
            self.assertEqual(selected['run']['run_id'],run['run_id']); self.assertEqual(selected['diagnostics']['checks'][0]['decision'],'FAIL')
            self.assertEqual(self.client.get('/v1/status?include_diagnostics=true',headers={'Authorization':'Bearer secret'}).status_code,400)
            self.assertEqual(self.client.get('/v1/status?run_id=x&include_diagnostics=yes',headers={'Authorization':'Bearer secret'}).status_code,400)
            self.assertEqual(self.client.get('/v1/status?run_id=x&run_id=y',headers={'Authorization':'Bearer secret'}).status_code,400)
        finally:
            self.store.list_tasks=original_tasks; self.store.get_artifact=original_artifact

    def test_error_diagnostics_use_exact_saved_task_incident_and_redact_text(self):
        receipt=self.post().json()['response']; run=self.store.get_run(receipt['run_id'])['run']
        task=self.store.list_tasks(run['run_id'],run['current_attempt'])[0]
        incident={'contract_version':1,'incident_id':'incident_diagnostic_fixture','run_id':run['run_id'],
            'evaluation_generation':run['current_attempt'],'task_id':task['task_id'],'code':'VALIDATOR_CRASH',
            'message':'validator token=secret failed','recovery_owner':'SYSTEM','state':'OPEN',
            'owner_action_required':False,'artifact_refs':[]}
        self.store.record_incident(incident)
        original=self.store.list_tasks
        completed=dict(task,state='COMPLETED',result={'decision':'ERROR','findings':[],'artifact_refs':[]})
        self.store.list_tasks=lambda rid,generation:[completed] if rid==run['run_id'] else []
        try:
            diagnostics=self.coordinator._author_diagnostics(run)
            report=diagnostics['checks'][0]['error_report']
            self.assertEqual(report['code'],'VALIDATOR_CRASH')
            self.assertNotIn('secret',report['message'])
            self.assertIn('[REDACTED]',report['message'])
        finally: self.store.list_tasks=original

    def test_duplicate_outer_json_and_changed_payload_conflict(self):
        bad=self.client.post('/v1/submit',headers={'Authorization':'Bearer secret'},content='{"idempotency_key":"key","idempotency_key":"x","request":{}}')
        self.assertEqual(bad.status_code,400)
        self.assertEqual(self.post().status_code,200)
        changed=dict(self.case.request,feature_label='different')
        self.assertEqual(self.post(request=changed).status_code,409)

    def test_startup_reconcile_and_ambiguous_effect_restart(self):
        # Admission with no coordinator is durably committed in the actual WR03 facade.
        self.client.__exit__(None,None,None)
        self.raise_after_effect=True
        response=self.post(); self.assertEqual(response.status_code,200)
        claim=self.store.claim_outbox(lease_ms=1)
        self.assertIsNotNone(claim)
        self.coordinator.handlers[claim['payload']['kind']]=self.coordinator.handlers['TASK_READY']
        self.coordinator._deliver(claim)
        self.assertIsNotNone(self.coordinator.last_error, claim)
        # Expire only the claim lease through its public claim API, then restart.
        self.coordinator.reconcile_interval=.05
        self.coordinator.start()
        import time
        deadline=time.monotonic()+3
        while self.store.outbox_summary().get('CLAIMED',0) and time.monotonic()<deadline: time.sleep(.01)
        self.coordinator.stop()
        self.assertEqual(len(self.calls),1)
        self.assertEqual(self.store.outbox_summary().get('ACKNOWLEDGED',0),2) # task dispatch + incident page projection

    def test_configured_os_guard_blocks_child_process_then_releases(self):
        parent=workflow_coordinator.load_coordinator(self.config); parent.start()
        code="import workflow_coordinator as w,sys; c=w.load_coordinator(sys.argv[1]); c.start(); c.stop()"
        try:
            failed=subprocess.run([sys.executable,'-c',code,str(self.config)],capture_output=True,text=True,timeout=10)
            self.assertNotEqual(failed.returncode,0,failed.stdout+failed.stderr)
        finally: parent.stop()
        passed=subprocess.run([sys.executable,'-c',code,str(self.config)],capture_output=True,text=True,timeout=10)
        self.assertEqual(passed.returncode,0,passed.stdout+passed.stderr)


if __name__ == '__main__': unittest.main()
