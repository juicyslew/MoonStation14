"""Targeted retry and admission regressions for gate finalization."""
import contextlib
import io
import json
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch

try:
    import test_workflow as fixtures
except ModuleNotFoundError:
    from tooling.agent_workflow import test_workflow as fixtures

wf = fixtures.wf


class GateFinishRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.WorkflowTests(methodName='runTest')
        self.fixture.setUp()
        self.root = self.fixture.root / 'work' / 'agent_exchange'

    def tearDown(self):
        self.fixture.tearDown()

    def _ready_args(self, ident):
        return Namespace(id=ident, actor='integration-lead', result='ready_for_owner',
                          finding=['Independent review confirms the change.'],
                          scoped_review='fidelity-reviewer', finish=True)

    def test_report_retry_recovers_transient_owner_registration_once(self):
        ident = 'finish-recovery'
        path, folder, _, _ = self.fixture._visual_candidate_with_scoped_receipt(ident)
        self.fixture.run_checks(ident)
        self.fixture.bind_completed_execution(ident)
        candidate = wf.readjson(path)
        candidate['gate_request']['origin_session_id'] = 'recovery-owner-session'
        wf.writejson(path, candidate)
        from tooling.agent_workflow import owner_attention, owner_feedback, gate_dispatch

        real_register = owner_attention.register_request
        calls = []

        def transient_then_real(*args, **kwargs):
            calls.append(1)
            if len(calls) == 1:
                raise OSError('simulated transient registration failure')
            return real_register(*args, **kwargs)

        args = self._ready_args(ident)
        with patch.dict('sys.modules', {'owner_feedback': owner_feedback,
                                        'owner_attention': owner_attention,
                                        'gate_dispatch': gate_dispatch}), \
             patch.object(owner_attention, 'register_request', side_effect=transient_then_real), \
             patch.object(owner_feedback, 'ensure_started', return_value={'status': 'started'}), \
             patch.object(gate_dispatch, 'deliver_gate_completion', return_value={'status': 'unavailable'}), \
             patch.object(wf, 'send_manual_notification', return_value='notification transport stubbed'), \
             contextlib.redirect_stdout(io.StringIO()):
            wf.cmd_gate_report(args, self.root)
            report_path = folder / f'{ident}-integration-lead-review.json'
            report_bytes = report_path.read_bytes()
            report_hash = wf.sha(report_path)
            state = wf.readjson(path)
            outcome = state['gate_outcome']
            manual_path = self.root / 'manual-tests.md'
            manual_bytes = manual_path.read_bytes()
            self.assertNotIn('owner_review', state)

            wf.cmd_gate_report(args, self.root)
            recovered = wf.readjson(path)
            self.assertEqual(recovered['owner_review']['request_generation'], 1)
            self.assertEqual(report_path.read_bytes(), report_bytes)
            self.assertEqual(wf.sha(report_path), report_hash)
            self.assertEqual(recovered['gate_outcome'], outcome)
            self.assertEqual(manual_path.read_bytes(), manual_bytes)
            recovered_request = recovered.get('owner_request_generation')

            wf.cmd_gate_report(args, self.root)
            final = wf.readjson(path)
            self.assertEqual(report_path.read_bytes(), report_bytes)
            self.assertEqual(wf.sha(report_path), report_hash)
            self.assertEqual(final['gate_outcome'], outcome)
            self.assertEqual(manual_path.read_bytes(), manual_bytes)
            self.assertEqual(final.get('owner_request_generation'), recovered_request)
        self.assertEqual(len(calls), 3)  # one transient failure, then two idempotent real registrations

    def test_completed_receipt_does_not_bypass_not_run_report_evidence(self):
        ident = 'recovery-not-run'
        path, folder, _, _ = self.fixture._visual_candidate_with_scoped_receipt(ident)
        self.fixture.run_checks(ident)
        self.fixture.bind_completed_execution(ident)
        candidate = wf.readjson(path)
        candidate['gate_request']['origin_session_id'] = 'not-run-owner'
        wf.writejson(path, candidate)
        report = folder / f'{ident}-independent-review.json'
        report.write_text(json.dumps({'candidate_id': ident, 'snapshot_key': candidate['snapshot_key'],
                                      'reviewer': 'integration-lead', 'decision': 'ready_for_owner',
                                      'findings': ['Independent review.'], 'viewed_images': [],
                                      'evidence_summary': {'status': 'NOT_RUN', 'checks': []}}))
        candidate['review'] = {'result': 'ready_for_owner', 'report_path': str(report),
                               'report_sha256': wf.sha(report), 'viewed_images': []}
        wf.writejson(path, candidate)
        before_candidate, before_report = path.read_bytes(), report.read_bytes()
        manual = self.root / 'manual-tests.md'
        before_manual = manual.read_bytes() if manual.exists() else None
        args = Namespace(id=ident, result='ready_for_owner', report=str(report))
        with self.assertRaises((ValueError, wf.GateFinalizationPending)):
            wf.cmd_gate_finish(args, self.root)
        self.assertEqual(path.read_bytes(), before_candidate)
        self.assertEqual(report.read_bytes(), before_report)
        self.assertEqual(manual.read_bytes() if manual.exists() else None, before_manual)

    def test_explicit_docs_no_checks_can_finalize_after_python_validation(self):
        ident = 'recovery-docs'
        plan = {'title': 'Docs-only fixture', 'kind': 'docs', 'no_checks_rationale': 'Documentation-only change.',
                'acceptance_criteria': ['The docs clarify the workflow.'], 'fixture': 'seed.txt',
                'manual_steps': ['Read the updated documentation.'], 'checks': []}
        planfile = self.fixture.root / 'docs-plan.json'
        planfile.write_text(json.dumps(plan))
        self.fixture.cli('submit', ident, '--agent', 'author', '--base-ref', self.fixture.base,
                         '--build-identity', 'isolated Python fixture', '--steps', 'no live mod candidate',
                         '--unverified', 'Minecraft not run', '--gate-plan', planfile)
        self.fixture.run_checks(ident)
        self.fixture.bind_completed_execution(ident)
        candidate_path = self.root / 'candidates' / f'{ident}.json'
        candidate = wf.readjson(candidate_path)
        candidate['gate_request'] = {'prompt_admitted': True, 'snapshot_key': candidate['snapshot_key'],
                                     'session_id': 'fixture-lead', 'origin_session_id': 'docs-owner'}
        wf.writejson(candidate_path, candidate)
        report = self.fixture.root / 'docs-review.json'
        report.write_text(json.dumps({'candidate_id': ident, 'snapshot_key': candidate['snapshot_key'],
                                      'reviewer': 'integration-lead', 'decision': 'ready_for_owner',
                                      'findings': ['Documentation criterion reviewed.'], 'viewed_images': [],
                                      'evidence_summary': wf._gate_evidence_summary(candidate, self.root, 'ready_for_owner')}))
        candidate['review'] = {'result': 'ready_for_owner', 'report_path': str(report),
                               'report_sha256': wf.sha(report), 'viewed_images': []}
        wf.writejson(candidate_path, candidate)
        from tooling.agent_workflow import gate_dispatch
        with patch.dict('sys.modules', {'gate_dispatch':gate_dispatch}), \
             patch.object(gate_dispatch, 'deliver_gate_completion', return_value={'status': 'unavailable'}), \
             patch.object(wf, '_ensure_owner_feedback', return_value={'status': 'started'}), \
             patch.object(wf, 'native_notify', return_value='listener_launched; visibility unverified'), \
             contextlib.redirect_stdout(io.StringIO()):
            wf.cmd_gate_finish(Namespace(id=ident, result='ready_for_owner', report=str(report)), self.root)
        final = wf.readjson(candidate_path)
        self.assertEqual(final['gate_outcome']['result'], 'ready_for_owner')
        self.assertEqual(wf._gate_evidence_summary(final, self.root, 'ready_for_owner')['status'], 'PASS')


if __name__ == '__main__':
    unittest.main()
