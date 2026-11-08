import json
import unittest
from pathlib import Path
from unittest.mock import patch

from tooling.agent_workflow import gate_dispatch, gate_execution, gate_liveness, workflow_runtime
from tooling.agent_workflow import test_gate_premature_recovery as recovery_fixture


class RecoveredContinuationTests(unittest.TestCase):
    def setUp(self):
        aliases=patch.dict('sys.modules', {'gate_dispatch':gate_dispatch,
                                           'gate_execution':gate_execution,
                                           'gate_liveness':gate_liveness})
        aliases.start(); self.addCleanup(aliases.stop)

    def test_repair_collects_both_scoped_reviews_and_continues_same_lead_once_each(self):
        fixture=recovery_fixture.PrematureRecoveryTests(); fixture.setUp(); self.addCleanup(fixture.doCleanups)
        root=fixture.root; ident='r31'; cp=root/'candidates'/'r31.json'
        candidate=workflow_runtime.readjson(cp)
        candidate['gate_request']['central_worktree']=str(root)
        candidate['scoped_reviews']={name:{'snapshot_key':'snap','dispatch_pending':True,'collection_pending':True,
            'lead_continuation_pending':False,'reviewer_session_id':name+'-session'} for name in ('reviewer-a','reviewer-b')}
        workflow_runtime.writejson(cp,candidate)
        with patch('tooling.agent_workflow.gate_execution.read',return_value=fixture.job):
            repaired=gate_liveness.recover_premature_outcome(root,ident,expected_snapshot='snap',
                expected_lead_session_id='lead',expected_report_sha256=fixture.report_sha,
                expected_receipt_sha256=fixture.receipt_sha,workflow=fixture.wf)
        self.assertEqual(repaired['status'],'recovered')
        job_before=fixture.job.copy()
        gate_execution._write(gate_execution._path(root,ident),fixture.job)
        dispatches=[]; posts=[]; report_bytes=json.dumps({'candidate_id':ident,'snapshot_key':'snap','reviewer':'independent','decision':'needs_changes','findings':['check']})
        def collect(args, exchange, workflow):
            reviewer=args.reviewer
            candidate=workflow.readjson(cp); records=candidate['scoped_reviews']; record=records[reviewer]
            if not (record.get('dispatch_pending') or record.get('collection_pending')):
                return {'status':'reported'}
            dispatches.append(reviewer)
            report=json.loads(report_bytes); report['reviewer']=reviewer
            report_path=root/(reviewer+'.json')
            report_path.write_text(json.dumps(report,sort_keys=True))
            record.update(dispatch_pending=False,collection_pending=False,report_path=str(report_path),
                report_sha256=workflow.sha(report_path),reviewer=reviewer,execution_job_id='r31',receipt_sha256=fixture.receipt_sha)
            gate_dispatch._write_dynamic(root,ident,workflow,'scoped_reviews',records)
            return {'status':'reported'}
        def transport(server,method,path,body=None,timeout=15):
            if method=='GET' and path.startswith('/api/mcp?'):
                return 200,{'location':{'directory':str(root)},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]}
            if method=='GET' and path=='/api/session/active':
                return 200,{'data':({'lead':{'type':'running'}} if len(posts)>=2 else {})}
            if method=='GET' and path=='/api/session/lead':
                return 200,{'data':{'id':'lead','agent':'integration-lead','directory':str(root),
                    'metadata':{'candidate_id':ident,'snapshot_key':'snap','workflow_gate':True}}}
            if method=='GET' and path.endswith('/inbox'): return 200,{'data':[]}
            if method=='POST' and path=='/api/session/lead/prompt':
                posts.append(body); return 200,{'data':{'id':body['id'],'sessionID':'lead',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError((method,path,body))
        with patch.object(gate_execution,'reconcile',return_value={'pending':0,'active':0,'blocked':0,'launched':0,'errors':[]}), \
             patch.object(gate_dispatch,'_dispatch_scoped_review',side_effect=collect):
            first=gate_liveness.reconcile(root,workflow=fixture.wf,request=transport,now=300)
            second=gate_liveness.reconcile(root,workflow=fixture.wf,request=transport,now=301)
            third=gate_liveness.reconcile(root,workflow=fixture.wf,request=transport,now=302)
        self.assertFalse(first['errors'] or second['errors'] or third['errors'],
                         (first,second,third,dispatches,posts,workflow_runtime.readjson(cp).get('scoped_reviews'),gate_liveness.read_health(root,ident)))
        self.assertEqual(dispatches,['reviewer-a','reviewer-b'])
        self.assertEqual(len(posts),2)
        self.assertEqual({p['metadata']['reviewer'] for p in posts},{'reviewer-a','reviewer-b'})
        self.assertTrue(all(p['metadata']['candidate_id']==ident and p['metadata']['snapshot_key']=='snap' for p in posts))
        self.assertTrue(all(p['metadata']['workflow_gate_scoped_review'] for p in posts))
        saved=workflow_runtime.readjson(cp)['scoped_reviews']
        self.assertTrue(all(not r['dispatch_pending'] and not r['collection_pending'] for r in saved.values()))
        # A further cycle is stable: no duplicate callback or lead continuation body.
        posts_before=list(posts); dispatches_before=list(dispatches)
        with patch.object(gate_execution,'reconcile',return_value={'pending':0,'active':0,'blocked':0,'launched':0,'errors':[]}), \
             patch.object(gate_dispatch,'_dispatch_scoped_review',side_effect=collect):
            gate_liveness.reconcile(root,workflow=fixture.wf,request=transport,now=303)
        self.assertEqual(posts,posts_before); self.assertEqual(dispatches,dispatches_before)
        self.assertEqual(fixture.job,job_before)
        self.assertEqual(gate_execution.read(root,ident),job_before)


if __name__=='__main__': unittest.main()
