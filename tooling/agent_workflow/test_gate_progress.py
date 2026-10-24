import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
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
        try:
            from . import agent_status
            from . import owner_attention
        except ImportError:
            import agent_status
            import owner_attention
        self.agent_status = agent_status
        self.owner_attention = owner_attention
        self.roster_patch = patch.object(agent_status, 'roster', side_effect=self.fixture_roster)
        self.roster_patch.start()
        self.addCleanup(self.roster_patch.stop)
        self.write_activity_snapshot(time.time())

    def fixture_roster(self, root, candidates=()):
        values = []
        for candidate in candidates:
            raw = self.agent_status._worktree(candidate.get('worktree'))
            if raw and raw not in {item['id'] for item in values}:
                values.append({'id': raw, 'worktree': raw, 'label': Path(raw).name,
                               'core': Path(raw).name.startswith('core-')})
        return values

    def write_activity_snapshot(self, now):
        roster = self.fixture_roster(self.root, [c for p in (self.root / 'candidates').glob('*.json')
                                                 if (c := gp._read(p))])
        (self.root / self.agent_status.SNAPSHOT).write_text(json.dumps({
            'checked_at': now, 'errors': [],
            'agents': [dict(slot, activity='idle', summary='Fixture idle') for slot in roster],
            'active_sessions': {},
        }), encoding='utf-8')
    def tearDown(self):
        self.temp.cleanup()

    def dom(self, page, now):
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
        node = shutil.which('node') or shutil.which('node.exe')
        if not node:
            self.skipTest('Node.js unavailable for isolated board DOM fixture')
        syntax = subprocess.run([node, '--check', '--input-type=commonjs'], input=script,
                                capture_output=True, text=True, encoding='utf-8', timeout=15)
        self.assertEqual(syntax.returncode, 0, syntax.stderr)
        try:
            from .test_gate_progress_dom_support import NODE_DOM_HARNESS, run_node
        except ImportError:
            from test_gate_progress_dom_support import NODE_DOM_HARNESS, run_node
        harness = NODE_DOM_HARNESS.replace('__NOW__', str(now))
        inspect = r"""
console.log(JSON.stringify({
 text:Object.fromEntries(['working-cards','waiting-cards','active-cards','ready-cards','health-banner'].map(id=>[id,document.getElementById(id).textContent])),
 count:Object.fromEntries(['working','waiting','active','ready'].map(id=>[id,document.getElementById(id+'-cards').childNodes.filter(x=>x.tag==='article').length])),
 links:(function walk(e){return (e.tag==='a'?[e.href]:[]).concat(e.childNodes.flatMap(walk))})(document.getElementById('ready-cards')),
 alertCount:['working','waiting','active','ready'].reduce((n,id)=>n+(function walk(e){return (e.attributes.role==='alert'?1:0)+e.childNodes.reduce((m,x)=>m+walk(x),0)})(document.getElementById(id+'-cards')),0)
}));
"""
        result = run_node(node, harness + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout.strip().splitlines()[-1])

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
        with patch.object(gp.time, 'time', return_value=first['updated_at'] + 20):
            second = gp.update(self.root, c, 'running')
            self.assertEqual(first['stage_started_at'], second['stage_started_at'])
            for key in ('argv', 'log', 'runner_pid', 'child_pid'):
                self.assertEqual(first[key], second[key])

    def test_execution_board_requires_bound_live_claim_and_terminal_phase_keeps_evidence(self):
        try:
            from . import gate_execution
        except ImportError:
            import gate_execution
        c=self.candidate('worker')
        gate_execution._write(gate_execution._path(self.root,'worker'),{'job_id':'worker','candidate_id':'worker',
            'snapshot_key':c['snapshot_key'],'state':'running','claim':'a'*64,'attempt':1,'worker_pid':4321})
        with patch.object(gp,'_pid_alive',return_value=True):
            self.assertTrue(gp._execution_worker_alive(self.root,c))
        with patch.object(gp,'_pid_alive',return_value=False):
            self.assertFalse(gp._execution_worker_alive(self.root,c))
        old=self.progress('worker',c,phase='running',check_index=2,total=3,runner_pid=98,child_pid=99,argv=['python'],log='check.log')
        gp.update(self.root,c,'interrupted',detail='worker dead')
        saved=json.loads((self.root/'progress'/'worker.json').read_text(encoding='utf-8'))
        self.assertEqual(saved['phase'],'interrupted')
        self.assertEqual((saved['check_index'],saved['runner_pid'],saved['child_pid']),(2,98,99))

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
                           revision_sequence=1, gate_outcome={'result': 'ready_for_owner'})
        (self.root / 'gate-health').mkdir()
        (self.root / 'gate-health/final.json').write_text(json.dumps({
            'candidate_id': 'final', 'snapshot_key': 'snapshot-1', 'session_id': 'lead-final',
            'state': 'api_unavailable', 'checked_at': 9999, 'last_activity': None,
            'reason': 'unknown'}), encoding='utf-8')
        row = next(x for x in gp._rows(self.root) if x['id'] == 'final')
        self.assertEqual(row['state'], 'ready_for_owner')
        self.assertEqual(gp._view_model(row, 10000)['title'], 'ready_for_owner')
        projected = self.agent_status.project(self.root)
        self.assertNotIn('ready_manual', [a['column'] for a in projected['agents']])

    def test_four_column_agent_board_dom_folds_current_work_and_live_check_proof(self):
        now = 1_800_000_000
        live = self.candidate('live-check', author='tree-a', sprint_id='sprint-a', revision_sequence=1,
                              submitted_at=now - 1000)
        newer = self.candidate('newer-head', author='tree-a', sprint_id='sprint-a', revision_sequence=2,
                                submitted_at=now - 10)
        queued = self.candidate('queued-check', author='tree-a', sprint_id='sprint-b', revision_sequence=1,
                                submitted_at=now - 900)
        waiting = self.candidate('waiting-validation', author='tree-q', sprint_id='sprint-q', revision_sequence=1)
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
        self.progress('waiting-validation', waiting, phase='queued', updated_at=now - 20,
                      runner_pid=7004, started=now - 20)
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
        with patch.object(gp.time, 'time', return_value=now), \
             patch.object(gp, '_pid_alive', side_effect=lambda pid: pid in {7001, 7004}), \
             patch.object(self.owner_attention, 'collect', return_value=groups):
            self.write_activity_snapshot(now)
            snapshot = json.loads((self.root / self.agent_status.SNAPSHOT).read_text(encoding='utf-8'))
            for agent in snapshot['agents']:
                agent['activity'] = 'working'
            (self.root / self.agent_status.SNAPSHOT).write_text(json.dumps(snapshot), encoding='utf-8')
            page = gp.render(self.root).read_text(encoding='utf-8')
        headings = ['Working', 'Waiting on Validation', 'Active Validation', 'Ready for Manual testing']
        positions = [page.index('>'+heading+' <') for heading in headings]
        self.assertEqual(positions, sorted(positions))
        self.assertIn('grid-template-columns:repeat(4,minmax(0,1fr))', page)
        self.assertIn('@media(max-width:1150px){.columns{grid-template-columns:repeat(2,minmax(0,1fr))}', page)
        self.assertIn('@media(max-width:650px){.columns{grid-template-columns:1fr}', page)
        self.assertIn('Owner response and technical validation are separate. This dashboard is read-only.', page)
        dom = self.dom(page, now)
        self.assertIn('live-check', dom['text']['active-cards'])
        self.assertIn('Check 2/3', dom['text']['active-cards'])
        self.assertIn('240s', dom['text']['active-cards'])
        self.assertIn('7001', dom['text']['active-cards'])
        self.assertIn('Open current check log', dom['text']['active-cards'])
        self.assertIn('gradlew.bat focusedTest', dom['text']['active-cards'])
        self.assertIn('sprint-b', dom['text']['active-cards'])
        self.assertIn('Other current work', dom['text']['active-cards'])
        self.assertIn('sprint-a', dom['text']['active-cards'])
        self.assertIn('waiting-validation', dom['text']['waiting-cards'])
        self.assertNotIn('station-atlas-legend-ranking-r1', dom['text']['active-cards'])
        self.assertNotIn('station-atlas-large-plans-r1', dom['text']['active-cards'])
        self.assertIn('Owner test', dom['text']['ready-cards'])
        self.assertIn('Inspect the panel', dom['text']['ready-cards'])
        self.assertNotIn('manual-feedback', dom['text']['ready-cards'])
        self.assertEqual(dom['count']['active'], 1)
        self.assertIn('station-atlas-legend-ranking-r1', dom['text']['working-cards'])
        self.assertIn('atlas-b', dom['text']['working-cards'])
        self.assertIn('needs-fix', dom['text']['working-cards'])
        self.assertIn('Attach the missing evidence.', dom['text']['working-cards'])
        self.assertIn('Re-run the focused check.', dom['text']['working-cards'])
        self.assertNotIn('Technical report', dom['text']['working-cards'])
        self.assertIn('manual-feedback', dom['text']['working-cards'])
        self.assertIn('UNCONFIRMED', dom['text']['working-cards'])
        self.assertIn('errors; status may be unknown', dom['text']['health-banner'])
        self.assertIn('observer unavailable', dom['text']['health-banner'])

    def test_core_idle_unknown_and_noncore_idle_are_distinct_in_dom(self):
        now = 1_800_000_000
        agents = [{'id': f'C:/agents/core-{i}', 'label': f'Agent {i:02d}',
                   'worktree': f'C:/agents/core-{i}', 'core': True, 'column': 'working',
                   'activity': 'idle', 'summary': 'Idle', 'additional_work': []}
                  for i in range(1, 5)]
        agents.extend([
            {'id': 'C:/agents/consultant', 'label': 'Main consultant',
             'worktree': 'C:/agents/consultant', 'core': False, 'column': 'working',
             'activity': 'idle', 'summary': 'Idle'},
            {'id': 'C:/agents/unknown', 'label': 'Agent 06',
             'worktree': 'C:/agents/unknown', 'core': True, 'column': 'working',
             'activity': 'unknown', 'summary': 'Activity unknown'},
        ])
        projection = {'agents': agents, 'checked_at': now, 'errors': ['Agent 06 activity API unavailable']}
        with patch.object(gp.time, 'time', return_value=now), \
             patch.object(self.agent_status, 'project', return_value=projection):
            page = gp.render(self.root).read_text(encoding='utf-8')
        dom = self.dom(page, now)
        self.assertEqual(dom['count']['working'], 6)
        self.assertEqual(dom['alertCount'], 4)
        self.assertIn('Agent 01 — Idle', dom['text']['working-cards'])
        self.assertIn('Core agent idle — needs work', dom['text']['working-cards'])
        self.assertIn('Main consultant — Idle', dom['text']['working-cards'])
        self.assertIn('Agent 06 — Activity unknown', dom['text']['working-cards'])
        self.assertIn('Agent 06 activity API unavailable', dom['text']['health-banner'])
        self.assertNotIn('Idle', dom['text']['working-cards'].split('Agent 06 — Activity unknown')[-1])

    def test_active_review_card_does_not_invent_check_or_idle_alert(self):
        now = 1_800_000_000
        review = {'id': 'review-agent', 'label': 'Agent 02', 'worktree': 'fixture-agent-02',
                  'core': True, 'column': 'active_validation', 'activity': 'idle',
                  'summary': 'Review current candidate', 'candidate_id': 'candidate-review',
                  'validation_stage': 'review', 'additional_work': []}
        with patch.object(gp.time, 'time', return_value=now), \
             patch.object(gp, '_project_agents', return_value={
                 'agents': [review], 'checked_at': now, 'errors': []}):
            page = gp.render(self.root).read_text(encoding='utf-8')
        dom = self.dom(page, now)
        active = dom['text']['active-cards']
        self.assertEqual(dom['count']['active'], 1)
        self.assertIn('Integration lead reviewing candidate', active)
        self.assertNotIn('Check null/null', active)
        self.assertNotIn('PID null', active)
        self.assertEqual(dom['alertCount'], 0)

    def test_real_agent_status_projection_idle_validation_and_manual_are_not_idle_alerts(self):
        agent_status = self.agent_status

        now = 1_800_000_000
        worktrees = [str((Path(self.temp.name) / f'core-{i:02d}').resolve()) for i in range(1, 4)]
        slots = [{'id': agent_status._worktree(path), 'label': f'Agent {i:02d}', 'worktree': path,
                  'core': True} for i, path in enumerate(worktrees, 1)]
        snapshot = {'checked_at': now, 'errors': [], 'agents': [dict(slot, activity='idle') for slot in slots],
                    'active_sessions': {}}
        rows = [
            {'author': slots[0]['id'], 'id': 'queued-a', 'sprint_id': 's-a', 'validation_active': False,
             'phase': 'queued', 'state': 'queued', 'health': {'state': 'active'}, 'updated_at': now,
             'runner_pid': 333},
            {'author': slots[1]['id'], 'id': 'live-b', 'sprint_id': 's-b', 'validation_active': True,
             'phase': 'running', 'state': 'running', 'health': {}, 'updated_at': now,
             'check_index': 1, 'total': 2, 'runner_pid': 222, 'started': now, 'log': 'check.log'},
        ]
        attention = {'awaiting_owner': [{'candidate_id': 'manual-c', 'worktree': slots[2]['worktree'], 'title': 'Inspect console',
                                          'manual_steps': ['Verify the indicator'],
                                          'fixture': 'manual-fixture'}],
                     'agent_work': [], 'unconfirmed': [], 'completed': []}
        rows[2:2] = [{'author': slots[2]['id'], 'id': 'manual-c', 'sprint_id': 's-c',
                      'validation_active': False, 'phase': 'finished', 'state': 'ready_for_owner',
                      'health': {}, 'updated_at': now}]
        heads = [{'head': {'id': row['id'], 'worktree': row['author'], 'sprint_id': row['sprint_id'],
                           'gate_request': {'session_id': 'lead-queued'} if row['id'] == 'queued-a' else {},
                           'gate_outcome': {}, 'submitted_at': now}}
                 for row in rows]
        with patch.object(agent_status, 'roster', return_value=slots), \
             patch.object(self.owner_attention, 'current_work', return_value=heads), \
             patch.object(gp, '_pid_alive', side_effect=lambda pid: pid == 333), \
             patch.object(gp.time, 'time', return_value=now), \
             patch.object(gp, '_rows', return_value=rows), \
             patch.object(self.owner_attention, 'collect', return_value=attention):
            projection = agent_status._project(self.root, rows, attention, now, snapshot)
            (self.root / agent_status.SNAPSHOT).write_text(json.dumps(snapshot), encoding='utf-8')
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertEqual([(a['column'], a['activity']) for a in projection['agents']], [
            ('waiting_validation', 'idle'), ('active_validation', 'idle'), ('ready_manual', 'idle')])
        self.assertEqual([a['manual_steps'] for a in projection['agents'] if a['column'] == 'ready_manual'],
                         [['Verify the indicator']])
        dom = self.dom(page, now)
        self.assertIn('Waiting on validation', dom['text']['waiting-cards'])
        self.assertIn('Active validation', dom['text']['active-cards'])
        self.assertIn('Ready for manual testing', dom['text']['ready-cards'])
        self.assertIn('Verify the indicator', dom['text']['ready-cards'])
        self.assertEqual(dom['alertCount'], 0)

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
        self.progress('one', c, snapshot_key='old-snapshot', phase='running', check_index=7, total=7,
                      argv=['MISMATCH_ATTACKER_COMMAND'], log='MISMATCH_ATTACKER_LOG',
                      runner_pid=987654, child_pid=987655)
        row = next(r for r in gp._rows(self.root) if r['id'] == 'one')
        self.assertEqual(row['snapshot'], c['snapshot_key'][:12])
        self.assertNotEqual(row['phase'], 'running')
        self.assertNotEqual(row['state'], 'VALIDATION RUNNING')
        for field in ('runner_pid', 'child_pid', 'log', 'argv', 'check_index', 'total'):
            self.assertNotIn('MISMATCH_ATTACKER', str(row.get(field)))
        self.assertNotEqual(row['runner_pid'], 987654)
        self.assertNotEqual(row['log'], 'MISMATCH_ATTACKER_LOG')
        page = gp.render(self.root).read_text(encoding='utf-8')
        data = json.loads(re.search(r'const data=(.*?);\nfunction elem', page, re.S).group(1))
        self.assertFalse(any(a['column'] == 'active_validation' for a in data['agents']))
        self.assertFalse(any(a.get('candidate_id') == 'one' and a['column'] == 'waiting_validation'
                             for a in data['agents']))

    def test_queued_running_stale_and_verdict_precedence(self):
        queued = self.candidate('queued', author='a', sprint_id='queued-sprint', revision_sequence=1)
        self.progress('queued', queued, phase='queued', updated_at=9999999999)
        finished = self.candidate('finished', author='b', sprint_id='finished-sprint', revision_sequence=1, gate_outcome={'result': 'ready_for_owner'}, author_completion={'status': 'delivered'}, gate_request={'session_id': 'lead-1'})
        self.progress('finished', finished, phase='running', updated_at=1)
        stale = self.candidate('stale', author='c', sprint_id='stale-sprint', revision_sequence=1)
        self.progress('stale', stale, phase='running', updated_at=1)
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('queued', page)
        rows = {r['id']: r for r in gp._rows(self.root)}
        self.assertEqual(gp._view_model(rows['stale'], time.time())['title'], 'UNCONFIRMED / STALLED')
        self.assertIn('stale', page)
        self.assertIn('Date.now()/1000', page)
        model = {'state': 'running', 'phase': 'running', 'updated': 100, 'started': 50}
        self.assertFalse(gp._view_model(model, 140)['stale'])
        self.assertEqual(gp._view_model(model, 200)['title'], 'UNCONFIRMED / STALLED')
        self.assertEqual(gp._view_model(model, 140)['age'], 40)
        self.assertEqual(gp._view_model(model, 140)['elapsed'], 90)
        model['verdict'] = 'ready_for_owner'
        model['state'] = 'ready_for_owner'
        self.assertEqual(gp._view_model(model, 200)['elapsed'], 50)
        self.assertEqual(rows['finished']['session'], 'lead-1')
        self.assertNotIn('finished', [a['candidate_id'] for a in self.agent_status.project(self.root)['agents'] if a['column'] == 'ready_manual'])

    def test_latest_per_author_and_escaping(self):
        self.candidate('old', author='same', submitted_at=1)
        self.candidate('new', author='same', submitted_at=2, gate_outcome={'result': 'needs_changes'}, owner_decision={'note': '<script>'})
        unsafe = self.candidate('unsafe', author='<img src=x>', snapshot='snap<&')
        self.progress('unsafe', unsafe, phase='running', detail='<script>alert(1)</script>', log='unsafe <file>.txt')
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('new', page)
        unsafe_card = {'id': 'unsafe', 'label': 'Fixture agent', 'worktree': 'fixture', 'core': False, 'column': 'working', 'activity': 'working', 'summary': '<img src=x onerror=alert(1)>', 'additional_work': []}
        with patch.object(gp, '_project_agents', return_value={'agents': [unsafe_card], 'checked_at': time.time(), 'errors': []}):
            page = gp.render(self.root).read_text(encoding='utf-8')
        dom = self.dom(page, time.time())
        self.assertEqual(dom['count']['working'], 1)
        self.assertIn('<img src=x onerror=alert(1)>', dom['text']['working-cards'])
        self.assertNotIn('<img src=x onerror=alert(1)>', page)

    def test_superseded_nonrunning_history_hidden_and_current_head_visible(self):
        self.candidate('newer', author='same', sprint_id='one', revision_sequence=2, submitted_at=99)
        older = self.candidate('active-old', author='same', sprint_id='one', revision_sequence=1, submitted_at=1)
        self.progress('active-old', older, phase='blocked', updated_at=2)
        pending = self.candidate('pending', author='pending-author', sprint_id='two', revision_sequence=1)
        self.progress('pending', pending, phase='pending')
        page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertNotIn('active-old', page)
        self.assertIn('pending', page)
        stale_row = next(r for r in gp._rows(self.root) if r['id'] == 'pending')
        self.assertEqual(stale_row['state'], 'UNCONFIRMED / STALLED')
        self.assertIn('pending', page)

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
        with patch.object(self.owner_attention, 'collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('No agents in this state.', page)
        self.assertIn('Ready for Manual testing', page)
        self.assertIn('Waiting on Validation', page)
        self.assertIn('Active Validation', page)
        self.assertNotIn('completed', page.casefold())
        self.assertIn('manual_steps', page)
        self.assertIn('function render(now)', page)
        self.assertIn('Date.now()/1000', page)
        self.assertIn("host.replaceChildren()", page)
        self.assertIn('Activity unknown', page)

    def test_unconfirmed_prevents_verified_empty_queue(self):
        (self.root / 'owner-feedback-observer.json').write_text(json.dumps({'status': 'idle', 'updated_at': 9999999999, 'errors': []}), encoding='utf-8')
        groups = {'awaiting_owner': [], 'agent_work': [], 'unconfirmed': [{'id': 'old', 'reason': 'head unknown'}], 'completed': []}
        with patch.object(self.owner_attention, 'collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        self.assertIn('id="health-banner"', page)
        self.assertIn('Agent status unknown; do not infer an empty queue.', page)

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
        with patch.object(self.owner_attention, 'collect', return_value=groups):
            page = gp.render(self.root).read_text(encoding='utf-8')
        data = json.loads(re.search(r'const data=(.*?);\nfunction elem', page, re.S).group(1))
        active_path = self.agent_status._worktree('C:/agents/active')
        projected = next(a for a in data['agents'] if a['id'] == active_path)
        expected_url = (self.root / 'reports' / 'review.html').resolve().as_uri()
        self.assertEqual(projected['report_url'], expected_url)
        self.assertEqual(projected['worktree'], active_path)
        dom = self.dom(page, time.time())
        self.assertIn(expected_url, dom['links'])

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
