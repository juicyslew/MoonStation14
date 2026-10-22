import copy
import tempfile
import unittest
import hashlib
from pathlib import Path

from workflow_check_packs import resolve_check_packs
from workflow_runtime import resolve_gate_plan, validate_gate_plan
from workflow_store import ProtocolError
from validation_evidence import collect_evidence


class CheckPackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        (self.repo / 'manual.md').write_text('manual setup', encoding='utf-8')
        self.request = {
            'purpose': 'OWNER_READY', 'implementation_complete': True, 'feature_label': 'Example',
            'sprint_id': 'sprint-1', 'source_worktree': str(self.repo), 'input_digest': 'a' * 64,
            'criteria': [{'criterion_id': 'c1', 'status': 'COMPLETE', 'evidence_refs': ['test']}],
            'manual_steps': ['Open the result'],
            'check_packs': [
                {'pack_id': 'unit', 'kind': 'unit', 'selectors': ['org.example.FeatureTest.works']},
                {'pack_id': 'readiness', 'kind': 'readiness', 'selectors': ['ui', 'interaction']},
                {'pack_id': 'focused_gametest', 'kind': 'focused_gametest', 'selectors': ['feature.smoke']},
                {'pack_id': 'package', 'kind': 'package', 'selectors': []},
                {'pack_id': 'provenance', 'kind': 'provenance', 'selectors': []},
            ]}
        self.policy = {'criteria': ['c1'], 'coverage': {'c1': ['unit', 'readiness', 'focused_gametest', 'package', 'provenance']}, 'selector_requirements': {'unit':['org.example.FeatureTest.works'], 'readiness':['ui'], 'focused_gametest':['feature.smoke']}, 'fixture': 'manual.md', 'package_artifacts': ['build/libs/output.jar']}

    def tearDown(self): self.temp.cleanup()

    def resolve(self, req=None, policy=None):
        return resolve_check_packs(req or self.request, repo=self.repo, policy=policy or self.policy)

    def test_deterministic_order_and_existing_producer_plan(self):
        result = self.resolve()
        self.assertEqual([p['pack_id'] for p in result['packs']], ['provenance', 'unit', 'readiness', 'focused_gametest', 'package'])
        self.assertEqual(result, self.resolve())
        validate_gate_plan(result['plan'], self.repo)
        self.assertEqual(resolve_gate_plan(result['plan'], self.repo), result['plan'])

    def test_narrow_unit_and_exact_readiness_commands(self):
        checks = self.resolve()['plan']['checks']
        self.assertEqual(checks[0]['argv'], ['./gradlew', 'test', '--rerun', '--tests', 'org.example.FeatureTest.works', '--no-daemon'])
        self.assertEqual(checks[1]['argv'], ['./gradlew', 'featureReadiness', '-Pms14Readiness=ui,interaction', '--no-daemon'])
        self.assertTrue(all(c['argv'][-1] == '--no-daemon' for c in checks))

    def test_package_is_non_destructive_and_runs_after_focused_checks(self):
        result = self.resolve()
        checks = result['plan']['checks']
        package = checks[-1]
        self.assertEqual(package['argv'], ['./gradlew', 'cleanJar', 'assemble', '--no-daemon'])
        validate_gate_plan(result['plan'], self.repo)
        self.assertEqual([c['kind'] for c in checks[:-1]], ['junit', 'readiness', 'gametest'])
        # Plan resolution is pure: existing focused-check evidence remains untouched.
        sentinel = self.repo / 'build' / 'reports' / 'unit.xml'
        sentinel.parent.mkdir(parents=True)
        sentinel.write_text('preserved evidence', encoding='utf-8')
        self.resolve()
        self.assertEqual(sentinel.read_text(encoding='utf-8'), 'preserved evidence')

    def test_package_evidence_observes_current_window_without_claiming_production(self):
        import os, time
        artifact = self.repo / 'build/libs/output.jar'
        artifact.parent.mkdir(parents=True)
        artifact.write_bytes(b'reproducible jar')
        before = {str(artifact.resolve()): {'mtime_ns': artifact.stat().st_mtime_ns,
            'size': artifact.stat().st_size, 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()}}
        started = time.time()
        time.sleep(0.02)
        artifact.unlink()
        artifact.write_bytes(b'reproducible jar')
        os.utime(artifact, (started + 0.02, started + 0.02))
        report = collect_evidence({'kind':'build','artifacts':['build/libs/output.jar'],'exit_code':0},
            self.repo, started, started + 1, self.repo/'unused.log', before,
            hash_file=lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest(),
            safe_path=lambda root, relative: root/relative)
        row = report['artifacts'][0]
        self.assertTrue(row['preexisting'])
        self.assertFalse(row['changed_this_run'])
        self.assertTrue(row['observed_fresh_this_run'])
        self.assertNotIn('produced_this_run', row)

    def test_untouched_old_package_is_not_observed_fresh(self):
        import hashlib, time
        artifact = self.repo / 'build/libs/output.jar'
        artifact.parent.mkdir(parents=True)
        artifact.write_bytes(b'old package')
        old = time.time() - 30
        import os
        os.utime(artifact, (old, old))
        before = {str(artifact.resolve()): {'mtime_ns': artifact.stat().st_mtime_ns,
            'size': artifact.stat().st_size, 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()}}
        now = time.time()
        report = collect_evidence({'kind':'build','artifacts':['build/libs/output.jar'],'exit_code':0},
            self.repo, now, now + 1, self.repo/'unused.log', before,
            hash_file=lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest(),
            safe_path=lambda root, relative: root/relative)
        self.assertFalse(report['artifacts'][0]['observed_fresh_this_run'])

    def test_invalid_selectors_rejected(self):
        for selectors in (['*'], ['org.example.Test;evil'], ['org.example.Test', 'org.example.Test']):
            req = copy.deepcopy(self.request); req['check_packs'][0]['selectors'] = selectors
            with self.assertRaises(ProtocolError): self.resolve(req)

    def test_trusted_selector_binding_requires_minimum_and_allows_narrow_addition(self):
        req = copy.deepcopy(self.request)
        req['check_packs'][2]['selectors'] = ['unrelated']
        with self.assertRaises(ProtocolError): self.resolve(req)
        req['check_packs'][2]['selectors'] = ['feature.smoke', 'extra.tag']
        self.assertEqual(self.resolve(req)['packs'][3]['selectors'], req['check_packs'][2]['selectors'])

    def test_requirements_must_match_selected_focused_packs_exactly(self):
        for pid in ('unit', 'readiness', 'focused_gametest'):
            policy = copy.deepcopy(self.policy); policy['selector_requirements'].pop(pid)
            with self.assertRaises(ProtocolError): self.resolve(policy=policy)
        policy = copy.deepcopy(self.policy); policy['selector_requirements']['unknown'] = ['x']
        with self.assertRaises(ProtocolError): self.resolve(policy=policy)
        req = copy.deepcopy(self.request); req['check_packs'] = [p for p in req['check_packs'] if p['pack_id'] not in ('unit','readiness','focused_gametest')]
        policy = copy.deepcopy(self.policy); policy['selector_requirements'] = {}; policy['coverage']['c1'] = ['package','provenance']
        self.resolve(req, policy)

    def test_unknown_mismatched_duplicate_pack_rejected(self):
        for mutate in ('unknown', 'mismatch', 'duplicate'):
            req = copy.deepcopy(self.request)
            if mutate == 'unknown': req['check_packs'][0]['pack_id'] = 'arbitrary'
            elif mutate == 'mismatch': req['check_packs'][0]['kind'] = 'readiness'
            else: req['check_packs'].append(copy.deepcopy(req['check_packs'][0]))
            with self.assertRaises(ProtocolError): self.resolve(req)

    def test_missing_required_pack_and_coverage_rejected(self):
        req = copy.deepcopy(self.request); req['check_packs'] = [p for p in req['check_packs'] if p['pack_id'] != 'package']
        with self.assertRaises(ProtocolError): self.resolve(req)
        policy = copy.deepcopy(self.policy); policy['coverage']['c1'] = ['unit', 'package']
        with self.assertRaises(ProtocolError): self.resolve(policy=policy)

    def test_full_gametest_requires_exact_trusted_exception(self):
        req = copy.deepcopy(self.request); req['check_packs'].append({'pack_id': 'full_gametest', 'kind': 'full_gametest', 'selectors': []})
        self.policy['coverage']['c1'].append('full_gametest')
        with self.assertRaises(ProtocolError): self.resolve(req)
        wrong = {'id': 'auth', 'sprint_id': 'other', 'source_worktree': str(self.repo), 'input_digest': 'a'*64, 'allow': 'full_gametest'}
        policy = copy.deepcopy(self.policy); policy['owner_exceptions'] = [wrong]
        req['owner_exception_id'] = 'auth'
        with self.assertRaises(ProtocolError): self.resolve(req, policy)

    def test_exact_exception_allows_full_suite_and_binds_input(self):
        req = copy.deepcopy(self.request); req['check_packs'] = [p for p in req['check_packs'] if p['pack_id'] != 'focused_gametest']; req['check_packs'].append({'pack_id': 'full_gametest', 'kind': 'full_gametest', 'selectors': []}); req['owner_exception_id'] = 'auth'
        self.policy['selector_requirements'].pop('focused_gametest')
        self.policy['coverage']['c1'].remove('focused_gametest'); self.policy['coverage']['c1'].append('full_gametest')
        self.policy['owner_exceptions'] = [{'id': 'auth', 'sprint_id': 'sprint-1', 'source_worktree': str(self.repo), 'input_digest': 'a'*64, 'allow': 'full_gametest'}]
        self.assertIn('runGameTestServer', self.resolve(req)['plan']['checks'][2]['argv'])
        req['input_digest'] = 'b'*64
        with self.assertRaises(ProtocolError): self.resolve(req)

    def test_package_and_provenance_are_explicit_and_change_plan_digest(self):
        first = self.resolve()
        changed = copy.deepcopy(self.request); changed['check_packs'][0]['selectors'] = ['org.example.OtherTest']
        policy = copy.deepcopy(self.policy); policy['selector_requirements']['unit'] = ['org.example.OtherTest']
        second = self.resolve(changed, policy)
        self.assertNotEqual(first['plan'], second['plan'])
        self.assertTrue(all(c['argv'][-1] == '--no-daemon' for c in first['plan']['checks']))
        self.assertEqual(first['program_checks'], ['managed_config_provenance'])
        req = copy.deepcopy(self.request); req['check_packs'] = [p for p in req['check_packs'] if p['pack_id'] != 'provenance']
        with self.assertRaises(ProtocolError): self.resolve(req)

    def test_fixture_and_artifacts_are_repo_relative(self):
        policy = copy.deepcopy(self.policy); policy['fixture'] = '../outside'
        with self.assertRaises(ProtocolError): self.resolve(policy=policy)

    def test_provenance_selectors_must_be_empty(self):
        req = copy.deepcopy(self.request); req['check_packs'][-1]['selectors'] = ['anything']
        with self.assertRaises(ProtocolError): self.resolve(req)

    def test_gradle_method_selector_uses_dot_not_hash(self):
        req = copy.deepcopy(self.request); req['check_packs'][0]['selectors'] = ['org.example.FeatureTest#works']
        with self.assertRaises(ProtocolError): self.resolve(req)

    def test_full_and_focused_gametests_are_mutually_exclusive(self):
        req = copy.deepcopy(self.request); req['check_packs'].append({'pack_id': 'full_gametest', 'kind': 'full_gametest', 'selectors': []})
        with self.assertRaises(ProtocolError): self.resolve(req)

    def test_duplicate_artifact_and_non_json_input_are_protocol_errors(self):
        policy = copy.deepcopy(self.policy); policy['package_artifacts'] = ['build/out.jar', 'build/out.jar']
        with self.assertRaises(ProtocolError): self.resolve(policy=policy)
        req = copy.deepcopy(self.request); req['extra'] = object()
        with self.assertRaises(ProtocolError): self.resolve(req)

    def test_unhashable_unknown_pack_fields_are_protocol_errors(self):
        req = copy.deepcopy(self.request); req['check_packs'][0]['pack_id'] = []
        with self.assertRaises(ProtocolError): self.resolve(req)
        policy = copy.deepcopy(self.policy); policy['coverage']['c1'] = [[]]
        with self.assertRaises(ProtocolError): self.resolve(policy=policy)
        policy = copy.deepcopy(self.policy); policy['package_artifacts'] = ['/outside.jar']
        with self.assertRaises(ProtocolError): self.resolve(policy=policy)


if __name__ == '__main__': unittest.main()
