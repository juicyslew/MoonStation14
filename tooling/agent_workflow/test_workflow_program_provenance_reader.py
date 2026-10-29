"""Reader-side rejection of malformed but content-addressed program proofs."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_store
from test_workflow_native_execution import admit_native_fixture
import test_workflow_review as review_fixtures


class ProgramProvenanceReaderTests(unittest.TestCase):
    def test_rejects_malformed_hashed_program_proofs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _case, store, submission, check_id = admit_native_fixture(
                root, caller='program-reader-proof', key='program-reader-proof', delay=0,
                packs=[
                    {'pack_id': 'provenance', 'selectors': [], 'covers': ['c1']},
                    {'pack_id': 'package', 'selectors': [], 'covers': ['c1']},
                ],
                program_checks=['managed_config_provenance'],
            )
            planner = review_fixtures.ReviewPlanningTests().coordinator(store, submission['run_id'])
            result = review_fixtures.ReviewPlanningTests().run_check(
                root, store, submission['run_id'], check_id)
            self.assertEqual(result['result']['decision'], 'PASS')
            review = planner.plan_reviews(submission['run_id'])[0]
            planner.verified_current_checks(review['task_id'])
            receipt_binding = next(store.get_artifact(ref) for ref in result['result']['artifact_refs']
                                   if store.get_artifact(ref)['artifact'].get('type') == 'CHECK_RECEIPT')
            receipt = json.loads(Path(receipt_binding['artifact']['path']).read_bytes())
            row = receipt['program_checks'][0]

            corruptions = {
                'missing row': lambda r: r.update(program_checks=[]),
                'duplicate row': lambda r: r['program_checks'].append(dict(r['program_checks'][0])),
                'wrong coverage': lambda r: r['program_checks'][0].update(covers=['other']),
                'wrong config digest': lambda r: r['program_checks'][0]['evidence'].update(config_sha256='0' * 64),
                'wrong package digest': lambda r: r['program_checks'][0]['evidence']['package_artifacts'][0].update(sha256='0' * 64),
                'wrong source input': lambda r: r['program_checks'][0]['evidence']['package_artifacts'][0].update(source_input_digest='wrong'),
                'unbound artifact': lambda r: r['program_checks'][0]['evidence']['package_artifacts'][0].update(artifact_ref='not-a-build'),
            }
            for label, corrupt in corruptions.items():
                with self.subTest(label=label):
                    altered = json.loads(json.dumps(receipt))
                    corrupt(altered)
                    raw = json.dumps(altered, separators=(',', ':'), sort_keys=True).encode()
                    path = root / (label.replace(' ', '-') + '.json')
                    path.write_bytes(raw)
                    binding = json.loads(json.dumps(receipt_binding))
                    binding['artifact'].update(path=str(path), sha256=hashlib.sha256(raw).hexdigest(), size_bytes=len(raw))

                    # The substituted receipt is independently hash-valid; only its inner proof is malformed.
                    receipt_ref = next(ref for ref in result['result']['artifact_refs']
                                       if store.get_artifact(ref)['artifact'].get('type') == 'CHECK_RECEIPT')
                    original_get = store.get_artifact
                    with patch.object(store, 'get_artifact', side_effect=lambda ref: binding if ref == receipt_ref else original_get(ref)):
                        with self.assertRaises(workflow_store.ProtocolError):
                            planner.verified_current_checks(review['task_id'])


if __name__ == '__main__':
    unittest.main()
