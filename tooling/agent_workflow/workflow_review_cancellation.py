"""Fail-closed cancellation for one durably admitted review prompt."""
import hashlib
import json
import time
import urllib.parse
import uuid
from pathlib import Path

try:
    import gate_dispatch, workflow_store, workflow_task_artifacts, workflow_review_transport, workflow_review_control, workflow_review_history
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_dispatch, workflow_store, workflow_task_artifacts, workflow_review_transport, workflow_review_control, workflow_review_history



def cancelled_review_readiness_settled(task, event, cancellation, resources):
    """Pure proof check for locally discharging a never-started cancelled review wake."""
    if (not isinstance(task, dict) or not isinstance(event, dict) or
        task.get('kind') != 'REVIEW' or task.get('state') != 'CANCELLED'): return False
    payload = event.get('payload') if isinstance(event, dict) else None
    event_id = event.get('event_id', event.get('id'))
    if (not isinstance(payload, dict) or event.get('kind') != 'TASK_READY' or
        event.get('payload') != payload or payload.get('kind') != 'TASK_READY' or payload.get('event_id') != event_id or
        event.get('run_id') != task.get('run_id') or event.get('generation') != task.get('evaluation_generation') or
        payload.get('task_id') != task.get('task_id') or
        payload.get('run_id') != task.get('run_id') or payload.get('evaluation_generation') != task.get('evaluation_generation') or
        payload.get('assignment_generation') != task.get('assignment_generation') or
        payload.get('input_digest') != task.get('input_digest') or
        not isinstance(event.get('payload_digest'),str) or workflow_store.digest(payload) != event.get('payload_digest')): return False
    if not isinstance(cancellation, dict) or type(cancellation.get('confirmed')) is not int or cancellation.get('confirmed') != 1: return False
    proof = cancellation.get('proof')
    expected = {'task_id':task.get('task_id'), 'run_id':task.get('run_id'),
        'generation':task.get('evaluation_generation'), 'assignment_generation':task.get('assignment_generation'),
        'input_digest':task.get('input_digest'), 'kind':'DB_NEVER_STARTED'}
    if not isinstance(proof, dict) or any(proof.get(k) != v for k,v in expected.items()): return False
    if not isinstance(resources, list) or any(r.get('state') != 'RELEASED' for r in resources if isinstance(r,dict)): return False
    return all(isinstance(r,dict) for r in resources)


class ReviewCancellation:
    def __init__(self, store, planner, transport, *, server, request=None,
                 sleep=time.sleep, active_timeout=10.0, poll_interval=0.1):
        self.store, self.planner, self.transport = store, planner, transport
        self.server = workflow_review_transport.ReviewDispatchBackend._loopback(server)
        self.request = request or gate_dispatch._request
        self.sleep, self.active_timeout, self.poll_interval = sleep, active_timeout, poll_interval

    def cancel(self, event_id, payload):
        task=self.store.get_task(payload.get('task_id')) if isinstance(payload,dict) else None
        sid=task.get('assigned_session_id') if isinstance(task,dict) else None
        if not sid: return 'UNKNOWN'
        try:
            try: import workflow_review_effects
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_effects
            lock=workflow_review_effects.review_effect_guard(self.store,self.server,sid,nonblocking=True)
        except (BlockingIOError,TimeoutError): return 'PENDING'
        try: return self._cancel_locked(event_id,payload)
        finally: lock.close()

    def _cancel_locked(self, event_id, payload):
        if not isinstance(payload, dict): return 'UNKNOWN'
        task = self.store.get_task(payload.get('task_id'))
        if not task or task.get('kind') != 'REVIEW': return 'UNKNOWN'
        expected = {k:task.get(src) for k,src in [('task_id','task_id'),('run_id','run_id'),
                    ('evaluation_generation','evaluation_generation'),('assignment_generation','assignment_generation'),('input_digest','input_digest')]}
        if any(payload.get(k) != v for k,v in expected.items()): return 'UNKNOWN'
        saved_cancel = self.store.find_task_cancel_event(task['task_id'])
        if not saved_cancel or saved_cancel.get('event_id') != event_id or saved_cancel.get('payload') != payload:
            return 'UNKNOWN'
        resources = self.store.get_task_resources(task['task_id'])
        owned = [r for r in resources if r['resource_key'].startswith('review-session:')]
        if len(owned) != 1: return 'UNKNOWN'
        resource = owned[0]
        proof = resource['identity_proof']
        try:
            assignment = self.planner.build_assignment(task['task_id'])
            body = assignment['body']; session_id = task['assigned_session_id']
            message_id = 'msg_' + hashlib.sha256((task['task_id'] + assignment['body_sha256']).encode()).hexdigest()[:40]
            role = task['logical_key'].removeprefix('review:')
            binding = self.planner.reviewers[role]
            run = self.store.get_run(task['run_id'])['run']
            if (resource['state'] not in ('HELD','UNKNOWN','RELEASED') or proof.get('server') != self.server or
                proof.get('session_id') != session_id or proof.get('message_id') != message_id or
                proof.get('body_sha256') != assignment['body_sha256'] or proof.get('task_id') != task['task_id'] or
                proof.get('run_id') != task['run_id'] or proof.get('evaluation_generation') != task['evaluation_generation'] or
                 proof.get('assignment_generation') != task['assignment_generation'] or proof.get('input_digest') != task['input_digest'] or
                 binding.get('role') != 'reviewer' or not isinstance(binding.get('worktree'),str) or not binding.get('worktree') or
                 session_id == run['author'].get('session_id')): return 'UNKNOWN'
            if resource['state'] == 'RELEASED':
                return self._replay(task, resource)
            baseline=self.transport.get_session_baseline(task,assignment)
            if baseline is None:
                return self._cancel_uncreated(task,resource,proof,assignment,session_id)
            if not baseline: return self._unknown(resource,phase='baseline-missing')
            controls,command_context=self._retry_controls(task,assignment,session_id,proof,baseline)
            if controls is None: return self._unknown(resource,phase='retry-receipt-invalid')
            guard=self._history_guard(task,assignment,body,session_id,message_id,baseline,controls)
            if guard is None: return self._unknown(resource,phase='session-history-fence')
            # A verified receipt is durable proof of admission. Without one, inspect exact inbox only.
            admitted = self.transport._receipt(task, assignment)
            inbox = None
            if not admitted:
                code, inbox = self._api('GET', self._session_path(session_id) + '/inbox')
                admitted = self._observed_admission(code, inbox, body, session_id, task, assignment)
                if not admitted: return self._unknown(resource, phase='admission', status=code)
            if inbox is None:
                code,inbox=self._api('GET',self._session_path(session_id)+'/inbox')
            items=inbox.get('data',inbox) if isinstance(inbox,dict) else None
            owned=self._owned_items(items,session_id,message_id,body,task,assignment,baseline,controls) if code==200 else None
            if owned is None: return self._unknown(resource,phase='pre-interrupt-inbox',status=code)
            return self._drain(task, resource, proof, session_id, message_id, admitted, baseline, owned,command_context,guard)
        except Exception as exc:
            return self._unknown(resource,phase='validation-or-api',error=type(exc).__name__)

    def cleanup_completed(self, task_id):
        task=self.store.get_task(task_id)
        sid=task.get('assigned_session_id') if isinstance(task,dict) else None
        if not sid: return 'UNKNOWN'
        try:
            try: import workflow_review_effects
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_effects
            lock=workflow_review_effects.review_effect_guard(self.store,self.server,sid,nonblocking=True)
        except (BlockingIOError,TimeoutError): return 'PENDING'
        try: return self._cleanup_completed_locked(task_id)
        finally: lock.close()

    def _cleanup_completed_locked(self, task_id):
        task=self.store.get_task(task_id)
        if not task or task.get('kind')!='REVIEW' or task.get('state')!='COMPLETED': return 'UNKNOWN'
        resources=[r for r in self.store.get_task_resources(task_id) if r['resource_key'].startswith('review-session:')]
        if len(resources)!=1: return 'UNKNOWN'
        resource=resources[0]; proof=resource['identity_proof']
        if resource['state']=='RELEASED': return self._replay(task,resource)
        try:
            prepared,error=self._prepare_drain(task,resource)
            if error: return self._unknown(resource,phase=error[0],status=error[1])
            assignment,sid,mid,admitted,baseline,owned,command_context,guard=prepared
            return self._drain(task,resource,proof,sid,mid,admitted,baseline,owned,command_context,guard)
        except Exception as exc: return self._unknown(resource,phase='completed-cleanup',error=type(exc).__name__)

    def _prepare_drain(self, task, resource, *, allow_checkpoint=False):
        """Shared assignment/admission/baseline/history/inbox proof for both cleanup owners."""
        task_id=task['task_id']; proof=resource['identity_proof']
        assignment=self.planner.build_assignment(task_id); sid=task['assigned_session_id']
        mid='msg_'+hashlib.sha256((task_id+assignment['body_sha256']).encode()).hexdigest()[:40]
        if (proof.get('server')!=self.server or proof.get('session_id')!=sid or proof.get('message_id')!=mid or
            proof.get('body_sha256')!=assignment['body_sha256'] or proof.get('task_id')!=task_id or
            proof.get('run_id')!=task['run_id'] or proof.get('evaluation_generation')!=task['evaluation_generation'] or
            proof.get('assignment_generation')!=task['assignment_generation'] or proof.get('input_digest')!=task['input_digest']):
            return None,('admission',None)
        admitted=self._admission(task,assignment,sid)
        if admitted is not True and allow_checkpoint and self.get_quiescence_checkpoint(task_id): admitted=True
        if admitted is not True: return None,('admission',None)
        baseline=self.transport.get_session_baseline(task,assignment)
        if not baseline: return None,('baseline-missing',None)
        controls,command_context=self._retry_controls(task,assignment,sid,proof,baseline)
        if controls is None: return None,('retry-receipt-invalid',None)
        guard=self._history_guard(task,assignment,assignment['body'],sid,mid,baseline,controls)
        if guard is None: return None,('session-history-fence',None)
        code,inbox=self._api('GET',self._session_path(sid)+'/inbox')
        items=inbox.get('data',inbox) if isinstance(inbox,dict) else None
        owned=self._owned_items(items,sid,mid,assignment['body'],task,assignment,baseline,controls) if code==200 else None
        if owned is None: return None,('pre-interrupt-inbox',code)
        return (assignment,sid,mid,admitted,baseline,owned,command_context,guard),None

    def quiesce(self, task_id):
        """Stop an admitted active reviewer while retaining its task and capacity."""
        task=self.store.get_task(task_id)
        sid=task.get('assigned_session_id') if isinstance(task,dict) else None
        if not sid: return 'UNKNOWN'
        try:
            try: import workflow_review_effects
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_effects
            lock=workflow_review_effects.review_effect_guard(self.store,self.server,sid,nonblocking=True)
        except (BlockingIOError,TimeoutError): return 'PENDING'
        try: return self._quiesce_locked(task_id)
        finally: lock.close()

    def _quiesce_locked(self, task_id):
        task=self.store.get_task(task_id)
        if not task or task.get('kind')!='REVIEW' or task.get('state') not in ('DISPATCHING','CLAIMED','RUNNING'):
            return 'UNKNOWN'
        resources=[r for r in self.store.get_task_resources(task_id) if r['resource_key'].startswith('review-session:')]
        if len(resources)!=1: return 'UNKNOWN'
        resource=resources[0]; proof=resource['identity_proof']
        if resource['state'] not in ('HELD','UNKNOWN'): return 'UNKNOWN'
        try:
            prepared,error=self._prepare_drain(task,resource,allow_checkpoint=True)
            if error: return self._unknown(resource,phase=error[0],status=error[1])
            assignment,sid,mid,admitted,baseline,owned,command_context,guard=prepared
            pause=self.store.get_task_dependency_pause(task_id)
            if not pause or pause.get('paused_since_ms',-1)<0:
                binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
                key=self.server+':coordinator-loss'
                self.store.pause_dependency_task(task_id=task_id,dependency_key=key,
                    failure_id='coordinator-loss:'+task_id+':'+uuid.uuid4().hex,binding=binding,now_ms=int(time.time()*1000))
            return self._drain(task,resource,proof,sid,mid,admitted,baseline,owned,command_context,guard,quiesce=True)
        except Exception as exc: return self._unknown(resource,phase='quiescence',error=type(exc).__name__)

    def get_quiescence_checkpoint(self, task_id):
        task=self.store.get_task(task_id)
        resources=[r for r in self.store.get_task_resources(task_id) if r.get('resource_key','').startswith('review-session:')]
        if not task or len(resources)!=1: return None
        resource=resources[0]; ref=resource.get('cleanup_proof')
        if resource.get('state') not in ('HELD','UNKNOWN') or not ref: return None
        binding=self.store.get_artifact(ref)
        if not binding: return None
        art=binding.get('artifact',{})
        try:
            data=Path(art['path']).read_bytes(); saved=json.loads(data)
        except (OSError,ValueError,KeyError,TypeError): return None
        proof=resource.get('identity_proof',{})
        try:
            assignment=self.planner.build_assignment(task_id)
            baseline=self.transport.get_session_baseline(task,assignment)
            expected_mid='msg_'+hashlib.sha256((task_id+assignment['body_sha256']).encode()).hexdigest()[:40]
        except Exception: return None
        run=self.store.get_run(task['run_id'])
        if (not run or run['attempt']['generation']!=task.get('evaluation_generation') or
            resource.get('run_id')!=task.get('run_id') or resource.get('generation')!=task.get('evaluation_generation') or
            resource.get('task_id')!=task_id or resource.get('assignment_generation')!=task.get('assignment_generation') or
            proof.get('server')!=self.server or proof.get('session_id')!=task.get('assigned_session_id') or
            proof.get('body_sha256')!=assignment.get('body_sha256') or proof.get('input_digest')!=task.get('input_digest') or
            art.get('type')!='PROVENANCE' or art.get('producer_kind')!='TASK' or art.get('producer_task_id')!=task_id or
            art.get('sha256')!=hashlib.sha256(data).hexdigest() or art.get('size_bytes')!=len(data) or
            binding.get('input_digest')!=task.get('input_digest') or
            saved.get('purpose')!='REVIEW_QUIESCENCE' or saved.get('task_id')!=task_id or
            saved.get('run_id')!=task.get('run_id') or saved.get('generation')!=task.get('evaluation_generation') or
            saved.get('assignment_generation')!=task.get('assignment_generation') or saved.get('input_digest')!=task.get('input_digest') or
            saved.get('server')!=self.server or saved.get('body_sha256')!=assignment.get('body_sha256') or
            saved.get('message_id')!=expected_mid or not baseline or saved.get('baseline_ref')!=baseline.get('artifact_ref') or
            saved.get('session_id')!=proof.get('session_id') or saved.get('message_id')!=proof.get('message_id') or
            not saved.get('admission_proven') or not saved.get('inactive_confirmed') or not saved.get('inbox_empty')): return None
        return saved

    def _drain(self, task, resource, proof, sid, mid, admitted, baseline, pre_owned,command_context,guard,quiesce=False):
        def delete_ids(owned):
            for item in owned:
                status,_=self._api('DELETE',self._session_path(sid)+'/inbox/'+urllib.parse.quote(item['id'],safe=''))
                if status not in (200,204,404): return False
            return True
        try:
            if not delete_ids(pre_owned): return self._unknown(resource,phase='delete-before-interrupt')
            status,reply=self._api('POST',self._session_path(sid)+'/interrupt?resume=false')
            if status != 200 or not isinstance(reply,dict) or not isinstance(reply.get('interrupted'),bool): return self._unknown(resource,phase='interrupt',status=status)
            deadline=time.monotonic()+self.active_timeout
            while True:
                status,active=self._api('GET','/api/session/active')
                data=active.get('data') if isinstance(active,dict) else None
                if status != 200 or not isinstance(data,dict): return self._unknown(resource,phase='active-query',status=status)
                entry=data.get(sid)
                if sid not in data: break
                if not isinstance(entry,dict) or entry.get('type')!='running': return self._unknown(resource,phase='active-shape',status=status)
                if time.monotonic()>=deadline: return self._unknown(resource,phase='active-timeout',status=status)
                self.sleep(self.poll_interval)
            status,inbox=self._api('GET',self._session_path(sid)+'/inbox')
            items=inbox.get('data',inbox) if isinstance(inbox,dict) else None
            assignment=self.planner.build_assignment(task['task_id'])
            post_owned=self._owned_items(items,sid,mid,assignment['body'],task,assignment,baseline,command_context['controls'])
            if post_owned is None: return self._unknown(resource,phase='post-interrupt-inbox',status=status)
            all_owned={x['id']:x for x in pre_owned+post_owned}
            if not delete_ids(post_owned): return self._unknown(resource,phase='delete-after-interrupt')
            status,inbox=self._api('GET',self._session_path(sid)+'/inbox')
            items=inbox.get('data',inbox) if isinstance(inbox,dict) else None
            if status != 200 or not isinstance(items,list) or items: return self._unknown(resource,phase='final-inbox',status=status)
            evidence={'purpose':'REVIEW_QUIESCENCE' if quiesce else 'REVIEW_CLEANUP','task_id':task['task_id'],'run_id':task['run_id'],'generation':task['evaluation_generation'],
                'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
                'session_id':sid,'message_id':mid,'admission_proven':bool(admitted),
                'server':self.server,'body_sha256':proof['body_sha256'],
                'inactive_confirmed':True,'message_absent':True,
                'baseline_ref':baseline['artifact_ref'],'baseline_observed_ms':baseline['record']['observed_ms'],
                'owned_items':[{'id':x['id'],'text_sha256':x['text_sha256']} for x in all_owned.values()],
                'retry_commands_sha256':command_context['digest'],'retry_control_ids':command_context['control_ids'],
                'history_guard':guard,
                'inbox_empty':True,
                'observations':['owned inbox delete before interrupt','interrupt resume=false','active map owned session inactive',
                                'owned inbox delete after interrupt','owned message absent from inbox']}
            aid,_=workflow_task_artifacts.register_bytes(self.store,task,Path(self.planner.artifact_dir),'PROVENANCE',workflow_store.canonical_json(evidence).encode())
            if quiesce:
                self.store.record_review_quiescence(task['task_id'],claim_id=resource['claim_id'],identity_proof=proof,artifact_ref=aid)
                return 'APPLIED'
            current=self.store.get_task(task['task_id'])
            if current['state']=='CANCEL_REQUESTED': return self._release_and_confirm(task,resource,proof,aid)
            self.store.release_resource(resource['resource_key'],claim_id=resource['claim_id'],run_id=task['run_id'],generation=task['evaluation_generation'],task_id=task['task_id'],assignment_generation=task['assignment_generation'],identity_proof=proof,cleanup_proof=aid)
            return 'APPLIED'
        except Exception as exc: return self._unknown(resource,phase='drain-exception',error=type(exc).__name__)

    def _replay(self, task, resource):
        ref=resource.get('cleanup_proof'); binding=self.store.get_artifact(ref) if ref else None
        if not binding: return 'UNKNOWN'
        art=binding.get('artifact',{}); proof=resource['identity_proof']
        try:
            data=Path(art['path']).read_bytes(); saved=json.loads(data)
        except (OSError,ValueError,KeyError,TypeError): return 'UNKNOWN'
        if saved.get('purpose')=='REVIEW_NO_EXTERNAL_EFFECT_CLEANUP':
            if (self.transport.has_registered_prompt_evidence(task) or art.get('run_id')!=task['run_id'] or
                art.get('producer_kind')!='TASK' or art.get('producer_id')!=task['task_id'] or
                art.get('producer_task_id')!=task['task_id'] or art.get('type')!='PROVENANCE' or
                art.get('immutable') is not True or art.get('sha256')!=hashlib.sha256(data).hexdigest() or
                art.get('size_bytes')!=len(data) or binding.get('input_digest')!=task['input_digest'] or
                any(saved.get(k)!=v for k,v in {'task_id':task['task_id'],'run_id':task['run_id'],
                    'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                    'input_digest':task['input_digest'],'session_id':proof.get('session_id'),'server':self.server,
                    'body_sha256':proof.get('body_sha256')}.items()) or
                saved.get('intent_absent') is not True or saved.get('verified_creation_absent') is not True or
                saved.get('prompt_receipt_absent') is not True or saved.get('session_not_found') is not True): return 'UNKNOWN'
            if task['state']=='CANCEL_REQUESTED':
                self.store.confirm_task_cancelled(task_id=task['task_id'],run_id=task['run_id'],
                    evaluation_generation=task['evaluation_generation'],assignment_generation=task['assignment_generation'],
                    input_digest=task['input_digest'],cleanup_artifact_ref=ref)
            return 'APPLIED'
        if saved.get('purpose')=='REVIEW_UNPROMPTED_SESSION_CLEANUP':
            if self.transport.has_registered_prompt_evidence(task): return 'UNKNOWN'
            assignment=self.planner.build_assignment(task['task_id'])
            sid=task.get('assigned_session_id'); mid='msg_'+hashlib.sha256(
                (task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
            try:
                intent=self.transport._creation_artifact(task)
                created=self.transport._creation_artifact(task,verified=True)
                intent_binding=self.store.get_artifact(self.transport._creation_id(task))
                created_binding=self.store.get_artifact(self.transport._creation_id(task)+'_verified')
            except Exception: return 'UNKNOWN'
            intent_art=intent_binding.get('artifact',{}) if intent_binding else {}
            created_art=created_binding.get('artifact',{}) if created_binding else {}
            expected={'task_id':task['task_id'],'run_id':task['run_id'],
                'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                'input_digest':task['input_digest'],'session_id':sid,'server':self.server,
                'message_id':mid,'body_sha256':assignment['body_sha256'],
                'creation_intent_ref':intent_art.get('artifact_id'),'creation_intent_sha256':intent_art.get('sha256'),
                'verified_creation_ref':created_art.get('artifact_id'),'verified_creation_sha256':created_art.get('sha256')}
            if (not intent or not created or not intent_binding or not created_binding or
                intent!=saved.get('creation_record') or created!=saved.get('verified_record') or
                art.get('run_id')!=task['run_id'] or art.get('producer_kind')!='TASK' or
                art.get('producer_id')!=task['task_id'] or art.get('producer_task_id')!=task['task_id'] or
                art.get('type')!='PROVENANCE' or art.get('immutable') is not True or
                art.get('sha256')!=hashlib.sha256(data).hexdigest() or art.get('size_bytes')!=len(data) or
                binding.get('input_digest')!=task['input_digest'] or any(saved.get(k)!=v for k,v in expected.items()) or
                saved.get('inactive_confirmed') is not True or saved.get('inbox_empty') is not True or
                saved.get('history_empty') is not True): return 'UNKNOWN'
            if task['state']=='CANCEL_REQUESTED':
                self.store.confirm_task_cancelled(task_id=task['task_id'],run_id=task['run_id'],
                    evaluation_generation=task['evaluation_generation'],assignment_generation=task['assignment_generation'],
                    input_digest=task['input_digest'],cleanup_artifact_ref=ref)
            return 'APPLIED'
        assignment=self.planner.build_assignment(task['task_id'])
        baseline=self.transport.get_session_baseline(task,assignment)
        controls,command_context=self._retry_controls(task,assignment,proof.get('session_id'),proof,baseline) if baseline else (None,None)
        owned=saved.get('owned_items')
        if (controls is None or saved.get('retry_commands_sha256')!=command_context['digest'] or
            saved.get('retry_control_ids')!=command_context['control_ids'] or
            not baseline or baseline.get('artifact_ref')!=saved.get('baseline_ref') or
            baseline.get('record',{}).get('observed_ms')!=saved.get('baseline_observed_ms') or
            not isinstance(owned,list) or any(not isinstance(x,dict) or not isinstance(x.get('id'),str) or
                not isinstance(x.get('text_sha256'),str) or len(x['text_sha256'])!=64 for x in owned) or
            len({x['id'] for x in owned})!=len(owned) or
            'msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40] not in {x['id'] for x in owned} or
            not set(command_context['control_ids']).issubset({x['id'] for x in owned}) or
            art.get('producer_kind')!='TASK' or art.get('producer_id')!=task['task_id'] or art.get('producer_task_id')!=task['task_id'] or
            art.get('type')!='PROVENANCE' or art.get('immutable') is not True or art.get('sha256')!=hashlib.sha256(data).hexdigest() or
            art.get('size_bytes')!=len(data) or binding.get('input_digest')!=task['input_digest'] or saved.get('task_id')!=task['task_id'] or
            saved.get('session_id')!=proof.get('session_id') or saved.get('message_id')!=proof.get('message_id') or
            saved.get('run_id')!=task['run_id'] or saved.get('generation')!=task['evaluation_generation'] or
            saved.get('assignment_generation')!=task['assignment_generation'] or saved.get('server')!=self.server or
            saved.get('body_sha256')!=proof.get('body_sha256') or saved.get('input_digest')!=task['input_digest'] or
            not isinstance(saved.get('baseline_ref'),str) or not isinstance(saved.get('owned_items'),list) or
            not saved.get('admission_proven') or not saved.get('inactive_confirmed') or not saved.get('message_absent') or not saved.get('inbox_empty')): return 'UNKNOWN'
        guard=saved.get('history_guard')
        if (not isinstance(guard,dict) or guard.get('server')!=self.server or
            guard.get('session_id')!=proof.get('session_id') or guard.get('baseline_ref')!=saved.get('baseline_ref') or
            guard.get('baseline_observed_ms')!=saved.get('baseline_observed_ms') or
            guard.get('no_foreign_postbaseline_user') is not True or guard.get('current_identity_matches') is not True or
            not isinstance(guard.get('agent'),str) or not isinstance(guard.get('directory'),str) or
            guard.get('users_sha256')!=hashlib.sha256(workflow_store.canonical_json(guard.get('users')).encode()).hexdigest()): return 'UNKNOWN'
        if task['state']=='CANCEL_REQUESTED':
            self.store.confirm_task_cancelled(task_id=task['task_id'],run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],cleanup_artifact_ref=ref)
        return 'APPLIED'

    def _cancel_uncreated(self,task,resource,proof,assignment,sid):
        """Resolve either an untouched reservation or a verified, unprompted session."""
        try:
            if self.transport.has_registered_prompt_evidence(task):
                return self._unknown(resource,phase='registered-prompt-evidence')
            intent=self.transport._creation_artifact(task)
            created=self.transport._creation_artifact(task,verified=True)
            receipt=self.transport._receipt(task,assignment)
            status,session_reply=self._api('GET',self._session_path(sid))
            if receipt:
                return self._unknown(resource,phase='creation-or-prompt-evidence')
            if intent is None and created is None:
                if status!=404:
                    return self._unknown(resource,phase='uncreated-session-query',status=status)
                extra={'intent_absent':True,'verified_creation_absent':True,'prompt_receipt_absent':True,'session_not_found':True}
            else:
                if intent is None or created is None or status!=200:
                    return self._unknown(resource,phase='creation-or-session-identity',status=status)
                if not self.transport.session_matches_assignment(task,assignment,session_reply):
                    return self._unknown(resource,phase='created-session-identity')
                return self._cancel_unprompted(task,resource,proof,assignment,sid,intent,created)
            evidence={'purpose':'REVIEW_NO_EXTERNAL_EFFECT_CLEANUP','task_id':task['task_id'],
                'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
                'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
                'session_id':sid,'server':self.server,'body_sha256':assignment['body_sha256'],
                **extra}
            aid,_=workflow_task_artifacts.register_bytes(self.store,task,Path(self.planner.artifact_dir),
                'PROVENANCE',workflow_store.canonical_json(evidence).encode())
            return self._release_and_confirm(task,resource,proof,aid)
        except Exception as exc:
            return self._unknown(resource,phase='uncreated-session-cleanup',error=type(exc).__name__)

    def _cancel_unprompted(self,task,resource,proof,assignment,sid,intent,created):
        """Release a verified fresh session only after bounded proof of no activity."""
        try:
            if self.transport.has_registered_prompt_evidence(task):
                return self._unknown(resource,phase='registered-prompt-evidence')
            status,active_reply=self._api('GET','/api/session/active')
            active=active_reply.get('data') if isinstance(active_reply,dict) else None
            if status!=200 or not isinstance(active,dict) or sid in active:
                return self._unknown(resource,phase='unprompted-active-map',status=status)
            status,inbox_reply=self._api('GET',self._session_path(sid)+'/inbox')
            inbox=inbox_reply.get('data') if isinstance(inbox_reply,dict) else None
            if status!=200 or not isinstance(inbox,list) or inbox:
                return self._unknown(resource,phase='unprompted-inbox',status=status)
            messages=workflow_review_history.read_since(self.request,self.server,sid,0)
            if messages:
                return self._unknown(resource,phase='unprompted-history')
            intent_binding=self.store.get_artifact(self.transport._creation_id(task))
            created_binding=self.store.get_artifact(self.transport._creation_id(task)+'_verified')
            if not intent_binding or not created_binding:
                return self._unknown(resource,phase='unprompted-artifact-binding')
            intent_art=intent_binding.get('artifact',{}); created_art=created_binding.get('artifact',{})
            evidence={'purpose':'REVIEW_UNPROMPTED_SESSION_CLEANUP','task_id':task['task_id'],
                'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
                'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
                'server':self.server,'session_id':sid,'message_id':'msg_'+hashlib.sha256(
                    (task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40],
                'body_sha256':assignment['body_sha256'],'creation_intent_ref':intent_binding.get('artifact',{}).get('artifact_id'),
                'creation_intent_sha256':intent_art.get('sha256'),'verified_creation_ref':created_art.get('artifact_id'),
                'verified_creation_sha256':created_art.get('sha256'),'inactive_confirmed':True,
                'inbox_empty':True,'history_empty':True,'creation_record':intent,'verified_record':created,
                'observations':['shared actual session identity matched','active map omitted assigned session',
                    'assigned session inbox empty','bounded complete assigned session history empty']}
            aid,_=workflow_task_artifacts.register_bytes(self.store,task,Path(self.planner.artifact_dir),
                'PROVENANCE',workflow_store.canonical_json(evidence).encode())
            return self._release_and_confirm(task,resource,proof,aid)
        except Exception as exc:
            return self._unknown(resource,phase='unprompted-session-cleanup',error=type(exc).__name__)

    def _release_and_confirm(self,task,resource,proof,artifact_ref):
        self.store.release_resource(resource['resource_key'],claim_id=resource['claim_id'],run_id=task['run_id'],
            generation=task['evaluation_generation'],task_id=task['task_id'],
            assignment_generation=task['assignment_generation'],identity_proof=proof,cleanup_proof=artifact_ref)
        self.store.confirm_task_cancelled(task_id=task['task_id'],run_id=task['run_id'],
            evaluation_generation=task['evaluation_generation'],assignment_generation=task['assignment_generation'],
            input_digest=task['input_digest'],cleanup_artifact_ref=artifact_ref)
        return 'APPLIED'

    def _unknown(self, resource, *, phase='adapter', status=None, error=None):
        if resource.get('state') == 'HELD':
            try: self.store.mark_resource_unknown(resource['claim_id'],identity_proof=resource['identity_proof'])
            except Exception: pass
        proof=resource.get('identity_proof',{}); task_id=resource.get('task_id')
        context={'task_id':task_id,'phase':phase,'status':status,'error':error}
        incident_id='incident_review_cancel_'+hashlib.sha256(workflow_store.canonical_json(context).encode()).hexdigest()[:24]
        try:
            self.store.record_incident({'contract_version':1,'incident_id':incident_id,'run_id':proof.get('run_id'),
                'evaluation_generation':proof.get('evaluation_generation'),'task_id':task_id,
                'code':'REVIEW_CANCEL_UNKNOWN','message':f"Review cancellation remains UNKNOWN at {phase} (status {status if status is not None else 'unknown'}; {error or 'no exception'}).",
                'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]})
        except Exception: pass
        return 'UNKNOWN'

    def _admission(self, task, assignment, sid):
        if self.transport._receipt(task,assignment): return True
        code,inbox=self._api('GET',self._session_path(sid)+'/inbox')
        return self._observed_admission(code,inbox,assignment['body'],sid,task,assignment)

    def _observed_admission(self, status, reply, body, sid, task, assignment):
        items=reply.get('data',reply) if isinstance(reply,dict) else None
        if status!=200 or not isinstance(items,list): return False
        expected={'review_event_id':body['envelope']['event_id'],'task_id':task['task_id'],'body_sha256':assignment['body_sha256']}
        for item in items:
            if not isinstance(item,dict) or item.get('id')!=('msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]): continue
            if item.get('sessionID',item.get('session_id',item.get('sessionId'))) != sid: continue
            try: gate_dispatch._acknowledged_prompt({'data':item},sid,{'id':item['id'],'text':workflow_store.canonical_json(body),'delivery':'queue'},expected)
            except Exception: continue
            return True
        return False

    def _owned_items(self, items, sid, original_id, body, task, assignment, baseline, controls):
        if not isinstance(items,list): return None
        record=baseline.get('record',{})
        observed=record.get('observed_ms')
        if isinstance(observed,bool) or not isinstance(observed,int): return None
        expected={'review_event_id':body['envelope']['event_id'],'task_id':task['task_id'],'body_sha256':assignment['body_sha256']}
        result=[]; control_map={x['id']:x for x in controls}
        for item in items:
            if not isinstance(item,dict): return None
            item_sid=item.get('sessionID',item.get('session_id',item.get('sessionId')))
            if item_sid != sid: return None
            text=(item.get('payload') or {}).get('text') if isinstance(item.get('payload'),dict) else item.get('text')
            if not isinstance(text,str): return None
            text_sha=hashlib.sha256(text.encode()).hexdigest()
            if item.get('id')==original_id:
                delivery=item.get('delivery')
                if delivery not in ('queue','steer'): return None
                try:
                    gate_dispatch._acknowledged_prompt({'data':item},sid,{'id':original_id,'text':workflow_store.canonical_json(body),'delivery':delivery},expected)
                except Exception: return None
                result.append({'id':original_id,'text_sha256':text_sha})
                continue
            if item.get('id') in control_map:
                control=control_map[item['id']]
                if control.get('control_kind')=='synthetic':
                    if workflow_review_control.synthetic_snapshot(item,sid,observed)!=control: return None
                    result.append({'id':item['id'],'text_sha256':text_sha}); continue
                try: gate_dispatch._acknowledged_prompt({'data':item},sid,control['body'],control['metadata'])
                except Exception: return None
                if text != control['text']: return None
                result.append({'id':item['id'],'text_sha256':text_sha}); continue
            normalized=workflow_review_control.synthetic_snapshot(item,sid,observed)
            if normalized is None: return None
            result.append({'id':normalized['id'],'text_sha256':normalized['text_sha256']})
        present={x['id'] for x in result}
        if original_id not in present:
            result.append({'id':original_id,'text_sha256':hashlib.sha256(workflow_store.canonical_json(body).encode()).hexdigest()})
            present.add(original_id)
        result.extend({'id':cid,'text_sha256':control_map[cid]['text_sha256']} for cid in control_map if cid not in present)
        return result

    def _retry_controls(self, task, assignment, sid, proof, baseline):
        getter=getattr(self.store,'list_review_retry_receipts',None)
        if not callable(getter): return None,None
        try: receipts=getter(task['task_id'])
        except Exception: return None,None
        if not isinstance(receipts,list): return None,None
        for receipt in receipts:
            if not isinstance(receipt,dict) or receipt.get('status')!='INTENT' or receipt.get('effect_status')=='APPLIED': continue
            try:
                try: import workflow_review_retry
                except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_retry
                observer=workflow_review_retry.ReviewRetryAdapter(self.planner,server=self.server,request=self.request,transport=self.transport)
                observed=observer.observe_effect(task['task_id'],receipt)
                if observed != 'APPLIED': return None,None
                if observed=='APPLIED':
                    caller=receipt.get('caller')
                    if not isinstance(caller,str) or not caller: return None,None
                    self.store.record_review_retry_effect(task_id=task['task_id'],caller=caller,command_id=receipt['command_id'])
                    final={'status':'STOPPING','task_id':task['task_id'],'command_id':receipt['command_id'],
                        'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
                    self.store.complete_review_retry(task_id=task['task_id'],caller=caller,command_id=receipt['command_id'],response=final)
                    receipt.update(final); receipt['final_receipt']=final; receipt['effect_status']='APPLIED'
            except Exception: return None,None
        try:
            try: import workflow_review_control
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_control
            canonical=workflow_review_control.continuation_bodies(receipts,task,assignment,self.server)
            canonical_by_id={body['id']:body for body in canonical}
        except Exception: return None,None
        binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
        controls=[]; contexts=[]
        for receipt in receipts:
            if (not isinstance(receipt,dict) or receipt.get('task_id')!=task['task_id'] or
                receipt.get('status') not in ('INTENT','RESUMED','UNKNOWN','STOPPING') or receipt.get('binding')!=binding or
                receipt.get('proof')!=proof or not isinstance(receipt.get('command_id'),str) or not receipt['command_id']): return None,None
            mode=receipt.get('mode'); snapshot=receipt.get('controls_snapshot')
            if receipt.get('status') in ('RESUMED','UNKNOWN','STOPPING'):
                final=receipt.get('final_receipt')
                if (not isinstance(final,dict) or final.get('status')!=receipt.get('status') or
                    final.get('task_id')!=task['task_id'] or final.get('command_id')!=receipt.get('command_id') or
                    final.get('assignment_generation')!=task['assignment_generation'] or final.get('input_digest')!=task['input_digest']): return None,None
            if receipt.get('status')=='INTENT' and receipt.get('effect_status') not in (None,'APPLIED'): return None,None
            if mode=='CONTROL_PROMPT':
                if not isinstance(snapshot,list) or not snapshot: return None,None
                for saved in snapshot:
                    if not isinstance(saved,dict): return None,None
                    control=canonical_by_id.get(saved.get('id'))
                    if control is None: return None,None
                    metadata=control['metadata']; text=control['text']
                    controls.append({'id':control['id'],'text':text,'text_sha256':hashlib.sha256(text.encode()).hexdigest(),
                        'body':control,'metadata':metadata})
            elif mode=='INTERRUPT_RESUME' and isinstance(snapshot,list):
                for saved in snapshot:
                    normalized=workflow_review_control.validate_synthetic_snapshot(saved,sid,baseline['record']['observed_ms'])
                    if normalized is None: return None,None
                    controls.append({**normalized,'control_kind':'synthetic'})
            elif mode not in ('PATCH','INTERRUPT_RESUME'):
                return None,None
            contexts.append({'command_id':receipt['command_id'],'status':receipt['status'],'mode':mode,'controls_snapshot':snapshot})
        raw=workflow_store.canonical_json(contexts)
        if len({c['id'] for c in controls})!=len(controls) or any(c['id']==proof.get('message_id') for c in controls): return None,None
        return controls,{'controls':controls,'control_ids':[x['id'] for x in controls],
            'digest':hashlib.sha256(raw.encode()).hexdigest()}

    def _history_guard(self, task, assignment, body, sid, original_id, baseline, controls):
        status,session_reply=self._api('GET',self._session_path(sid))
        if status!=200 or not self.transport.session_matches_assignment(task,assignment,session_reply): return None
        observed=baseline['record']['observed_ms']
        try: messages=workflow_review_history.read_since(self.request,self.server,sid,observed)
        except (workflow_review_history.HistoryUnavailable,TypeError,ValueError): return None
        expected={original_id:(workflow_store.canonical_json(body),{
            'review_event_id':body['envelope']['event_id'],'task_id':task['task_id'],'body_sha256':assignment['body_sha256']})}
        for control in controls:
            if control.get('body'):
                expected[control['id']]=(control['text'],control['metadata'])
        user_rows=[]
        for message in messages:
            if not isinstance(message,dict) or message.get('type')!='user': continue
            stamp=message.get('time'); created=stamp.get('created') if isinstance(stamp,dict) else None
            if isinstance(created,bool) or not isinstance(created,int): return None
            if created < observed: continue
            mid=message.get('id')
            binding=expected.get(mid)
            if binding is None: return None
            text,metadata=binding
            if not workflow_review_transport.verify_owned_prompt_record(message,message_id=mid,session_id=sid,text=text,metadata=metadata): return None
            user_rows.append({'id':mid,'created_ms':created,'text_sha256':hashlib.sha256(text.encode()).hexdigest(),
                'metadata_sha256':hashlib.sha256(workflow_store.canonical_json(metadata).encode()).hexdigest()})
        session=session_reply.get('data',session_reply)
        return {'server':self.server,'session_id':sid,'agent':session.get('agent'),
            'directory':str(Path(session.get('directory',session.get('location',{}).get('directory',''))).resolve()),
            'baseline_ref':baseline['artifact_ref'],'baseline_observed_ms':observed,
            'users':sorted(user_rows,key=lambda x:(x['created_ms'],x['id'])),
            'users_sha256':hashlib.sha256(workflow_store.canonical_json(sorted(user_rows,key=lambda x:(x['created_ms'],x['id']))).encode()).hexdigest(),
            'no_foreign_postbaseline_user':True,'current_identity_matches':True}

    def _api(self, method, path, body=None): return self.request(self.server,method,path,body)

    @staticmethod
    def _session_path(sid): return '/api/session/'+urllib.parse.quote(sid,safe='')
