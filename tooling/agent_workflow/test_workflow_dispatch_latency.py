"""WR11 latency evidence driver for the real coordinator outbox dispatcher.

Run directly with the workflow venv. The harness measures committed durable
outbox events through ExplicitCoordinator._run/_deliver; handler execution is
an inert local stand-in, so these figures are coordinator dispatch only, not
native execution, provider, or model-startup latency.
"""
import json
import math
import argparse
import tempfile
import threading
import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_store
import workflow_runtime
import test_workflow_store_delivery as delivery_fixtures
from preflight_test_support import make_preflight_case
import workflow_preflight


def distribution(values):
    ordered = sorted(values)
    if not ordered:
        raise ValueError('at least one actual measurement is required')
    def percentile(p):
        # Nearest-rank percentile, preserving every measured sample.
        return ordered[max(0, math.ceil(p * len(ordered)) - 1)]
    return {'sample_count': len(ordered), 'raw_ms': ordered,
            'p50_ms': percentile(.50), 'p95_ms': percentile(.95),
            'max_ms': ordered[-1]}


class _Handler:
    def __init__(self):
        self.received = {}
        self.condition = threading.Condition()
        self.block = False
        self.release = threading.Event()

    def observe(self, event_id, payload):
        return 'ABSENT'

    def dispatch(self, event_id, payload):
        if self.block:
            self.release.wait(10)
        with self.condition:
            self.received[event_id] = time.perf_counter_ns()
            self.condition.notify_all()


def _commit_event(store, coordinator, event_id):
    """Commit a durable synthetic coordinator wake using the real SQLite store."""
    db = store._connect()
    try:
        db.execute('BEGIN IMMEDIATE')
        event = {'contract_version': 1, 'event_id': event_id,
                 'kind': 'WR11_LATENCY_WAKE'}
        workflow_store.WorkflowStore._event(db, event)
        db.execute('INSERT INTO outbox(event_id) VALUES(?)', (event_id,))
        db.commit()
        committed = time.perf_counter_ns()
        coordinator.notify_committed()
        return committed
    except BaseException:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        db.close()


def _wait_for(handler, expected, timeout=5):
    deadline = time.monotonic() + timeout
    with handler.condition:
        while len(handler.received) < expected:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AssertionError(f'bounded dispatch wait expired: {len(handler.received)}/{expected}')
            handler.condition.wait(remaining)


def measure_phase(root, *, count, occupied):
    Path(root).mkdir(parents=True, exist_ok=True)
    store = workflow_store.WorkflowStore(Path(root) / 'latency.sqlite')
    handler = _Handler()
    handler.block = False
    c = workflow_coordinator.ExplicitCoordinator(
        store, bindings={'fixture': object()}, artifact_dir=Path(root) / 'artifacts',
        handlers={'WR11_LATENCY_WAKE': handler}, identity_verifier=lambda _: True,
        reconcile_interval=30)
    commits = {}
    native_queue_ms = []
    c.start()
    try:
        if occupied:
            # Hold the exact named/file mutex acquired by native workers.
            native_lock = workflow_runtime.gate_lock(Path(root) / 'native-lock')
            for i in range(count):
                event = f'wr11-1-{i}'
                commits[event] = _commit_event(store, c, event)
                _wait_for(handler, i + 1)
            # Workers are cheap fixture threads running the real native mutex
            # owner; they don't launch native validation or claim model startup.
            starts = [threading.Event() for _ in range(count)]
            acquired = [None] * count
            def queued_worker(index):
                starts[index].set()
                before = time.perf_counter_ns()
                lock = workflow_runtime.gate_lock(Path(root) / 'native-lock')
                acquired[index] = (time.perf_counter_ns(), before)
                lock.close()
            workers = [threading.Thread(target=queued_worker, args=(i,)) for i in range(count)]
            for worker in workers: worker.start()
            for started in starts:
                if not started.wait(2): raise AssertionError('native fixture worker did not start')
            # Keep capacity occupied long enough for the entire controlled burst
            # to queue; then release the actual shared native mutex.
            time.sleep(.025)
            native_lock.close()
            for worker in workers:
                worker.join(5)
                if worker.is_alive(): raise AssertionError('native mutex queue failed to drain')
            native_queue_ms = [(end - begin) / 1_000_000 for end, begin in acquired]
        else:
            # Submit the next observation only after prior dispatch completion so
            # this series represents an idle coordinator rather than self-created backlog.
            for i in range(count):
                event = f'wr11-0-{i}'
                commits[event] = _commit_event(store, c, event)
                _wait_for(handler, i + 1)
        return ([(handler.received[e] - committed) / 1_000_000
                 for e, committed in commits.items()], native_queue_ms)
    finally:
        handler.release.set()
        c.stop()


def measure_completion_delivery(root, count=30):
    """Measure real committed delivery/outbox event to fake author API POST."""
    import hashlib
    import workflow_result_delivery
    samples = {'commit_to_result_handler_ms': [], 'commit_to_fake_post_ms': [],
               'handler_to_fake_post_ms': []}
    for i in range(count):
        fixture = delivery_fixtures.DeliveryEnqueueTests(methodName='runTest')
        fixture.setUp()
        try:
            author = fixture.run['author']
            delivery_id = 'delivery_' + hashlib.sha256(
                (fixture.event['event_id'] + 'AUTHOR' + workflow_store.canonical_json(author)).encode()).hexdigest()[:24]
            stamps = {}
            transport = []
            def fake_request(server, method, path, body=None):
                now = time.perf_counter_ns()
                transport.append((method, now))
                if method == 'GET':
                    return 200, {'id': author['session_id'], 'directory': author['worktree'], 'agent': 'fixture-author'}
                return 200, {'id': body['id'], 'sessionID': author['session_id'], 'type': 'user',
                             'delivery': body['delivery'],
                             'payload': {'text': body['text'], 'metadata': body['metadata']}}
            backend = workflow_result_delivery.AuthorResultDelivery(
                fixture.store, server='http://127.0.0.1:4096', request=fake_request)
            class TimedDelivery:
                def observe(self, event_id, payload): return backend.observe(event_id, payload)
                def dispatch(self, event_id, payload):
                    stamps['handler'] = time.perf_counter_ns()
                    return backend.dispatch(event_id, payload)
            handler = TimedDelivery()
            coordinator = workflow_coordinator.ExplicitCoordinator(
                fixture.store, bindings={'fixture': object()}, artifact_dir=Path(fixture.tmp.name) / 'artifacts',
                handlers={'RESULT_AVAILABLE': handler}, identity_verifier=lambda _: True, reconcile_interval=10)
            coordinator.start()
            try:
                # Actual durable transaction inserts the terminal result event's
                # AUTHOR delivery and its RESULT_AVAILABLE outbox wake.
                fixture.enqueue(delivery_id, 'AUTHOR', author)
                committed = time.perf_counter_ns()
                coordinator.notify_committed()
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline and not any(m == 'POST' for m, _ in transport):
                    time.sleep(.001)
                if not any(m == 'POST' for m, _ in transport):
                    raise AssertionError(f'author fake backend POST not reached: {fixture.store.outbox_summary()}')
                post = next(t for m, t in transport if m == 'POST')
                samples['commit_to_result_handler_ms'].append((stamps['handler'] - committed) / 1_000_000)
                samples['commit_to_fake_post_ms'].append((post - committed) / 1_000_000)
                samples['handler_to_fake_post_ms'].append((post - stamps['handler']) / 1_000_000)
            finally:
                coordinator.stop()
        finally:
            fixture.tearDown()
    return {name: distribution(values) for name, values in samples.items()}


def measure_preflight(root, count=3):
    values = []
    for i in range(count):
        case_root = Path(root) / f'preflight-{i}'
        case_root.mkdir(parents=True, exist_ok=True)
        case = make_preflight_case(case_root, caller='wr11-preflight', key=f'preflight-{i}')
        context = workflow_preflight.PreflightContext(
            case.repo, case.author, case.policy, [case.artifact], case.receipt,
            __import__('hashlib').sha256(case.receipt.read_bytes()).hexdigest())
        before = time.perf_counter_ns()
        workflow_preflight.preflight(case.request, caller=case.caller, context=context)
        values.append((time.perf_counter_ns() - before) / 1_000_000)
    return distribution(values)


def _await_transport_acceptance(store, delivery_id, deadline, *, monotonic=None, sleep=None):
    """Observe the same durable delivery until ACK persistence or the original deadline."""
    monotonic = monotonic or time.monotonic
    sleep = sleep or time.sleep
    while True:
        delivery = store.get_delivery(delivery_id)
        observed_at = monotonic()
        if delivery is not None and delivery.get('state') == 'TRANSPORT_ACCEPTED' and observed_at < deadline:
            return delivery, True
        if observed_at >= deadline:
            return delivery, False
        sleep(min(.001, deadline - observed_at))


def measure_terminal_feedback(root, count=30):
    """Conservative call-start-to-author-POST bound through terminal event commit."""
    import workflow_result_delivery
    samples = {'terminal_completion_call_start_to_fake_post_ms': []}
    for i in range(count):
        fixture = delivery_fixtures.DeliveryEnqueueTests(methodName='runTest')
        fixture.setUp()
        coordinator = None
        try:
            # DeliveryEnqueueTests seeds a valid saved run; move its fixture state
            # back to CHECKING to exercise public terminal transitions normally.
            attempt = dict(fixture.attempt, phase='CHECKING', result=None,
                           proposed_result=None, closure=None, proposed_closure=None)
            fixture.db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=?',
                               (json.dumps(attempt), fixture.run['run_id']))
            fixture.db.commit()
            author = fixture.run['author']
            posts = []
            def request(server, method, path, body=None):
                if method == 'GET':
                    return 200, {'id': author['session_id'], 'directory': author['worktree'], 'agent': 'fixture-author'}
                posts.append((time.perf_counter_ns(), body))
                return 200, {'id': body['id'], 'sessionID': author['session_id'], 'type': 'user',
                             'delivery': body['delivery'],
                             'payload': {'text': body['text'], 'metadata': body['metadata']}}
            handler = workflow_result_delivery.AuthorResultDelivery(
                fixture.store, server='http://127.0.0.1:4096', request=request)
            coordinator = workflow_coordinator.ExplicitCoordinator(
                fixture.store, bindings={'fixture': object()}, artifact_dir=Path(fixture.tmp.name) / 'artifacts',
                handlers={'RESULT_AVAILABLE': handler}, identity_verifier=lambda _: True,
                reconcile_interval=.02)
            coordinator.start()
            incident = {'contract_version': 1, 'incident_id': f'incident_wr11_{i}',
                        'run_id': fixture.run['run_id'], 'evaluation_generation': 1,
                        'code': 'WR11_FIXTURE', 'message': 'Bounded terminal latency fixture.',
                        'recovery_owner': 'SYSTEM', 'state': 'OPEN',
                        'owner_action_required': False, 'artifact_refs': []}
            stopping = fixture.store.transition_attempt(
                fixture.run['run_id'], 1, expected_sequence=0, phase='STOPPING',
                proposed_result='ERROR', incident=incident, command_id=f'wr11-stop-{i}')
            start = time.perf_counter_ns()  # before DECIDED transaction; includes commit conservatively
            terminal = fixture.store.transition_attempt(
                fixture.run['run_id'], 1, expected_sequence=stopping['sequence'], phase='DECIDED',
                terminal_result='ERROR', cleanup_state='NOT_REQUIRED', incident=incident,
                command_id=f'wr11-decide-{i}')
            event_id = terminal['event_id']
            event = fixture.store.get_event(event_id)['payload']
            delivery_id = 'delivery_' + __import__('hashlib').sha256(
                (event_id + 'AUTHOR' + workflow_store.canonical_json(author)).encode()).hexdigest()[:24]
            coordinator.notify_committed()
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline and not posts:
                time.sleep(.001)
            delivery, accepted_before_deadline = _await_transport_acceptance(
                fixture.store, delivery_id, deadline)
            posted_ns, body = posts[0] if posts else (None, None)
            saved = fixture.store.get_terminal_result(fixture.run['run_id'], 1)
            metadata=body.get('metadata') if isinstance(body,dict) else None
            matches={
                'post_observed':body is not None,
                'accepted_before_deadline':accepted_before_deadline,
                'saved_terminal_result':saved is not None and saved.get('result')=='ERROR',
                'delivery_registered':delivery is not None,
                'delivery_event_payload_matches':delivery is not None and delivery.get('payload')==event,
                'delivery_transport_accepted':delivery is not None and delivery.get('state')=='TRANSPORT_ACCEPTED',
                'post_event_id_matches':isinstance(metadata,dict) and metadata.get('event_id')==event_id,
                'post_result_digest_matches':isinstance(metadata,dict) and metadata.get('result_digest')==event.get('result_digest'),
            }
            if not all(matches.values()):
                diagnostic={'saved_result':saved,'event_id':event_id,'event_payload':event,
                    'delivery_id':delivery_id,'delivery_state':delivery.get('state') if delivery else None,
                    'delivery_payload':delivery.get('payload') if delivery else None,
                    'posted_metadata':metadata,'matches':matches,'outbox':fixture.store.outbox_summary()}
                raise AssertionError('terminal feedback transport acceptance/binding mismatch: '+
                    json.dumps(diagnostic,sort_keys=True,default=str))
            samples['terminal_completion_call_start_to_fake_post_ms'].append(
                (posted_ns - start) / 1_000_000)
        finally:
            if coordinator is not None:
                coordinator.stop()
            fixture.tearDown()
    return {name: distribution(values) for name, values in samples.items()}


class DispatchLatencyEvidence(unittest.TestCase):
    def test_transport_acceptance_waits_for_durable_ack_and_respects_deadline(self):
        class Store:
            def __init__(self, states): self.states=list(states); self.reads=0
            def get_delivery(self, delivery_id):
                self.reads+=1
                state=self.states[min(self.reads-1,len(self.states)-1)]
                return {'delivery_id':delivery_id,'state':state}

        clock=[0.0]
        def sleep(seconds): clock[0]+=seconds
        store=Store(['PENDING','TRANSPORT_ACCEPTED'])
        delivery,ready=_await_transport_acceptance(store,'delivery-fixed',1.0,
            monotonic=lambda:clock[0],sleep=sleep)
        self.assertEqual('TRANSPORT_ACCEPTED',delivery['state'])
        self.assertTrue(ready); self.assertEqual(2,store.reads)

        clock[0]=0.0; store=Store(['PENDING'])
        delivery,ready=_await_transport_acceptance(store,'delivery-fixed',.002,
            monotonic=lambda:clock[0],sleep=sleep)
        self.assertEqual('PENDING',delivery['state'])
        self.assertFalse(ready); self.assertEqual(.002,clock[0])

    def test_percentile_definition_and_sample_boundary(self):
        summary = distribution([9.0, 1.0, 4.0, 2.0])
        self.assertEqual(4, summary['sample_count'])
        self.assertEqual(2.0, summary['p50_ms'])
        self.assertEqual(9.0, summary['p95_ms'])
        self.assertEqual(9.0, summary['max_ms'])
        with self.assertRaises(ValueError):
            distribution([])

    def test_measure_actual_coordinator_idle_and_occupied_dispatch(self):
        count = 30
        with tempfile.TemporaryDirectory() as tmp:
            idle, _ = measure_phase(Path(tmp) / 'idle', count=count, occupied=False)
            occupied, native_queue = measure_phase(Path(tmp) / 'occupied', count=count, occupied=True)
            evidence = {
                'method': 'perf_counter_ns around committed SQLite event transaction through actual ExplicitCoordinator outbox claim/_deliver and handler entry',
                'percentile': 'nearest-rank: sorted[ceil(p*n)-1]; no sample trimming',
                'idle_commit_to_handler': distribution(idle),
                'native_occupied_commit_to_handler': distribution(occupied),
                'fixture_gate_lock_queue_wait': distribution(native_queue),
                'limits': ['Occupied samples hold the real workflow_runtime.gate_lock used by native worker ownership; waiting workers are cheap fixture threads, not launched native check processes.',
                           'Coordinator handler remains available while native capacity is held; fake producer spawn is the mutex-acquisition boundary, not actual process startup.',
                           'Backend request/model startup and completion-to-notification are NOT MEASURED.',
                           'Preflight is excluded; all timings start immediately before durable event commit.',
                           'These are local fixture observations, not a hardware-independent SLO guarantee.'],
                'targets_ms': {'commit_to_dispatch_p95': 100, 'ready_to_backend_p95': 1000,
                               'completion_commit_to_notification_p95': 1000},
            }
            evidence['target_assessment'] = {
                'commit_to_dispatch_idle': 'PASS' if evidence['idle_commit_to_handler']['p95_ms'] < 100 else 'FAIL',
                'commit_to_dispatch_native_occupied': 'PASS' if evidence['native_occupied_commit_to_handler']['p95_ms'] < 100 else 'FAIL',
                'native_mutex_queue_wait': 'MEASURED',
                'ready_to_backend': 'NOT_MEASURED',
                'completion_commit_to_notification': 'NOT_MEASURED',
            }
            out = Path(tmp) / 'native-latency.json'
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
            log = out.with_suffix('.log')
            log.write_text('\n'.join([
                f"idle commit-to-handler n={evidence['idle_commit_to_handler']['sample_count']} p50={evidence['idle_commit_to_handler']['p50_ms']:.3f}ms p95={evidence['idle_commit_to_handler']['p95_ms']:.3f}ms max={evidence['idle_commit_to_handler']['max_ms']:.3f}ms",
                f"native-occupied commit-to-handler n={evidence['native_occupied_commit_to_handler']['sample_count']} p50={evidence['native_occupied_commit_to_handler']['p50_ms']:.3f}ms p95={evidence['native_occupied_commit_to_handler']['p95_ms']:.3f}ms max={evidence['native_occupied_commit_to_handler']['max_ms']:.3f}ms",
                f"fixture-held gate_lock queue wait n={evidence['fixture_gate_lock_queue_wait']['sample_count']} p50={evidence['fixture_gate_lock_queue_wait']['p50_ms']:.3f}ms p95={evidence['fixture_gate_lock_queue_wait']['p95_ms']:.3f}ms max={evidence['fixture_gate_lock_queue_wait']['max_ms']:.3f}ms",
                'ready-to-backend and completion-to-notification: NOT MEASURED',
            ]) + '\n', encoding='utf-8')
            self.assertEqual(count, evidence['idle_commit_to_handler']['sample_count'])
            self.assertEqual(count, evidence['native_occupied_commit_to_handler']['sample_count'])
            self.assertEqual(count, evidence['fixture_gate_lock_queue_wait']['sample_count'])

    def test_completion_delivery_and_preflight_boundaries(self):
        with tempfile.TemporaryDirectory() as tmp:
            completion = measure_completion_delivery(Path(tmp) / 'completion', count=30)
            preflight = measure_preflight(Path(tmp) / 'preflight', count=3)
            self.assertEqual(30, completion['commit_to_fake_post_ms']['sample_count'])
            self.assertEqual(3, preflight['sample_count'])
            (Path(tmp) / 'completion-latency.json').write_text(json.dumps({
                'method': 'perf_counter_ns immediately after actual store.enqueue_delivery commit + coordinator.notify_committed to real RESULT_AVAILABLE coordinator handler and AuthorResultDelivery fake transport POST',
                'percentile': 'nearest-rank ceil(p*n), all samples retained',
                **completion,
                'targets_ms': {'completion_commit_to_notification_p95': 1000},
                'target_assessment': {'completion_commit_to_notification': 'PASS' if completion['commit_to_result_handler_ms']['p95_ms'] < 1000 else 'FAIL'},
                'model_startup': 'NOT_MEASURED; fake local API transport only',
            }, indent=2) + '\n', encoding='utf-8')
            (Path(tmp) / 'preflight-latency.json').write_text(json.dumps({
                'method': 'perf_counter_ns around workflow_preflight.preflight using three independent make_preflight_case disposable Git source fixtures',
                'percentile': 'nearest-rank ceil(p*n), all samples retained',
                'scope': 'includes authoritative Git/source capture and preflight validation; excluded from dispatcher latency targets',
                'preflight': preflight,
            }, indent=2) + '\n', encoding='utf-8')

    def test_terminal_result_commit_to_author_notification_bound(self):
        with tempfile.TemporaryDirectory() as tmp:
            terminal = measure_terminal_feedback(Path(tmp) / 'terminal', count=30)
            measured = terminal['terminal_completion_call_start_to_fake_post_ms']
            self.assertEqual(30, measured['sample_count'])
            self.assertLess(measured['p95_ms'], 1000)
            (Path(tmp) / 'terminal-feedback-latency.json').write_text(json.dumps({
                'method': 'perf_counter_ns immediately before public STOPPING + DECIDED transition_attempt calls through their commits, automatic terminal RESULT_AVAILABLE/author delivery outbox, coordinator thread and fake AuthorResultDelivery POST',
                'percentile': 'nearest-rank ceil(p*n), all samples retained',
                **terminal,
                'target_ms': {'terminal_completion_call_start_to_notification_p95': 1000},
                'target_assessment': {'terminal_completion_call_start_to_notification': 'PASS' if measured['p95_ms'] < 1000 else 'FAIL'},
                'scope': 'Conservative upper bound includes terminal database commit and coordinator wake; terminal result and author delivery event are generated by the public store transition, not inserted by the fixture.',
                'model_startup': 'NOT_MEASURED; local fake transport only',
            }, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-dir', type=Path,
                        help='write canonical native-latency.json/.log evidence here (opt-in)')
    parser.add_argument('--terminal-feedback-only', action='store_true',
                        help='emit only terminal-feedback-latency.json/.log; requires --evidence-dir')
    options, remaining = parser.parse_known_args()
    # unittest remains the cheap fixture validation; its report is temporary.
    # Canonical evidence is only emitted by an explicit one-off driver invocation.
    if options.evidence_dir:
        if options.terminal_feedback_only:
            with tempfile.TemporaryDirectory() as tmp:
                terminal = measure_terminal_feedback(Path(tmp) / 'terminal', count=30)
                measured = terminal['terminal_completion_call_start_to_fake_post_ms']
                evidence = {
                    'method': 'perf_counter_ns immediately before public DECIDED transition_attempt call through its transaction commit, generated result/delivery event and coordinator result handler to fake author POST',
                    'percentile': 'nearest-rank ceil(p*n), all samples retained',
                    **terminal,
                    'target_ms': {'terminal_completion_call_start_to_notification_p95': 1000},
                    'target_assessment': {'terminal_completion_call_start_to_notification': 'PASS' if measured['p95_ms'] < 1000 else 'FAIL'},
                    'scope': 'The terminal public transition atomically generated RESULT_AVAILABLE event, author delivery and outbox row; metric starts before transition, conservatively including commit cost. Verified saved terminal result, bound event payload/digest and TRANSPORT_ACCEPTED delivery for each sample.',
                    'model_startup': 'NOT_MEASURED; fake local API transport only',
                }
                options.evidence_dir.mkdir(parents=True, exist_ok=True)
                (options.evidence_dir / 'terminal-feedback-latency.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
                d = measured
                (options.evidence_dir / 'terminal-feedback-latency.log').write_text(
                    f"terminal_completion_call_start_to_fake_post n={d['sample_count']} p50={d['p50_ms']:.3f}ms p95={d['p95_ms']:.3f}ms max={d['max_ms']:.3f}ms\n"
                    f"1s completion notification p95 target: {'PASS' if d['p95_ms'] < 1000 else 'FAIL'}\nmodel startup/inference: NOT MEASURED (fake backend)\n", encoding='utf-8')
            raise SystemExit(0)
        with tempfile.TemporaryDirectory() as tmp:
            temporary = Path(tmp)
            idle, _ = measure_phase(temporary / 'idle', count=30, occupied=False)
            occupied, native_queue = measure_phase(temporary / 'occupied', count=30, occupied=True)
            completion = measure_completion_delivery(temporary / 'completion', count=30)
            preflight = measure_preflight(temporary / 'preflight', count=3)
            target = options.evidence_dir
            target.mkdir(parents=True, exist_ok=True)
            data = {'method': 'perf_counter_ns; durable SQLite commit then actual coordinator.notify_committed through ExplicitCoordinator._run/_deliver; native capacity held with workflow_runtime.gate_lock',
                    'percentile': 'nearest-rank ceil(p*n), all samples retained',
                    'idle_commit_to_handler': distribution(idle),
                    'native_occupied_commit_to_handler': distribution(occupied),
                    'fixture_gate_lock_queue_wait': distribution(native_queue),
                    'targets_ms': {'commit_to_dispatch_p95': 100, 'ready_to_backend_p95': 1000, 'completion_commit_to_notification_p95': 1000},
                    'target_assessment': {'idle_commit_to_dispatch': 'PASS' if distribution(idle)['p95_ms'] < 100 else 'FAIL',
                                          'native_occupied_commit_to_dispatch': 'PASS' if distribution(occupied)['p95_ms'] < 100 else 'FAIL',
                                          'ready_to_backend': 'NOT_MEASURED', 'completion_commit_to_notification': 'NOT_MEASURED'},
                    'limits': ['Native mutex is the real workflow_runtime.gate_lock; queued producers are fixture threads, not actual validation processes.',
                               'Mutex acquisition is the fake producer spawn boundary; real backend process startup is not measured.',
                               'Preflight is excluded; no model inference/startup.']}
            (target / 'native-latency.json').write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
            lines = []
            for key in ('idle_commit_to_handler', 'native_occupied_commit_to_handler', 'fixture_gate_lock_queue_wait'):
                d = data[key]
                lines.append(f"{key} n={d['sample_count']} p50={d['p50_ms']:.3f}ms p95={d['p95_ms']:.3f}ms max={d['max_ms']:.3f}ms")
            lines.append('ready-to-backend and completion-commit-to-notification: NOT MEASURED')
            (target / 'native-latency.log').write_text('\n'.join(lines) + '\n', encoding='utf-8')
            completion_data = {
                'method': 'perf_counter_ns immediately after actual store.enqueue_delivery commit + coordinator.notify_committed to actual RESULT_AVAILABLE dispatch and AuthorResultDelivery fake transport POST',
                'percentile': 'nearest-rank ceil(p*n), all samples retained', **completion,
                'targets_ms': {'completion_commit_to_notification_p95': 1000},
                'target_assessment': {'completion_commit_to_notification': 'PASS' if completion['commit_to_result_handler_ms']['p95_ms'] < 1000 else 'FAIL'},
                'model_startup': 'NOT_MEASURED; fake local API transport only'}
            (target / 'completion-latency.json').write_text(json.dumps(completion_data, indent=2) + '\n', encoding='utf-8')
            completion_lines = []
            for key, value in completion.items():
                completion_lines.append(f"{key} n={value['sample_count']} p50={value['p50_ms']:.3f}ms p95={value['p95_ms']:.3f}ms max={value['max_ms']:.3f}ms")
            completion_lines.append('model startup/inference: NOT MEASURED (fake backend)')
            (target / 'completion-latency.log').write_text('\n'.join(completion_lines) + '\n', encoding='utf-8')
            preflight_data = {
                'method': 'perf_counter_ns around workflow_preflight.preflight on three independent make_preflight_case disposable Git fixtures',
                'percentile': 'nearest-rank ceil(p*n), all samples retained',
                'scope': 'includes authoritative Git/source capture and preflight validation; excluded from dispatcher latency targets',
                'preflight': preflight}
            (target / 'preflight-latency.json').write_text(json.dumps(preflight_data, indent=2) + '\n', encoding='utf-8')
            p = preflight
            (target / 'preflight-latency.log').write_text(
                f"authoritative-preflight n={p['sample_count']} p50={p['p50_ms']:.3f}ms p95={p['p95_ms']:.3f}ms max={p['max_ms']:.3f}ms (includes Git/source capture)\n", encoding='utf-8')
    else:
        unittest.main(argv=[__file__, *remaining])
