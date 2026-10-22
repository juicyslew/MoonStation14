"""One-shot, explicitly commanded continuation of a paused review session."""
import hashlib
import time
import urllib.parse

try:
    import gate_dispatch, workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_dispatch, workflow_store


def _created(record):
    raw=record.get('time',{}).get('created') if isinstance(record,dict) and isinstance(record.get('time'),dict) else None
    try: return int(raw)
    except (TypeError,ValueError): return -1


class ReviewRetryAdapter:
    """Retry a provider-paused canonical prompt without creating another turn."""
    def __init__(self, planner, *, server, request, now_ms=None, agents=None, transport=None, cancellation=None):
        self.planner, self.store, self.server, self.request = planner, planner.store, server.rstrip('/'), request
        self.now_ms = now_ms or (lambda: int(time.time() * 1000))
        self.agents = dict(agents or getattr(transport, 'agents', {}) or {})
        self.transport = transport
        self.cancellation = cancellation

    def _owned_history_context(self, task, assignment):
        try:
            import workflow_review_history, workflow_review_control
        except ModuleNotFoundError:
            from tooling.agent_workflow import workflow_review_history, workflow_review_control
        event=self.store.get_event(assignment['body']['envelope']['event_id'])
        if not event: raise ValueError('dispatch event unavailable')
        sid=task['assigned_session_id']; body=assignment['body']; mid='msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
        text=workflow_store.canonical_json(body)
        meta={'review_event_id':body['envelope']['event_id'],'task_id':task['task_id'],'body_sha256':assignment['body_sha256']}
        expected={mid:(text,meta)}
        for control in workflow_review_control.continuation_bodies(self.store.list_review_retry_receipts(task['task_id']),task,assignment,self.server):
            expected[control['id']]=(control['text'],control['metadata'])
        records=workflow_review_history.read_since(self.request,self.server,sid,event['created_ms'])
        records=[r for r in records if _created(r)>=event['created_ms']]
        return expected,records,event['created_ms']

    def _original_admission_proven(self, task, assignment, expected, records, inbox):
        try:
            import workflow_review_transport
        except ModuleNotFoundError:
            from tooling.agent_workflow import workflow_review_transport
        original_id=next(iter(expected))
        text,metadata=expected[original_id]
        if any(workflow_review_transport.verify_owned_prompt_record(r,message_id=original_id,
                session_id=task['assigned_session_id'],text=text,metadata=metadata) for r in records+inbox):
            return True
        try:
            if self.cancellation is None:
                import workflow_review_cancellation
                transport=self.transport or workflow_review_transport.ReviewDispatchBackend(
                    self.planner,server=self.server,agents=self.agents,request=self.request)
                self.cancellation=workflow_review_cancellation.ReviewCancellation(
                    self.store,self.planner,transport,server=self.server,request=self.request)
            saved=self.cancellation.get_quiescence_checkpoint(task['task_id'])
            proof={'purpose':'REVIEW_QUIESCENCE','task_id':task['task_id'],'run_id':task['run_id'],
                'generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                'input_digest':task['input_digest'],'session_id':task['assigned_session_id'],
                'message_id':original_id,'body_sha256':assignment['body_sha256']}
            return isinstance(saved,dict) and all(saved.get(k)==v for k,v in proof.items())
        except Exception:
            return False

    def observe_effect(self, task_id, intent):
        """Read-only proof of a previously recorded retry effect."""
        try:
            import workflow_review_transport, workflow_review_history, workflow_review_control
        except ModuleNotFoundError:
            from tooling.agent_workflow import workflow_review_transport, workflow_review_history, workflow_review_control
        try:
            task=self.store.get_task(task_id)
            binding=intent.get('binding'); proof=intent.get('proof')
            if not task or task.get('kind')!='REVIEW' or task.get('state') not in ('DISPATCHING','CLAIMED','RUNNING','CANCEL_REQUESTED','COMPLETED') or not binding or not proof:
                return 'UNKNOWN'
            actual={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            run=self.store.get_run(task['run_id'])
            resources=self.store.get_task_resources(task_id)
            owned=[r for r in resources if r.get('state') in ('HELD','UNKNOWN') and r.get('identity_proof')==proof]
            if actual!=binding or not run or run['run']['current_attempt']!=task['evaluation_generation'] or len(owned)!=1:
                return 'UNKNOWN'
            assignment=self.planner.build_assignment(task_id); sid=task['assigned_session_id']
            if intent.get('effect_status')=='APPLIED': return 'APPLIED'
            if self.transport is None:
                self.transport=workflow_review_transport.ReviewDispatchBackend(self.planner,server=self.server,agents=self.agents,request=self.request)
            quoted=urllib.parse.quote(sid)
            status,reply=self.request(self.server,'GET','/api/session/'+quoted)
            if status!=200 or not self.transport.session_matches_assignment(task,assignment,reply): return 'UNKNOWN'
            status,active=self.request(self.server,'GET','/api/session/active')
            active=active.get('data',active) if isinstance(active,dict) else None
            if status!=200 or not isinstance(active,dict) or (sid in active and active[sid] is None): return 'UNKNOWN'
            status,inbox=self.request(self.server,'GET','/api/session/'+quoted+'/inbox')
            rows=inbox.get('data') if isinstance(inbox,dict) else None
            if status!=200 or not isinstance(rows,list): return 'UNKNOWN'
            body=assignment['body']; mid=proof['message_id']; text=workflow_store.canonical_json(body)
            meta={'review_event_id':body['envelope']['event_id'],'task_id':task_id,'body_sha256':assignment['body_sha256']}
            expected,records,event_time=self._owned_history_context(task,assignment)
            for r in records:
                if r.get('type')=='user':
                    spec=expected.get(r.get('id'))
                    if spec is None or not workflow_review_transport.verify_owned_prompt_record(r,message_id=r.get('id'),session_id=sid,text=spec[0],metadata=spec[1]): return 'UNKNOWN'
            for r in rows:
                ident=r.get('id') if isinstance(r,dict) else None
                if ident in expected and not workflow_review_transport.verify_owned_prompt_record(r,message_id=ident,session_id=sid,text=expected[ident][0],metadata=expected[ident][1]): return 'UNKNOWN'
            # The original assignment admission is the immutable context for every retry effect.
            if not self._original_admission_proven(task,assignment,expected,records,rows): return 'UNKNOWN'
            mode=intent.get('mode'); intent_ms=intent.get('intent_ms')
            def exact(record, ident, expected_text, expected_meta):
                return workflow_review_transport.verify_owned_prompt_record(record,message_id=ident,session_id=sid,text=expected_text,metadata=expected_meta)
            if mode=='CONTROL_PROMPT':
                snapshots=intent.get('controls_snapshot') or []
                if not snapshots: return 'UNKNOWN'
                snap=snapshots[0]; control=snap.get('body')
                if not isinstance(control,dict) or snap.get('body_sha256')!=hashlib.sha256(workflow_store.canonical_json(control).encode()).hexdigest(): return 'UNKNOWN'
                ident=control.get('id')
                if any(exact(r,ident,control.get('text'),control.get('metadata')) and _created(r)>=intent_ms for r in records): return 'APPLIED'
                if any(isinstance(r,dict) and r.get('id')==ident and exact(r,ident,control.get('text'),control.get('metadata')) for r in rows): return 'APPLIED'
            elif mode=='PATCH':
                if any(isinstance(r,dict) and r.get('id')==mid and r.get('delivery')=='steer' and exact(r,mid,text,meta) for r in rows): return 'APPLIED'
                if any(r.get('id')!=mid and r.get('type')=='assistant' and _created(r)>=intent_ms for r in records): return 'APPLIED'
            elif mode=='INTERRUPT_RESUME':
                controls=intent.get('controls_snapshot') or []
                if controls and not any(any(isinstance(r,dict) and r.get('id')==x.get('id') for r in rows) for x in controls):
                    entry=active.get(sid)
                    if isinstance(entry,dict) and entry.get('type')=='running' and any(r.get('type')=='assistant' and _created(r)>=intent_ms for r in records): return 'APPLIED'
        except Exception:
            return 'UNKNOWN'
        return 'UNKNOWN'

    def retry(self, task_id, command_id, authenticated_caller):
        task = self.store.get_task(task_id) if isinstance(task_id, str) else None
        sid = task.get('assigned_session_id') if isinstance(task, dict) else None
        if not sid:
            return {'status':'REJECTED','reason':'review task is not active'}
        try:
            try: import workflow_review_effects
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_effects
            lock=workflow_review_effects.review_effect_guard(self.store,self.server,sid,nonblocking=True)
        except (BlockingIOError,TimeoutError):
            return {'status':'PENDING','reason':'review-session effects are in progress; retry the same command'}
        try:
            return self._retry_locked(task_id,command_id,authenticated_caller)
        finally:
            lock.close()

    def _retry_locked(self, task_id, command_id, authenticated_caller):
        if not all(isinstance(v, str) and v.strip() for v in (task_id, command_id, authenticated_caller)):
            return {'status':'REJECTED','reason':'task, command, and authenticated caller are required'}
        task = self.store.get_task(task_id)
        saved = self.store.get_review_retry(task_id=task_id, caller=authenticated_caller, command_id=command_id)
        if saved and saved.get('status') in ('RESUMED','STOPPING'):
            return saved
        if saved and saved.get('status') == 'INTENT':
            return self._reconcile(task_id, command_id, authenticated_caller, saved)
        if not task or task.get('kind') != 'REVIEW' or task.get('state') not in ('DISPATCHING','CLAIMED','RUNNING'):
            return {'status':'REJECTED','reason':'review task is not active'}
        pause = self.store.get_task_dependency_pause(task_id)
        if not pause or not pause.get('active'):
            return {'status':'REJECTED','reason':'review task is not dependency-paused'}
        assignment = self.planner.build_assignment(task_id)
        body = assignment['body']; envelope = body.get('envelope', {})
        sid = task.get('assigned_session_id')
        message_id = 'msg_' + hashlib.sha256((task_id + assignment['body_sha256']).encode()).hexdigest()[:40]
        binding = {k: task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
        resources = [r for r in self.store.get_task_resources(task_id) if r.get('state') == 'HELD']
        proof = {'server':self.server,'session_id':sid,'message_id':message_id,'body_sha256':assignment['body_sha256'],
                 'task_id':task_id,'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
                 'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
        if len(resources) != 1 or resources[0].get('identity_proof') != proof:
            return {'status':'REJECTED','reason':'exclusive review-session claim does not match task binding'}
        event = self.store.get_event(envelope.get('event_id'))
        if not event or event.get('kind') != 'DISPATCH' or event.get('payload') != envelope:
            return {'status':'REJECTED','reason':'saved review assignment is stale'}
        try:
            try: import workflow_review_transport, workflow_review_control
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_transport, workflow_review_control
            if self.transport is None:
                self.transport=workflow_review_transport.ReviewDispatchBackend(self.planner,server=self.server,agents=self.agents,request=self.request)
            quoted = urllib.parse.quote(sid)
            status, session_reply = self.request(self.server, 'GET', '/api/session/' + quoted)
            session = session_reply.get('data', session_reply) if isinstance(session_reply, dict) else None
            if status != 200 or not self.transport.session_matches_assignment(task,assignment,session_reply):
                return {'status':'UNKNOWN','reason':'bound session could not be verified'}
            status, active = self.request(self.server, 'GET', '/api/session/active')
            active = active.get('data', active) if isinstance(active,dict) else None
            if status != 200 or not isinstance(active,dict) or (sid in active and active[sid] is None):
                return {'status':'UNKNOWN','reason':'active-session map could not be verified'}
            if sid in active:
                return {'status':'UNKNOWN','reason':'review session is active; refusing a second wake'}
            status, listing = self.request(self.server, 'GET', '/api/session/' + quoted + '/inbox')
            rows = listing.get('data') if isinstance(listing,dict) else None
            if status != 200 or not isinstance(rows,list):
                return {'status':'UNKNOWN','reason':'session history could not be verified'}
            text = workflow_store.canonical_json(body)
            expected_meta = {'review_event_id':envelope['event_id'],'task_id':task_id,'body_sha256':assignment['body_sha256']}
            own = [m for m in rows if isinstance(m,dict) and m.get('id') == message_id]
            try: expected_users,history,event_time=self._owned_history_context(task,assignment)
            except Exception: return {'status':'UNKNOWN','reason':'complete review-session history is unavailable'}
            for record in history:
                if record.get('type')=='user':
                    proof_item=expected_users.get(record.get('id'))
                    if proof_item is None or not workflow_review_transport.verify_owned_prompt_record(record,message_id=record.get('id'),session_id=sid,text=proof_item[0],metadata=proof_item[1]):
                        return {'status':'UNKNOWN','reason':'foreign or modified user history conflicts with review task'}
            for item in rows:
                if isinstance(item,dict) and item.get('type')=='user':
                    proof_item=expected_users.get(item.get('id'))
                    if proof_item is None or not workflow_review_transport.verify_owned_prompt_record(item,message_id=item.get('id'),session_id=sid,text=proof_item[0],metadata=proof_item[1]):
                        return {'status':'UNKNOWN','reason':'inbox contains unrecognized or modified user control'}
            if not self._original_admission_proven(task,assignment,expected_users,history,rows):
                return {'status':'UNKNOWN','reason':'original prompt admission is unproven'}
            if len(own)==1 and own[0].get('delivery')=='queue':
                if not workflow_review_transport.verify_owned_prompt_record(own[0],message_id=message_id,session_id=sid,text=text,metadata=expected_meta):
                    return {'status':'UNKNOWN','reason':'canonical queued prompt proof conflicts'}
                mode='PATCH'; controls=None
                intent=self.store.begin_review_retry(task_id=task_id,caller=authenticated_caller,command_id=command_id,binding=binding,proof=proof,mode=mode,controls_snapshot=controls)
                if intent.get('disposition') == 'REPLAYED':
                    return {'status':'UNKNOWN','reason':'prior retry intent exists; refusing another wake'}
                try:
                    status, reply = self.request(self.server,'PATCH','/api/session/'+quoted+'/inbox/'+urllib.parse.quote(message_id),{'delivery':'steer'})
                except Exception:
                    status, reply = None, None
                applied = status == 204
                if not applied:
                    return {'status':'UNKNOWN','reason':'steer outcome unproven; do not repeat this wake'}
            elif not own:
                messages=history
                roots=[m for m in messages if isinstance(m,dict) and m.get('id')==message_id and m.get('type')=='user']
                if roots and (len(roots)!=1 or not workflow_review_transport.verify_owned_prompt_record(roots[0],message_id=message_id,session_id=sid,text=text,metadata=expected_meta)):
                    return {'status':'UNKNOWN','reason':'original canonical prompt is not proven in history'}
                controls=[]
                for item in rows:
                    if isinstance(item,dict) and item.get('type')=='user':
                        if item.get('id')==message_id: continue
                        saved_snapshots=[s for receipt in self.store.list_review_retry_receipts(task_id) for s in receipt.get('controls_snapshot',[])]
                        snapshot=next((s for s in saved_snapshots if s.get('id')==item.get('id')),None)
                        if snapshot is None: return {'status':'UNKNOWN','reason':'inbox control has no retained canonical receipt'}
                        controls.append(snapshot)
                        continue
                    snapshot=workflow_review_control.synthetic_snapshot(item,sid,event_time)
                    if snapshot is None or item.get('delivery')!='steer':
                        return {'status':'UNKNOWN','reason':'inbox contains unsupported or foreign continuation control'}
                    controls.append(snapshot)
                if not controls:
                    # Explicit owner action supplies a new checkpoint control, never a replay of the assignment.
                    try: import workflow_review_control
                    except ModuleNotFoundError: from tooling.agent_workflow import workflow_review_control
                    control_body=workflow_review_control.continuation_body(task,assignment,command_id)
                    control_meta=control_body['metadata']; control_text=control_body['text']; control_id=control_body['id']
                    controls=[{'id':control_id,'session_id':sid,'body_sha256':hashlib.sha256(workflow_store.canonical_json(control_body).encode()).hexdigest(),
                        'text':control_text,'metadata':control_meta,'body':control_body}]
                    intent=self.store.begin_review_retry(task_id=task_id,caller=authenticated_caller,command_id=command_id,
                        binding=binding,proof=proof,mode='CONTROL_PROMPT',controls_snapshot=controls)
                    if intent.get('disposition')=='REPLAYED': return {'status':'UNKNOWN','reason':'prior retry intent exists; refusing another wake'}
                    try: ack_status,ack=self.request(self.server,'POST','/api/session/'+quoted+'/prompt',control_body)
                    except Exception: ack_status,ack=None,None
                    if ack_status!=200:
                        return {'status':'UNKNOWN','reason':'continuation admission acknowledgement unavailable; reconcile same command'}
                    try: gate_dispatch._acknowledged_prompt(ack,sid,control_body,control_meta)
                    except gate_dispatch.DispatchError:
                        return {'status':'UNKNOWN','reason':'continuation admission acknowledgement did not match exact control'}
                    applied=True
                else:
                    intent=self.store.begin_review_retry(task_id=task_id,caller=authenticated_caller,command_id=command_id,binding=binding,proof=proof,mode='INTERRUPT_RESUME',controls_snapshot=controls)
                    if intent.get('disposition')=='REPLAYED': return {'status':'UNKNOWN','reason':'prior retry intent exists; refusing another wake'}
                    try: status,reply=self.request(self.server,'POST','/api/session/'+quoted+'/interrupt?resume=true')
                    except Exception: status,reply=None,None
                    if status!=200 or not isinstance(reply,dict) or not isinstance(reply.get('interrupted'),bool): return {'status':'UNKNOWN','reason':'resume interrupt acknowledgement unproven'}
                    status,active_after=self.request(self.server,'GET','/api/session/active')
                    active_after=active_after.get('data',active_after) if isinstance(active_after,dict) else None
                    if status!=200 or not isinstance(active_after,dict) or sid not in active_after:
                        return {'status':'UNKNOWN','reason':'resume accepted but owned session wake is not yet observed'}
                    applied=True
            else:
                return {'status':'UNKNOWN','reason':'original prompt inbox state is ambiguous'}
            self.store.record_review_retry_effect(task_id=task_id,caller=authenticated_caller,command_id=command_id)
            current=self.store.get_run(task['run_id'])
            if current and current.get('attempt',{}).get('phase')=='STOPPING':
                receipt={'status':'STOPPING','task_id':task_id,'command_id':command_id,
                         'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
                         'effect_status':'APPLIED'}
                return self.store.complete_review_retry(task_id=task_id,caller=authenticated_caller,command_id=command_id,response=receipt)['response']
            result = self.store.resume_dependency_task(task_id=task_id, dependency_key=pause['dependency_key'], binding=binding, now_ms=self.now_ms())
            receipt = {'status':'RESUMED','task_id':task_id,'command_id':command_id,'paused_ms':result.get('paused_ms'),
                       'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
            completed=self.store.complete_review_retry(task_id=task_id, caller=authenticated_caller, command_id=command_id, response=receipt)
            return completed['response']
        except (workflow_store.StoreError, KeyError, TypeError, ValueError) as exc:
            return {'status':'UNKNOWN','reason':str(exc)[:400]}

    def _reconcile(self, task_id, command_id, caller, intent):
        task=self.store.get_task(task_id)
        binding=intent.get('binding'); proof=intent.get('proof')
        if not task or not binding or not proof:
            return {'status':'UNKNOWN','reason':'retry intent binding is incomplete'}
        actual={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
        held=[r for r in self.store.get_task_resources(task_id) if r.get('state')=='HELD']
        run=self.store.get_run(task['run_id']) if task else None
        pause=self.store.get_task_dependency_pause(task_id)
        if (not task or task.get('kind')!='REVIEW' or task.get('state') not in ('DISPATCHING','CLAIMED','RUNNING') or
            not run or run['run']['current_attempt']!=task['evaluation_generation'] or not pause or not pause.get('active') or
            actual!=binding or len(held)!=1 or held[0].get('identity_proof')!=proof):
            return {'status':'UNKNOWN','reason':'retry intent binding or held resource changed'}
        if self.observe_effect(task_id,intent)!='APPLIED':
            return {'status':'UNKNOWN','reason':'no post-intent evidence proves retry effect was applied'}
        self.store.record_review_retry_effect(task_id=task_id,caller=caller,command_id=command_id)
        current=self.store.get_run(task['run_id'])
        if current and current.get('attempt',{}).get('phase')=='STOPPING':
            receipt={'status':'STOPPING','task_id':task_id,'command_id':command_id,
                     'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
                     'effect_status':'APPLIED'}
            return self.store.complete_review_retry(task_id=task_id,caller=caller,command_id=command_id,response=receipt)['response']
        if pause and pause.get('active'):
            resumed=self.store.resume_dependency_task(task_id=task_id,dependency_key=pause['dependency_key'],binding=binding,now_ms=self.now_ms())
            paused_ms=resumed.get('paused_ms')
        else: paused_ms=self.store.task_paused_duration_ms(task_id,self.now_ms())
        receipt={'status':'RESUMED','task_id':task_id,'command_id':command_id,'paused_ms':paused_ms,
                 'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
        return self.store.complete_review_retry(task_id=task_id,caller=caller,command_id=command_id,response=receipt)['response']
