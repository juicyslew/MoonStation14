"""Reconcile review sessions after coordinator or transport loss."""


class ReviewQuiescence:
    def __init__(self, store, cancellation):
        self.store = store
        self.cancellation = cancellation
        self._unresolved = set()
        self._states = {}

    def _review_tasks(self):
        runs = self.store.list_runs_by_phase(('REVIEWING', 'STOPPING'))
        result = []
        for run_id in runs:
            saved = self.store.get_run(run_id)
            if saved is None:
                continue
            attempt = saved['run']['current_attempt']
            result.extend(task for task in self.store.list_tasks(run_id, attempt)
                          if task.get('kind') == 'REVIEW'
                          and task.get('state') in ('DISPATCHING', 'CLAIMED', 'RUNNING'))
        return result

    def reconcile(self, *, unresolved_only=False):
        confirmed, unknown, pending = [], [], []
        tasks = self._review_tasks()
        # A separate cancellation/cleanup owner may settle an orphan while it is
        # no longer in an active phase. Retire its health marker only on durable
        # resource-release proof, never merely because the task disappeared.
        for task_id in tuple(self._unresolved):
            task = self.store.get_task(task_id)
            if task is None:
                continue
            resources = self.store.get_task_resources(task_id)
            review_resources = [r for r in resources
                                if str(r.get('resource_key', '')).startswith('review-session:')]
            if (review_resources and all(r.get('state') == 'RELEASED' and r.get('cleanup_proof')
                                         for r in review_resources)):
                self._unresolved.discard(task_id)
                self._states.pop(task_id, None)
        selected = [t for t in tasks if t['task_id'] in self._unresolved] if unresolved_only else tasks
        for task in selected:
            resources = self.store.get_task_resources(task['task_id'])
            review_resources = [r for r in resources
                                if str(r.get('resource_key', '')).startswith('review-session:')]
            if not any(r.get('state') in ('HELD', 'UNKNOWN') for r in review_resources):
                continue
            outcome = self.cancellation.quiesce(task['task_id'])
            if outcome == 'APPLIED':
                confirmed.append(task['task_id']); self._unresolved.discard(task['task_id'])
                self._states.pop(task['task_id'], None)
            elif outcome == 'PENDING':
                pending.append(task['task_id']); self._unresolved.add(task['task_id'])
                self._states[task['task_id']] = 'PENDING'
            else:
                unknown.append(task['task_id']); self._unresolved.add(task['task_id'])
                self._states[task['task_id']] = 'UNKNOWN'
        return {'confirmed': confirmed, 'unknown': sorted(k for k,v in self._states.items() if v == 'UNKNOWN'),
                'pending': sorted(k for k,v in self._states.items() if v == 'PENDING')}
