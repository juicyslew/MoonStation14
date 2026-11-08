"""Monitor durable OpenCode review history for bound provider failures."""
import urllib.parse
import json
import time
import hashlib
from pathlib import Path

try:
    import workflow_dependency, workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_dependency, workflow_store


class ReviewDependencyMonitor:
    def __init__(self, planner, *, server, request, now_ms=None):
        self.planner, self.store, self.server, self.request = planner, planner.store, server.rstrip('/'), request
        self.now_ms = now_ms or (lambda: int(time.time() * 1000))

    def poll(self, task_id):
        task = self.store.get_task(task_id)
        if not task or task.get('kind') != 'REVIEW':
            return 'UNKNOWN'
        if self.store.task_is_dependency_paused(task_id): return 'PAUSED'
        resources = self.store.get_task_resources(task_id)
        assignment = self.planner.build_assignment(task_id)
        envelope = assignment['body']['envelope']
        message_id = 'msg_' + hashlib.sha256((task_id + assignment['body_sha256']).encode()).hexdigest()[:40]
        proof = {'server':self.server,'session_id':task['assigned_session_id'],'message_id':message_id,
                 'body_sha256':assignment['body_sha256'],'task_id':task_id,'run_id':task['run_id'],
                 'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                 'input_digest':task['input_digest']}
        held = [r for r in resources if r.get('state') == 'HELD' and r.get('identity_proof') == proof]
        if len(held) != 1:
            return 'UNKNOWN'
        event = self.store.get_event(envelope['event_id'])
        if not event or event['kind'] != 'DISPATCH' or event['payload'] != envelope:
            return 'UNKNOWN'
        binding = {k: task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
        session_id = task['assigned_session_id']
        pause_state=self.store.get_task_dependency_pause(task_id)
        lower_bound=max(event['created_ms'],(pause_state or {}).get('last_resumed_ms') or 0)
        try:
            try: import workflow_review_history
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_history
            records = workflow_review_history.read_since(self.request, self.server, session_id, event['created_ms'])
        except workflow_review_history.HistoryUnavailable:
            return 'UNKNOWN'
        try: import workflow_review_transport
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_transport
        expected_text = workflow_store.canonical_json(assignment['body'])
        expected_metadata = {'review_event_id':envelope['event_id'],'task_id':task_id,'body_sha256':assignment['body_sha256']}
        try:
            try: import workflow_review_control
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_control
            control_bodies=workflow_review_control.continuation_bodies(self.store.list_review_retry_receipts(task_id),task,assignment,self.server)
        except Exception:
            return 'UNKNOWN'
        expected_users={message_id:(expected_text,expected_metadata)}
        for control in control_bodies:
            expected_users[control['id']]=(control['text'],control['metadata'])
        prompt_found = False
        for message in records:
            created = message.get('time', {}).get('created') if isinstance(message, dict) and isinstance(message.get('time'), dict) else None
            try: created_ms = int(created)
            except (TypeError, ValueError): created_ms = -1
            if isinstance(message, dict) and message.get('type') == 'user' and created_ms >= event['created_ms']:
                proof=expected_users.get(message.get('id'))
                if proof is None or not workflow_review_transport.verify_owned_prompt_record(message,message_id=message['id'],
                    session_id=session_id,text=proof[0],metadata=proof[1]):
                    return 'UNKNOWN'
                if message.get('id')==message_id: prompt_found = True
        if not prompt_found:
            status, queued = self.request(self.server,'GET','/api/session/'+urllib.parse.quote(session_id)+'/inbox')
            items=queued.get('data') if isinstance(queued,dict) else None
            if status!=200 or not isinstance(items,list): return 'UNKNOWN'
            prompt_records=[m for m in items if isinstance(m,dict) and m.get('id')==message_id]
            if len(prompt_records)!=1 or not workflow_review_transport.verify_owned_prompt_record(
                prompt_records[0],message_id=message_id,session_id=session_id,text=expected_text,metadata=expected_metadata):
                return 'UNKNOWN'
            prompt_found=True
        status, queued = self.request(self.server,'GET','/api/session/'+urllib.parse.quote(session_id)+'/inbox')
        items=queued.get('data') if isinstance(queued,dict) else None
        if status!=200 or not isinstance(items,list): return 'UNKNOWN'
        for item in items:
            if not isinstance(item,dict) or item.get('type')!='user': continue
            proof=expected_users.get(item.get('id'))
            if proof is None or not workflow_review_transport.verify_owned_prompt_record(item,message_id=item['id'],session_id=session_id,text=proof[0],metadata=proof[1]):
                return 'UNKNOWN'
        for message in records:
            if not isinstance(message, dict) or not isinstance(message.get('id'), str):
                continue
            created = message.get('time', {}).get('created') if isinstance(message.get('time'), dict) else None
            error = message.get('error')
            if not isinstance(error, dict) or not isinstance(error.get('type'), str) or not error['type'].startswith('provider.'):
                continue
            try:
                created_ms = int(created)
            except (TypeError, ValueError):
                continue
            if created_ms < lower_bound:
                continue
            if not isinstance(error.get('message'), str):
                continue
            # OpenCode owns automatic retries for transport resets. Treating one
            # as a durable dependency pause fences the task while that retry is
            # already in flight; only actionable provider failures are paused.
            if error['type'] == 'provider.transport':
                continue
            status_code=error.get('status')
            if status_code is not None and (not isinstance(status_code,int) or not 100 <= status_code <= 599): continue
            failure_id = message['id']
            source = {'ownership_basis':'exclusive_session_exact_queued_prompt_no_foreign_postdispatch_user',
                'mode':'HISTORY_CONSUMED' if any(m.get('id')==message_id for m in records if isinstance(m,dict)) else 'QUEUED_ORIGINAL',
                'original_message_id':message_id,'body_sha256':assignment['body_sha256'],
                'message_id':failure_id,'type':error['type'],'status':status_code,'created':created,'message':error['message'][:1000]}
            from workflow_task_artifacts import register_bytes
            ref,_ = register_bytes(self.store, task, Path(self.planner.artifact_dir), 'PROVENANCE', json.dumps(source, sort_keys=True, separators=(',', ':')).encode())
            workflow_dependency.pause(self.store, task_id=task_id, dependency_key=self.server + ':' + error['type'], failure_id=failure_id, binding=binding, now_ms=self.now_ms(), evidence_refs=(ref,))
            return 'PAUSED'
        return 'CLEAR'
