import json
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

try:
    from . import gate_progress as gp
    from .workflow_page_fixtures import sample
    from . import workflow_store
    from .test_gate_progress_dom_support import NODE_DOM_HARNESS, run_node
except ImportError:
    import gate_progress as gp
    from workflow_page_fixtures import sample
    import workflow_store
    from test_gate_progress_dom_support import NODE_DOM_HARNESS, run_node


class WorkflowPageTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_stage_timer_ticks_in_place_and_handles_missing_and_future_epochs(self):
        value=sample()
        value['columns']['Queued'][0]['stage_started_ms']=0
        value['columns']['Checking'][0]['stage_started_ms']=0
        value['columns']['Reviewing'][0]['stage_started_ms']=90000
        page=gp.workflow_page(value)
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1)
        harness=NODE_DOM_HARNESS.replace('__NOW__','120')+r'''let clock=120000;Date.now=()=>clock;'''
        observed=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+r'''
renderWorkflow(data.workflow);const section=name=>document.getElementById('columns').children.find(x=>x.children[0].textContent===name);const text=name=>section(name).children[1].children[0].children.find(x=>x.className==='workflow-stage-time')?.textContent;const before=text('Reviewing'),card=section('Reviewing').children[1].children[0],count=section('Reviewing').children[1].children.length;clock=181000;fixtureIntervals.find(x=>x.delay===1000).fn();console.log(JSON.stringify({queued:text('Queued'),checking:text('Checking'),reviewing:text('Reviewing'),before,countAfter:section('Reviewing').children[1].children.length,sameCard:card===section('Reviewing').children[1].children[0],missing:text('Queued')}));
''',timeout=15)
        self.assertEqual(observed.returncode,0,observed.stderr)
        result=json.loads(observed.stdout.strip().splitlines()[-1])
        self.assertEqual(result['reviewing'],'In 1m 31s')
        self.assertEqual(result['queued'],'In 3m 1s')
        self.assertEqual(result['checking'],'In 3m 1s')
        self.assertEqual(result['before'],'In 30s')
        self.assertTrue(result['sameCard'])
        self.assertEqual(result['countAfter'],1)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_accepted_dismissal_is_local_keyed_and_failure_remains_visible(self):
        value = sample()
        accepted = value['columns']['Accepted'][0]
        accepted.update(dismissible=True, dismiss_key='run-generation-result-A', status_text='Accepted')
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1).replace('connectWorkflow();', '', 1)
        inspect = r'''
boardTransport.snapshotConnected=true;workflowConnected=true;renderWorkflow(data.workflow);
const acceptedHost=()=>document.getElementById('columns').children[5].children[1];
const acceptedCard=()=>acceptedHost().children.find(x=>x.dataset.cardId==='run_accepted');
let card=acceptedCard();const dismiss=card.children.find(x=>x.dataset.action==='dismiss');
if(!dismiss||dismiss.disabled)throw Error('dismiss action missing or unexpectedly locked');
const before=global.requests.length;dismiss.onclick();if(acceptedCard())throw Error('accepted card not hidden');if(acceptedHost().children.length!==1)throw Error('other passed card should remain visible');
if(global.requests.length!==before)throw Error('dismiss performed a request');
const stored=localStorage.getItem('moonstation.integration.workflow-dismissals.v1');if(stored!=='["run-generation-result-A"]'||stored.includes('dummy-owner-secret'))throw Error('unsafe stored dismissal value '+stored);
renderWorkflow(data.workflow);if(acceptedCard()||acceptedHost().children.length!==1)throw Error('stored key did not hide only the accepted card');
data.workflow.columns.Accepted[0].dismissible=false;renderWorkflow(data.workflow);
if(!acceptedCard())throw Error('undismissible fault was hidden');
data.workflow.columns.Accepted[0].dismissible=true;data.workflow.columns.Accepted[0].dismiss_key='run-generation-result-B';renderWorkflow(data.workflow);
if(!acceptedCard())throw Error('new acceptance key was hidden');
global.failLocalStorage=true;data.workflow.columns.Accepted[0].dismiss_key='run-generation-result-C';renderWorkflow(data.workflow);
acceptedCard().children.find(x=>x.dataset.action==='dismiss').onclick();if(acceptedCard()||acceptedHost().children.length!==1)throw Error('memory fallback dismissal failed');
console.log('local dismissal, re-render persistence, new keys, fault visibility, and storage fallback verified');
'''
        harness = NODE_DOM_HARNESS.replace('__NOW__', '0') + 'global.requests=[];\n'
        result = run_node(shutil.which('node') or shutil.which('node.exe'), harness + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('local dismissal, re-render persistence, new keys, fault visibility, and storage fallback verified', result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_dismissal_loaded_from_storage_hides_only_exact_accepted_key(self):
        value = sample()
        accepted = value['columns']['Accepted'][0]
        accepted.update(phase='Accepted', dismissible=True, dismiss_key='accepted-key', status_text='Accepted')
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1).replace('connectWorkflow();', '', 1)
        harness = NODE_DOM_HARNESS.replace('__NOW__', '0') + "localStorage.setItem('moonstation.integration.workflow-dismissals.v1',JSON.stringify(['accepted-key']));\n"
        inspect = r'''
const acceptedHost=()=>document.getElementById('columns').children[5].children[1],acceptedCard=()=>acceptedHost().children.find(x=>x.dataset.cardId==='run_accepted');
renderWorkflow(data.workflow);if(acceptedCard()||acceptedHost().children.length!==1)throw Error('stored accepted key was not loaded selectively during initialization');
data.workflow.columns.Accepted[0].dismissible=false;renderWorkflow(data.workflow);
if(!acceptedCard()||acceptedHost().children.length!==2)throw Error('stored preference hid a non-dismissible card');
console.log('persisted key loads; false dismissible remains visible');
'''
        result = run_node(shutil.which('node') or shutil.which('node.exe'), harness + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('persisted key loads; false dismissible remains visible', result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_collapsed_workflow_card_is_compact_and_six_columns_are_ordered(self):
        value = sample()
        card = value['columns']['Checking'][0]
        card.update(status_text='Checks failed — changes needed', session_id='session-private',
                    source_id='source-private', check='CHECK', actor='ACTOR-private',
                    owner_state='OPEN', next_action='ACTOR action-private')
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1).replace('connectWorkflow();', '', 1)
        inspect = r'''
boardTransport.streamLive=true;renderWorkflow(data.workflow);
const cols=document.getElementById('columns'), names=cols.children.map(x=>x.children[0].textContent);
const card=cols.children[3].children[1].children[0], visible=card.children.slice(0,2).map(x=>x.textContent).join(' ');
const details=card.querySelector('details').textContent;
if(JSON.stringify(names)!==JSON.stringify(['Idle','Building','Queued','Checking','Reviewing','Passed']))throw Error(JSON.stringify(names));
for(const value of ['ACTOR-private','session-private','source-private','CHECK','OPEN'])if(visible.includes(value))throw Error('collapsed leaked '+value);
if(!visible.includes('Fixture Checking')||!visible.includes('Checks failed — changes needed'))throw Error(visible);
 for(const value of ['ACTOR-private','session-private','source-private','CHECK','OPEN','ACTOR action-private','input_digest','result_digest','owner_state'])if(details.includes(value))throw Error('technical details leaked '+value);
console.log('compact card and six ordered columns verified');
'''
        result = run_node(shutil.which('node') or shutil.which('node.exe'), NODE_DOM_HARNESS.replace('__NOW__', '0') + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('compact card and six ordered columns verified', result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_collapsed_terminal_error_badge_and_cause_are_visible(self):
        value=sample();card=value['columns']['Checking'][0]
        card.update(badge='Validation error',status_text='Stopped — validation error',is_running=False)
        card['details']['current_error']='Service unavailable'
        page=gp.workflow_page(value)
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1).replace('connectWorkflow();','',1)
        inspect=r'''
renderWorkflow(data.workflow);const card=document.getElementById('columns').children[3].children[1].children[0];
const collapsed=card.children.filter(x=>x.tagName!=='DETAILS').map(x=>x.textContent).join(' ');
        if(!collapsed.includes('Validation error')||!card.querySelector('details').textContent.includes('validation-service problem'))throw Error('terminal error cause is hidden: '+collapsed);
if(card.querySelector('details').open)throw Error('Details unexpectedly expanded');
console.log('collapsed terminal error badge and cause are visible');'''
        result=run_node(shutil.which('node') or shutil.which('node.exe'),NODE_DOM_HARNESS.replace('__NOW__','0')+script+inspect,timeout=15)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('collapsed terminal error badge and cause are visible',result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_summary_is_concise_and_keeps_attention_without_diagnostics(self):
        value=sample();card=value['columns']['Checking'][0]
        card.update(status_text='Checks failed', details_title='A concise feature context')
        card['details'].update(title='A concise feature context',
            summary=['A concise feature context','A useful progress update.'], owner_attention=['A useful problem'],
            manual_steps=['UNIQUE_MANUAL_SENTINEL'],
            criteria=[{'criterion_id':'C1','description':'Output matches expected value','status':'PENDING'}],
            artifacts=[{'type':'report','path':'reports/run-report.txt'}])
        page=gp.workflow_page(value);script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1).replace('connectWorkflow();','',1)
        inspect=r'''
renderWorkflow(data.workflow);const card=document.getElementById('columns').children[3].children[1].children[0],d=card.querySelector('details');
if(d.querySelector('summary').textContent!=='Summary')throw Error('disclosure is not labeled Summary');
const paragraphs=d.children.filter(x=>x.tag==='p');if(paragraphs.length>2)throw Error('too many Summary paragraphs: '+paragraphs.length);
const body=d.textContent,inline=card.querySelector('.workflow-status').textContent;
if((body.match(/Checks failed/g)||[]).length)throw Error('status repeated in Summary: '+body);
if((body.match(/A concise feature context/g)||[]).length!==1)throw Error('context missing or duplicated: '+body);
for(const x of ['Output matches expected value','reports/run-report.txt','Validation','Automated checks'])if(body.includes(x))throw Error('verbose detail leaked: '+x);
        if(!body.includes('Needs attention')||!body.includes('A useful problem'))throw Error('attention cause lost: '+body);
const c=data.workflow.columns.Checking[0];c.details.title='<img src=x onerror=alert(1)>'+('x'.repeat(400));renderWorkflow(data.workflow);
const safe=document.getElementById('columns').children[3].children[1].children[0].querySelector('details');
        const summary=safe.children.find(x=>x.tag==='p');if(!summary||summary.textContent.length>240||safe.children.some(x=>x.tag==='img'))throw Error('summary was not bounded/plain text');
console.log('Summary is concise, nonduplicative, and retains attention causes');'''
        harness=NODE_DOM_HARNESS.replace('__NOW__','0')+'global.navigator={clipboard:{writeText:async()=>{}}};\n'
        result=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+inspect,timeout=15)
        self.assertEqual(result.returncode,0,result.stderr);self.assertIn('Summary is concise, nonduplicative, and retains attention causes',result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_actual_workflow_dom_hides_persistent_faults_and_history_but_keeps_current_attention(self):
        value = sample()
        value['faults'] = [{'id': 'retained-fault', 'owner_action_required': True,
                            'capabilities': ['seen'], 'message': 'archival fault marker'}]
        value['history'] = [{'id': 'retained-history', 'message': 'archival history marker'}]
        value['current_owner_attention'] = [{'run_id':'current-run','summary':'Current owner action'}]
        current = value['columns']['Checking'][0]
        current.update(fault='Current card fault marker', details={**current.get('details',{}), 'owner_attention':['Current owner action']})
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
        script = script.replace('connectWorkflow();', '', 1)
        harness = NODE_DOM_HARNESS.replace('__NOW__', '0')
        inspect = r'''
boardTransport.streamLive=true;
renderWorkflow(data.workflow);
const banner=document.getElementById('health-banner').textContent;
const needs=document.getElementById('needs-you').textContent;
const current=document.getElementById('columns').children[3].textContent;
if(banner.includes('Persistent faults')||banner.includes('History')||banner.includes('archival fault marker')||banner.includes('archival history marker')) throw new Error(banner);
if(!needs.includes('Needs attention: 1 workflow fault')) throw new Error('operative fault notice missing: '+needs);
if(needs.includes('owner test')||needs.includes('decision')) throw new Error('owner decision feedback present: '+needs);
        if(!current.includes('Current owner action')) throw new Error('current owner action missing: '+current);
console.log('archival sections omitted; current fault and owner attention retained');
'''
        result = run_node(shutil.which('node') or shutil.which('node.exe'), harness + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('archival sections omitted; current fault and owner attention retained', result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_actual_workflow_dom_omits_archival_history_notice(self):
        value = sample()
        value['system'] = {'native_occupancy': {'held': 2, 'unknown': 0, 'state': 'current'}, 'queue': {'queued_tasks': 3, 'state': 'current'}, 'ai_availability': 'available'}
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
        script = script.replace('connectWorkflow();', '', 1)
        harness = NODE_DOM_HARNESS.replace('__NOW__', '0')
        inspect = r'''
boardTransport.streamLive=true;
for(const legacy of [{available:true,path:'archive/path'}, {available:false,control_index:'archive/index'}]) {
  data.workflow.legacy_history=legacy;
  renderWorkflow(data.workflow);
  const text=document.getElementById('health-banner').textContent;
  if(text.includes('Saved pre-cutover board')||text.includes('pre-cutover history unavailable')||text.includes('Copy history path')) throw new Error(text);
  if(!text.includes('Transport connected')||!text.includes('Native slots: 2 held')||!text.includes('Queue: 3 queued')) throw new Error(text);
}
console.log('legacy notice omitted; live status retained');
'''
        result = run_node(shutil.which('node') or shutil.which('node.exe'), harness + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('legacy notice omitted; live status retained', result.stdout)

    def test_shared_page_saved_snapshot_privacy_and_seven_lists(self):
        value = sample()
        page = gp.workflow_page(value)
        self.assertEqual(page.count('class="column"'), 4)  # Shared legacy scaffolding is replaced by the runtime branch.
        self.assertIn("const names=['Idle','Building','Queued','Checking','Reviewing','Passed']", page)
        self.assertIn('.workflow-columns{display:grid;grid-template-columns:repeat(6,minmax(0,1fr))', page)
        self.assertIn('Disconnected — last saved snapshot at', page)
        self.assertNotIn('<meta http-equiv="refresh" content="3">', page)
        self.assertIn("document.querySelector('meta[http-equiv=\"refresh\"]')?.remove()", page)
        self.assertIn('function elem(tag,text,cls)', page)
        self.assertIn('function renderWorkflow(w)', page)
        self.assertIn('0000000000000000000000000000000000000000000000000000000000000005', page)
        self.assertNotIn('dummy-owner-secret', page)
        self.assertIn('workflowDismissStorageKey', page)
        self.assertIn('Fixture finding', page)
        self.assertIn('function renderWorkflowDetails(d,c)', page)
        self.assertNotIn("'actor','next_actor','next_action'", page)

    def test_render_workflow_mode_writes_without_legacy_observation_path(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'saved.html'
            self.assertEqual(gp.render(Path(temp) / 'missing-exchange', output, sample()), output.resolve())
            page = output.read_text(encoding='utf-8')
            self.assertIn('run_accepted', page)
            self.assertNotIn('owner-feedback-observer', page)

    def test_legacy_render_keeps_refresh_directive(self):
        self.assertIn('<meta http-equiv="refresh" content="3">', gp._PAGE.replace('__DATA__', '{}'))

    def test_workflow_page_escapes_payload_and_requires_version(self):
        value = sample()
        value['columns']['Building'][0]['label'] = '<script>alert(1)</script>'
        page = gp.workflow_page(value)
        self.assertNotIn('<script>alert(1)</script>', page)
        self.assertIn('\\u003cscript\\u003e', page)
        with self.assertRaises(ValueError):
            gp.workflow_page({'version': 2})

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_actual_workflow_dom_and_owner_decision_fetch_contract(self):
        value = sample()
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
        executable = script[script.index('function elem(tag,text,cls)'):]
        self.assertNotIn('request_changes', executable)
        self.assertNotIn('/v1/owner/decision', executable)
        self.assertNotIn('ownerAction(card,c', executable)
        script = script.replace('connectWorkflow();', '', 1)
        harness = NODE_DOM_HARNESS.replace('__NOW__', '0') + r'''
const requests=[];global.fetch=async(url,opt={})=>{requests.push({url,opt});if(url==='/v1/board/snapshot')return{status:200,ok:true,json:async()=>data.workflow};throw Error('unexpected '+url)};
'''
        inspect = r'''
(async()=>{
renderWorkflow(data.workflow);
const passed=document.getElementById('columns').children[5].children[1];
if(passed.children.length!==2)throw Error('both canonical passed cards must be visible');
for(const card of passed.children){const details=card.querySelector('details');if(!details||details.open)throw Error('passed card details must be readonly and initially collapsed');}
if(document.querySelectorAll('form').length)throw Error('owner-decision form is present');
if(document.querySelectorAll('button').some(b=>/accept|request changes/i.test(b.textContent)))throw Error('owner-decision control is present');
const testingCard=passed.children.find(x=>x.dataset.cardId==='run_testing');const manual=testingCard?.children.find(x=>x.dataset.action==='manual');if(!manual)throw Error('manual steps control missing');manual.onclick();
if(requests.some(x=>x.opt.method==='POST'))throw Error('manual steps click performed POST');
const reviewing=data.workflow.columns.Reviewing[0];workflowConnected=true;
await ownerAction(new FixtureElement('div'),reviewing,'cancel_active');
const post=requests.find(x=>x.opt.method==='POST');if(!post||post.url!=='/v1/cancel')throw Error('active cancellation endpoint missing');
const body=JSON.parse(post.opt.body);if(body.run_id!=='run_reviewing'||body.evaluation_generation!==1||body.expected_sequence!==2||body.reason!=='CANCELLED')throw Error('cancel payload schema mismatch: '+JSON.stringify(body));
console.log('readonly passed cards, local manual action, and active cancel payload verified');
})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
'''
        result = run_node(shutil.which('node') or shutil.which('node.exe'), harness + script + inspect, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('readonly passed cards, local manual action, and active cancel payload verified', result.stdout)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_passed_cards_render_by_terminal_recency_and_other_columns_stay_ordered(self):
        value=sample()
        value['columns']['Your testing'][0].update(id='z-new',result_created_ms=300)
        value['columns']['Accepted'][0].update(id='a-mid',result_created_ms=200)
        value['columns']['Accepted'].append({'id':'b-tie','label':'Tie','result_created_ms':200,'details':{},'capabilities':[]})
        value['columns']['Your testing'].append({'id':'a-missing','label':'Missing','result_created_ms':None,'details':{},'capabilities':[]})
        value['columns']['Checking'].append({'id':'second-check','label':'Second check','details':{}})
        page=gp.workflow_page(value)
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1).replace('connectWorkflow();','',1)
        result=run_node(shutil.which('node') or shutil.which('node.exe'),NODE_DOM_HARNESS.replace('__NOW__','0')+script+r'''
renderWorkflow(data.workflow);const cols=document.getElementById('columns');
const ids=cols.children[5].children[1].children.map(x=>x.dataset.cardId);
if(JSON.stringify(ids)!==JSON.stringify(['z-new','a-mid','b-tie','a-missing']))throw Error(JSON.stringify(ids));
const checking=cols.children[3].children[1].children.map(x=>x.dataset.cardId);
if(JSON.stringify(checking)!==JSON.stringify(['run_checking','second-check']))throw Error(JSON.stringify(checking));
console.log('Passed recency, ties, missing timestamp, and untouched column order verified');
''',timeout=15)
        self.assertEqual(result.returncode,0,result.stderr)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_auto_connect_renews_cookie_after_coordinator_restart(self):
        value=sample()
        value['columns']['Your testing'][0]['capabilities']=['accept']
        page=gp.workflow_page(value)
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1)
        harness=NODE_DOM_HARNESS.replace('__NOW__','0')+r'''
const requests=[];let snapshots=0;global.fetch=async(url,opt)=>{requests.push({url,opt});if(url==='/v1/board/snapshot'&&snapshots++===0)return{status:403,ok:false};if(url==='/integration-status')return{status:200,ok:true};if(url==='/v1/board/snapshot')return{status:200,ok:true,json:async()=>data.workflow};if(url==='/v1/board/events')return{status:200,ok:true,body:{getReader:()=>({read:()=>new Promise(()=>{})})}};throw Error('unexpected request '+url)};
'''
        observed=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+r'''
(async()=>{for(let i=0;i<100;i++){await new Promise(resolve=>setImmediate(resolve));if(document.getElementById('owner-transport').textContent.includes('connected'))break}console.log(JSON.stringify({requests:requests.map(x=>({url:x.url,credentials:x.opt.credentials||'default'})),connected:document.getElementById('owner-transport').textContent,tokenField:elements.has('owner-token'),connectButton:elements.has('owner-connect')}))})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
''',timeout=15)
        self.assertEqual(observed.returncode,0,observed.stderr)
        self.assertTrue(observed.stdout.strip(),'cookie renewal browser harness emitted no results')
        result=json.loads(observed.stdout.strip().splitlines()[-1])
        self.assertEqual([x['url'] for x in result['requests']],['/v1/board/snapshot','/integration-status','/v1/board/snapshot','/v1/board/events'])
        self.assertTrue(all(x['credentials']=='same-origin' for x in result['requests']))
        self.assertFalse(result['tokenField'])
        self.assertFalse(result['connectButton'])
        self.assertIn('connected',result['connected'].lower())

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_delivered_sse_baseline_review_change_disconnect_and_reconnect(self):
        page=gp.workflow_page(sample())
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1)
        harness=NODE_DOM_HARNESS.replace('__NOW__','0')+r'''
const readers=[],requests=[];let snapshots=0;global.crypto={randomUUID:()=> 'stable'};
global.fetch=async(url,opt={})=>{requests.push({url,opt});if(url==='/v1/board/snapshot'){snapshots++;return{ok:true,status:200,json:async()=>structuredClone(data.workflow)}}if(url==='/v1/board/events')return{ok:true,status:200,body:{getReader(){const q=[];let wake;const reader={read(){return new Promise((resolve,reject)=>{const x={resolve,reject};if(q.length)resolve(q.shift());else (wake=x)})},push(bytes){if(wake){wake.resolve({value:bytes,done:false});wake=null}else q.push({value:bytes,done:false})},end(){if(wake){wake.resolve({done:true});wake=null}}};readers.push(reader);return reader}}};throw Error('unexpected '+url)};
'''
        observed=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+r'''
const tick=()=>new Promise(resolve=>setImmediate(resolve)),frame=n=>new TextEncoder().encode('event: board-revision\ndata: {"revision":'+n+'}\n\n');
(async()=>{for(let i=0;i<100&&!readers.length;i++)await tick();if(!readers.length)throw Error('stream did not open');readers[0].push(frame(0));for(let i=0;i<100&&snapshots<2;i++)await tick();await tick();const baseline={connected:workflowConnected,review:workflowNeedsReview,snapshots};readers[0].push(new TextEncoder().encode(': heartbeat\n\n'));await tick();const heartbeat=snapshots;const card=data.workflow.columns['Your testing'][0],host=document.getElementById('columns').children.find(x=>x.children[0].textContent==='Passed').children[1],ui=host.children[0];ui.querySelector('details').open=true;const form=ui.querySelector('form');if(form){form.children[1].value='keep draft'}card.bindings.sequence++;card.bindings.input_digest='changed';card.bindings.result_id='new-result';readers[0].push(frame(1));for(let i=0;i<100&&snapshots<3;i++)await tick();await tick();const changed={connected:workflowConnected,review:workflowNeedsReview,label:document.getElementById('owner-transport').textContent,banner:document.getElementById('health-banner').textContent,locked:document.querySelectorAll('.workflow-card button').every(x=>x.disabled),draft:host.children[0]?.querySelector('form')?.children[1]?.value,details:host.children[0]?.querySelector('details')?.open};readers[0].end();for(let i=0;i<100&&!fixtureTimers.size;i++)await tick();await tick();console.log(JSON.stringify({baseline,heartbeat,changed,disconnected:document.getElementById('owner-transport').textContent,reconnect:fixtureTimers.size>0,navigation:requests||[]}))})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
''',timeout=15)
        self.assertEqual(observed.returncode,0,observed.stderr)
        result=json.loads(observed.stdout.strip().splitlines()[-1])
        self.assertTrue(result['baseline']['connected'])
        self.assertFalse(result['baseline']['review'])
        self.assertEqual(result['heartbeat'],result['baseline']['snapshots'])
        self.assertFalse(result['changed']['connected'])
        self.assertTrue(result['changed']['review'])
        self.assertIn('connected',result['changed']['label'].lower())
        self.assertIn('Transport connected',result['changed']['banner'])
        self.assertTrue(result['changed']['locked'])
        self.assertIn('Disconnected',result['disconnected'])
        self.assertTrue(result['reconnect'])
        self.assertTrue(all(request['opt'].get('method','GET')!='POST' for request in result['navigation']))

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_activity_revision_refresh_does_not_lock_actions_but_workflow_revision_does(self):
        value = sample()
        value['columns']['Reviewing'][0]['capabilities'] = ['cancel_active']
        page = gp.workflow_page(value)
        script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
        harness = NODE_DOM_HARNESS.replace('__NOW__', '0') + r'''
const readers=[],requests=[];let snapshots=0,baseline;global.fetch=async(url,opt={})=>{requests.push({url,opt});if(url==='/v1/board/snapshot'){snapshots++;baseline??=structuredClone(data.workflow);const next=structuredClone(baseline);if(snapshots>=3)next.columns.Building.push({id:'author-activity:author-a',run_id:null,bindings:{},label:'Author A',status_text:'Working'});if(snapshots>=4)next.columns.Reviewing[0].bindings.sequence++;return{ok:true,status:200,json:async()=>next}}if(url==='/v1/board/events')return{ok:true,status:200,body:{getReader(){let wake;const reader={read(){return new Promise(resolve=>{wake=resolve})},push(value){wake?.({value,done:false});wake=null}};readers.push(reader);return reader}}};throw Error('unexpected '+url)};
'''
        observed = run_node(shutil.which('node') or shutil.which('node.exe'), harness + script + r'''
const tick=()=>new Promise(resolve=>setImmediate(resolve)),frame=(revision,workflow_revision)=>new TextEncoder().encode('event: board-revision\ndata: '+JSON.stringify({revision,workflow_revision})+'\n\n');
const activeCancel=()=>document.getElementById('columns').children.find(x=>x.children[0].textContent==='Reviewing').children[1].children[0].children.find(x=>x.dataset.action==='cancel_active');
(async()=>{for(let i=0;i<100&&!readers.length;i++)await tick();readers[0].push(frame(1,1));for(let i=0;i<100&&snapshots<2;i++)await tick();for(let i=0;i<5;i++)await tick();const baseline={connected:workflowConnected,review:workflowNeedsReview,enabled:!activeCancel().disabled};readers[0].push(frame(2,1));for(let i=0;i<100&&snapshots<3;i++)await tick();for(let i=0;i<5;i++)await tick();const host=document.getElementById('columns').children.find(x=>x.children[0].textContent==='Building').children[1],activity={connected:workflowConnected,review:workflowNeedsReview,enabled:!activeCancel().disabled,authorCards:host.children.filter(x=>x.dataset.cardId?.startsWith('author-activity:')).length};readers[0].push(frame(3,2));for(let i=0;i<100&&snapshots<4;i++)await tick();for(let i=0;i<5;i++)await tick();const changed={connected:workflowConnected,review:workflowNeedsReview,enabled:!activeCancel().disabled};console.log(JSON.stringify({baseline,activity,changed,snapshots}));})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
''', timeout=15)
        self.assertEqual(observed.returncode, 0, observed.stderr)
        result = json.loads(observed.stdout.strip().splitlines()[-1])
        self.assertTrue(result['baseline']['connected'])
        self.assertTrue(result['baseline']['enabled'])
        self.assertFalse(result['activity']['review'])
        self.assertTrue(result['activity']['connected'])
        self.assertTrue(result['activity']['enabled'])
        self.assertEqual(result['activity']['authorCards'], 1)
        self.assertTrue(result['changed']['review'])
        self.assertFalse(result['changed']['connected'])
        self.assertFalse(result['changed']['enabled'])

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_initial_sse_baseline_changed_binding_requires_review(self):
        page=gp.workflow_page(sample())
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1)
        harness=NODE_DOM_HARNESS.replace('__NOW__','0')+r'''
const readers=[],requests=[];let snapshots=0;global.crypto={randomUUID:()=> 'stable'};
global.fetch=async(url,opt={})=>{requests.push({url,opt});if(url==='/v1/board/snapshot'){snapshots++;const next=structuredClone(data.workflow);if(snapshots===2){const card=next.columns['Your testing'][0];card.bindings.sequence++;card.bindings.result_id='race-result'}return{ok:true,status:200,json:async()=>next}}if(url==='/v1/board/events')return{ok:true,status:200,body:{getReader(){let wake;const reader={read(){return new Promise(resolve=>{wake=resolve})},push(value){wake?.({value,done:false});wake=null}};readers.push(reader);return reader}}};throw Error('unexpected '+url)};
'''
        observed=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+r'''
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{for(let i=0;i<100&&!readers.length;i++)await tick();if(!readers.length)throw Error('stream did not open');readers[0].push(new TextEncoder().encode('event: board-revision\ndata: {"revision":0}\n\n'));for(let i=0;i<100&&snapshots<2;i++)await tick();for(let i=0;i<10;i++)await tick();console.log(JSON.stringify({streamLive:boardTransport.streamLive,connected:workflowConnected,review:workflowNeedsReview,snapshotConnected:boardTransport.snapshotConnected,banner:document.getElementById('health-banner').textContent,locked:document.querySelectorAll('.workflow-card button').every(x=>x.disabled),posts:requests.filter(x=>x.opt.method==='POST').length}));})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
''',timeout=15)
        self.assertEqual(observed.returncode,0,observed.stderr)
        result=json.loads(observed.stdout.strip().splitlines()[-1])
        self.assertTrue(result['streamLive'])
        self.assertFalse(result['connected'])
        self.assertTrue(result['review'])
        self.assertTrue(result['snapshotConnected'])
        self.assertIn('Transport connected',result['banner'])
        self.assertNotIn('Disconnected',result['banner'])
        self.assertTrue(result['locked'])
        self.assertEqual(result['posts'],0)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_initial_sse_baseline_new_action_card_requires_review(self):
        value=sample()
        action_card=value['columns']['Your testing'][0]
        action_card['capabilities']=['accept']
        baseline=json.loads(json.dumps(value))
        baseline['columns']['Your testing']=[]
        page=gp.workflow_page(baseline)
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1)
        harness=NODE_DOM_HARNESS.replace('__NOW__','0')+r'''
const readers=[],requests=[];let snapshots=0;const updated=__UPDATED__;global.fetch=async(url,opt={})=>{requests.push({url,opt});if(url==='/v1/board/snapshot'){snapshots++;return{ok:true,status:200,json:async()=>structuredClone(snapshots===1?data.workflow:updated)}}if(url==='/v1/board/events')return{ok:true,status:200,body:{getReader(){let wake;const reader={read(){return new Promise(resolve=>{wake=resolve})},push(value){wake?.({value,done:false});wake=null}};readers.push(reader);return reader}}};throw Error('unexpected '+url)};
'''.replace('__UPDATED__',json.dumps(value))
        observed=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+r'''
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{for(let i=0;i<100&&!readers.length;i++)await tick();if(!readers.length)throw Error('stream did not open');readers[0].push(new TextEncoder().encode('event: board-revision\ndata: {"revision":0}\n\n'));for(let i=0;i<100&&snapshots<2;i++)await tick();for(let i=0;i<10;i++)await tick();console.log(JSON.stringify({streamLive:boardTransport.streamLive,connected:workflowConnected,review:workflowNeedsReview,snapshotConnected:boardTransport.snapshotConnected,banner:document.getElementById('health-banner').textContent,locked:document.querySelectorAll('.workflow-card button').every(x=>x.disabled),posts:requests.filter(x=>x.opt.method==='POST').length,snapshots}));})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
''',timeout=15)
        self.assertEqual(observed.returncode,0,observed.stderr)
        result=json.loads(observed.stdout.strip().splitlines()[-1])
        self.assertTrue(result['streamLive'])
        self.assertFalse(result['connected'])
        self.assertTrue(result['review'])
        self.assertTrue(result['snapshotConnected'])
        self.assertIn('Transport connected',result['banner'])
        self.assertNotIn('Disconnected',result['banner'])
        self.assertTrue(result['locked'])
        self.assertEqual(result['posts'],0)
        self.assertEqual(result['snapshots'],2)

    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_snapshot_loading_and_refresh_failure_are_independent_of_sse_transport(self):
        value=sample();value['columns']['Reviewing'][0]['capabilities']=['cancel_active']
        page=gp.workflow_page(value)
        script=re.search(r'<script>\s*(.*?)\s*</script>',page,re.S).group(1)
        harness=NODE_DOM_HARNESS.replace('__NOW__','0')+r'''
let resolveInitial;const readers=[];let calls=0;global.fetch=async(url)=>{if(url==='/v1/board/snapshot'){calls++;if(calls===1)return new Promise(resolve=>resolveInitial=resolve);return{ok:false,status:503}}if(url==='/v1/board/events')return{ok:true,status:200,body:{getReader(){let wake;const reader={read(){return new Promise(resolve=>wake=resolve)},push(v){wake?.({value:v,done:false});wake=null}};readers.push(reader);return reader}}};throw Error('unexpected '+url)};
'''
        observed=run_node(shutil.which('node') or shutil.which('node.exe'),harness+script+r'''
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{for(let i=0;i<100&&!resolveInitial;i++)await tick();if(!resolveInitial)throw Error('initial snapshot request did not start');if(document.getElementById('owner-transport').textContent.includes('Disconnected'))throw Error('initial loading misreported disconnected');if(!document.getElementById('health-banner').textContent.includes('Loading initial workflow snapshot'))throw Error('initial loading state not shown');resolveInitial({ok:true,status:200,json:async()=>structuredClone(data.workflow)});for(let i=0;i<100&&!readers.length;i++)await tick();if(!readers.length)throw Error('SSE did not connect after snapshot');for(let i=0;i<100&&!workflowConnected;i++)await tick();const action=()=>document.getElementById('columns').children.find(x=>x.children[0].textContent==='Reviewing').children[1].children[0].children.find(x=>x.dataset.action==='cancel_active');if(!action()||action().disabled)throw Error('baseline action not enabled: '+JSON.stringify({label:document.getElementById('owner-transport').textContent,connected:workflowConnected,snapshot:boardTransport.snapshotConnected,live:boardTransport.streamLive,review:workflowNeedsReview}));readers[0].push(new TextEncoder().encode('event: board-revision\ndata: {"revision":1,"workflow_revision":2}\n\n'));for(let i=0;i<100&&calls<2;i++)await tick();for(let i=0;i<10;i++)await tick();const label=document.getElementById('owner-transport').textContent,banner=document.getElementById('health-banner').textContent;if(!boardTransport.streamLive||!label.includes('connected'))throw Error('snapshot failure disconnected healthy SSE: '+label);if(!banner.includes('HTTP 503')||!banner.includes('refresh failed'))throw Error('snapshot cause/staleness missing: '+banner);if(!action().disabled)throw Error('failed refresh left actions enabled');console.log('delayed snapshot, connected transport, stale refresh cause, and locked actions verified')})().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
''',timeout=15)
        self.assertEqual(observed.returncode,0,observed.stderr)
        self.assertIn('delayed snapshot, connected transport, stale refresh cause, and locked actions verified',observed.stdout)


if __name__ == '__main__':
    unittest.main()
