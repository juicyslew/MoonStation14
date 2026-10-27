import unittest

try:
    import validation_evidence
except ModuleNotFoundError:
    from tooling.agent_workflow import validation_evidence


class GameTestCompletionTests(unittest.TestCase):
    def test_actual_server_banner_is_correlated_across_large_separate_feeds(self):
        tracker = validation_evidence.GameTestCompletionTracker()
        self.assertIsNone(tracker.feed("GameTest tag preflight: 19/100 fixtures selected\n"))
        self.assertIsNone(tracker.feed("x" * 70000 + "\n"))
        state = tracker.feed("[16:53:59] [Server thread/INFO] [minecraft/GameTestServer]: ========= 19 GAME TESTS COMPLETE IN 12.23 s ======================\n")
        self.assertEqual(state, {"selected": 19, "total": 100, "elapsed": 12.23, "passed": False})

    def test_requires_valid_matching_preflight_and_native_banner(self):
        tracker = validation_evidence.GameTestCompletionTracker()
        self.assertIsNone(tracker.feed("[Server thread] 3 GAME TESTS COMPLETE IN 2 s\n"))
        tracker.feed("GameTest tag preflight: 4/4 fixtures selected\n")
        self.assertIsNone(tracker.feed("[Server thread] 3 GAME TESTS COMPLETE IN 2 s\n"))
        self.assertIsNone(validation_evidence.gametest_completion_state("GameTest tag preflight: 0/0 fixtures selected\n"))


if __name__ == "__main__":
    unittest.main()
