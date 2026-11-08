import unittest
import threading
import time
import tempfile
from pathlib import Path
from unittest.mock import patch
from tooling.agent_workflow import agent_status

class CodingActivityTests(unittest.TestCase):
    def test_empty_configuration_makes_no_api_call(self):
        def fail(*a,**k): raise AssertionError('unexpected API call')
        result=agent_status.observe_coding_agents([],request=fail)
        self.assertTrue(result['complete']); self.assertEqual(result['agents'],[])

    def test_only_designated_slots_and_primary_agent_are_observed(self):
        with tempfile.TemporaryDirectory() as temp:
            roots=[]
            for i in range(1,5):
                p=Path(temp)/f'MoonStation14-agent-{i:02d}'; p.mkdir(); roots.append(str(p))
            headers={'s':{'id':'s','agent':'orchestrate-coding','title':'Implement feature','location':{'directory':roots[0]}}}
            result=agent_status.observe_coding_agents(roots+['C:/repo/MoonStation14','C:/repo/MoonStation14-agent-05'],request=self.fixture({'s':{'type':'running'}},headers,roles={'*':{'orchestrate-coding':'primary'}}))
            self.assertTrue(result['complete'])
            self.assertEqual([a['activity'] for a in result['agents']],['working','idle','idle','idle'])
            self.assertEqual(result['agents'][0]['session_id'],'s')

    def _observe(self, roots):
        def request(_,method,path,timeout):
            if path=='/api/session/active': return 200,{'data':{'s':{'type':'running'}}}
            if path=='/api/session/s': return 200,{'data':{'id':'s','agent':'orchestrate-coding','title':'Implement feature','location':{'directory':roots[0]}}}
            if path.startswith('/api/agent?'): return 200,{'location':{'directory':roots[0]},'data':[{'name':'orchestrate-coding','mode':'primary'}]}
            if path.startswith('/api/permission/request?') or path.startswith('/api/form?'): return 200,{'location':{'directory':roots[0]},'data':[]}
            raise AssertionError(path)
        result=agent_status.observe_coding_agents(roots+['C:/repo/MoonStation14','C:/repo/MoonStation14-agent-05'],request=request)
        self.assertTrue(result['complete'])
        self.assertEqual([a['activity'] for a in result['agents']],['working','idle','idle','idle'])
        self.assertEqual(result['agents'][0]['session_id'],'s')

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        base=Path(self.tmp.name)
        self.roots=[]
        for i in range(1,5):
            p=base/f'MoonStation14-agent-{i:02d}'; p.mkdir(); self.roots.append(str(p))
        self.main=str((base/'MoonStation14').resolve())
        self.fifth=str((base/'MoonStation14-agent-05').resolve())

    def tearDown(self): self.tmp.cleanup()

    def fixture(self, active, headers, roles=None, permissions=None, forms=None):
        roles=roles or {'orchestrate-code':'primary'}
        def request(_,method,path,timeout):
            if path=='/api/session/active': return 200,{'data':active}
            if path.startswith('/api/session/'):
                sid=path.rsplit('/',1)[-1]
                return 200,{'data':headers[sid]}
            if path.startswith('/api/agent?'):
                import urllib.parse
                loc=urllib.parse.parse_qs(path.split('?',1)[1]).get('location[directory]', [''])[0]
                return 200,{'location':{'directory':loc},'data':[{'name':n,'mode':m} for n,m in roles.get(loc,roles.get(loc.casefold(),roles.get('*',{}))).items()]}
            if path.startswith('/api/permission/request?') or path.startswith('/api/form?'):
                import urllib.parse
                loc=urllib.parse.parse_qs(path.split('?',1)[1]).get('location[directory]', [''])[0]
                values=permissions if path.startswith('/api/permission') else forms
                return 200,{'location':{'directory':loc},'data':values if values is not None else []}
            raise AssertionError(path)
        return request

    def test_active_child_counts_for_inactive_qualified_parent(self):
        root=self.roots[3]
        headers={'parent':{'id':'parent','agent':'orchestrate-code','location':{'directory':root}},
                 'child':{'id':'child','parentID':'parent','agent':'subagent','location':{'directory':root}}}
        result=agent_status.observe_coding_agents(self.roots,request=self.fixture({'child':{'type':'running'}},headers,roles={'*':{'orchestrate-code':'primary'}}))
        self.assertEqual(result['agents'][3]['activity'],'working')
        self.assertEqual(result['agents'][3]['session_id'],'parent')
        self.assertEqual(result['agents'][3]['summary'],'Active coding work')

    def test_permission_and_form_pending_are_waiting(self):
        root=self.roots[0]
        headers={'root':{'id':'root','agent':'orchestrate-code','location':{'directory':root}},
                 'child':{'id':'child','parentID':'root','agent':'helper','location':{'directory':root}}}
        cases=[({'sessionID':'root'},'permission'),({'sessionID':'child'},'form')]
        for item,kind in cases:
            with self.subTest(kind=kind):
                result=agent_status.observe_coding_agents(self.roots,request=self.fixture({},headers,
                    roles={'*':{'orchestrate-code':'primary'}},permissions=[item] if kind=='permission' else [],forms=[item] if kind=='form' else []))
                self.assertEqual(result['agents'][0]['activity'],'waiting')

    def test_nonprimary_and_foreign_slots_never_claim_work(self):
        root=self.roots[0]; foreign=self.main
        headers={sid:{'id':sid,'agent':agent,'location':{'directory':loc}} for sid,agent,loc in
                 [('review','reviewer',root),('help','orchestrate-helper',root),('main','orchestrate-code',foreign),('five','orchestrate-code',self.fifth)]}
        active={sid:{'type':'running'} for sid in headers}
        result=agent_status.observe_coding_agents(self.roots+[self.main,self.fifth],request=self.fixture(active,headers))
        self.assertEqual(len(result['agents']),4)
        self.assertEqual(result['agents'][0]['activity'],'idle')

    def test_malformed_global_role_and_wait_responses_fail_closed(self):
        root=self.roots[0]
        valid={'s':{'id':'s','agent':'orchestrate-code','location':{'directory':root}}}
        def broken(path):
            def request(_,method,actual,timeout):
                if actual=='/api/session/active': return 200,{'data':{'s':{'type':'running'}}}
                if actual=='/api/session/s': return 200,{'data':valid['s']}
                if actual.startswith('/api/agent?'): return (200,{'data':[]}) if path=='role' else (200,{'bad':1})
                if actual.startswith('/api/permission/request?'): return (200,{'data':[]} ) if path!='wait' else (200,{'data':[{}]})
                if actual.startswith('/api/form?'): return 200,{'data':[]}
                raise AssertionError(actual)
            result=agent_status.observe_coding_agents(self.roots,request=request)
            self.assertFalse(result['complete']); self.assertEqual(result['agents'][0]['activity'],'unknown')
        broken('wait'); broken('role-malformed')
        for active,headers in [({'bad':{'type':'wat'}},{}),({'s':{'type':'running'}},{})]:
            request=self.fixture(active,headers)
            result=agent_status.observe_coding_agents(self.roots,request=request)
            self.assertFalse(result['complete']); self.assertEqual(result['agents'][0]['activity'],'unknown')
            self.assertEqual(result['agents'][0]['summary'],'Activity unavailable')

    def test_expired_budget_stops_before_another_request(self):
        calls=[]
        def request(*args,**kwargs):
            calls.append(args[2]); return 200,{'data':{}}
        ticks=iter([0,0,10])
        with patch.object(agent_status.time,'monotonic',side_effect=lambda: next(ticks,10)):
            result=agent_status.observe_coding_agents(self.roots,request=request,budget_seconds=1)
        self.assertFalse(result['complete']); self.assertEqual(result['agents'][0]['activity'],'unknown')
        self.assertEqual(calls,['/api/session/active'])

    def test_bounded_parallel_agent_reads_fit_shared_short_deadline(self):
        barrier=threading.Barrier(4,timeout=1)
        active=0; peak=0; lock=threading.Lock()
        def request(_,method,path,timeout):
            nonlocal active,peak
            if path=='/api/session/active': return 200,{'data':{}}
            if path.startswith('/api/agent?') or path.startswith('/api/permission/request?') or path.startswith('/api/form?'):
                with lock: active+=1; peak=max(peak,active)
                try:
                    barrier.wait()
                    import urllib.parse
                    root=urllib.parse.parse_qs(path.split('?',1)[1]).get('location[directory]',[''])[0]
                    return 200,{'location':{'directory':root},'data':[]}
                finally:
                    with lock: active-=1
            raise AssertionError(path)
        result=agent_status.observe_coding_agents(self.roots,request=request,budget_seconds=1)
        self.assertTrue(result['complete'],result['errors'])
        self.assertEqual(peak,4)

    def test_active_request_exception_is_not_exposed_in_observer_output(self):
        marker='PRIVATE_CREDENTIAL_MARKER_foreign_session'
        def request(*args,**kwargs): raise RuntimeError(marker)
        result=agent_status.observe_coding_agents(self.roots,request=request)
        self.assertFalse(result['complete'])
        self.assertTrue(all(a['activity']=='unknown' for a in result['agents']))
        self.assertNotIn(marker,repr(result))

    def test_waiting_overrides_active_root_or_child(self):
        root=self.roots[0]
        headers={'root':{'id':'root','agent':'orchestrate-code','title':'Coding','location':{'directory':root}},
                 'child':{'id':'child','parentID':'root','agent':'helper','location':{'directory':root}}}
        for sid,active in [('root',{'root':{'type':'running'}}),('child',{'child':{'type':'running'}})]:
            result=agent_status.observe_coding_agents(self.roots,request=self.fixture(active,headers,
                roles={'*':{'orchestrate-code':'primary'}},permissions=[{'sessionID':'root'}] if sid=='root' else [],
                forms=[{'sessionID':'child'}] if sid=='child' else []))
            self.assertEqual(result['agents'][0]['activity'],'waiting')

    def test_unresolved_parent_cycle_and_depth_fail_closed(self):
        root=self.roots[0]
        cases=[{'s':{'id':'s','agent':'helper','parentID':'missing','location':{'directory':root}}},
               {'s':{'id':'s','agent':'helper','parentID':'s','location':{'directory':root}}}]
        for headers in cases:
            result=agent_status.observe_coding_agents(self.roots,request=self.fixture({'s':{'type':'running'}},headers,
                roles={'*':{'orchestrate-code':'primary'}}))
            self.assertFalse(result['complete']); self.assertEqual(result['agents'][0]['activity'],'unknown')

    def test_location_scoped_responses_must_match_requested_root(self):
        for badendpoint in ('role','pending'):
            def request(_,method,path,timeout):
                if path=='/api/session/active': return 200,{'data':{}}
                if path.startswith('/api/agent?'): return 200,{'location':{'directory':self.main},'data':[]}
                return 200,{'location':{'directory':self.main},'data':[]}
            result=agent_status.observe_coding_agents(self.roots,request=request)
            self.assertFalse(result['complete']); self.assertTrue(all(x['activity']=='unknown' for x in result['agents']))

    def test_role_name_must_be_primary_in_its_own_worktree(self):
        root=self.roots[0]
        headers={'s':{'id':'s','agent':'orchestrate-shared','location':{'directory':root}}}
        roles={root:{'orchestrate-shared':'subagent'}, self.roots[1]:{'orchestrate-shared':'primary'}}
        result=agent_status.observe_coding_agents(self.roots,request=self.fixture({'s':{'type':'running'}},headers,roles=roles))
        self.assertEqual(result['agents'][0]['activity'],'idle')

if __name__=='__main__': unittest.main()
