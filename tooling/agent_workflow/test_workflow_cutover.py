"""Private-fixture tests for the offline drain barrier only."""
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from unittest import mock

import workflow_cutover
import admission


class WF:
    def __init__(self): self.locks=[]
    def gate_lock(self, path, nonblocking=False):
        lock=mock.Mock(); lock.path=Path(path); self.locks.append(lock); return lock
    def require_clear_gate(self, root): return None
    def readjson(self, path): return json.loads(Path(path).read_text())


class CutoverBarrierTests(unittest.TestCase):
    def setUp(self):
        self.real_close_verified_hold=admission.close_verified_hold
        patcher=mock.patch.object(admission,'close_verified_hold')
        patcher.start()
        self.addCleanup(patcher.stop)
    def test_clear_snapshot_requires_recheck_and_releases_existing_guards(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); barrier=workflow_cutover.DrainBarrier(d, {}, workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}):
                with barrier:
                    self.assertEqual(barrier.initial['status'],'OBSERVED_ZERO')
                    self.assertEqual(barrier.recheck()['status'],'OBSERVED_ZERO')
                self.assertEqual(len(wf.locks),3)
                self.assertTrue(all(x.close.called for x in wf.locks))

    def test_requested_job_and_owner_attention_block(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); directory=root/'gate-execution'; directory.mkdir()
            (directory/'candidate.json').write_text(json.dumps({'job_id':'candidate','state':'requested'}))
            wf=WF(); barrier=workflow_cutover.DrainBarrier(root,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={'awaiting_owner':[{}],'agent_work':[],'unconfirmed':[{}],'completed':[]}):
                with barrier:
                    result=barrier.inspect()
                    self.assertEqual(result['status'],'HELD')
                    codes={x['code'] for x in result['blockers']}
                    self.assertIn('JOB_NONTERMINAL_OR_UNKNOWN',codes)
                    self.assertIn('AWAITING_OWNER',codes)

    def test_partial_lock_acquisition_releases_acquired_guards(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); wf.gate_lock=mock.Mock(side_effect=[wf.locks.append(mock.Mock()) or wf.locks[-1], BlockingIOError('busy')])
            barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)):
                with self.assertRaises(BlockingIOError): barrier.__enter__()
            self.assertTrue(wf.locks[0].close.called)

    def test_metadata_or_worker_lock_contention_refuses_barrier_and_cleans_prior_guards(self):
        for suffix in ('candidate','candidate-worker'):
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as d:
                root=Path(d); (root/'candidates').mkdir();
                (root/'candidates'/'candidate.json').write_text(json.dumps({'id':'candidate','worktree':{'worktree':d},'sprint_id':'s'}))
                wf=WF(); original=wf.gate_lock
                def lock(path,nonblocking=False):
                    if str(path).endswith(suffix): raise BlockingIOError('held')
                    return original(path,nonblocking)
                wf.gate_lock=lock
                barrier=workflow_cutover.DrainBarrier(root,{},workflow=wf)
                with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                     mock.patch.object(workflow_cutover.owner_attention,'current_work',return_value=[]):
                    with self.assertRaises(BlockingIOError): barrier.__enter__()
                self.assertTrue(all(item.close.called for item in wf.locks))

    def test_busy_sprint_guard_refuses_entry_and_releases_guards(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'candidates').mkdir()
            (root/'candidates'/'candidate.json').write_text(json.dumps({'id':'candidate','worktree':{'worktree':d},'sprint_id':'sprint'}))
            wf=WF(); wf.try_sprint_lock=lambda *a:None
            barrier=workflow_cutover.DrainBarrier(root,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'current_work',return_value=[]):
                with self.assertRaisesRegex(BlockingIOError,'sprint mutation guard busy'): barrier.__enter__()
            self.assertTrue(all(lock.close.called for lock in wf.locks))

    def test_recheck_detects_new_job_or_owner_request_and_bad_inbox(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); wf=WF(); empty={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}
            barrier=workflow_cutover.DrainBarrier(root,{},workflow=wf,request=lambda *a: {'status':500})
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value=empty):
                with barrier:
                    barrier.initial=barrier.inspect(); barrier._records_initial=dict(barrier._records); barrier._heads_initial=dict(barrier._heads)
                    directory=root/'gate-execution'; directory.mkdir()
                    (directory/'new.json').write_text(json.dumps({'job_id':'new','state':'requested'}))
                    result=barrier.recheck()
                    self.assertEqual(result['status'],'HELD')
                    self.assertIn('JOB_NONTERMINAL_OR_UNKNOWN',{x['code'] for x in result['current']['blockers']})

    def test_wrong_thread_release_retains_mutex_and_owner_can_release(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}):
                barrier.__enter__(); result=[]
                thread=threading.Thread(target=lambda: self._capture(result,barrier._release))
                thread.start(); thread.join(2)
                self.assertIsInstance(result[0],RuntimeError)
                self.assertTrue(barrier._locks)
                barrier._release()

    def test_failed_mutex_close_remains_owned_for_same_thread_retry(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}):
                barrier.__enter__(); failed=barrier._locks[-1]
                failed.close.side_effect=[RuntimeError('release uncertainty'),None]
                with self.assertRaisesRegex(RuntimeError,'release uncertainty'): barrier._release()
                self.assertIn(failed,barrier._locks)
                self.assertEqual(barrier.recheck()['status'],'HELD')
                barrier._release()

    def test_recheck_after_exit_or_wrong_thread_is_held_without_inspection(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf)
            request=mock.Mock(return_value=(200,[])); barrier.request=request
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}):
                with barrier:
                    before=request.call_count
                    result=[]
                    t=threading.Thread(target=lambda:result.append(barrier.recheck()))
                    t.start(); t.join(2)
                    self.assertEqual(result[0]['status'],'HELD')
                    self.assertEqual(request.call_count,before)
                before=request.call_count
                self.assertEqual(barrier.recheck()['status'],'HELD')
                self.assertEqual(request.call_count,before)

    def test_malformed_canonical_inbox_response_is_held(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); groups=[{'head':{'id':'head','gate_request':{'session_id':'lead'}}}]
            barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf,request=lambda *a:(200,{'unexpected':[]}))
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'current_work',return_value=groups), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}):
                with barrier:
                    result=barrier.inspect()
                    self.assertEqual(result['status'],'HELD')
                    self.assertIn('LEAD_INBOX_UNKNOWN',{x['code'] for x in result['blockers']})

    @staticmethod
    def _capture(results, callback):
        try: callback()
        except Exception as exc: results.append(exc)

    def test_known_identity_alive_or_unknown_blocks_even_completed_job(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); jobs=root/'gate-execution'; jobs.mkdir()
            (jobs/'done.json').write_text(json.dumps({'job_id':'done','state':'completed','worker_identity':{'pid':12,'created':3}}))
            wf=WF(); barrier=workflow_cutover.DrainBarrier(root,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}), \
                 mock.patch.object(workflow_cutover.gate_execution,'identity_state',return_value='unknown'):
                with barrier:
                    result=barrier.inspect()
                    self.assertIn('JOB_PROCESS_PRESENT_OR_UNKNOWN',{x['code'] for x in result['blockers']})

    def test_separate_observer_reconcile_cannot_launch_while_barrier_owns_launch_mutex(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); wf=WF(); barrier=workflow_cutover.DrainBarrier(root,{},workflow=wf)
            # Exercise the real Windows/OS mutex implementation for the observer process.
            import workflow_runtime
            barrier.workflow=workflow_runtime
            script=("import json,sys; import workflow_runtime,gate_execution; "
                    "print(json.dumps(gate_execution.reconcile(sys.argv[1],workflow=workflow_runtime)))")
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}):
                with barrier:
                    proc=subprocess.run([sys.executable,'-c',script,str(root)],capture_output=True,text=True,timeout=20,
                                        env={**os.environ,'PYTHONPATH':str(Path(__file__).resolve().parent)})
                    self.assertEqual(proc.returncode,0,proc.stderr)
                    self.assertEqual(json.loads(proc.stdout)['launched'],0)

    def test_lead_inbox_only_structured_workflow_handoff_blocks(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); head={'id':'head-1','gate_request':{'session_id':'lead-1'}}
            groups=[{'head':head}]
            request=mock.Mock(return_value=(200,{'data':[{'id':'ordinary','delivery':'message','payload':{'metadata':{}}},
                                                         {'id':'workflow','delivery':'message','payload':{'metadata':{'workflow_gate_request':True}}}]}))
            barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf,request=request)
            attention={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'current_work',return_value=groups), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',return_value=attention):
                with barrier:
                    result=barrier.inspect()
                    self.assertIn('LEAD_INBOX_WORKFLOW_PENDING',{x['code'] for x in result['blockers']})
                    request.assert_called_with(None,'GET','/api/session/lead-1/inbox')
                    self.assertEqual(result['lead_inboxes'][0]['queued_workflow'],[{'workflow_metadata':['workflow_gate_request'],'delivery':'message'}])

    def test_later_owner_request_is_detected_on_fresh_recheck(self):
        with tempfile.TemporaryDirectory() as d:
            wf=WF(); empty={k:[] for k in ('awaiting_owner','agent_work','unconfirmed','completed')}
            attention=mock.Mock(side_effect=[empty,{**empty,'awaiting_owner':[{'id':'new'}]}])
            barrier=workflow_cutover.DrainBarrier(d,{},workflow=wf)
            with mock.patch.object(workflow_cutover.admission,'verified_hold_handle',return_value=(123,None)), \
                 mock.patch.object(workflow_cutover.owner_attention,'collect',attention):
                with barrier:
                    self.assertEqual(barrier.initial['status'],'OBSERVED_ZERO')
                    result=barrier.recheck()
                    self.assertEqual(result['status'],'HELD')
                    self.assertIn('AWAITING_OWNER',{x['code'] for x in result['current']['blockers']})

    @unittest.skipUnless(os.name=='nt','Windows owned-file handle semantics required')
    def test_owned_hold_handle_is_64bit_safe_and_does_not_remove_hold(self):
        with tempfile.TemporaryDirectory() as d:
            receipt=admission.begin_hold(d,owner='fixture',control_id='fixture-control',note='unit test')
            handle,kernel=admission.verified_hold_handle(d,receipt)
            self.real_close_verified_hold(handle)
            self.assertTrue((Path(d)/'gate-maintenance.json').is_file())


if __name__ == '__main__': unittest.main()
