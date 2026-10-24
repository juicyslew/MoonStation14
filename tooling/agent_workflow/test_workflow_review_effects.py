import tempfile
import threading
import unittest
from pathlib import Path

import workflow_review_effects


class StoreFixture:
    def __init__(self, path): self.path=str(path)


class ReviewEffectGuardTests(unittest.TestCase):
    def test_same_identity_excludes_and_different_session_is_independent(self):
        with tempfile.TemporaryDirectory() as tmp:
            store=StoreFixture(Path(tmp)/'workflow.sqlite')
            held=workflow_review_effects.review_effect_guard(store,'http://localhost:1','sid')
            try:
                outcome=[]; attempted=threading.Event()
                def same_thread_attempt():
                    attempted.set()
                    try:
                        nested=workflow_review_effects.review_effect_guard(store,'http://localhost:1','sid')
                    except (BlockingIOError,TimeoutError): outcome.append('busy')
                    else: nested.close(); outcome.append('acquired')
                worker=threading.Thread(target=same_thread_attempt); worker.start()
                self.assertTrue(attempted.wait(2)); worker.join(2)
                self.assertFalse(worker.is_alive()); self.assertEqual(['busy'],outcome)
                other=workflow_review_effects.review_effect_guard(store,'http://localhost:1','other-sid')
                other.close()
            finally: held.close()


if __name__=='__main__': unittest.main()
