"""The website presents owner outcomes as read-only board history."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import gate_progress
import test_workflow_owner_api as owner_api_fixture
from test_gate_progress_dom_support import NODE_DOM_HARNESS


class BoardOwnerRoundTripTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('node') or shutil.which('node.exe'), 'Node.js unavailable')
    def test_pass_and_accepted_history_are_read_only_in_website(self):
        with tempfile.TemporaryDirectory() as tmp:
            app, case, store, receipt, sequence, event = owner_api_fixture.OwnerApiBindingTests().pass_app(Path(tmp))
            from starlette.testclient import TestClient
            client = TestClient(app)
            response = client.get('/v1/board/snapshot', headers={'Authorization': 'Bearer owner-token'})
            self.assertEqual(200, response.status_code, response.text)
            snapshot = response.json()
            card = snapshot['columns']['Your testing'][0]
            self.assertEqual('PASS', card['focused_result'])
            manual_steps = card['details'].get('manual_steps', [])
            self.assertTrue(manual_steps, 'fixture must contain actual manual steps')
            snapshot['history'].append({'phase': 'Accepted', 'run_id': receipt['run_id'],
                'label': card['label'], 'details': {'focused_result': 'PASS', 'manual_steps': manual_steps}})
            page = gate_progress.workflow_page(snapshot)
            script = re.search(r'<script>\s*(.*?)\s*</script>', page, re.S).group(1)
            harness = NODE_DOM_HARNESS.replace('__NOW__', '0') + r'''
const requests=[];global.fetch=async(url,options={})=>{requests.push({url,method:options.method||'GET'});throw Error('unexpected request')};
'''
            inspect = r'''
boardTransport.snapshotConnected=true;workflowConnected=true;renderWorkflow(data.workflow);
const text=document.getElementById('columns').textContent;
if(!text.includes('Passed'))throw Error('PASS result not shown as Passed');
const forms=document.querySelectorAll?document.querySelectorAll('form'):[];
const all=document.getElementById('columns');
const find=(n,p)=>p(n)?n:(n.children||[]).map(x=>find(x,p)).find(Boolean)||null;
if(find(all,x=>x.tag==='form'))throw Error('feedback form rendered');
if(find(all,x=>x.tag==='button'&&/accept|request changes|cancel/i.test(x.textContent)))throw Error('owner decision control rendered');
const manual=find(all,x=>x.tag==='button'&&x.dataset.action==='manual');
if(!manual)throw Error('manual steps disclosure button missing');
manual.onclick();
const renderedCard=find(all,x=>x.tag==='article'&&x.textContent.includes(data.workflow.columns['Your testing'][0].label));
if(!renderedCard)throw Error('Passed card missing');
if(!data.workflow.columns['Your testing'][0].details.manual_steps.every(step=>renderedCard.textContent.includes(step)))throw Error('actual manual steps missing from rendered card details');
if(requests.some(x=>x.method==='POST'))throw Error('website sent owner decision POST');
console.log('read-only Passed projection and manual history verified');
'''
            result = subprocess.run([shutil.which('node') or shutil.which('node.exe'), '-'],
                input=harness + script + inspect, capture_output=True, text=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('read-only Passed projection and manual history verified', result.stdout)


if __name__ == '__main__':
    unittest.main()
