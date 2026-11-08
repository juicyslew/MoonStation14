"""Regression for readiness finish attempts across admitted-job lifecycle."""
import json
import copy
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch

try:
    import test_workflow as fixtures
except ModuleNotFoundError:
    from tooling.agent_workflow import test_workflow as fixtures

wf = fixtures.wf


class GateFinalizationLifecycleTests(unittest.TestCase):
    def setUp(self):
        from tooling.agent_workflow import gate_dispatch, gate_execution, gate_liveness
        aliases=patch.dict('sys.modules', {'gate_dispatch':gate_dispatch,
                                           'gate_execution':gate_execution,
                                           'gate_liveness':gate_liveness})
        aliases.start(); self.addCleanup(aliases.stop)
        self.fixture = fixtures.WorkflowTests(methodName='runTest')
        self.fixture.setUp()
        self.root = self.fixture.root / 'work' / 'agent_exchange'

    def tearDown(self):
        self.fixture.tearDown()

    def test_pending_finish_waits_for_same_bound_job_and_failed_receipt(self):
        from tooling.agent_workflow import gate_execution

        plan = {'title':'lifecycle fixture','kind':'bugfix','acceptance_criteria':['fixture evidence'],
                'fixture':'seed.txt','manual_steps':['Inspect fixture'],
                'checks':[{'argv':['python','-c','raise SystemExit(1)'],'rationale':'intentional fixture failure',
                           'covers':['fixture evidence']}]}
        planfile = self.fixture.root / 'plan.json'
        planfile.write_text(json.dumps(plan))
        self.fixture.cli('submit','lifecycle-1','--agent','author','--base-ref',self.fixture.base,
                         '--build-identity','Python fixture','--steps','fixture only',
                         '--unverified','No Minecraft execution','--gate-plan',planfile)
        cp = self.root / 'candidates' / 'lifecycle-1.json'
        candidate = wf.readjson(cp)
        candidate['gate_request'] = {'prompt_admitted':True,'snapshot_key':candidate['snapshot_key'],
                                     'session_id':'fixture-lead','origin_session_id':'fixture-author'}
        wf.writejson(cp,candidate)
        job = gate_execution.request(self.root,'lifecycle-1',workflow=wf)

        with self.assertRaises(wf.GateFinalizationPending):
            wf.readiness_finalization_guard(candidate,self.root,'needs_changes',None)
        self.assertIsNone(candidate.get('gate_outcome'))
        self.assertIsNone(candidate.get('review'))
        self.assertIsNone(candidate.get('author_completion'))

        # Execute the admitted producer through the existing finite Python runner.
        with self.assertRaisesRegex(ValueError,'gate checks failed'):
            self.fixture.run_checks('lifecycle-1',timeout=10)
        candidate = wf.readjson(cp)
        receipt = Path(candidate['gate_validation']['path'])
        job.update(state='completed',receipt={'path':str(receipt),'sha256':wf.sha(receipt)})
        gate_execution._write(gate_execution._path(self.root,'lifecycle-1'),job)
        gate, data, summary = wf.readiness_finalization_guard(candidate,self.root,'needs_changes',None)
        self.assertEqual(data['passed'],False)
        self.assertEqual(summary['status'],'FAILED_OR_PARTIAL')
        self.assertEqual(wf.sha(gate),job['receipt']['sha256'])

        # The real MCP error mapper must distinguish liveness from bad input.
        from tooling.agent_workflow import workflow_mcp, gate_dispatch
        pending_payload=workflow_mcp._error_payload(wf.GateFinalizationPending('observer still owns job'),gate_dispatch,wf)
        self.assertEqual(pending_payload['code'],'validation_pending')
        self.assertIn('do not poll, resubmit',pending_payload['next'])
        self.assertNotEqual(pending_payload['code'],'invalid_request')

        # Matrix against actual report/finish entry point; every refusal must
        # occur before report, review, outcome, or author receipt persistence.
        matrix_report=wf.author_report_dir(candidate)/'lifecycle-1-integration-lead-review.json'
        matrix_args=Namespace(id='lifecycle-1',actor='integration-lead',result='needs_changes',
                              finding=['Actual failed check requires a fix.'],scoped_review=None,finish=True)
        base_job=copy.deepcopy(job)
        def assert_unwritten(error_type, message=''):
            before=wf.readjson(cp)
            with self.assertRaises(error_type, msg=message): wf.cmd_gate_report(matrix_args,self.root)
            after=wf.readjson(cp)
            self.assertEqual(after,before)
            self.assertFalse(matrix_report.exists())
            for key in ('review','gate_outcome','author_completion'):
                self.assertEqual(after.get(key),before.get(key))
        for state in ('requested','running'):
            for decision in ('needs_changes','ready_for_owner'):
                matrix_args.result=decision
                variant=copy.deepcopy(base_job); variant['state']=state
                gate_execution._write(gate_execution._path(self.root,'lifecycle-1'),variant)
                assert_unwritten(wf.GateFinalizationPending,f'{state}/{decision}')
        matrix_args.result='needs_changes'
        invalid_jobs=[]
        for key,value in (('snapshot_key','wrong-snapshot'),('lead_session_id','wrong-lead'),('plan_digest','wrong-plan')):
            variant=copy.deepcopy(base_job); variant[key]=value; invalid_jobs.append(variant)
        variant=copy.deepcopy(base_job); variant['receipt']={'path':base_job['receipt']['path'],'sha256':'0'*64}; invalid_jobs.append(variant)
        variant=copy.deepcopy(base_job); variant['receipt']={'path':str(self.fixture.root/'elsewhere.json'),'sha256':base_job['receipt']['sha256']}; invalid_jobs.append(variant)
        for state in ('requested','launching','running','unknown','interrupted'):
            variant=copy.deepcopy(base_job); variant['state']=state; invalid_jobs.append(variant)
        for variant in invalid_jobs:
            gate_execution._write(gate_execution._path(self.root,'lifecycle-1'),variant)
            assert_unwritten(ValueError,str(variant))
        gate_execution._write(gate_execution._path(self.root,'lifecycle-1'),base_job)

        # Required reviewers admitted but not yet collected must hold the real
        # report/finish command before it writes even a provisional review.
        candidate['scoped_reviews'] = {
            reviewer:{'snapshot_key':candidate['snapshot_key'],'dispatch_pending':True,
                      'collection_pending':True,'session_id':reviewer+'-session'}
            for reviewer in ('review','fidelity-reviewer')}
        for reviewer,record in candidate['scoped_reviews'].items():
            path=self.fixture.root/(reviewer+'-report.json')
            wf.writejson(path,{'candidate_id':'lifecycle-1','snapshot_key':candidate['snapshot_key'],
                               'reviewer':reviewer,'decision':'ready_for_owner',
                               'findings':['Independent scoped review is complete.'],'viewed_images':[]})
            record.update(report_path=str(path),report_sha256=wf.sha(path))
        wf.writejson(cp,candidate)
        args = Namespace(id='lifecycle-1',actor='integration-lead',result='needs_changes',
                         finding=['The validation check failed; address its evidence.'],
                         scoped_review=None,finish=True)
        report_path = wf.author_report_dir(candidate) / 'lifecycle-1-integration-lead-review.json'
        with self.assertRaises(wf.GateFinalizationPending):
            wf.cmd_gate_report(args,self.root)
        held = wf.readjson(cp)
        self.assertFalse(report_path.exists())
        self.assertIsNone(held.get('review'))
        self.assertIsNone(held.get('gate_outcome'))
        self.assertIsNone(held.get('author_completion'))

        for reviewer in ('review','fidelity-reviewer'):
            for pending_key in ('dispatch_pending','collection_pending'):
                record=candidate['scoped_reviews'][reviewer]
                record.update(dispatch_pending=False,collection_pending=False)
                record[pending_key]=True
                wf.writejson(cp,candidate)
                with self.assertRaises(wf.GateFinalizationPending): wf.cmd_gate_report(args,self.root)
                self.assertFalse(report_path.exists())
                self.assertIsNone(wf.readjson(cp).get('review'))
            record.update(dispatch_pending=False,collection_pending=False,
                          snapshot_key='wrong-snapshot')
            wf.writejson(cp,candidate)
            with self.assertRaises(ValueError): wf.cmd_gate_report(args,self.root)
            self.assertFalse(report_path.exists())
            self.assertIsNone(wf.readjson(cp).get('review'))
            record['snapshot_key']=candidate['snapshot_key']
            record['report_sha256']='0'*64
            wf.writejson(cp,candidate)
            with self.assertRaises(ValueError): wf.cmd_gate_report(args,self.root)
            self.assertFalse(report_path.exists())
            self.assertIsNone(wf.readjson(cp).get('review'))
            record['report_sha256']=wf.sha(Path(record['report_path']))

        # Model the existing collector callback: valid scoped report bytes are
        # saved, hashed, and the same admitted records leave pending state.
        for reviewer in ('review','fidelity-reviewer'):
            record = candidate['scoped_reviews'][reviewer]
            record.update(dispatch_pending=False,collection_pending=False)
        wf.writejson(cp,candidate)
        # Guard now consumes actual failed receipt evidence plus both real,
        # hash-bound scoped reports; ready scoped opinions do not erase failure.
        _,_,summary = wf.readiness_finalization_guard(candidate,self.root,'needs_changes',None)
        self.assertEqual(summary['status'],'FAILED_OR_PARTIAL')
        ready_args=Namespace(id='lifecycle-1',actor='integration-lead',result='ready_for_owner',
                             finding=['Ready is impossible with failed validation.'],scoped_review=None,finish=True)
        with self.assertRaises(ValueError): wf.cmd_gate_report(ready_args,self.root)
        self.assertFalse(report_path.exists())
        self.assertIsNone(wf.readjson(cp).get('review'))
        self.assertIsNone(wf.readjson(cp).get('gate_outcome'))
        from tooling.agent_workflow import gate_dispatch
        posts=[]
        def transport(server,method,path,body=None,timeout=15):
            if method=='GET' and path=='/api/session/fixture-author':
                return 200,{'data':{'id':'fixture-author','agent':'author',
                                   'directory':candidate['worktree']['worktree']}}
            if method=='POST' and path=='/api/session/fixture-author/prompt':
                posts.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'fixture-author','type':'user',
                                    'delivery':'steer','payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError((method,path,body))
        with patch.object(gate_dispatch,'_server',return_value=None), \
             patch.object(gate_dispatch,'_check_v2'), \
             patch.object(gate_dispatch,'_request',side_effect=transport):
            wf.cmd_gate_report(args,self.root)
        finished = wf.readjson(cp)
        self.assertEqual(finished['gate_outcome']['result'],'needs_changes')
        lead_report = wf.read_report(finished['review']['report_path'])
        self.assertEqual(lead_report['evidence_summary']['status'],'FAILED_OR_PARTIAL')
        self.assertEqual(lead_report['findings'],args.finding)
        self.assertEqual(wf.sha(Path(finished['review']['report_path'])),finished['review']['report_sha256'])
        # Exact command retry reuses the immutable bound verdict/report.
        with patch.object(gate_dispatch,'_server',return_value=None), \
             patch.object(gate_dispatch,'_check_v2'), \
             patch.object(gate_dispatch,'_request',side_effect=transport):
            wf.cmd_gate_report(args,self.root)
        self.assertEqual(wf.readjson(cp)['gate_outcome'],finished['gate_outcome'])
        self.assertEqual(len(posts),1)
        completion=wf.readjson(cp)['author_completion']
        self.assertEqual(completion['status'],'delivered')
        self.assertEqual(posts[0]['id'],completion['id'])


if __name__ == '__main__':
    unittest.main()
