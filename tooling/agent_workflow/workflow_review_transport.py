"""Explicit, fail-closed transport for dedicated, task-bound reviewer sessions."""
import hashlib
import ipaddress
import json
import time
import urllib.parse
from pathlib import Path

try:
    import gate_dispatch, workflow_store
    import workflow_review_effects
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_dispatch, workflow_store, workflow_review_effects


class ReviewDispatchBackend:
    def __init__(self, planner, *, server, agents, request=None):
        self.planner = planner
        self.store = planner.store
        self.server = self._loopback(server)
        self.agents = dict(agents)
        self.request = request or gate_dispatch._request

    @staticmethod
    def _loopback(server):
        parsed = urllib.parse.urlparse(server or '')
        try:
            address = ipaddress.ip_address(parsed.hostname)
        except (ValueError, TypeError):
            raise workflow_store.ProtocolError('explicit loopback server URL required')
        if (parsed.scheme not in ('http', 'https') or not address.is_loopback or
                parsed.username or parsed.password or parsed.path not in ('', '/') or parsed.query or parsed.fragment):
            raise workflow_store.ProtocolError('explicit loopback server URL required')
        return server.rstrip('/')

    def observe(self, event_id, payload):
        if not isinstance(payload, dict):
            return 'UNKNOWN'
        task_id = payload.get('task_id')
        task = self.store.get_task(task_id)
        if not task or task.get('kind') != 'REVIEW':
            return 'UNKNOWN'
        if self._settled_cancelled_readiness(event_id, payload, task): return 'SETTLED'
        if task['state'] in ('CLAIMED', 'RUNNING', 'COMPLETED'):
            return 'APPLIED'
        if task['state'] == 'QUEUED':
            try:
                assignment=self.planner.build_assignment(task_id)
                if self.has_registered_prompt_evidence(task):return 'UNKNOWN'
                intent=self._creation_artifact(task)
                if intent is None:return 'ABSENT'
                guard=workflow_review_effects.review_effect_guard(self.store,self.server,task['assigned_session_id'])
                try:
                    current=self.store.get_task(task_id)
                    if not current or current.get('state')!='QUEUED':return 'UNKNOWN'
                    status,reply=self.request(self.server,'GET','/api/session/'+urllib.parse.quote(task['assigned_session_id']))
                    if status!=200 or not self.session_matches_assignment(current,assignment,reply):return 'UNKNOWN'
                    return 'ABSENT'
                finally:guard.close()
            except Exception:return 'UNKNOWN'
        if task['state'] != 'DISPATCHING':
            return 'UNKNOWN'
        try:
            assignment = self.planner.build_assignment(task_id)
            if self._receipt(task, assignment):
                return 'APPLIED'
            sid=task['assigned_session_id']
            guard=workflow_review_effects.review_effect_guard(self.store,self.server,sid)
            try:
                current=self.store.get_task(task_id)
                if not current or current.get('state')!='DISPATCHING' or self._effect_eligibility(current)!='HELD':return 'UNKNOWN'
                if self.has_registered_prompt_evidence(current):return 'UNKNOWN'
                if self.get_session_baseline(current,assignment) is not None:return 'UNKNOWN'
                intent=self._creation_artifact(current)
                status,reply=self.request(self.server,'GET','/api/session/'+urllib.parse.quote(sid))
                if status==404 and intent is None:return 'ABSENT'
                if status!=200 or intent is None or not self.session_matches_assignment(task,assignment,reply):return 'UNKNOWN'
                return 'ABSENT'
            finally:guard.close()
        except Exception:
            return 'UNKNOWN'
        # We cannot safely infer absence from an undocumented OpenCode history API.
        return 'UNKNOWN'

    def _settled_cancelled_readiness(self, event_id, payload, task=None):
        if not isinstance(payload, dict): return False
        task = task or self.store.get_task(payload.get('task_id'))
        if not task or task.get('kind') != 'REVIEW' or task.get('state') != 'CANCELLED': return False
        event = self.store.get_event(event_id)
        cancellation = self.store.get_task_cancellation(task['task_id'])
        resources = self.store.get_task_resources(task['task_id'])
        try:
            try: import workflow_review_cancellation
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_cancellation
            return workflow_review_cancellation.cancelled_review_readiness_settled(task, event, cancellation, resources)
        except Exception: return False

    def dispatch(self, event_id, payload):
        task = self.store.get_task(payload.get('task_id')) if isinstance(payload,dict) else None
        if self._settled_cancelled_readiness(event_id,payload,task): return 'SETTLED'
        return self._dispatch(event_id,payload)

    def _dispatch(self, event_id, payload):
        """Existing dispatch implementation; wrapper fences never-started cancellations."""
        return self._dispatch_review(event_id,payload)

    def session_matches_assignment(self, task, assignment, reply):
        """Verify that the saved reviewer session still has its configured identity."""
        session=reply.get('data',reply) if isinstance(reply,dict) else None
        if not isinstance(session,dict): return False
        sid=task.get('assigned_session_id')
        role=task.get('logical_key','').removeprefix('review:')
        agent=self.agents.get(role)
        binding=self.planner.reviewers.get(role)
        try:
            run=self.store.get_run(task['run_id'])['run']
            directory=session.get('directory',session.get('location',{}).get('directory',''))
            expected=binding['worktree']
            same_worktree=Path(directory).resolve()==Path(expected).resolve()
        except (KeyError,TypeError,OSError): return False
        try:owned=self._creation_artifact(task)
        except workflow_store.ProtocolError:return False
        metadata=session.get('metadata',{})
        return bool(agent and binding and session.get('id')==sid and session.get('agent')==agent and
            sid!=run.get('author',{}).get('session_id') and same_worktree and owned is not None and
            metadata.get('ms14_review_task_id')==task['task_id'] and
            metadata.get('ms14_review_run_id')==task['run_id'] and
            metadata.get('ms14_review_evaluation_generation')==task['evaluation_generation'] and
            metadata.get('ms14_review_assignment_generation')==task['assignment_generation'])

    def _creation_id(self,task):
        return 'artifact_review_session_intent_'+hashlib.sha256(task['task_id'].encode()).hexdigest()[:24]

    def _creation_artifact(self,task,verified=False):
        aid=self._creation_id(task)+(('_verified') if verified else '')
        binding=self.store.get_artifact(aid)
        if not binding:return None
        artifact=binding['artifact']
        try:data=Path(artifact['path']).read_bytes();record=json.loads(data)
        except (OSError,ValueError,KeyError) as exc:raise workflow_store.ProtocolError('registered reviewer session artifact is unreadable') from exc
        role=task.get('logical_key','').removeprefix('review:'); agent=self.agents.get(role); reviewer=self.planner.reviewers.get(role)
        expected={'task_id':task['task_id'],'run_id':task['run_id'],'session_id':task['assigned_session_id'],
            'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
            'input_digest':task['input_digest'],'server':self.server,'agent':agent,
            'worktree':reviewer.get('worktree') if reviewer else None}
        expected['metadata']={'ms14_review_task_id':task['task_id'],'ms14_review_run_id':task['run_id'],
            'ms14_review_evaluation_generation':task['evaluation_generation'],
            'ms14_review_assignment_generation':task['assignment_generation']}
        if (artifact.get('type')!='PROVENANCE' or artifact.get('immutable') is not True or
            artifact.get('size_bytes')!=len(data) or artifact.get('sha256')!=hashlib.sha256(data).hexdigest() or
            artifact.get('producer_task_id')!=task['task_id'] or artifact.get('run_id')!=task['run_id'] or
            artifact.get('producer_kind')!='TASK' or artifact.get('producer_id')!=task['task_id'] or
            binding.get('input_digest')!=task['input_digest'] or any(record.get(k)!=v for k,v in expected.items()) or
            record.get('kind')!=('REVIEW_SESSION_CREATED' if verified else 'REVIEW_SESSION_CREATE_INTENT')):
            raise workflow_store.ProtocolError('registered reviewer session artifact does not match assignment')
        return record

    def _save_creation_artifact(self,task,record,verified=False):
        raw=workflow_store.canonical_json(record).encode()
        try:from workflow_submission import _write_immutable
        except ModuleNotFoundError:from tooling.agent_workflow.workflow_submission import _write_immutable
        aid=self._creation_id(task)+(('_verified') if verified else '')
        path=_write_immutable(Path(self.planner.artifact_dir)/(hashlib.sha256(task['task_id'].encode()).hexdigest()+('.review-session-created.json' if verified else '.review-session-intent.json')),raw)
        self.store.register_artifact({'contract_version':1,'artifact_id':aid,'run_id':task['run_id'],
            'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(path),'sha256':hashlib.sha256(raw).hexdigest(),
            'size_bytes':len(raw),'immutable':True,'producer_kind':'TASK','producer_id':task['task_id']},input_digest=task['input_digest'])

    def _ensure_session(self,task,assignment,agent,binding):
        """Caller holds the existing per-session guard and an eligible HELD reservation."""
        sid=task['assigned_session_id']; path='/api/session/'+urllib.parse.quote(sid)
        if self._effect_eligibility(task)!='HELD':return False
        intent=self._creation_artifact(task)
        status,reply=self.request(self.server,'GET',path)
        if status==200:
            if intent is None or not self.session_matches_assignment(task,assignment,reply):return False
            if not self._creation_artifact(task,verified=True):
                self._save_creation_artifact(task,self._creation_record(task,agent,binding,verified=True),True)
            return True
        if status!=404 or intent is not None:return False
        metadata={'ms14_review_task_id':task['task_id'],'ms14_review_run_id':task['run_id'],
            'ms14_review_evaluation_generation':task['evaluation_generation'],
            'ms14_review_assignment_generation':task['assignment_generation']}
        intent_record=self._creation_record(task,agent,binding,metadata)
        if self._effect_eligibility(task)!='HELD':return False
        self._save_creation_artifact(task,intent_record)
        if self._effect_eligibility(task)!='HELD':return False
        payload={'id':sid,'agent':agent,'location':{'directory':binding['worktree']},'metadata':metadata}
        status,ack=self.request(self.server,'POST','/api/session',payload)
        if status!=200:return False
        data=ack.get('data',ack) if isinstance(ack,dict) else None
        if not isinstance(data,dict) or data.get('id')!=sid:return False
        status,verified=self.request(self.server,'GET',path)
        if status!=200 or not self.session_matches_assignment(task,assignment,verified):return False
        self._save_creation_artifact(task,self._creation_record(task,agent,binding,metadata,verified=True),True)
        return True

    def _creation_record(self,task,agent,binding,metadata=None,verified=False):
        metadata=metadata or {'ms14_review_task_id':task['task_id'],'ms14_review_run_id':task['run_id'],
            'ms14_review_evaluation_generation':task['evaluation_generation'],
            'ms14_review_assignment_generation':task['assignment_generation']}
        return {'kind':'REVIEW_SESSION_CREATED' if verified else 'REVIEW_SESSION_CREATE_INTENT',
            'task_id':task['task_id'],'run_id':task['run_id'],'session_id':task['assigned_session_id'],
            'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
            'input_digest':task['input_digest'],'server':self.server,'agent':agent,'worktree':binding['worktree'],'metadata':metadata}

    def _effect_eligibility(self,task):
        """Validate the current task, cancellation fence, and exact owned reservation."""
        if not task or task.get('kind')!='REVIEW' or task.get('state') not in ('DISPATCHING','CANCEL_REQUESTED'):
            return None
        saved=self.store.get_task(task['task_id'])
        if saved is None or saved.get('state')!=task.get('state'):return None
        run_state=self.store.get_run(task['run_id'])
        if (not run_state or run_state['run'].get('current_attempt')!=task['evaluation_generation'] or
            run_state['attempt'].get('phase') in ('STOPPING','DECIDED','CLOSED') or
            self.store.find_task_cancel_event(task['task_id'])):return None
        assignment=self.planner.build_assignment(task['task_id'])
        sid=task['assigned_session_id']
        message='msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
        expected={'server':self.server,'session_id':sid,'message_id':message,'body_sha256':assignment['body_sha256'],
            'task_id':task['task_id'],'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
        key='review-session:'+workflow_store.digest({'server':self.server,'session_id':sid})
        resources=[r for r in self.store.get_task_resources(task['task_id']) if r.get('resource_key')==key]
        if len(resources)!=1 or resources[0].get('identity_proof')!=expected:return None
        return resources[0].get('state')

    def _dispatch_review(self, event_id, payload):
        if not isinstance(payload, dict):
            return 'UNKNOWN'
        task_id = payload.get('task_id')
        try:
            task = self.store.get_task(task_id)
            if not task or task['kind'] != 'REVIEW' or task['state'] not in ('QUEUED','DISPATCHING'):
                return 'UNKNOWN'
            assignment = self.planner.build_assignment(task_id)
            envelope = assignment['body']['envelope']
            session_id = task['assigned_session_id']
            role = task['logical_key'].removeprefix('review:')
            agent_name=self.agents.get(role)
            binding = self.planner.reviewers.get(role)
            if (not isinstance(agent_name, str) or not agent_name or not binding or
                    envelope.get('recipient_binding', {}).get('session_id') != session_id):
                return 'UNKNOWN'
            prompt_id = 'msg_' + hashlib.sha256((task_id + assignment['body_sha256']).encode()).hexdigest()[:40]
            text = workflow_store.canonical_json(assignment['body'])
            body_hash = hashlib.sha256(text.encode()).hexdigest()
            if body_hash != assignment['body_sha256']:return 'UNKNOWN'
            stable_event=envelope['event_id']
            body={'id':prompt_id,'text':text,'delivery':'queue','resume':True,
                'metadata':{'review_event_id':stable_event,'task_id':task_id,'body_sha256':assignment['body_sha256']}}
            if task['state']=='QUEUED':
                if self.has_registered_prompt_evidence(task):return 'UNKNOWN'
                prior_intent=self._creation_artifact(task)
                if prior_intent is None:
                    code,foreign=self.request(self.server,'GET','/api/session/'+urllib.parse.quote(session_id))
                    if code==200:return 'UNKNOWN'
                    if code!=404:return 'UNKNOWN'
                try:
                    self.store.dispatch_task(task_id=task_id,event_id=stable_event,
                        assigned_session_id=session_id,dispatch_envelope=envelope,
                        review_session_claim={'server':self.server,'session_id':session_id,
                            'message_id':prompt_id,'body_sha256':body_hash})
                except workflow_store.StoreError as exc:
                    if 'resource held' in str(exc):return 'PENDING'
                    return 'UNKNOWN'
            guard=workflow_review_effects.review_effect_guard(self.store,self.server,session_id)
            try:
                task=self.store.get_task(task_id)
                run_state=self.store.get_run(task['run_id'])
                attempt=run_state.get('attempt') if isinstance(run_state,dict) else None
                if (not task or task.get('state')!='DISPATCHING' or not attempt or
                    run_state['run'].get('current_attempt')!=task['evaluation_generation'] or
                    attempt.get('phase') in ('STOPPING','DECIDED','CLOSED') or
                    self.store.find_task_cancel_event(task_id)):
                    return 'UNKNOWN'
                if self._receipt(task,assignment):return 'APPLIED'
                if self.has_registered_prompt_evidence(task):return 'UNKNOWN'
                if not self._ensure_session(task,assignment,agent_name,binding):return 'UNKNOWN'
                return self._dispatch_prompt_locked(task,assignment,session_id,body,stable_event,body_hash)
            finally:guard.close()
        except Exception as exc:
            raise gate_dispatch.DispatchError(f'review dispatch outcome unknown: {exc}') from exc

    def _dispatch_prompt_locked(self,task,assignment,session_id,body,stable_event,body_hash):
            status, active_reply = self.request(self.server, 'GET', '/api/session/active')
            active = active_reply.get('data') if isinstance(active_reply,dict) else None
            if status != 200 or not isinstance(active,dict): return 'UNKNOWN'
            active_entry=active.get(session_id)
            if session_id in active:
                if not isinstance(active_entry,dict) or active_entry.get('type')!='running': return 'UNKNOWN'
                return 'PENDING'
            status,inbox_reply=self.request(self.server,'GET','/api/session/'+urllib.parse.quote(session_id)+'/inbox')
            inbox=inbox_reply.get('data') if isinstance(inbox_reply,dict) else None
            if status!=200 or not isinstance(inbox,list): return 'UNKNOWN'
            if inbox:return 'PENDING'
            if self._effect_eligibility(task)!='HELD':return 'UNKNOWN'
            self._save_baseline(task,session_id,assignment)
            if self._effect_eligibility(task)!='HELD':return 'UNKNOWN'
            status,reply=self.request(self.server,'POST','/api/session/'+urllib.parse.quote(session_id)+'/prompt',body)
            if status!=200:raise gate_dispatch.DispatchError('prompt POST was not acknowledged')
            gate_dispatch._acknowledged_prompt(reply,session_id,body,body['metadata'])
            self._save_receipt(task,stable_event,session_id,body,assignment)
            return 'APPLIED'

    def _receipt(self, task, assignment):
        artifact_id = self._receipt_artifact_id(task)
        binding = self.store.get_artifact(artifact_id)
        if not binding:
            return False
        artifact = binding['artifact']
        path = Path(artifact['path'])
        try:
            data = path.read_bytes()
            saved = json.loads(data)
        except (OSError, ValueError):
            return False
        expected = {'event_id':assignment['body']['envelope']['event_id'], 'task_id':task['task_id'],
                    'run_id':task['run_id'], 'evaluation_generation':task['evaluation_generation'],
                    'assignment_generation':task['assignment_generation'], 'session_id':task['assigned_session_id'],
                    'message_id':'msg_' + hashlib.sha256((task['task_id'] + assignment['body_sha256']).encode()).hexdigest()[:40],
                    'body_sha256':assignment['body_sha256'], 'acknowledged':True}
        return (artifact.get('producer_task_id') == task['task_id'] and artifact.get('run_id') == task['run_id'] and
                artifact.get('producer_kind') == 'TASK' and artifact.get('producer_id') == task['task_id'] and
                artifact.get('type') == 'PROVENANCE' and artifact.get('immutable') is True and
                artifact.get('size_bytes') == len(data) and artifact.get('sha256') == hashlib.sha256(data).hexdigest() and
                binding.get('input_digest') == task['input_digest'] and saved == expected)

    def get_session_baseline(self, task, assignment):
        artifact_id=self._baseline_artifact_id(task)
        binding=self.store.get_artifact(artifact_id)
        if not binding:return None
        try:
            artifact=binding['artifact']; data=Path(artifact['path']).read_bytes(); saved=json.loads(data)
        except (KeyError,OSError,ValueError):return None
        expected={'kind':'REVIEW_SESSION_BASELINE','server':self.server,'session_id':task['assigned_session_id'],
            'task_id':task['task_id'],'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
            'body_sha256':assignment['body_sha256'],'current_session_inactive':True,'inbox_empty':True}
        valid=(artifact.get('producer_task_id')==task['task_id'] and artifact.get('producer_kind')=='TASK' and
            artifact.get('producer_id')==task['task_id'] and artifact.get('type')=='PROVENANCE' and
            artifact.get('immutable') is True and artifact.get('size_bytes')==len(data) and
            artifact.get('sha256')==hashlib.sha256(data).hexdigest() and binding.get('input_digest')==task['input_digest'] and
            all(saved.get(k)==v for k,v in expected.items()))
        return {'artifact_ref':artifact_id,'record':saved} if valid else None

    def _save_baseline(self,task,session_id,assignment):
        existing=self.get_session_baseline(task,assignment)
        if existing is not None: return existing['artifact_ref']
        record={'kind':'REVIEW_SESSION_BASELINE','server':self.server,'session_id':session_id,
            'task_id':task['task_id'],'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
            'body_sha256':assignment['body_sha256'],'observed_ms':int(time.time()*1000),
            'current_session_inactive':True,'inbox_empty':True}
        raw=workflow_store.canonical_json(record).encode()
        try: from workflow_submission import _write_immutable
        except ModuleNotFoundError: from tooling.agent_workflow.workflow_submission import _write_immutable
        aid=self._baseline_artifact_id(task)
        path=_write_immutable(Path(self.planner.artifact_dir)/(hashlib.sha256(task['task_id'].encode()).hexdigest()+'.review-session-baseline.json'),raw)
        self.store.register_artifact({'contract_version':1,'artifact_id':aid,'run_id':task['run_id'],
            'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(path),'sha256':hashlib.sha256(raw).hexdigest(),
            'size_bytes':len(raw),'immutable':True,'producer_kind':'TASK','producer_id':task['task_id']},input_digest=task['input_digest'])


    def _save_receipt(self, task, event_id, session_id, body, assignment):
        raw = workflow_store.canonical_json({'event_id': event_id, 'task_id': task['task_id'],
            'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'assignment_generation':task['assignment_generation'],'session_id': session_id,
            'message_id': body['id'], 'body_sha256': assignment['body_sha256'],
            'acknowledged': True}).encode()
        try:
            from workflow_submission import _write_immutable
        except ModuleNotFoundError:
            from tooling.agent_workflow.workflow_submission import _write_immutable
        path = _write_immutable(Path(self.planner.artifact_dir) / (hashlib.sha256(task['task_id'].encode()).hexdigest() + '.review-transport.json'),raw)
        artifact = {'contract_version':1,'artifact_id':self._receipt_artifact_id(task),
            'run_id':task['run_id'],'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(path),
            'sha256':hashlib.sha256(raw).hexdigest(),'size_bytes':len(raw),'immutable':True,
            'producer_kind':'TASK','producer_id':task['task_id']}
        self.store.register_artifact(artifact, input_digest=task['input_digest'])

    @staticmethod
    def _baseline_artifact_id(task):
        return 'artifact_review_session_baseline_'+hashlib.sha256(task['task_id'].encode()).hexdigest()[:24]

    @staticmethod
    def _receipt_artifact_id(task):
        return 'artifact_review_transport_'+hashlib.sha256(task['task_id'].encode()).hexdigest()[:24]

    def has_registered_prompt_evidence(self,task):
        """Report SQLite registration presence, including invalid/corrupt artifacts."""
        return any(self.store.get_artifact(artifact_id) is not None for artifact_id in
            (self._baseline_artifact_id(task),self._receipt_artifact_id(task)))


def verify_owned_prompt_record(record, *, message_id, session_id, text, metadata):
    """Verify exact queued user prompt in either message-history or inbox shape."""
    if not isinstance(record, dict) or record.get('id') != message_id or record.get('type') != 'user':
        return False
    payload = record.get('payload')
    actual_text = payload.get('text') if isinstance(payload,dict) else record.get('text')
    actual_metadata = payload.get('metadata') if isinstance(payload,dict) else record.get('metadata')
    record_sid = record.get('sessionID',record.get('session_id',record.get('sessionId')))
    if record_sid is not None and record_sid != session_id: return False
    return (actual_text == text and isinstance(actual_metadata,dict) and
            all(actual_metadata.get(k)==v for k,v in metadata.items()))
