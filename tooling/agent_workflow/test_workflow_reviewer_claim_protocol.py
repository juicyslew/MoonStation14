"""Regression coverage for reviewer MCP claims without an LLM-chosen command ID."""
import asyncio
import json
import unittest
from types import SimpleNamespace


class ReviewerClaimProtocolTests(unittest.TestCase):
    def test_public_reviewer_mcp_claim_uses_only_task_and_session(self):
        import httpx
        import workflow_coordinator
        import workflow_review_transport
        import test_workflow_reviewer_api as api_fixture
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        from workflow_coordinator_http_mcp import PROFILE_PATHS

        async def exercise():
            fixture=api_fixture.ReviewerAPITests(); fixture.setUp()
            try:
                task=fixture.task; session=task['assigned_session_id']
                transport=workflow_review_transport.ReviewDispatchBackend(fixture.planner,server='http://127.0.0.1:1234',
                    agents={'implementation':'workflow-reviewer-implementation'},request=lambda *args:fixture.fake_transport(*args))
                coordinator=workflow_coordinator.ExplicitCoordinator(fixture.store,bindings={'author-token':object()},
                    review_bindings={'review-token':fixture.binding},review_results=fixture.api,artifact_dir=fixture.root,
                    identity_verifier=lambda _:True)
                coordinator.review_pipeline=SimpleNamespace(transport=transport); app=coordinator.app()
                factory=lambda headers=None,timeout=None,auth=None: httpx.AsyncClient(
                    transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18444',headers=headers)
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],
                            {'Authorization':'Bearer review-token'},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            tools={t.name:t for t in (await client.list_tools()).tools}
                            claim_schema=tools['claim_task'].inputSchema
                            self.assertEqual({'task_id','session_id'},set(claim_schema['required']))
                            async def body(call): return json.loads(call.content[0].text)
                            async def status(**fields):
                                return await body(await client.call_tool('status',{'task_id':task['task_id'],
                                    'session_id':session,**fields}))
                            guide=status
                            response=await status(input_ref='review_guide',input_offset=0)
                            page=response['input_access']
                            guide_text=page['content']
                            while not page['eof']:
                                response=await status(input_ref='review_guide',input_offset=page['next_offset'])
                                page=response['input_access']
                                guide_text+=page['content']
                            self.assertIn('owner_followups',guide_text)
                            self.assertIn('validation_stage',guide_text)
                            payload={'task_id':task['task_id'],'session_id':session}
                            first=await body(await client.call_tool('claim_task',payload))
                            self.assertTrue(first.get('accepted'),first)
                            self.assertEqual('CLAIMED',fixture.store.get_task(task['task_id'])['state'])
                            import hashlib
                            import workflow_review_dependency
                            import workflow_store
                            assignment=fixture.planner.build_assignment(task['task_id'])
                            envelope=assignment['body']['envelope']
                            message_id='msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
                            original={'id':message_id,'type':'user','sessionID':session,
                                'text':workflow_store.canonical_json(assignment['body']),
                                'metadata':{'review_event_id':envelope['event_id'],'task_id':task['task_id'],
                                            'body_sha256':assignment['body_sha256']},
                                'time':{'created':fixture.store.get_event(envelope['event_id'])['created_ms']}}
                            reset={'id':'synthetic-reset','type':'assistant','time':{'created':original['time']['created']+1},
                                   'error':{'type':'provider.transport','message':'connection reset'}}
                            request=fixture.fake_transport
                            def history_request(server,method,path,request_body=None):
                                if method=='GET' and '/message?' in path:
                                    return 200,{'data':[reset,original],'cursor':{}}
                                return request(server,method,path,request_body)
                            monitor=workflow_review_dependency.ReviewDependencyMonitor(fixture.planner,
                                server='http://127.0.0.1:1234',request=history_request,
                                now_ms=lambda:original['time']['created']+10)
                            self.assertEqual('CLEAR',monitor.poll(task['task_id']))
                            self.assertFalse(fixture.store.task_is_dependency_paused(task['task_id']))
                            deadline=fixture.store.get_task(task['task_id'])['deadline_ms']
                            again=await body(await client.call_tool('claim_task',payload))
                            self.assertTrue(again.get('accepted'),again)
                            self.assertEqual('REPLAYED',again.get('disposition'))
                            self.assertEqual(deadline,fixture.store.get_task(task['task_id'])['deadline_ms'])
                            result={k:v for k,v in fixture.result().items() if k not in
                                {'contract_version','run_id','evaluation_generation','assignment_generation','input_digest'}}
                            base=dict(result)
                            criterion=fixture.planner.build_assignment(task['task_id'])['body']['review']['criteria'][0]['criterion_id']
                            current_checks=fixture.planner.verified_current_checks(task['task_id'])
                            self.assertTrue(current_checks)
                            current_check=next(check for check in current_checks if check['receipt_ref']==fixture.receipt)
                            self.assertEqual('PASS',current_check['evidence']['status'])
                            result['owner_followups']=[{'criterion_id':criterion,'message':'Physical click and audio quality await owner observation.',
                                'manual_check':'Owner tests numbered-bar clicks and hears the configured scream.', 'evidence_refs':[fixture.receipt]}]
                            invalid=dict(result, decision='FAIL', findings=[{'criterion_id':criterion,'category':'COVERAGE',
                                 'message':'An owner observation remains pending.','location':'manual verification',
                                 'evidence_refs':[fixture.receipt],'required_change':'Verify only after owner testing.',
                                 'validation_stage':'OWNER_TESTING',
                                 'coverage_basis':'INSUFFICIENT_CHECK_COVERAGE'}])
                            rejected=await body(await client.call_tool('complete_review',{'result':invalid,'session_id':session}))
                            self.assertFalse(rejected.get('accepted'),rejected)
                            self.assertEqual('CORRECT_RESULT',rejected.get('next_action'))
                            self.assertIn('owner_followups',rejected.get('message',''))
                            missing_execution=dict(base,decision='FAIL',findings=[{'criterion_id':criterion,
                                'category':'COVERAGE','message':'The required check did not execute.',
                                'location':'automated validation','evidence_refs':[fixture.receipt],
                                'required_change':f"Run the {current_check['kind']} check.",'validation_stage':'TECHNICAL',
                                'coverage_basis':'MISSING_EXECUTION_EVIDENCE','check_kind':current_check['kind']}])
                            rejected_execution=await body(await client.call_tool('complete_review',
                                {'result':missing_execution,'session_id':session}))
                            self.assertFalse(rejected_execution.get('accepted'),rejected_execution)
                            self.assertEqual('INVALID_REVIEW_RESULT',rejected_execution.get('code'))
                            self.assertEqual('CORRECT_RESULT',rejected_execution.get('next_action'))
                            self.assertIn('current',rejected_execution.get('message','').lower())
                            self.assertIn('receipt',rejected_execution.get('message','').lower())
                            claimed_deadline=fixture.store.get_task(task['task_id'])['deadline_ms']
                            unregistered_ref=dict(base,inspected_refs=[fixture.receipt,
                                'src/main/java/moonstation/body/BodyActionService.java'])
                            rejected_ref=await body(await client.call_tool('complete_review',
                                {'result':unregistered_ref,'session_id':session}))
                            self.assertFalse(rejected_ref.get('accepted'),rejected_ref)
                            self.assertEqual('INVALID_REVIEW_RESULT',rejected_ref.get('code'))
                            self.assertEqual('CORRECT_RESULT',rejected_ref.get('next_action'))
                            self.assertIn('src/main/java/moonstation/body/BodyActionService.java',
                                rejected_ref.get('message',''))
                            self.assertTrue(any(ref in rejected_ref.get('message','')
                                for ref in (fixture.receipt,'artifact')),
                                rejected_ref.get('message',''))
                            self.assertEqual('CLAIMED',fixture.store.get_task(task['task_id'])['state'])
                            self.assertEqual(claimed_deadline,fixture.store.get_task(task['task_id'])['deadline_ms'])
                            done=await body(await client.call_tool('complete_review',{'result':result,'session_id':session}))
                            self.assertTrue(done.get('accepted'),done)
                            self.assertEqual('COMPLETED',fixture.store.get_task(task['task_id'])['state'])
                            self.assertEqual('PASS',fixture.store.get_task(task['task_id'])['result']['decision'])
                            self.assertEqual(result['owner_followups'],fixture.store.get_task(task['task_id'])['result']['owner_followups'])
                            import workflow_review_pipeline
                            import workflow_task_artifacts
                            terminal_notifications=[]
                            pipeline=workflow_review_pipeline.ReviewPipeline(fixture.store,fixture.planner,fixture.api,
                                transport,None,notify_committed=terminal_notifications.append)
                            def cleanup_completed(task_id):
                                resource=next(r for r in fixture.store.get_task_resources(task_id)
                                    if r['resource_key'].startswith('review-session:'))
                                evidence=json.dumps({'fixture':'reviewer session has stopped after accepted completion',
                                    'task_id':task_id,'session_id':session},sort_keys=True).encode()
                                cleanup_ref,_=workflow_task_artifacts.register_bytes(fixture.store,
                                    fixture.store.get_task(task_id),fixture.root/'cleanup-artifacts','PROVENANCE',evidence)
                                fixture.store.release_resource(resource['resource_key'],claim_id=resource['claim_id'],
                                    run_id=resource['run_id'],generation=resource['generation'],task_id=task_id,
                                    assignment_generation=resource['assignment_generation'],
                                    identity_proof=resource['identity_proof'],cleanup_proof=cleanup_ref)
                                return 'APPLIED'
                            pipeline.review_cancellation=SimpleNamespace(cleanup_completed=cleanup_completed)
                            completed_event=fixture.store.get_event(done['result_id'])['payload']
                            aggregate=pipeline.completed(completed_event)
                            self.assertEqual('PASS',aggregate['decision'],aggregate)
                            self.assertEqual(result['owner_followups'],aggregate['owner_followups'])
                            self.assertEqual('DECIDED',fixture.store.get_run(fixture.run)['attempt']['phase'])
                            self.assertIsNone(fixture.store.get_run(fixture.run)['run'].get('owner_acceptance_id'))
                            terminal=fixture.store.get_terminal_result(fixture.run,task['evaluation_generation'])
                            self.assertEqual(result['owner_followups'],terminal['owner_followups'])
                            from workflow_result_delivery import _feature_feedback_text
                            author_text=_feature_feedback_text(terminal)
                            self.assertIn('Physical click and audio quality await owner observation.',author_text)
                            self.assertIn('not owner acceptance',author_text)
                            self.assertIn('WAIT_FOR_OWNER_TEST',author_text)
                            self.assertEqual(1,len(terminal_notifications))
            finally: fixture.tearDown()

        async def run():
            await exercise()
        asyncio.run(run())

    def test_public_mcp_technical_coverage_fail_remains_blocking_with_owner_followup(self):
        import asyncio, httpx
        import workflow_coordinator, workflow_review_transport, workflow_review_pipeline, workflow_task_artifacts
        import test_workflow_reviewer_api as api_fixture
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        from workflow_coordinator_http_mcp import PROFILE_PATHS

        async def exercise():
            fixture=api_fixture.ReviewerAPITests(); fixture.setUp()
            try:
                task=fixture.task; session=task['assigned_session_id']
                transport=workflow_review_transport.ReviewDispatchBackend(fixture.planner,server='http://127.0.0.1:1234',
                    agents={'implementation':'workflow-reviewer-implementation'},request=lambda *args:fixture.fake_transport(*args))
                coordinator=workflow_coordinator.ExplicitCoordinator(fixture.store,bindings={'author-token':object()},
                    review_bindings={'review-token':fixture.binding},review_results=fixture.api,artifact_dir=fixture.root,
                    identity_verifier=lambda _:True)
                coordinator.review_pipeline=SimpleNamespace(transport=transport); app=coordinator.app()
                factory=lambda headers=None,timeout=None,auth=None:httpx.AsyncClient(
                    transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18444',headers=headers)
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],
                            {'Authorization':'Bearer review-token'},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            async def body(call): return json.loads(call.content[0].text)
                            guide=await body(await client.call_tool('status',{'task_id':task['task_id'],'session_id':session,
                                'input_ref':'review_guide','input_offset':0}))
                            self.assertIn('input_access',guide)
                            claim=await body(await client.call_tool('claim_task',{'task_id':task['task_id'],'session_id':session}))
                            self.assertTrue(claim.get('accepted'),claim)
                            criterion=fixture.planner.build_assignment(task['task_id'])['body']['review']['criteria'][0]['criterion_id']
                            base={k:v for k,v in fixture.result().items() if k not in
                                {'contract_version','run_id','evaluation_generation','assignment_generation','input_digest'}}
                            followup={'criterion_id':criterion,'message':'Native-pixel scale observation awaits owner.',
                                'manual_check':'Owner checks the bar at native pixels.','evidence_refs':[fixture.receipt]}
                            base['owner_followups']=[followup]
                            invalid=dict(base,decision='FAIL',findings=[{'criterion_id':criterion,'category':'COVERAGE',
                                'message':'Owner observation is pending.','location':'manual test','evidence_refs':[fixture.receipt],
                                'required_change':'Wait for owner.','validation_stage':'OWNER_TESTING',
                                'coverage_basis':'INSUFFICIENT_CHECK_COVERAGE'}])
                            rejected=await body(await client.call_tool('complete_review',{'result':invalid,'session_id':session}))
                            self.assertFalse(rejected.get('accepted'),rejected)
                            self.assertEqual('CORRECT_RESULT',rejected.get('next_action'))
                            technical=dict(base,decision='FAIL',findings=[{'criterion_id':criterion,'category':'COVERAGE',
                                'message':'Automated evidence confirms the action bar fails to preserve assigned slots.',
                                'location':'BodyActionSlots resolution check','evidence_refs':[fixture.receipt],
                                'required_change':'Restore stable action-to-slot resolution.','validation_stage':'TECHNICAL',
                                'coverage_basis':'INSUFFICIENT_CHECK_COVERAGE'}])
                            done=await body(await client.call_tool('complete_review',{'result':technical,'session_id':session}))
                            self.assertTrue(done.get('accepted'),done)
                            self.assertEqual('FAIL',fixture.api.aggregate(fixture.run)['decision'])
                            notified=[]
                            pipeline=workflow_review_pipeline.ReviewPipeline(fixture.store,fixture.planner,fixture.api,
                                transport,None,notify_committed=notified.append)
                            def cleanup(task_id):
                                resource=next(x for x in fixture.store.get_task_resources(task_id) if x['resource_key'].startswith('review-session:'))
                                ref,_=workflow_task_artifacts.register_bytes(fixture.store,fixture.store.get_task(task_id),
                                    fixture.root/'cleanup-artifacts','PROVENANCE',b'fixture reviewer session stopped after result')
                                fixture.store.release_resource(resource['resource_key'],claim_id=resource['claim_id'],
                                    run_id=resource['run_id'],generation=resource['generation'],task_id=task_id,
                                    assignment_generation=resource['assignment_generation'],identity_proof=resource['identity_proof'],cleanup_proof=ref)
                                return 'APPLIED'
                            pipeline.review_cancellation=SimpleNamespace(cleanup_completed=cleanup)
                            aggregate=pipeline.completed(fixture.store.get_event(done['result_id'])['payload'])
                            self.assertEqual('FAIL',aggregate['decision'])
                            terminal=fixture.store.get_terminal_result(fixture.run,task['evaluation_generation'])
                            self.assertEqual('FAIL',terminal['result']); self.assertEqual('FIX_FINDINGS',terminal['next_action'])
                            self.assertIn('stable action-to-slot resolution',terminal['findings'][0]['required_change'])
                            self.assertEqual([followup],terminal['owner_followups']); self.assertEqual(1,len(notified))
            finally: fixture.tearDown()
        asyncio.run(exercise())


if __name__=='__main__': unittest.main()
