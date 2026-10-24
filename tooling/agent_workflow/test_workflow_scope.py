import copy
import hashlib
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

import workflow_preflight as preflight
import workflow_scope as scopes
from workflow_store import WorkflowStore, IdempotencyConflict
from preflight_test_support import make_preflight_case


class AuthorScopeTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); root=Path(self.tmp.name)
        self.case=make_preflight_case(root,caller='scope-caller',key='old',evidence=b'evidence')
        self.context=preflight.PreflightContext(self.case.repo,self.case.author,self.case.policy,[self.case.artifact],self.case.receipt,
            hashlib.sha256(self.case.receipt.read_bytes()).hexdigest())
        self.db=WorkflowStore(root/'workflow.sqlite')
        self.archive=root.parent/(root.name+'-archive')

    def tearDown(self): self.tmp.cleanup()

    def payload(self,feature,key):
        policy=copy.deepcopy(self.case.policy); policy['sprint_id']=feature; policy['criteria']=[feature+' criterion']
        policy['coverage']={feature+' criterion':['package','provenance']}
        evidence=self.case.repo.parent/(feature+'-evidence.txt'); evidence.write_text(feature+' evidence')
        artifact=copy.deepcopy(self.case.artifact)
        artifact['artifact_id']='artifact_'+feature.replace('-','_')
        artifact['path']=str(evidence); artifact['sha256']=hashlib.sha256(evidence.read_bytes()).hexdigest()
        artifact['size_bytes']=evidence.stat().st_size
        artifact['producer_id']=artifact['submission_id']='submit_'+hashlib.sha256(('scope-caller\0submit\0'+key).encode()).hexdigest()[:24]
        return {'contract_version':1,'policy':policy,'artifacts':[artifact],'submission_idempotency_key':key}

    def register(self, context, caller, payload, key):
        return scopes.register_author_scope(self.db,context,caller,payload,key,artifact_dir=self.archive)

    def test_independent_feature_scopes_and_idempotent_freeze(self):
        a=self.payload('feature-a','submission-a'); b=self.payload('feature-b','submission-b')
        first=self.register(self.context,'scope-caller',a,'register-a')
        self.assertEqual(self.register(self.context,'scope-caller',a,'register-a'),first)
        self.assertEqual(self.register(self.context,'scope-caller',a,'register-a-new-key'),first)
        second=self.register(self.context,'scope-caller',b,'register-b')
        ca=scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='submission-a',sprint_id='feature-a',policy_digest=first['policy_digest'])
        cb=scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='submission-b',sprint_id='feature-b',policy_digest=second['policy_digest'])
        ca.policy['criteria'].append('caller mutation')
        self.assertEqual(cb.policy['criteria'],['feature-b criterion'])
        self.assertEqual(scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='submission-a',sprint_id='feature-a',policy_digest=first['policy_digest']).policy['criteria'],['feature-a criterion'])
        self.assertEqual(self.db.list_runs_by_phase(['QUEUED']),[])

    def test_submission_key_cannot_be_rebound_and_caller_is_bound(self):
        a=self.payload('feature-a','same')
        receipt=self.register(self.context,'scope-caller',a,'register-a')
        with self.assertRaises(IdempotencyConflict): self.register(self.context,'scope-caller',self.payload('feature-b','different'),'register-a')
        with self.assertRaises(IdempotencyConflict): self.register(self.context,'scope-caller',self.payload('feature-b','same'),'register-b')
        with self.assertRaises(scopes.ScopeError): scopes.effective_context(self.db,self.context,caller='other',submission_idempotency_key='same',sprint_id='feature-a',policy_digest='x')
        changed=replace(self.context,repo=self.context.repo.parent)
        with self.assertRaises(scopes.ScopeError): scopes.effective_context(self.db,changed,caller='scope-caller',submission_idempotency_key='same',sprint_id='feature-a',policy_digest=receipt['policy_digest'])

    def test_registered_artifact_uses_external_immutable_archive_and_replay(self):
        payload=self.payload('archived-feature','archived-submit'); original=Path(payload['artifacts'][0]['path'])
        original_bytes=original.read_bytes(); original_path=str(original)
        receipt=self.register(self.context,'scope-caller',payload,'archive-key')
        stored=self.db.get_author_scope(caller='scope-caller',submission_key='archived-submit')['artifacts'][0]
        archived=Path(stored['path'])
        self.assertNotEqual(stored['path'],original_path)
        self.assertNotIn(str(self.context.repo),str(archived))
        self.assertEqual(archived.read_bytes(),original_bytes)
        original.unlink()
        effective=scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='archived-submit',sprint_id='archived-feature',policy_digest=receipt['policy_digest'])
        self.assertEqual(Path(effective.artifacts[0]['path']).read_bytes(),original_bytes)
        self.assertEqual(self.register(self.context,'scope-caller',payload,'archive-key'),receipt)
        self.assertEqual(archived.read_bytes(),original_bytes)
        archived.write_bytes(b'tampered')
        with self.assertRaises(scopes.ScopeError) as error:
            scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='archived-submit',sprint_id='archived-feature',policy_digest=receipt['policy_digest'])
        self.assertEqual(error.exception.code,'ARTIFACT_DRIFT')

    def test_floor_cannot_be_weakened(self):
        payload=self.payload('feature-a','submission-a')
        baseline=copy.deepcopy(self.context.policy); baseline['review_roles'].append('parity'); baseline['review_summaries']['parity']='trusted floor'
        with self.assertRaises(scopes.ScopeError) as error: self.register(replace(self.context,policy=baseline),'scope-caller',payload,'register')
        self.assertEqual(error.exception.code,'FLOOR_REVIEW_ROLES')

    def test_feature_specific_coverage_uses_required_pack_union_not_criterion_names(self):
        payload=self.payload('entirely-new-feature-criterion','new-submission')
        receipt=self.register(self.context,'scope-caller',payload,'new-criterion')
        self.assertEqual(receipt['status'],'REGISTERED')
        weakened=self.payload('another-new-criterion','weakened-submission')
        weakened['policy']['coverage']={'another-new-criterion':['package']}
        with self.assertRaises(scopes.ScopeError) as error:
            self.register(self.context,'scope-caller',weakened,'weakened')
        self.assertEqual(error.exception.code,'FLOOR_COVERAGE')

    def test_contract_version_requires_exact_non_boolean_integer(self):
        for value in (True,False,1.0,'1',None):
            with self.subTest(version=value):
                payload=self.payload('feature-version','submission-version')
                payload['contract_version']=value
                with self.assertRaises(scopes.ScopeError) as error:
                    self.register(self.context,'scope-caller',payload,'bad-version')
                self.assertEqual(error.exception.code,'SCOPE_INPUT')
                self.assertEqual(error.exception.field,'contract_version')
        payload=self.payload('feature-version','submission-version')
        payload.pop('contract_version')
        with self.assertRaises(scopes.ScopeError) as error:
            self.register(self.context,'scope-caller',payload,'missing-version')
        self.assertEqual(error.exception.code,'SCOPE_INPUT')
        self.assertEqual(error.exception.field,'payload')
        self.assertIsNone(self.db.get_author_scope(caller='scope-caller',submission_key='submission-version'))

    def test_shared_selector_and_trusted_exception_floors(self):
        payload=self.payload('feature-a','submission-a')
        floor_context=replace(self.context,policy={**self.context.policy,'selector_requirements':{'unit':['selector']}})
        payload=self.payload('feature-a','submission-a')
        category='unit'
        payload['policy']['selector_requirements'][category]=['different']
        with self.assertRaises(scopes.ScopeError) as error: self.register(floor_context,'scope-caller',payload,'selector')
        self.assertEqual(error.exception.code,'FLOOR_SELECTORS')
        payload=self.payload('feature-a','submission-b')
        payload['policy']['owner_exceptions'].append({'id':'extra','sprint_id':'feature-a','source_worktree':'x','input_digest':'0'*64,'allow':'full_gametest'})
        with self.assertRaises(scopes.ScopeError) as error: self.register(self.context,'scope-caller',payload,'exception')
        self.assertEqual(error.exception.code,'FLOOR_OWNER_EXCEPTIONS')
        payload=self.payload('feature-a','submission-c')
        payload['policy']['config_overrides'].append({'worktree':str(self.case.repo),'path':'opencode.jsonc','sha256':'0'*64,'authority_ref':'author-invented'})
        with self.assertRaises(scopes.ScopeError) as error: self.register(self.context,'scope-caller',payload,'override')
        self.assertEqual(error.exception.code,'FLOOR_CONFIG_OVERRIDES')
        payload=self.payload('feature-a','submission-d')
        payload['policy']['review_envelope_limit_bytes']=2048
        low_limit=replace(self.context,policy={**self.context.policy,'review_envelope_limit_bytes':1024})
        with self.assertRaises(scopes.ScopeError) as error: self.register(low_limit,'scope-caller',payload,'review-limit')
        self.assertEqual(error.exception.code,'FLOOR_REVIEW_LIMIT')

    def test_invalid_artifact_metadata_and_unregistered_scope_are_refused(self):
        payload=self.payload('feature-a','submission-a'); payload['artifacts'][0]['sha256']='0'*64
        with self.assertRaises(scopes.ScopeError) as error: self.register(self.context,'scope-caller',payload,'bad-hash')
        self.assertEqual(error.exception.code,'ARTIFACT_CONTRADICTION')
        with self.assertRaises(scopes.ScopeError) as error:
            scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='missing',sprint_id='feature-a',policy_digest='0'*64)
        self.assertEqual(error.exception.code,'SCOPE_NOT_REGISTERED')

    def test_wrong_producer_missing_bytes_and_lookup_mismatch(self):
        payload=self.payload('feature-a','submission-a')
        payload['artifacts'][0]['producer_id']='submit_'+'0'*24
        with self.assertRaises(scopes.ScopeError) as error: self.register(self.context,'scope-caller',payload,'producer')
        self.assertEqual(error.exception.code,'ARTIFACT_PROVENANCE')
        payload=self.payload('feature-a','submission-b'); Path(payload['artifacts'][0]['path']).unlink()
        with self.assertRaises(scopes.ScopeError) as error: self.register(self.context,'scope-caller',payload,'missing')
        self.assertEqual(error.exception.code,'ARTIFACT_MISSING')
        payload=self.payload('feature-a','submission-c'); receipt=self.register(self.context,'scope-caller',payload,'valid')
        for sprint,digest in [('wrong-sprint',receipt['policy_digest']),('feature-a','0'*64)]:
            with self.assertRaises(scopes.ScopeError) as error: scopes.effective_context(self.db,self.context,caller='scope-caller',submission_idempotency_key='submission-c',sprint_id=sprint,policy_digest=digest)
            self.assertEqual(error.exception.code,'SCOPE_MISMATCH')


if __name__=='__main__': unittest.main()
