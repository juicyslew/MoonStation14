import json, tempfile, unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from tooling.agent_workflow import agent_status

class StatusTests(unittest.TestCase):
 def fixture(self):
  tmp=tempfile.TemporaryDirectory(); family=Path(tmp.name)
  for n in ['MoonStation14','MoonStation14-agent-01','MoonStation14-agent-02','MoonStation14-agent-03','MoonStation14-agent-04','MoonStation14-agent-05']:
   (family/n).mkdir()
  root=family/'MoonStation14-agent-01/work/agent_exchange'; root.mkdir(parents=True)
  state={}; notes=[]
  def read(p): return json.loads(Path(p).read_text()) if Path(p).exists() else {}
  def write(p,v): state[str(p)]=v; Path(p).parent.mkdir(parents=True,exist_ok=True); Path(p).write_text(json.dumps(v))
  wf=SimpleNamespace(readjson=read,writejson=write,native_notify=lambda *a:notes.append(a))
  return tmp,family,root,wf,notes
 def request(self, active, headers=None):
  headers=headers or {}
  def call(server,method,path,timeout=30):
   self.assertIsNone(server); self.assertEqual(method,'GET'); self.assertEqual(timeout,12)
   return 200, {'data':active} if path.endswith('/active') else {'data':headers[path.rsplit('/',1)[-1]]}
  return call
 def test_authenticated_request_and_five_card_roster(self):
   tmp,fam,root,wf,notes=self.fixture()
   with patch.object(agent_status,'_dispatch',wraps=agent_status._dispatch):
    stats=agent_status.reconcile(root,wf,self.request({}),100,rows=[],attention={},notify=False)
   self.assertEqual(stats['core_monitored'],4); self.assertEqual(stats['monitored'],5)
   cards=agent_status.project(root,[],{},100)['agents']
   self.assertEqual(sum(x['worktree'].casefold()==str(fam/'MoonStation14-agent-01').casefold() for x in cards),1)
   self.assertEqual([x['label'] for x in cards],['Main consultant','Agent 01','Agent 02','Agent 03','Agent 04'])
   tmp.cleanup()
 def test_candidate_and_old_snapshot_cannot_restore_lead_card(self):
   tmp,fam,root,wf,notes=self.fixture(); lead=str(fam/'MoonStation14-agent-05')
   head={'id':'lead-candidate','worktree':{'worktree':lead},'sprint_id':'lead'}
   with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
    wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[{'id':lead.casefold(),'label':'Integration lead','activity':'working'}]})
    cards=agent_status.project(root,[],{},100)['agents']
   self.assertEqual(len(cards),5)
   self.assertNotIn(lead.casefold(),[c['id'] for c in cards])
   tmp.cleanup()
 def test_default_request_resolves_existing_authenticated_dispatcher(self):
  tmp,fam,root,wf,notes=self.fixture(); calls=[]
  def api(server,method,path,timeout=30):
   calls.append((server,method,path,timeout)); return 200,{'data':{}}
  with patch('tooling.agent_workflow.gate_dispatch._request',api):
   agent_status.reconcile(root,wf,None,100,rows=[],attention={},notify=False)
  self.assertEqual(calls,[ (None,'GET','/api/session/active',12) ])
  tmp.cleanup()
 def test_active_child_header_work_and_sid_and_latest_title(self):
  tmp,fam,root,wf,notes=self.fixture(); p=fam/'MoonStation14-agent-01'
  req=self.request({'child':{'type':'running'}},{'child':{'id':'child','location':{'directory':str(p)},'title':'Current task'}})
  agent_status.reconcile(root,wf,req,100,rows=[],attention={},notify=False)
  c=next(c for c in agent_status.project(root,[],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual((c['activity'],c['session_id'],c['summary']),('working','child','Current task'))
  tmp.cleanup()
 def test_multiple_active_headers_choose_newest_worktree_context_in_any_order(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  headers={'parent':{'id':'parent','location':{'directory':p},'title':'Old parent','time':{'updated':10}},
           'child':{'id':'child','location':{'directory':p},'title':'New child task','time':{'updated':20}}}
  for active in ({'parent':{'type':'running'},'child':{'type':'running'}},{'child':{'type':'running'},'parent':{'type':'running'}}):
   def req(server,method,path,timeout=30):
    return 200,{'data':active if path.endswith('/active') else headers[path.rsplit('/',1)[-1]]}
   agent_status.reconcile(root,wf,req,100,rows=[],attention={},notify=False)
   c=next(c for c in agent_status.project(root,[],{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual((c['session_id'],c['summary']),('child','New child task'))
  tmp.cleanup()
 def test_idle_episode_notifies_once_and_new_core_independently(self):
  tmp,fam,root,wf,notes=self.fixture(); req=self.request({})
  agent_status.reconcile(root,wf,req,100,rows=[],attention={},notify=True)
  agent_status.reconcile(root,wf,req,110,rows=[],attention={},notify=True)
  agent_status.reconcile(root,wf,req,120,rows=[],attention={},notify=True)
  self.assertEqual(len(notes),1)
  self.assertEqual(notes[0][1],'Core coding agents idle')
  self.assertEqual(notes[0][2].count('Agent '),4)
  agent_status.reconcile(root,wf,self.request({'b':{'type':'running'}},{'b':{'id':'b','location':{'directory':str(fam/'MoonStation14-agent-02')},'title':'B task'}}),121,rows=[],attention={},notify=True)
  agent_status.reconcile(root,wf,req,130,rows=[],attention={},notify=True)
  agent_status.reconcile(root,wf,req,140,rows=[],attention={},notify=True)
  self.assertEqual(len(notes),2); self.assertIn('Agent 02',notes[1][2]); self.assertNotIn('Agent 01',notes[1][2])
  tmp.cleanup()
 def test_manual_attention_and_feedback_reprojects_cached_snapshot(self):
  tmp,fam,root,wf,notes=self.fixture(); req=self.request({})
  attention={'awaiting_owner':[{'worktree':{'worktree':str(fam/'MoonStation14-agent-01')},'candidate_id':'c','title':'Manual'}]}
  agent_status.reconcile(root,wf,req,100,rows=[],attention=attention,notify=False)
  self.assertEqual(next(c for c in agent_status.project(root,[],attention,101)['agents'] if c['label']=='Agent 01')['column'],'ready_manual')
  feedback={'agent_work':[],'awaiting_owner':[]}
  self.assertEqual(next(c for c in agent_status.project(root,[],feedback,101)['agents'] if c['label']=='Agent 01')['column'],'working')
  tmp.cleanup()
 def test_stale_snapshot_unknown_and_not_ready(self):
  tmp,fam,root,wf,notes=self.fixture(); wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':1,'agents':[{'id':str(fam/'MoonStation14-agent-01').casefold(),'activity':'idle','column':'ready_manual'}]})
  card=next(c for c in agent_status.project(root,[],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual(card['activity'],'unknown'); self.assertEqual(card['column'],'working')
  tmp.cleanup()
 def test_api_failure_and_missing_header_do_not_claim_idle_or_alert(self):
  tmp,fam,root,wf,notes=self.fixture()
  fail=lambda *a,**k: (_ for _ in ()).throw(OSError('offline'))
  agent_status.reconcile(root,wf,fail,100,rows=[],attention={},notify=True)
  agent_status.reconcile(root,wf,self.request({'child':{'type':'running'}}),111,rows=[],attention={},notify=True)
  self.assertEqual(notes,[]); self.assertTrue(any(c['activity']=='unknown' for c in agent_status.project(root,[],{},111)['agents']))
  tmp.cleanup()
 def test_header_failure_is_visible_and_overflow_fails_closed(self):
  tmp,fam,root,wf,notes=self.fixture()
  missing=self.request({'x':{'type':'running'}})
  stats=agent_status.reconcile(root,wf,missing,100,rows=[],attention={},notify=False)
  self.assertTrue(any('header x unavailable' in e for e in stats['errors']))
  overflow=self.request({str(i):{'type':'running'} for i in range(65)})
  stats=agent_status.reconcile(root,wf,overflow,101,rows=[],attention={},notify=False)
  self.assertTrue(any('exceeds 64' in e for e in stats['errors']))
  tmp.cleanup()
 def test_current_needs_changes_and_old_ghost_not_waiting(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  current={'id':'current','worktree':{'worktree':p},'sprint_id':'s','revision_sequence':2,'gate_outcome':{'result':'needs_changes','findings':['fix me']},'gate_plan':{'title':'Current'}}
  oldrow={'id':'ghost','author':p,'state':'queued','sort_time':99,'validation_active':False}
  row={'id':'current','author':p,'state':'needs_changes','validation_active':False}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':current,'sprint_id':'s'}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[]})
   c=next(c for c in agent_status.project(root,[oldrow,row],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual(c['column'],'working'); self.assertEqual(c['findings'],['fix me']); self.assertEqual(c['additional_work'],[])
  tmp.cleanup()
 def test_physical_validation_and_live_queued_use_existing_rows(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  head={'id':'new-head','worktree':{'worktree':p},'sprint_id':'new','revision_sequence':1,'submitted_at':20,'gate_outcome':{'result':'needs_changes','findings':['head finding']}}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head,'sprint_id':'new'}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[]})
   row={'id':'old','author':p,'state':'VALIDATION RUNNING','validation_active':True,'check_index':2,'total':4,'started':80,'runner_pid':123,'log':'file:///check.log','detail':'live old check'}
   c=next(c for c in agent_status.project(root,[row],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual(c['column'],'active_validation'); self.assertEqual(c['validation_stage'],'checks'); self.assertEqual(c['candidate_id'],'old')
  self.assertEqual((c['check_index'],c['total'],c['started'],c['runner_pid'],c['log']),(2,4,80,123,'file:///check.log'))
  self.assertEqual(c['findings'],['head finding'])
  tmp.cleanup()
 def test_cross_sprint_head_uses_timestamp_not_revision_sequence(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  old={'id':'old','worktree':{'worktree':p},'sprint_id':'old-sprint','revision_sequence':50,'submitted_at':10,'gate_plan':{'title':'Older'}}
  new={'id':'new','worktree':{'worktree':p},'sprint_id':'new-sprint','revision_sequence':1,'submitted_at':20,'gate_plan':{'title':'Newer'}}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':old},{'head':new}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[]})
   c=next(c for c in agent_status.project(root,[],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual(c['candidate_id'],'new'); self.assertEqual(c['summary'],'Newer')
  tmp.cleanup()
 def test_ambiguous_legacy_uses_only_latest_known_row_as_unconfirmed_context(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-02')
  older={'id':'old','worktree':{'worktree':p},'sprint_id':'old','gate_plan':{'title':'Old pass'}}
  newer={'id':'new','worktree':{'worktree':p},'sprint_id':'new','gate_plan':{'title':'Merge title'}}
  ambiguous=[{'head':None,'legacy_candidates':[older,newer]}]
  rows=[{'id':'old','author':p,'state':'ready_for_owner','updated':10,'findings':[]},
        {'id':'new','author':p,'state':'needs_changes','updated':20,'findings':['JVM failure','callback failed']}]
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=ambiguous):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[{'id':str(fam/'MoonStation14-agent-02').casefold(),'activity':'idle'}]})
   c=next(c for c in agent_status.project(root,rows,{},100)['agents'] if c['label']=='Agent 02')
  self.assertEqual(c['column'],'working'); self.assertEqual(c['candidate_id'],'new')
  self.assertEqual(c['summary'],'Merge title'); self.assertIn('unconfirmed',c['detail'])
  self.assertEqual(c['findings'],['JVM failure','callback failed']); self.assertEqual(c['additional_work'],[])
  tmp.cleanup()
 def test_generic_active_header_does_not_hide_current_project_and_command_is_projected(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  head={'id':'current','worktree':{'worktree':p},'sprint_id':'s','submitted_at':10,'gate_plan':{'title':'Useful current project'}}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   req=self.request({'sid':{'type':'running'}},{'sid':{'id':'sid','location':{'directory':p},'title':'Agent 01 - worktree'}})
   agent_status.reconcile(root,wf,req,100,rows=[{'id':'current','author':p,'state':'VALIDATION RUNNING','validation_active':True,'argv':['gradle','test'],'check_index':3}],attention={},notify=False)
   c=next(c for c in agent_status.project(root,[{'id':'current','author':p,'state':'VALIDATION RUNNING','validation_active':True,'argv':['gradle','test'],'check_index':3}],{},100)['agents'] if c['label']=='Agent 01')
   persisted=next(x for x in json.loads((root/agent_status.SNAPSHOT).read_text())['agents'] if x['label']=='Agent 01')
   self.assertEqual(persisted['summary'],'Useful current project')
   self.assertEqual(c['summary'],'Useful current project'); self.assertEqual(c['command'],'gradle test')
  tmp.cleanup()
 def test_first_roster_slot_active_generic_header_uses_its_own_project(self):
  tmp=tempfile.TemporaryDirectory(); family=Path(tmp.name); p=family/'MoonStation14-agent-01'; p.mkdir()
  root=p/'work/agent_exchange'; root.mkdir(parents=True); wf=SimpleNamespace(
   readjson=lambda x: json.loads(Path(x).read_text()) if Path(x).exists() else {},
   writejson=lambda x,v: (Path(x).parent.mkdir(parents=True,exist_ok=True),Path(x).write_text(json.dumps(v))),
   native_notify=lambda *a:None)
  head={'id':'c','worktree':{'worktree':str(p)},'sprint_id':'s','submitted_at':10,'gate_plan':{'title':'Slot one task'}}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   req=self.request({'sid':{'type':'running'}},{'sid':{'id':'sid','location':{'directory':str(p)},'title':'Agent 01 - worktree'}})
   agent_status.reconcile(root,wf,req,100,rows=[{'id':'c','author':str(p),'state':'VALIDATION RUNNING','validation_active':True}],attention={},notify=False)
   card=next(x for x in agent_status.project(root,[{'id':'c','author':str(p),'state':'VALIDATION RUNNING','validation_active':True}],{},100)['agents'] if x['label']=='Agent 01')
  self.assertEqual(card['summary'],'Slot one task'); tmp.cleanup()
 def test_queued_requires_live_health_proof(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  head={'id':'dead','worktree':{'worktree':p},'sprint_id':'s'}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[]})
   rows=[{'id':'dead','author':p,'state':'queued','validation_active':False,'health':{'state':'blocked'}}]
   c=next(c for c in agent_status.project(root,rows,{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual(c['column'],'working')
   rows[0]['health']={'state':'active'}
   c=next(c for c in agent_status.project(root,rows,{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual(c['column'],'waiting_validation')
  tmp.cleanup()
 def test_current_queued_runner_pid_alive_without_health_is_waiting(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  head={'id':'candidate','snapshot_key':'snap','worktree':{'worktree':p},'sprint_id':'s','gate_request':{'session_id':'gate'}}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[]})
   row={'id':'candidate','author':p,'state':'queued','runner_pid':4321,'validation_active':False}
   with patch('tooling.agent_workflow.gate_progress._pid_alive',return_value=True):
    c=next(c for c in agent_status.project(root,[row],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual(c['column'],'waiting_validation')
  tmp.cleanup()
 def test_exact_active_lead_review_is_active_and_stopped_or_mismatched_is_not(self):
  tmp,fam,root,wf,notes=self.fixture(); p=str(fam/'MoonStation14-agent-01')
  head={'id':'candidate','snapshot_key':'snap','worktree':{'worktree':p},'sprint_id':'s','gate_request':{'session_id':'gate'},'gate_outcome':{}}
  session={'id':'gate','metadata':{'candidate_id':'candidate','snapshot_key':'snap','author_worktree':p,'workflow_gate':True}}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[{'id':str(fam/'MoonStation14-agent-01').casefold(),'activity':'idle'}],'active_sessions':{'gate':session}})
   row={'id':'candidate','author':p,'state':'awaiting activity','runner_pid':None,'validation_active':False}
   c=next(c for c in agent_status.project(root,[row],{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual(c['column'],'active_validation'); self.assertEqual(c['validation_stage'],'review')
   self.assertEqual(c['detail'],'Integration lead is reviewing this candidate')
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[{'id':str(fam/'MoonStation14-agent-01').casefold(),'activity':'idle'}],'active_sessions':{}})
   c=next(c for c in agent_status.project(root,[row],{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual(c['column'],'working')
   mismatched={'id':'gate','metadata':{'candidate_id':'other','snapshot_key':'snap','author_worktree':p,'workflow_gate':True}}
   wf.writejson(root/agent_status.SNAPSHOT,{'checked_at':100,'agents':[],'active_sessions':{'gate':mismatched}})
   c=next(c for c in agent_status.project(root,[row],{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual(c['column'],'working')
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[]):
   c=next(c for c in agent_status.project(root,[{'id':'ghost','author':p,'state':'queued','runner_pid':4321}],{},100)['agents'] if c['label']=='Agent 01')
   self.assertEqual(c['column'],'working')
  tmp.cleanup()
 def test_reconcile_snapshot_roundtrip_preserves_candidate_bound_active_lead(self):
  tmp,fam,root,wf,notes=self.fixture(); author=fam/'MoonStation14-agent-01'; lead=fam/'MoonStation14-agent-05'
  head={'id':'candidate','snapshot_key':'snap','worktree':{'worktree':str(author)},'sprint_id':'s','gate_request':{'session_id':'gate'},'gate_outcome':{}}
  metadata={'candidate_id':'candidate','snapshot_key':'snap','author_worktree':str(author),'workflow_gate':True}
  req=self.request({'gate':{'type':'running'}},{'gate':{'id':'gate','location':{'directory':str(lead)},'metadata':metadata,'title':'Review candidate'}})
  writes=[]; original=wf.writejson
  wf.writejson=lambda path,value:(writes.append(path),original(path,value))[-1]
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]):
   agent_status.reconcile(root,wf,req,100,rows=[],attention={},notify=True)
   rendered=agent_status.project(root,[],{},100)
  card=next(c for c in rendered['agents'] if c['label']=='Agent 01')
  self.assertEqual(card['column'],'active_validation'); self.assertEqual(card['validation_stage'],'review'); self.assertEqual(card['activity'],'idle')
  self.assertEqual(len(writes),1); self.assertIn('gate',json.loads((root/agent_status.SNAPSHOT).read_text())['active_sessions'])
  self.assertEqual(notes,[]); tmp.cleanup()
 def test_live_queued_runner_with_same_active_lead_remains_waiting(self):
  tmp,fam,root,wf,notes=self.fixture(); author=fam/'MoonStation14-agent-01'; lead=fam/'MoonStation14-agent-05'; p=str(author)
  head={'id':'candidate','snapshot_key':'snap','worktree':{'worktree':p},'sprint_id':'s','gate_request':{'session_id':'gate'},'gate_outcome':{}}
  meta={'candidate_id':'candidate','snapshot_key':'snap','author_worktree':p,'workflow_gate':True}
  req=self.request({'gate':{'type':'running'}},{'gate':{'id':'gate','location':{'directory':str(lead)},'metadata':meta,'title':'Review'}})
  row={'id':'candidate','author':p,'state':'queued','runner_pid':4321,'validation_active':False}
  with patch('tooling.agent_workflow.owner_attention.current_work',return_value=[{'head':head}]), patch('tooling.agent_workflow.gate_progress._pid_alive',return_value=True):
   agent_status.reconcile(root,wf,req,100,rows=[row],attention={},notify=False)
   card=next(c for c in agent_status.project(root,[row],{},100)['agents'] if c['label']=='Agent 01')
  self.assertEqual(card['column'],'waiting_validation'); self.assertIsNone(card['validation_stage']); tmp.cleanup()

 if __name__=='__main__': unittest.main()
