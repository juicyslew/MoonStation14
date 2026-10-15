import unittest
import sys
from types import SimpleNamespace
from unittest.mock import patch

from tooling.agent_workflow import workflow_observer as observer
from tooling.agent_workflow import owner_feedback


class Lock:
    def close(self): pass


class Workflow:
    def gate_lock(self, *args, **kwargs): return Lock()
    def writejson(self, path, data): pass


class ObserverTests(unittest.TestCase):
    def test_ensure_started_hides_all_child_streams(self):
        with patch.object(observer.subprocess, 'Popen', return_value='child') as launch:
            self.assertEqual(observer.ensure_started('.', Workflow()), 'child')
            kwargs = launch.call_args.kwargs
            self.assertIs(kwargs['stdin'], observer.subprocess.DEVNULL)
            self.assertIs(kwargs['stdout'], observer.subprocess.DEVNULL)
            self.assertIs(kwargs['stderr'], observer.subprocess.DEVNULL)

    def test_duplicate_singleton_lock_skips_all_component_work(self):
        wf=SimpleNamespace(gate_lock=lambda *a,**k: (_ for _ in ()).throw(BlockingIOError()))
        with patch.object(observer,'_workflow',return_value=wf), patch.object(observer,'reconcile_components') as reconcile:
            with self.assertRaises(BlockingIOError):
                observer.watch('.',request=lambda *a,**k:None,clock=lambda:0,sleep=lambda _:None)
        reconcile.assert_not_called()

    def test_shared_tick_runs_both_components_and_exits_after_idle(self):
        clock = [0.0]; cycles=[0]
        def advance(_):
            cycles[0] += 1
            if cycles[0] > 20: raise AssertionError('shared observer fixture exceeded 20 fake-clock cycles')
            clock[0] += 61
        wf = Workflow()
        owner = {'sessions': 0, 'errors': [], 'queue': {}}
        gate = {'pending': 0, 'active': 0, 'errors': []}
        with patch.object(observer, '_workflow', return_value=wf), \
             patch.object(owner_feedback, 'reconcile', return_value=owner) as reconcile_owner, \
             patch.object(observer, '_gate_reconcile', return_value=gate) as reconcile_gate, \
             patch.object(observer, '_render'):
            self.assertEqual(observer.watch('.', request=lambda *a, **k: None, clock=lambda: clock[0], sleep=advance), 0)
        self.assertEqual(reconcile_owner.call_count, 2)
        self.assertEqual(reconcile_gate.call_count, 2)

    def test_component_failures_are_isolated_and_renderer_failure_is_reported(self):
        wf = Workflow()
        with patch.object(observer, '_gate_reconcile', return_value={'pending': 0, 'errors': []}), \
             patch.object(observer, '_render', side_effect=RuntimeError('renderer')):
            with patch.object(owner_feedback, 'reconcile', side_effect=RuntimeError('owner')):
                owner, gate, errors = observer.reconcile_components('.', wf, None)
        self.assertTrue(owner['errors']); self.assertFalse(gate['errors'])
        self.assertTrue(any('renderer' in e for e in errors))

    def test_expired_readiness_uses_elapsed_time_and_pending_gate_error_stays_active(self):
        clock = [0.0]; calls=[]
        class WF(Workflow):
            def writejson(self, path, data):
                if str(path).endswith('workflow-observer.json'): calls.append(data)
        wf=WF(); owner={'sessions':0,'pending_owner':0,'errors':[]}; gate={'pending':1,'active':0,'errors':['api']}
        def owner_pass(*args, **kwargs):
            calls.append(('refresh',kwargs.get('refresh'))); return owner
        def gate_pass(*args, **kwargs): return gate
        def advance(_): clock[0] += 10
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',side_effect=owner_pass), \
             patch.object(observer,'_gate_reconcile',side_effect=gate_pass), patch.object(observer,'_render'):
            # pending gate remains active, so stop deliberately after the t=30 assertions
            def sleep(_):
                advance(_)
                if clock[0] >= 40: raise StopIteration
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep)
        refreshes=[value for item in calls if isinstance(item,tuple) for kind,value in [item] if kind=='refresh']
        self.assertIn(True,refreshes)
        self.assertEqual(refreshes.count(True),2)  # startup and elapsed 30-second refresh
        self.assertTrue(any(isinstance(item,dict) and item.get('status')=='error' for item in calls))

    def test_package_observer_ignores_unrelated_flat_owner_feedback_module(self):
        fake=SimpleNamespace(reconcile=lambda *a,**k: self.fail('wrong flat module selected'))
        with patch.dict(sys.modules, {'owner_feedback':fake}), \
             patch.object(owner_feedback,'reconcile',return_value={'sessions':0,'pending_owner':0,'errors':[]}) as correct, \
             patch.object(observer,'_gate_reconcile',return_value={'pending':0,'active':0,'errors':[]}), \
             patch.object(observer,'_render'):
            owner,_,_=observer.reconcile_components('.',Workflow(),None)
        correct.assert_called_once(); self.assertEqual(owner['pending_owner'],0)

    def test_startup_owner_error_fails_closed_with_empty_gate(self):
        clock=[0.0]; gate_calls=[]; cycles=[0]
        wf=Workflow()
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',side_effect=RuntimeError('owner API')), \
             patch.object(observer,'_gate_reconcile',side_effect=lambda *a,**k:(gate_calls.append(1) or {'pending':0,'active':0,'errors':[]})), patch.object(observer,'_render'):
            def sleep(_):
                cycles[0] += 1
                if cycles[0] > 20: raise AssertionError('startup failure fixture exceeded 20 fake-clock cycles')
                clock[0]+=61
                if len(gate_calls)>1: raise StopIteration
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep)
        self.assertGreaterEqual(len(gate_calls),2)

    def test_periodic_owner_error_preserves_pending_then_successful_none_idles(self):
        clock=[0.0]; owner_calls=[]; gate_calls=[]; cycles=[0]
        wf=Workflow()
        def owner_pass(*args,**kwargs):
            owner_calls.append(clock[0])
            if len(owner_calls)==1: return {'sessions':0,'pending_owner':1,'errors':[]}
            if len(owner_calls)==2: raise RuntimeError('temporary owner failure')
            return {'sessions':0,'pending_owner':0,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',side_effect=owner_pass), \
             patch.object(observer,'_gate_reconcile',side_effect=lambda *a,**k:(gate_calls.append(1) or {'pending':0,'active':0,'errors':[]})), patch.object(observer,'_render'):
            def sleep(_):
                cycles[0] += 1
                if cycles[0] > 20: raise AssertionError('periodic failure fixture exceeded 20 fake-clock cycles')
                clock[0]+=10
            result=observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep)
        self.assertEqual(result,0)
        self.assertGreaterEqual(len(owner_calls),3)
        self.assertGreaterEqual(len(gate_calls),2)

    def test_once_runs_owner_gate_and_render(self):
        wf=Workflow()
        owner={'pending_owner':1,'errors':[]}; gate={'pending':0,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), patch.object(observer,'reconcile_components',return_value=(owner,gate,[])) as run, \
             patch.object(observer,'_gate_reconcile') as gate_only, patch('sys.stdout'):
            self.assertEqual(observer.main(['--exchange','.','--once']),0)
        run.assert_called_once(); gate_only.assert_not_called()


if __name__ == '__main__': unittest.main()
