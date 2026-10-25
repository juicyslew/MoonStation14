import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import workflow_preflight as preflight
from workflow_store import digest
from workflow_check_packs import resolve_check_packs
from jsonschema import Draft202012Validator
from preflight_test_support import init_repository, make_preflight_case


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        case=make_preflight_case(Path(self.tmp.name),caller='caller-test',key='idem',evidence=b'submission evidence')
        for name in ('repo','author','evidence','artifact_id','caller','key','submission_id','artifact','policy','request','receipt'):
            setattr(self,name,getattr(case,name))

    def _write_receipt(self):
        row={'target':'main','path':'opencode.jsonc','new_sha256':hashlib.sha256((self.repo/'opencode.jsonc').read_bytes()).hexdigest()}
        data={'main_root':str(self.repo),'shared_root':str(self.repo.parent),'source_hashes':{},'created_utc':'2026-01-01T00:00:00Z','files':[row]}
        b=json.dumps(data,separators=(',',':')).encode(); self.receipt.write_bytes(b)

    def context(self):
        return preflight.PreflightContext(self.repo,self.author,self.policy,[self.artifact],self.receipt,hashlib.sha256(self.receipt.read_bytes()).hexdigest())

    def tearDown(self): self.tmp.cleanup()

    def test_capture_includes_untracked_and_deleted_tracked(self):
        (self.repo/'tracked.txt').unlink()
        m = preflight.capture_source(self.repo)
        paths = {x['path']:x['state'] for x in m['files']}
        self.assertEqual(paths['dirty.txt'], 'FILE')
        self.assertEqual(paths['tracked.txt'], 'DELETED')

    def test_source_capture_is_deterministic(self):
        self.assertEqual(preflight.capture_source(self.repo), preflight.capture_source(self.repo))

    def test_review_renderer_keeps_required_identity_fields(self):
        req={'feature_label':'Example','manual_steps':['Do it'],'criteria':[],'input_digest':'a'*64}
        out=preflight.render_review_input(req, {'artifact_refs':[],'event_id':'event_x','run_id':'run_x','task_id':'task_x','session_id':'session','worktree':'/repo','evaluation_generation':3,'assignment_generation':2,'deadline_ms':9999}, 'implementation','objective')
        self.assertEqual(out['envelope']['required_next_action'],'TASK_ASSIGNMENT')
        self.assertEqual(out['envelope']['input_digest'],'a'*64)
        self.assertEqual(out['envelope']['evaluation_generation'],3)
        self.assertIn('Never run native checks',out['instructions'])

    def test_preflight_schema_validates_policy_and_bundle_definition(self):
        schema=json.loads((Path(__file__).parents[2]/'docs/development-workflow-redesign/contracts/preflight.schema.json').read_text())
        Draft202012Validator.check_schema(schema)
        self.assertIn('bundle',schema['$defs'])

    def test_actual_preflight_happy_flow_returns_detached_bundle(self):
        bundle=preflight.preflight(self.request,caller=self.caller,context=self.context())
        self.assertEqual(bundle['submission_id'],self.submission_id)
        schema=json.loads((Path(__file__).parents[2]/'docs/development-workflow-redesign/contracts/preflight.schema.json').read_text())
        Draft202012Validator({'$schema':schema['$schema'],'$defs':schema['$defs'],'$ref':'#/$defs/bundle'}).validate(bundle)
        self.assertEqual(bundle['review_inputs'][0]['size_bytes'],len(__import__('workflow_store').canonical_json(bundle['review_inputs'][0]['body']).encode()))
        self.policy['review_summaries']['implementation']='mutated caller input'
        self.assertNotEqual(bundle['review_policy']['review_summaries']['implementation'],self.policy['review_summaries']['implementation'])

    def test_unknown_submission_field_rejected(self):
        bad=dict(self.request,unknown_field=True)
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(bad,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.verdict,'FAIL')

    def test_checkpoint_incomplete_rejected(self):
        bad=dict(self.request,implementation_complete=False)
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(bad,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'REQUEST_SCHEMA')

    def test_wrong_source_digest_rejected(self):
        bad=dict(self.request,input_digest='0'*64)
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(bad,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'SOURCE_STALE')

    def test_wrong_plan_digest_rejected(self):
        bad=dict(self.request,plan_digest='0'*64)
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(bad,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'PLAN_DIGEST')

    def test_artifact_changed_rejected(self):
        self.evidence.write_text('changed bytes')
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(self.request,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'ARTIFACT_CONTRADICTION')

    def test_objective_oversize_is_measured(self):
        self.policy['review_envelope_limit_bytes']=1024
        self.policy['review_summaries']['implementation']='large objective '*100
        self.request['policy_digest']=digest(self.policy)
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(self.request,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'REVIEW_ENVELOPE_OVERSIZE')

    def test_config_receipt_is_bound_to_agent_worktree_not_sibling_main(self):
        old_repo=self.repo; self.repo=old_repo.parent/'MoonStation14-agent-01'; self.repo.mkdir(); init_repository(self.repo)
        self.author['worktree']=str(self.repo)
        self.policy['source_worktree']=str(self.repo); self.policy['author']=self.author
        self.request['author']=self.author; self.request['source_worktree']=str(self.repo)
        main=old_repo; 
        (main/'opencode.jsonc').write_text('{"main":true}')
        (self.repo/'opencode.jsonc').write_text('{"agent":true}')
        self.request['input_digest']=digest(preflight.capture_source(self.repo))
        self.policy['fixture']='manual.md'
        self.request['policy_digest']=digest(self.policy)
        self.request['plan_digest']=digest(resolve_check_packs(self.request,repo=self.repo,policy=self.policy))
        rows=[{'target':'main','path':'opencode.jsonc','new_sha256':hashlib.sha256((main/'opencode.jsonc').read_bytes()).hexdigest()},
              {'target':'MoonStation14-agent-01','path':'opencode.jsonc','new_sha256':hashlib.sha256((self.repo/'opencode.jsonc').read_bytes()).hexdigest()}]
        data={'main_root':str(main),'shared_root':str(main.parent),'source_hashes':{},'created_utc':'2026-01-01T00:00:00Z','files':rows}
        self.receipt.write_text(json.dumps(data)); ctx=self.context()
        bundle=preflight.preflight(self.request,caller=self.caller,context=ctx)
        self.assertEqual(bundle['config_provenance']['target'],'MoonStation14-agent-01')
        rows[1]['new_sha256']=rows[0]['new_sha256']; self.receipt.write_text(json.dumps(data)); ctx=self.context()
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(self.request,caller=self.caller,context=ctx)
        self.assertEqual(cm.exception.code,'CONFIG_CHANGED')

    def test_exact_config_override_is_trusted_only_for_exact_binding(self):
        (self.repo/'opencode.jsonc').write_text('{"tooling":true}')
        self.request['input_digest']=digest(preflight.capture_source(self.repo))
        actual=hashlib.sha256((self.repo/'opencode.jsonc').read_bytes()).hexdigest()
        self.policy['config_overrides']=[{'worktree':str(self.repo),'path':'opencode.jsonc','sha256':actual,'authority_ref':'tooling-change-1'}]
        self.request['policy_digest']=digest(self.policy)
        self.assertEqual(preflight.preflight(self.request,caller=self.caller,context=self.context())['config_provenance']['override_authority'],'tooling-change-1')
        for field,value in [('worktree',str(self.repo.parent)),('path','other.jsonc'),('sha256','0'*64)]:
            with self.subTest(field=field):
                self.policy['config_overrides'][0][field]=value; self.request['policy_digest']=digest(self.policy)
                with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(self.request,caller=self.caller,context=self.context())
                self.assertEqual(cm.exception.code,'CONFIG_CHANGED' if field=='worktree' else 'POLICY_SCHEMA')

    def test_duplicate_refs_within_criterion_fail_but_cross_criterion_refs_are_unique_set(self):
        self.policy['criteria']=['c1','c2']; self.policy['coverage']={'c1':['package'],'c2':['provenance']}
        self.request['criteria']=[{'criterion_id':'c1','status':'COMPLETE','evidence_refs':[self.artifact_id]}, {'criterion_id':'c2','status':'COMPLETE','evidence_refs':[self.artifact_id]}]
        self.request['policy_digest']=digest(self.policy)
        self.request['plan_digest']=digest(resolve_check_packs(self.request,repo=self.repo,policy=self.policy))
        self.assertEqual(len(preflight.preflight(self.request,caller=self.caller,context=self.context())['verified_artifacts']),1)
        self.request['criteria'][0]['evidence_refs']=[self.artifact_id,self.artifact_id]
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(self.request,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'EVIDENCE_DUPLICATE')

    def test_owner_ready_requires_actual_manual_steps_in_payload_size(self):
        self.policy['review_envelope_limit_bytes']=16000
        self.request['manual_steps']=['x'*17000]; self.request['policy_digest']=digest(self.policy)
        self.request['plan_digest']=digest(resolve_check_packs(self.request,repo=self.repo,policy=self.policy))
        with self.assertRaises(preflight.PreflightFailure) as cm: preflight.preflight(self.request,caller=self.caller,context=self.context())
        self.assertEqual(cm.exception.code,'REVIEW_ENVELOPE_OVERSIZE')
        self.assertIn('bytes; limit is 16000',cm.exception.message)

    def test_failure_fields_are_typed(self):
        err=preflight.PreflightFailure('FAIL','CODE','field','specific','change')
        self.assertEqual((err.verdict,err.field,err.required_change),('FAIL','field','change'))


if __name__ == '__main__': unittest.main()
