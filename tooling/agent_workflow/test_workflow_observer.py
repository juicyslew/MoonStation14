import unittest
import sys
from types import SimpleNamespace
from unittest.mock import Mock, patch

from tooling.agent_workflow import workflow_observer as observer
from tooling.agent_workflow import owner_feedback


class Lock:
    def close(self): pass


class Workflow:
    def gate_lock(self, *args, **kwargs): return Lock()
    def writejson(self, path, data): pass


class ImmediateFuture:
    def __init__(self, fn, *args, **kwargs):
        try: self.value, self.error = fn(*args, **kwargs), None
        except Exception as exc: self.value, self.error = None, exc
    def done(self): return True
    def result(self):
        if self.error: raise self.error
        return self.value


class ImmediateExecutor:
    def __init__(self, **kwargs): pass
    def submit(self, fn, *args, **kwargs): return ImmediateFuture(fn, *args, **kwargs)
    def shutdown(self, **kwargs): pass


class HeldFuture:
    def __init__(self): self.ready = False; self.value = None; self.error = None
    def done(self): return self.ready
    def result(self):
        if self.error: raise self.error
        return self.value
    def finish(self, value): self.value=value; self.ready=True
    def fail(self, error): self.error=error; self.ready=True


class HeldExecutor:
    def __init__(self, **kwargs): self.futures=[]
    def submit(self, fn, *args, **kwargs):
        future=HeldFuture(); future.fn=fn; future.args=args; future.kwargs=kwargs; self.futures.append(future); return future
    def shutdown(self, **kwargs): pass


def held_owner_and_immediate_gate(**kwargs):
    return held_executor if kwargs.get('thread_name_prefix') == 'workflow-owner-feedback' else ImmediateExecutor(**kwargs)

held_executor = None


class ObserverTests(unittest.TestCase):
    def test_blocked_gate_worker_does_not_starve_agent_poll_and_health_is_truthful(self):
        clock=[0.0]; executors={}; health=[]; agent_ticks=[]
        class WF(Workflow):
            def writejson(self,path,data):
                if str(path).endswith('workflow-observer.json'): health.append(data)
        def factory(**kwargs):
            prefix=kwargs.get('thread_name_prefix')
            if prefix not in executors: executors[prefix]=HeldExecutor()
            return executors[prefix]
        wf=WF()
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',return_value={'sessions':1,'pending_owner':1,'errors':[]}), \
                patch.object(observer,'_gate_reconcile',side_effect=lambda *a,**k: (_ for _ in ()).throw(AssertionError('held task must not run inline'))), \
                patch.object(observer,'_reconcile_agent_status',side_effect=lambda *a,**k:(agent_ticks.append(clock[0]) or {'core_monitored':1,'errors':[]})), patch.object(observer,'_render'):
            def sleep(_):
                clock[0]+=10
                if clock[0]>=20: raise StopIteration
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=factory)
        self.assertEqual(len(executors['workflow-gate-liveness'].futures),1)
        self.assertGreaterEqual(len(agent_ticks),2)
        self.assertTrue(any(x['components']['gate_liveness']['reconciliation_pending'] for x in health))

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
                observer.watch('.',request=lambda *a,**k:None,clock=lambda:0,sleep=lambda _:None,executor_factory=ImmediateExecutor)
        reconcile.assert_not_called()

    def test_agent_status_reuses_shared_workflow_and_request_with_notifications(self):
        workflow=Workflow(); request=object()
        module=SimpleNamespace(reconcile=Mock(return_value={'core_monitored':4,'errors':[]}))
        with patch.object(observer,'_component',return_value=module) as resolve:
            result=observer._reconcile_agent_status('exchange',workflow,request,123)
        resolve.assert_called_once_with('agent_status')
        self.assertEqual(result['core_monitored'],4)
        module.reconcile.assert_called_once_with('exchange',workflow=workflow,request=request,now=123,notify=True)

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
             patch.object(observer, '_reconcile_agent_status', return_value={'core_monitored':0,'errors':[]}) as reconcile_agents, \
             patch.object(observer, '_render'):
             self.assertEqual(observer.watch('.', request=lambda *a, **k: None, clock=lambda: clock[0], sleep=advance, executor_factory=ImmediateExecutor), 0)
        self.assertEqual(reconcile_owner.call_count, 2)
        self.assertEqual(reconcile_gate.call_count, 2)
        self.assertGreaterEqual(reconcile_agents.call_count, 2)  # immediate startup poll plus periodic polls

    def test_component_failures_are_isolated_and_renderer_failure_is_reported(self):
        wf = Workflow()
        with patch.object(observer, '_gate_reconcile', return_value={'pending': 0, 'errors': []}), \
             patch.object(observer, '_reconcile_agent_status', return_value={'core_monitored':0,'errors':[]}), \
             patch.object(observer, '_render', side_effect=RuntimeError('renderer')):
            with patch.object(owner_feedback, 'reconcile', side_effect=RuntimeError('owner')):
                owner, gate, agent, errors = observer.reconcile_components('.', wf, None)
        self.assertTrue(owner['errors']); self.assertFalse(gate['errors'])
        self.assertTrue(any('renderer' in e for e in errors))

    def test_implicit_agent_timestamp_is_taken_after_slow_owner_and_gate_work(self):
        clock_values=iter((100.0, 131.0)); observed=[]
        with patch.object(owner_feedback,'reconcile',return_value={'sessions':0,'errors':[]}), \
             patch.object(observer,'_gate_reconcile',return_value={'pending':0,'errors':[]}), \
             patch.object(observer,'_reconcile_agent_status',side_effect=lambda *a: (observed.append(a[-1]) or {'errors':[]})), \
             patch.object(observer,'_render'), patch.object(observer.time,'time',side_effect=lambda: next(clock_values)):
            observer.reconcile_components('.',Workflow(),None)
        self.assertEqual(observed,[131.0])

    def test_held_known_empty_owner_pass_keeps_health_active_until_completion(self):
        global held_executor
        clock=[0.0]; health=[]; executor=HeldExecutor(); held_executor=executor
        class WF(Workflow):
            def writejson(self,path,data):
                if str(path).endswith('workflow-observer.json'): health.append(data)
        wf=WF(); empty={'sessions':0,'pending_owner':0,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), \
             patch.object(owner_feedback,'reconcile',return_value=empty), \
             patch.object(observer,'_gate_reconcile',return_value={'pending':0,'active':0,'errors':[]}), \
             patch.object(observer,'_reconcile_agent_status',return_value={'core_monitored':0,'errors':[]}), patch.object(observer,'_render'):
            def sleep(_):
                clock[0]+=10
                if clock[0]==10: executor.futures[0].finish(empty)
                if clock[0]==100:
                    self.assertEqual(len(executor.futures),2)
                    self.assertFalse(any(item['status']=='stopped' for item in health))
                    executor.futures[1].finish(empty)
            result=observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=held_owner_and_immediate_gate)
        self.assertEqual(result,0)
        self.assertTrue(any(item['components']['owner_feedback']['owner_pending'] for item in health))

    def test_known_idle_observer_stops_after_grace_without_spurious_refresh(self):
        global held_executor
        clock=[0.0]; executor=HeldExecutor(); held_executor=executor; health=[]
        class WF(Workflow):
            def writejson(self,path,data):
                if str(path).endswith('workflow-observer.json'): health.append(data)
        wf=WF(); empty={'sessions':0,'pending_owner':0,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',return_value=empty), \
             patch.object(observer,'_gate_reconcile',return_value={'pending':0,'active':0,'errors':[]}), \
             patch.object(observer,'_reconcile_agent_status',return_value={'core_monitored':0,'errors':[]}), patch.object(observer,'_render'):
            def sleep(_):
                clock[0]+=10
                if clock[0]==10: executor.futures[0].finish(empty)
                if clock[0]==40:
                    self.assertEqual(len(executor.futures),2)
                    self.assertFalse(executor.futures[1].kwargs['refresh'])
                if clock[0]==70: executor.futures[1].finish(empty)
            result=observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=held_owner_and_immediate_gate)
        self.assertEqual(result,0)
        self.assertGreaterEqual(len(executor.futures),2)

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
             patch.object(observer,'_gate_reconcile',side_effect=gate_pass), \
             patch.object(observer,'_reconcile_agent_status',return_value={'core_monitored':0,'errors':[]}), patch.object(observer,'_render'):
            # pending gate remains active, so stop deliberately after the t=30 assertions
            def sleep(_):
                advance(_)
                if clock[0] >= 40: raise StopIteration
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=ImmediateExecutor)
        refreshes=[value for item in calls if isinstance(item,tuple) for kind,value in [item] if kind=='refresh']
        self.assertIn(True,refreshes)
        self.assertEqual(refreshes.count(True),2)  # startup and elapsed 30-second refresh
        self.assertTrue(any(isinstance(item,dict) and item.get('status')=='error' for item in calls))

    def test_package_observer_ignores_unrelated_flat_owner_feedback_module(self):
        fake=SimpleNamespace(reconcile=lambda *a,**k: self.fail('wrong flat module selected'))
        with patch.dict(sys.modules, {'owner_feedback':fake}), \
             patch.object(owner_feedback,'reconcile',return_value={'sessions':0,'pending_owner':0,'errors':[]}) as correct, \
             patch.object(observer,'_gate_reconcile',return_value={'pending':0,'active':0,'errors':[]}), \
             patch.object(observer,'_reconcile_agent_status',return_value={'core_monitored':0,'errors':[]}), \
             patch.object(observer,'_render'):
            owner,_,_,_=observer.reconcile_components('.',Workflow(),None)
        correct.assert_called_once(); self.assertEqual(owner['pending_owner'],0)

    def test_startup_owner_error_fails_closed_with_empty_gate(self):
        clock=[0.0]; gate_calls=[]; cycles=[0]
        wf=Workflow()
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',side_effect=RuntimeError('owner API')), \
             patch.object(observer,'_gate_reconcile',side_effect=lambda *a,**k:(gate_calls.append(1) or {'pending':0,'active':0,'errors':[]})), \
             patch.object(observer,'_reconcile_agent_status',return_value={'core_monitored':0,'errors':[]}), patch.object(observer,'_render'):
            def sleep(_):
                cycles[0] += 1
                if cycles[0] > 20: raise AssertionError('startup failure fixture exceeded 20 fake-clock cycles')
                clock[0]+=61
                if len(gate_calls)>1: raise StopIteration
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=ImmediateExecutor)
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
             patch.object(observer,'_gate_reconcile',side_effect=lambda *a,**k:(gate_calls.append(1) or {'pending':0,'active':0,'errors':[]})), \
             patch.object(observer,'_reconcile_agent_status',return_value={'core_monitored':0,'errors':[]}), patch.object(observer,'_render'):
            def sleep(_):
                cycles[0] += 1
                if cycles[0] > 20: raise AssertionError('periodic failure fixture exceeded 20 fake-clock cycles')
                clock[0]+=10
            result=observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=ImmediateExecutor)
        self.assertEqual(result,0)
        self.assertGreaterEqual(len(owner_calls),3)
        self.assertGreaterEqual(len(gate_calls),2)

    def test_core_agents_keep_shared_watcher_alive_and_agent_failure_recovers(self):
        clock=[0.0]; cycles=[0]; agent_calls=[]; health=[]
        class WF(Workflow):
            def writejson(self,path,data):
                if str(path).endswith('workflow-observer.json'): health.append(data)
        wf=WF()
        owner={'sessions':0,'pending_owner':0,'errors':[]}
        gate={'pending':0,'active':0,'errors':[]}
        def agent_status(*args,**kwargs):
            agent_calls.append(clock[0])
            if len(agent_calls)==1: return {'monitored':4,'core_monitored':4,'idle':4,'errors':['temporary scan error']}
            return {'monitored':4,'core_monitored':4,'idle':4,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',return_value=owner) as owner_poll, \
             patch.object(observer,'_gate_reconcile',return_value=gate) as gate_poll, \
             patch.object(observer,'_reconcile_agent_status',side_effect=agent_status), patch.object(observer,'_render'):
            def sleep(_):
                cycles[0]+=1
                if cycles[0]>3: raise StopIteration
                clock[0]+=61
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=ImmediateExecutor)
        self.assertGreaterEqual(len(agent_calls),3)
        self.assertGreaterEqual(owner_poll.call_count,2); self.assertGreaterEqual(gate_poll.call_count,2)
        self.assertTrue(any('temporary scan error' in str(h['components']['agent_status'].get('errors')) for h in health))
        self.assertTrue(all('owner_pending' in h['components']['owner_feedback'] and 'gate_liveness' in h['components'] for h in health))
        self.assertFalse(any(h['status']=='stopped' for h in health))

    def test_periodic_agent_error_reaches_shared_health_then_clears_on_recovery(self):
        clock=[0.0]; cycles=[0]; calls=[]; health=[]
        class WF(Workflow):
            def writejson(self,path,data):
                if str(path).endswith('workflow-observer.json'): health.append(data)
        wf=WF(); owner={'sessions':0,'pending_owner':0,'errors':[]}; gate={'pending':0,'active':0,'errors':[]}
        def agent(*args,**kwargs):
            calls.append(clock[0])
            if len(calls)==2: return {'monitored':4,'core_monitored':4,'errors':['agent API unavailable']}
            return {'monitored':4,'core_monitored':4,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), patch.object(owner_feedback,'reconcile',return_value=owner), \
             patch.object(observer,'_gate_reconcile',return_value=gate), patch.object(observer,'_reconcile_agent_status',side_effect=agent), \
             patch.object(observer,'_render'):
            def sleep(_):
                cycles[0]+=1
                if cycles[0]>3: raise StopIteration
                clock[0]+=10
            with self.assertRaises(StopIteration): observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=ImmediateExecutor)
        failed=next(h for h in health if 'agent API unavailable' in h['errors'])
        self.assertEqual(failed['status'],'error')
        self.assertEqual(failed['components']['agent_status']['core_monitored'],4)
        self.assertTrue(any(h['status']=='active' and not h['errors'] for h in health[health.index(failed)+1:]))
        self.assertFalse(any(h['status']=='stopped' for h in health))

    def test_held_owner_pass_does_not_block_observer_and_recovers_without_overlap(self):
        global held_executor
        clock=[0.0]; health=[]; rendered=[]; agents=[]; executor=HeldExecutor(); held_executor=executor
        class WF(Workflow):
            def writejson(self,path,data):
                if str(path).endswith('workflow-observer.json'): health.append(data)
        wf=WF(); gate={'pending':0,'active':0,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), \
             patch.object(owner_feedback,'reconcile',return_value={'sessions':0,'pending_owner':0,'errors':[]}), \
             patch.object(observer,'_gate_reconcile',return_value=gate), \
             patch.object(observer,'_reconcile_agent_status',side_effect=lambda *a,**k:(agents.append(a[-1]) or {'core_monitored':0,'errors':[]})), \
             patch.object(observer,'_render',side_effect=lambda root:rendered.append(clock[0])):
            def sleep(_):
                clock[0]+=10
                if clock[0] == 40: executor.futures[0].fail(RuntimeError('first owner failure'))
                if clock[0] == 50:
                    self.assertEqual(len(executor.futures),1)  # completion + 10s cadence
                if clock[0] == 60: executor.futures[1].finish({'sessions':0,'pending_owner':0,'errors':[]})
                if clock[0] >= 70: raise StopIteration
            with self.assertRaises(StopIteration):
                observer.watch('.',request=lambda *a,**k:None,clock=lambda:clock[0],sleep=sleep,executor_factory=held_owner_and_immediate_gate)
        self.assertEqual(len(executor.futures),2)
        self.assertGreaterEqual(len(agents),6); self.assertGreaterEqual(len(rendered),6); self.assertGreaterEqual(len(health),6)
        self.assertTrue(any(h['components']['owner_feedback'].get('owner_pending') and h['components']['owner_feedback'].get('unknown_work') for h in health))
        self.assertTrue(any('first owner failure' in str(h['errors']) for h in health))
        self.assertTrue(any(h['components']['owner_feedback'].get('owner_pending') is False and not h['components']['owner_feedback'].get('errors') for h in health))

    def test_once_runs_owner_gate_and_render(self):
        wf=Workflow()
        owner={'pending_owner':1,'errors':[]}; gate={'pending':0,'errors':[]}
        agent={'core_monitored':0,'errors':[]}
        with patch.object(observer,'_workflow',return_value=wf), patch.object(observer,'reconcile_components',return_value=(owner,gate,agent,[])) as run, \
             patch.object(observer,'_gate_reconcile') as gate_only, patch('sys.stdout'):
            self.assertEqual(observer.main(['--exchange','.','--once']),0)
        run.assert_called_once(); gate_only.assert_not_called()


if __name__ == '__main__': unittest.main()
