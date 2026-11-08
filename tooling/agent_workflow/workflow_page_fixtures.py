"""Durable small WR09 renderer fixture, independent of build artifacts."""
import copy


def sample():
    names = ('Building', 'Queued', 'Checking', 'Reviewing', 'Your testing', 'Accepted')
    columns = {}
    for index, name in enumerate(names):
        ident = ('run_building', 'run_queued', 'run_checking', 'run_reviewing', 'run_testing', 'run_accepted')[index]
        caps = ([], [], ['cancel_active'], ['cancel_active', 'retry_review'],
                ['manual', 'accept', 'request_changes', 'owner_cancel'], [])[index]
        columns[name] = [{
            'id': ident, 'run_id': ident, 'generation': 1, 'sequence': 2,
            'input_digest': f'{index + 1:064x}', 'label': f'Fixture {name}', 'phase': name,
            'agent': 'fixture-agent', 'revision': 'r1', 'step': 'CHECK', 'actor': 'Owner',
            'next_action': 'Review fixture evidence', 'badge': 'status unknown',
            'focused_result': 'PASS' if name in ('Your testing', 'Accepted') else 'unknown',
            'owner_state': 'OPEN', 'regression_state': 'NOT_STARTED',
            'details': {'criteria': [{'criterion_id': 'C1', 'description': 'Fixture criterion',
                                      'evidence_refs': ['artifact-fixture']}],
                        'findings': [{'code': 'F1', 'summary': 'Fixture finding'}],
                        'manual_steps': ['Inspect fixture result'], 'artifacts': [], 'events': [],
                        'deliveries': [], 'owner_decisions': []},
            'capabilities': caps,
            'bindings': {'run_id': ident, 'generation': 1, 'input_digest': f'{index + 1:064x}',
                         'sequence': 2, 'result_id': 'result-fixture',
                         'result_digest': 'result-digest-fixture',
                         'review_task_id': 'task_run_reviewing' if name == 'Reviewing' else None},
        }]
    return copy.deepcopy({
        'version': 1, 'available': True, 'captured_at_ms': 1760000000000,
        'connection': {'live': False, 'mode': 'saved', 'health': 'unknown'},
        'worktree': 'C:/fixture', 'columns': columns, 'history': [], 'faults': [],
        'system': {'native_occupancy': {'held': 0, 'unknown': 0, 'state': 'free'},
                   'queue': {'queued_tasks': 0, 'state': 'idle'},
                   'ai_availability': 'unknown'},
    })
