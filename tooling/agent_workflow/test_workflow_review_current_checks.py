import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_review
import workflow_store


class VerifiedCurrentChecksTests(unittest.TestCase):
    def fixture(self, root):
            run_id, check_id, ref, generation = 'run-current', 'check-current', 'receipt-current', 7
            request, bundle = {'input_digest':'input-current','plan_digest':'plan-current','policy_digest':'policy-current'}, {}
            review = {'task_id':'review-current','kind':'REVIEW','run_id':run_id,'input_digest':'input-current','evaluation_generation':generation}
            check = {'task_id':check_id,'kind':'CHECK','run_id':run_id,'evaluation_generation':generation,'input_digest':'input-current','state':'COMPLETED','result':{'decision':'PASS','artifact_refs':[ref]}}
            receipt = {'task_id':check_id,'run_id':run_id,'input_digest':'input-current','plan_digest':'plan-current','policy_digest':'policy-current','checks':[{'kind':'gametest','covers':['criterion-1'],'exit_code':0,'evidence':{'status':'PASS','counts':{'selected':17,'total':1121,'passed':17}}}]}
            raw = json.dumps(receipt, separators=(',',':'), sort_keys=True).encode()
            artifact_path = root/'receipt.json'
            artifact_path.write_bytes(raw)
            artifact_item = {'artifact':{'type':'CHECK_RECEIPT','producer_kind':'TASK','producer_id':check_id,'producer_task_id':check_id,'run_id':run_id,'path':str(artifact_path),'sha256':hashlib.sha256(raw).hexdigest(),'size_bytes':len(raw)}}
            class Store:
                def get_task(self, task_id): return review if task_id == review['task_id'] else None
                def get_run(self, _run_id): return {'attempt':{'generation':review['evaluation_generation']}}
                def list_tasks(self, *_args): return [check]
                def get_artifact(self, artifact_id): return artifact_item if artifact_id == ref else None
            coordinator = workflow_review.ReviewCoordinator.__new__(workflow_review.ReviewCoordinator)
            coordinator.store = Store()
            coordinator._inputs = lambda _run: (request,bundle)
            return coordinator, coordinator.store, request, check, review, ref, receipt

    def test_current_bound_receipt_yields_check_facts(self):
        with tempfile.TemporaryDirectory() as tmp:
            coordinator, _, _, _, review, ref, _ = self.fixture(Path(tmp))
            facts = coordinator.verified_current_checks(review['task_id'])
            self.assertEqual(ref, facts[0]['receipt_ref'])
            self.assertEqual(('gametest', 0, {'status':'PASS','counts':{'selected':17,'total':1121,'passed':17}}),
                             (facts[0]['kind'], facts[0]['exit_code'], facts[0]['evidence']))

    def assert_rejected(self, mutate=None, alter_artifact=None, alter_run=None):
        with tempfile.TemporaryDirectory() as tmp:
            coordinator, store, request, check, review, ref, receipt = self.fixture(Path(tmp))
            if mutate: mutate(check, review, receipt)
            if alter_run:
                original = coordinator.store.get_run
                coordinator.store.get_run = lambda rid: alter_run(original(rid))
            if alter_artifact:
                original = coordinator.store.get_artifact
                coordinator.store.get_artifact = lambda aid: alter_artifact(original(aid))
            with self.assertRaises(workflow_store.ProtocolError):
                coordinator.verified_current_checks(review['task_id'])

    def test_rejects_inner_receipt_input_digest(self):
        def alter(item):
            meta=item['artifact']; raw=Path(meta['path']).read_bytes(); receipt=json.loads(raw)
            receipt['input_digest']='different'; data=json.dumps(receipt,separators=(',',':'),sort_keys=True).encode()
            Path(meta['path']).write_bytes(data); meta['sha256']=hashlib.sha256(data).hexdigest(); meta['size_bytes']=len(data)
            return item
        self.assert_rejected(alter_artifact=alter)

    def test_rejects_old_review_generation(self):
        self.assert_rejected(mutate=lambda _c,r,_x:r.update(evaluation_generation=99))

    def test_rejects_check_input_different_from_review(self):
        self.assert_rejected(mutate=lambda c,_r,_x:c.update(input_digest='different'))

    def test_rejects_mismatched_producer_task(self):
        self.assert_rejected(alter_artifact=lambda item: (item['artifact'].update(producer_task_id='other') or item))

    def test_rejects_corrupted_receipt_bytes(self):
        def alter(item):
            Path(item['artifact']['path']).write_bytes(b'{corrupt')
            return item
        self.assert_rejected(alter_artifact=alter)


if __name__ == '__main__':
    unittest.main()
