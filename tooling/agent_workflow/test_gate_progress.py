import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch
from pathlib import Path

try:
    from . import gate_progress as gp
except ImportError:
    import gate_progress as gp


class GateProgressTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='gate-progress-', dir=Path.home() / 'AppData/Local/Temp/opencode')
        self.root = Path(self.temp.name) / 'exchange'
        (self.root / 'candidates').mkdir(parents=True)

    def tearDown(self):
        self.temp.cleanup()

    def candidate(self, ident, author='author-a', snapshot='snapshot-1', **extra):
        c = {'id': ident, 'snapshot_key': snapshot, 'worktree': {'worktree': author}, **extra}
        (self.root / 'candidates' / f'{ident}.json').write_text(json.dumps(c), encoding='utf-8')
        return c

    def progress(self, ident, c, **values):
        (self.root / 'progress').mkdir(exist_ok=True)
        row = {'candidate_id': ident, 'snapshot_key': c['snapshot_key'], 'phase': 'running', 'updated_at': 1, **values}
        (self.root / 'progress' / f'{ident}.json').write_text(json.dumps(row), encoding='utf-8')

    def test_update_contract_binds_sidecar_and_creates_board(self):
        c = self.candidate('one')
        r = gp.update(self.root, c, 'running', check_index=1, total=2, argv=['python', 'check'], log='run.log', runner_pid=4, child_pid=5, started=10, detail='checking')
        saved = json.loads((self.root / 'progress/one.json').read_text(encoding='utf-8'))
        self.assertEqual((saved['candidate_id'], saved['snapshot_key'], saved['phase']), ('one', 'snapshot-1', 'running'))
        self.assertTrue(saved['updated_at'])
        self.assertEqual(r['check_index'], 1)
        self.assertTrue((self.root / 'integration-status.html').is_file())

    def test_stage_clock_stable_for_same_phase(self):
        c = self.candidate('clock')
        first = gp.update(self.root, c, 'running', argv=['python', 'test'], log='check.log', runner_pid=42, child_pid=43)
        with patch('tooling.agent_workflow.gate_progress.time.time', return_value=first['updated_at'] + 20):
            second = gp.update(self.root, c, 'running')
        self.assertEqual(first['stage_started_at'], second['stage_started_at'])
        for key in ('argv', 'log', 'runner_pid', 'child_pid'):
            self.assertEqual(first[key], second[key])

    def test_admitted_and_review_liveness_contract(self):
        admitted = self.candidate('admitted', gate_request={'session_id': 'lead-a'})
        self.progress('admitted', admitted, phase='admitted', updated_at=9999999999)
        row = next(x for x in gp._rows(self.root) if x['id'] == 'admitted')
        self.assertEqual(row['state'], 'admitted')
        review = self.candidate('review', author='b', gate_request={'session_id': 'lead-b'})
        self.progress('review', review, phase='awaiting_review', updated_at=1)
        row = next(x for x in gp._rows(self.root) if x['id'] == 'review')
        self.assertEqual(gp._view_model(row, 10000)['title'], 'UNCONFIRMED / STALLED')
        (self.root / 'gate-health').mkdir()
        (self.root / 'gate-health/review.json').write_text(json.dumps({
            'candidate_id': 'review', 'snapshot_key': 'snapshot-1', 'session_id': 'lead-b',
            'state': 'active', 'checked_at': 9999, 'last_activity': 9999, 'reason': 'fresh assistant/tool activity observed', 'registered_at': 1,
            'recovery_count': 0, 'receipt': 'r', 'terminal_message_id': None}), encoding='utf-8')
        row = next(x for x in gp._rows(self.root) if x['id'] == 'review')
        self.assertTrue(gp._view_model(row, 10000)['fresh'])
        self.assertFalse(gp._view_model(row, 10000)['stale'])

    def test_liveness_must_match_session_and_terminal_verdict_wins(self):
        c = self.candidate('bound', gate_request={'session_id': 'expected'},
                           gate_outcome={'result': 'ready_for_owner'})
        (self.root / 'gate-health').mkdir()
        (self.root / 'gate-health/bound.json').write_text(json.dumps({
            'candidate_id': 'bound', 'snapshot_key': 'snapshot-1', 'session_id': 'wrong',
            'state': 'blocked', 'checked_at': 9999999999}), encoding='utf-8')
        row = next(x for x in gp._rows(self.root) if x['id'] == 'bound')
        self.assertEqual(row['health'], {})
        self.assertEqual(row['state'], 'ready_for_owner')

    def test_observing_and_api_unavailable_stay_unconfirmed_despite_fresh_polls(self):
        (self.root / 'gate-health').mkdir()
        for ident, state, reason in (('observing', 'observing', 'Unconfirmed: no current execution evidence'),
                                     ('api-down', 'api_unavailable', 'service unavailable')):
            c = self.candidate(ident, author=ident, gate_request={'session_id': 'lead-' + ident})
            self.progress(ident, c, phase='awaiting_review', updated_at=100)
            health = {'candidate_id': ident, 'snapshot_key': 'snapshot-1', 'session_id': 'lead-' + ident,
                      'state': state, 'checked_at': 9990, 'last_activity': 100,
                      'reason': reason, 'registered_at': 1, 'recovery_count': 0}
            (self.root / 'gate-health' / (ident + '.json')).write_text(json.dumps(health), encoding='utf-8')
            row = next(x for x in gp._rows(self.root) if x['id'] == ident)
            model = gp._view_model(row, 10000)
            self.assertTrue(model['stale'])
            self.assertIn('UNCONFIRMED', model['title'])
            if state == 'api_unavailable':
                self.assertIn(reason, row['detail'])
                self.assertIn('OPERATIONAL STATUS UNAVAILABLE', model['title'])
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('UNCONFIRMED — no current execution proof', page)
        self.assertIn('OPERATIONAL STATUS UNAVAILABLE — UNCONFIRMED', page)

    def test_polling_does_not_extend_activity_or_stage_age(self):
        c = {'id': 'polling', 'snapshot_key': 'snapshot-1', 'state': 'active',
             'updated': 100, 'stage_started_at': 100,
             'health': {'state': 'active', 'checked_at': 9999, 'last_activity': 100,
                        'reason': 'fresh assistant/tool activity observed'}}
        model = gp._view_model(c, 10000)
        self.assertFalse(model['fresh'])
        self.assertTrue(model['stale'])
        self.assertEqual(model['title'], 'UNCONFIRMED / STALLED')

    def test_final_verdict_wins_unknown_health_in_python_and_browser(self):
        c = self.candidate('final', gate_request={'session_id': 'lead-final'},
                           gate_outcome={'result': 'ready_for_owner'})
        (self.root / 'gate-health').mkdir()
        (self.root / 'gate-health/final.json').write_text(json.dumps({
            'candidate_id': 'final', 'snapshot_key': 'snapshot-1', 'session_id': 'lead-final',
            'state': 'api_unavailable', 'checked_at': 9999, 'last_activity': None,
            'reason': 'unknown'}), encoding='utf-8')
        row = next(x for x in gp._rows(self.root) if x['id'] == 'final')
        self.assertEqual(row['state'], 'ready_for_owner')
        self.assertEqual(gp._view_model(row, 10000)['title'], 'ready_for_owner')
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('"state": "ready_for_owner"', page)

    def test_three_column_board_dom_uses_current_owner_and_live_check_proof(self):
        now = 1_800_000_000
        live = self.candidate('live-check', author='tree-a', sprint_id='sprint-a', revision_sequence=1,
                              submitted_at=now - 1000)
        newer = self.candidate('newer-head', author='tree-a', sprint_id='sprint-a', revision_sequence=2,
                                submitted_at=now - 10)
        queued = self.candidate('queued-check', author='tree-a', sprint_id='sprint-b', revision_sequence=1,
                                submitted_at=now - 900)
        needs = self.candidate('needs-fix', author='tree-b', sprint_id='sprint-c', revision_sequence=1,
                               gate_outcome={'result': 'needs_changes', 'findings': ['Attach the missing evidence.', 'Re-run the focused check.']},
                               review={'report_path': str(self.root / 'reports' / 'needs.json')})
        ready = self.candidate('ready-now', author='tree-c', sprint_id='sprint-d', revision_sequence=1,
                               gate_outcome={'result': 'ready_for_owner'},
                               owner_review={'state': 'awaiting_owner', 'candidate_id': 'ready-now', 'snapshot_key': 'snapshot-1'})
        feedback = self.candidate('manual-feedback', author='tree-d', sprint_id='sprint-e', revision_sequence=1,
                                  gate_outcome={'result': 'ready_for_owner'},
                                  owner_review={'state': 'feedback_received', 'candidate_id': 'manual-feedback'})
        ghost = self.candidate('station-atlas-legend-ranking-r1', author='tree-e', sprint_id='atlas-a', revision_sequence=1)
        ghost2 = self.candidate('station-atlas-large-plans-r1', author='tree-e', sprint_id='atlas-b', revision_sequence=1)
        self.progress('live-check', live, phase='running', updated_at=now - 240, check_index=2, total=3,
                      argv=['gradlew.bat', 'focusedTest'], log=str(self.root / 'logs' / 'check.log'),
                      runner_pid=7001, child_pid=7002, started=now - 240)
        self.progress('queued-check', queued, phase='queued', updated_at=now - 300,
                      runner_pid=7003, started=now - 300)
        self.progress(ghost['id'], ghost, phase='running', updated_at=now - 10000, check_index=1,
                      total=1, argv=['gradlew.bat', 'old'], log='old.log', runner_pid=7991)
        self.progress(ghost2['id'], ghost2, phase='running', updated_at=now - 10000, check_index=1,
                      total=1, argv=['gradlew.bat', 'old'], log='old2.log', runner_pid=7992)
        ready_item = {'id': 'ready-now', 'candidate_id': 'ready-now', 'title': 'Owner test',
                      'sprint_id': 'sprint-d', 'worktree': {'worktree': 'tree-c'},
                      'requested_at': now - 30, 'manual_steps': ['Inspect the panel'], 'reason': 'current owner request'}
        groups = {'awaiting_owner': [ready_item],
                  'agent_work': [{'id': 'manual-feedback', 'candidate_id': 'manual-feedback',
                                  'title': 'Prior owner reply', 'worktree': {'worktree': 'tree-d'},
                                  'reason': 'owner responded; agent triage required'}],
                  'unconfirmed': [{'worktree': 'tree-f', 'sprint_id': 'legacy',
                                   'candidate_ids': ['legacy-unordered'],
                                   'reason': 'legacy candidate ordering is unknown; reconcile exact head'}],
                  'completed': [{'id': 'accepted-history', 'title': 'Old accepted work',
                                 'worktree': {'worktree': 'tree-z'}, 'reason': 'owner accepted'}]}
        (self.root / 'owner-feedback-observer.json').write_text(json.dumps({
            'status': 'unavailable', 'updated_at': now - 600, 'errors': ['observer unavailable', 'retry deferred']}), encoding='utf-8')
        with patch('tooling.agent_workflow.gate_progress.time.time', return_value=now), \
             patch('tooling.agent_workflow.gate_progress._pid_alive', side_effect=lambda pid: pid == 7001), \
             patch('tooling.agent_workflow.owner_attention.collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('id="validation-column"', page)
        self.assertIn('id="owner-column"', page)
        self.assertIn('id="other-column"', page)
        self.assertIn('@media(max-width:850px){.columns{grid-template-columns:1fr}', page)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
        node = shutil.which('node') or shutil.which('node.exe')
        if not node:
            self.skipTest('Node.js unavailable for isolated board DOM fixture')
        harness = r"""
class FixtureElement {
  constructor(tag){this.tag=tag;this.children=[];this.hidden=false;this.className='';this._text='';}
  append(...items){this.children.push(...items);}
  replaceChildren(...items){this.children=[...items];}
  set textContent(v){this._text=String(v);this.children=[];}
  get textContent(){return this._text+this.children.map(x=>x.textContent||'').join('');}
  get childNodes(){return this.children;}
}
const elements=new Map();
const document={createElement:t=>new FixtureElement(t),getElementById:id=>{if(!elements.has(id))elements.set(id,new FixtureElement('div'));return elements.get(id);}};
Date.now=()=>1800000000000;
"""
        inspect = r"""
console.log(JSON.stringify(Object.fromEntries(['validation-cards','owner-cards','owner-empty','other-cards','history-cards','observer-health'].map(id=>[id,document.getElementById(id).textContent]))));
"""
        result = subprocess.run([node, '-e', harness + script + inspect], capture_output=True,
                                text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        dom = json.loads(result.stdout.strip().splitlines()[-1])
        self.assertIn('live-check', dom['validation-cards'])
        self.assertIn('Check 2/3', dom['validation-cards'])
        self.assertIn('240s', dom['validation-cards'])
        self.assertIn('7001', dom['validation-cards'])
        self.assertIn('Open current check log', dom['validation-cards'])
        self.assertIn('gradlew.bat focusedTest', dom['validation-cards'])
        self.assertNotIn('queued-check', dom['validation-cards'])
        self.assertNotIn('station-atlas-legend-ranking-r1', dom['validation-cards'])
        self.assertNotIn('station-atlas-large-plans-r1', dom['validation-cards'])
        self.assertIn('Owner test', dom['owner-cards'])
        self.assertNotIn('manual-feedback', dom['owner-cards'])
        self.assertIn('Owner test', dom['owner-cards'])
        self.assertIn('queued-check', dom['other-cards'])
        self.assertIn('station-atlas-legend-ranking-r1', dom['other-cards'])
        self.assertIn('station-atlas-large-plans-r1', dom['other-cards'])
        self.assertIn('UNCONFIRMED / STALLED', dom['other-cards'])
        self.assertIn('needs-fix', dom['other-cards'])
        self.assertIn('Attach the missing evidence.', dom['other-cards'])
        self.assertIn('Re-run the focused check.', dom['other-cards'])
        self.assertIn('Open report', dom['other-cards'])
        self.assertIn('Prior owner reply', dom['other-cards'])
        self.assertNotIn('Old accepted work', dom['owner-cards'])
        self.assertIn('legacy candidate ordering is unknown', dom['other-cards'])
        self.assertIn('Owner-response queue is not verified', dom['observer-health'])
        self.assertIn('observer unavailable', dom['observer-health'])
        self.assertIn('Old accepted work', dom['history-cards'])

    def test_superseded_head_hidden_without_dropping_independent_project(self):
        self.candidate('old-head', author='tree-a', sprint_id='one', revision_sequence=1,
                       gate_request={'session_id': 'old'})
        self.candidate('new-head', author='tree-a', sprint_id='one', revision_sequence=2,
                       gate_request={'session_id': 'new'})
        self.candidate('other-project', author='tree-b', sprint_id='two', revision_sequence=1,
                       gate_request={'session_id': 'other'})
        rows = gp._rows(self.root)
        ids = {row['id'] for row in rows}
        self.assertNotIn('old-head', ids)
        self.assertIn('new-head', ids)
        self.assertIn('other-project', ids)

    def test_render_honors_explicit_output(self):
        target = self.root / 'reports' / 'custom.html'
        self.assertEqual(gp.render(self.root, target), target.resolve())
        self.assertTrue(target.is_file())

    def test_snapshot_mismatch_ignored_and_no_request_inference(self):
        c = self.candidate('one', gate_request={'session_id': 'session'})
        self.progress('one', c, snapshot_key='old-snapshot', phase='running')
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('awaiting activity', page)
        self.assertNotIn('running —', page)

    def test_queued_running_stale_and_verdict_precedence(self):
        queued = self.candidate('queued', author='a')
        self.progress('queued', queued, phase='queued', updated_at=9999999999)
        finished = self.candidate('finished', author='b', gate_outcome={'result': 'ready_for_owner'}, author_completion={'status': 'delivered'}, gate_request={'session_id': 'lead-1'})
        self.progress('finished', finished, phase='running', updated_at=1)
        stale = self.candidate('stale', author='c')
        self.progress('stale', stale, phase='running', updated_at=1)
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('queued', page)
        self.assertIn('UNCONFIRMED / STALLED', page)
        self.assertIn('Date.now()/1000', page)
        self.assertIn('"verdict": "ready_for_owner"', page)
        self.assertIn('"delivery": "delivered"', page)
        model = {'state': 'running', 'phase': 'running', 'updated': 100, 'started': 50}
        self.assertFalse(gp._view_model(model, 140)['stale'])
        self.assertEqual(gp._view_model(model, 200)['title'], 'UNCONFIRMED / STALLED')
        self.assertEqual(gp._view_model(model, 140)['age'], 40)
        self.assertEqual(gp._view_model(model, 140)['elapsed'], 90)
        model['verdict'] = 'ready_for_owner'
        model['state'] = 'ready_for_owner'
        self.assertEqual(gp._view_model(model, 200)['elapsed'], 50)
        self.assertIn('lead-1', page)

    def test_latest_per_author_and_escaping(self):
        self.candidate('old', author='same', submitted_at=1)
        self.candidate('new', author='same', submitted_at=2, gate_outcome={'result': 'needs_changes'}, owner_decision={'note': '<script>'})
        unsafe = self.candidate('unsafe', author='<img src=x>', snapshot='snap<&')
        self.progress('unsafe', unsafe, phase='running', detail='<script>alert(1)</script>', log='unsafe <file>.txt')
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('new', page)
        # Legacy ordering is deliberately surfaced as unconfirmed, never owner-actionable.
        self.assertIn('old', page)
        self.assertIn('legacy candidate ordering is unknown', page)
        self.assertIn('\\u003cscript\\u003e', page)
        self.assertNotIn('<script>alert(1)</script>', page)

    def test_active_older_candidate_retained_and_phase_statuses(self):
        self.candidate('newer', author='same', submitted_at=99)
        older = self.candidate('active-old', author='same', submitted_at=1)
        self.progress('active-old', older, phase='blocked', updated_at=2)
        pending = self.candidate('pending', author='pending-author')
        self.progress('pending', pending, phase='pending')
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('active-old', page)
        self.assertIn('pending', page)
        self.assertIn('"phase": "blocked"', page)
        self.assertIn('"state": "UNCONFIRMED / STALLED"', page)

    def test_concurrent_updates_leave_valid_board(self):
        c = self.candidate('one')
        errors = []
        def work(i):
            try:
                gp.update(self.root, c, 'running', detail=str(i))
            except Exception as exc:
                errors.append(exc)
        threads = [threading.Thread(target=work, args=(i,)) for i in range(8)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertFalse(errors)
        board = (self.root / 'integration-status.html').read_text(encoding='utf-8')
        self.assertIn('Integration status', board)

    def test_owner_first_queue_separates_confirmed_agent_and_unknown(self):
        items = [
            {'id': 'review-a', 'title': 'Alpha', 'sprint_id': 'alpha', 'worktree': {'worktree': 'C:/repo/worktree-a'}, 'requested_at': 100, 'manual_steps': ['Check door', 'Report result'], 'origin_session_id': 'session-a'},
            {'id': 'review-b', 'title': 'Beta', 'sprint_id': 'beta', 'worktree': {'worktree': 'C:/repo/worktree-b'}, 'requested_at': 100, 'manual_steps': ['Check pump'], 'origin_session_id': 'session-b'},
        ]
        groups = {'awaiting_owner': items, 'agent_work': [{'id': 'triage', 'reason': 'owner responded; agent triage required'}],
                  'unconfirmed': [{'id': 'legacy', 'reason': 'legacy request'}], 'completed': [{'id': 'accepted'}]}
        (self.root / 'owner-feedback-observer.json').write_text(json.dumps({'status': 'watching', 'updated_at': 9999999999, 'errors': []}), encoding='utf-8')
        with patch('tooling.agent_workflow.owner_attention.collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('Alpha', page); self.assertIn('Beta', page)
        self.assertIn('legacy request', page); self.assertIn('triage', page)
        self.assertIn('Ready for owner', page)
        self.assertIn('Validation actively running', page)
        self.assertIn('Other states', page)
        self.assertIn("for(const x of completed)", page)
        self.assertIn('manual_steps', page)
        self.assertIn('function render(now)', page)
        self.assertIn('Date.now()/1000', page)
        self.assertIn("oh.replaceChildren()", page)
        self.assertIn('Unconfirmed — reconcile evidence', page)

    def test_unconfirmed_prevents_verified_empty_queue(self):
        (self.root / 'owner-feedback-observer.json').write_text(json.dumps({'status': 'idle', 'updated_at': 9999999999, 'errors': []}), encoding='utf-8')
        groups = {'awaiting_owner': [], 'agent_work': [], 'unconfirmed': [{'id': 'old', 'reason': 'head unknown'}], 'completed': []}
        with patch('tooling.agent_workflow.owner_attention.collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('Owner queue status is unknown; do not infer an empty queue.', page)
        self.assertIn('head unknown', page)
        self.assertIn('Unconfirmed — reconcile evidence', page)

    def test_report_path_normalized_and_legacy_worktree_string(self):
        from tooling.agent_workflow import owner_attention
        legacy = {'id': 'legacy', 'snapshot_key': 'old', 'sprint_id': 'old-sprint', 'worktree': {'worktree': 'C:/agents/legacy'}, 'revision_sequence': 0}
        current = {'id': 'registered', 'snapshot_key': 'snap', 'sprint_id': 'sprint', 'revision_sequence': 1,
                   'worktree': {'worktree': 'C:/agents/active'}, 'gate_outcome': {'result': 'ready_for_owner'},
                   'owner_review': {'state': 'awaiting_owner', 'candidate_id': 'registered', 'snapshot_key': 'snap', 'origin_session_id': 'session',
                                    'requested_at': 100, 'readiness': {'eligible': True, 'checked_at': 9999999999, 'snapshot_key': 'snap', 'review_sha256': None, 'validation_sha256': None}},
                   'review': {'report_path': str(self.root / 'reports' / 'review.html')}}
        for c in (legacy, current):
            (self.root / 'candidates' / (c['id'] + '.json')).write_text(json.dumps(c), encoding='utf-8')
        class Workflow:
            @staticmethod
            def readjson(path): return json.loads(Path(path).read_text(encoding='utf-8'))
        groups = owner_attention.collect(self.root, workflow=Workflow)
        self.assertTrue(any(isinstance(item.get('worktree'), str) and 'legacy' in item['worktree'].casefold() for item in groups['unconfirmed']))
        self.assertEqual(groups['awaiting_owner'][0]['report_path'], str(self.root / 'reports' / 'review.html'))
        with patch('tooling.agent_workflow.owner_attention.collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn((self.root / 'reports' / 'review.html').resolve().as_uri(), page)
        self.assertIn("typeof w==='string'?w", page)

    @unittest.skipUnless(os.name == 'nt', 'Windows PowerShell launcher compatibility test')
    def test_launcher_noopen_with_spaced_paths_under_both_powershells(self):
        repo = self.root.parent / 'MoonStation14 fixture'
        shared = self.root.parent / 'MoonStation14-shared' / 'tooling' / 'agent_workflow'
        exchange = self.root.parent / 'Asset Building Workspace' / 'work' / 'agent_exchange'
        repo.mkdir(parents=True)
        shared.mkdir(parents=True)
        (exchange / 'candidates').mkdir(parents=True)
        shutil.copy2(Path(__file__).parents[2] / 'integration-status.ps1', repo / 'integration-status.ps1')
        shutil.copy2(Path(__file__).with_name('gate_progress.py'), shared / 'gate_progress.py')
        (shared / 'owner_feedback.py').write_text(
            "import argparse,json\nfrom pathlib import Path\np=argparse.ArgumentParser();p.add_argument('--exchange');g=p.add_mutually_exclusive_group(required=True);g.add_argument('--once',action='store_true');g.add_argument('--ensure-started',action='store_true');a=p.parse_args();r=Path(a.exchange);f=r/'fixture-feedback-calls.json';calls=json.loads(f.read_text()) if f.exists() else [];calls.append('once' if a.once else 'ensure-started');f.write_text(json.dumps(calls))\n",
            encoding='utf-8')
        for host in ('powershell', 'pwsh'):
            executable = shutil.which(host) or shutil.which(host + '.exe')
            if not executable:
                self.fail(f'required PowerShell host is unavailable: {host}')
            result = subprocess.run(
                [executable, '-NoProfile', '-WindowStyle', 'Hidden', '-File', str(repo / 'integration-status.ps1'), '-NoOpen'],
                capture_output=True, text=True, encoding='utf-8', timeout=30,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(Path(result.stdout.strip()), exchange / 'integration-status.html')
        self.assertTrue((exchange / 'integration-status.html').is_file())
        self.assertEqual(json.loads((exchange / 'fixture-feedback-calls.json').read_text()), ['once', 'ensure-started', 'once', 'ensure-started'])


if __name__ == '__main__':
    unittest.main()
