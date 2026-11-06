import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from prepare_impact_substeps_comparison import (
    APPROVED_DIRECTORIES,
    COMPARISON_DIRECTORY,
    REGRESSION_DIRECTORY,
    DIAGNOSTIC_DIRECTORY,
    COST_SUBSTEPS2_DIRECTORY,
    COST_SUBSTEPS10_DIRECTORY,
    SHELL_SUBSTEPS10_DIRECTORY,
    SIX_SUBSTEPS_TRIAL_DIRECTORY,
    SIX_SUBSTEPS_REGRESSION_DIRECTORY,
    COST_SUBSTEPS6_DIRECTORY,
    SIX_SUBSTEPS_REGRESSION_R33_DIRECTORY,
    SIX_SUBSTEPS_FAILURE_FOLLOWUP_R34_DIRECTORY,
    SIX_SUBSTEPS_IDLE_PROBE_R35_DIRECTORY,
    SIX_SUBSTEPS_REGRESSION_R36_DIRECTORY,
    R37_COST_SUBSTEPS2_DIRECTORY,
    R37_COST_SUBSTEPS6_DIRECTORY,
    R38_FLIGHT_OFFLINE_UNLOAD_DIRECTORY,
    R39_FLIGHT_OFFLINE_UNLOAD_DIRECTORY,
    R40_CARGO_NATURAL_SAVE_DIRECTORY,
    R41_CARGO_POSTJOIN_DIRECTORY,
    R42_FRAME_ANCHOR_DIRECTORY,
    R43_FRAME_ANCHOR_DIRECTORY,
    R44_FRAME_ANCHOR_DIRECTORY,
    R45_BODY_UNLOAD_ORDER_DIRECTORY,
    R46_BODY_TICK_ADMISSION_DIRECTORY,
    R47_FRAME_COMPOSITION_DIRECTORY,
    R48_POST_RESTORE_MOTION_DIRECTORY,
    prepare_run_directory,
)


class PrepareImpactSubstepsComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "build").mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def test_creates_only_minimal_config_in_fresh_approved_directory(self):
        target = prepare_run_directory(self.root,
                                       f"build/{COMPARISON_DIRECTORY}", 10)
        self.assertEqual({"config"}, {p.name for p in target.iterdir()})
        config = target / "config" / "sable-server.toml"
        self.assertEqual("sub_level_substeps_per_tick = 10\n",
                         config.read_text(encoding="utf-8"))
        self.assertEqual({"sable-server.toml"},
                         {p.name for p in (target / "config").iterdir()})

    def test_creates_minimal_config_in_fresh_six_substep_trial(self):
        target = prepare_run_directory(self.root,
                                       f"build/{SIX_SUBSTEPS_TRIAL_DIRECTORY}", 6)
        self.assertEqual({"config"}, {p.name for p in target.iterdir()})
        self.assertEqual("sub_level_substeps_per_tick = 6\n",
                         (target / "config" / "sable-server.toml").read_text(encoding="utf-8"))
        self.assertFalse((self.root / "config" / "sable-server.toml").exists())

    def test_creates_fresh_shell_substeps10_target_without_touching_owner_path(self):
        target = prepare_run_directory(self.root,
                                       f"build/{SHELL_SUBSTEPS10_DIRECTORY}", 10)
        self.assertEqual({"config"}, {p.name for p in target.iterdir()})
        self.assertEqual("sub_level_substeps_per_tick = 10\n",
                         (target / "config" / "sable-server.toml").read_text(encoding="utf-8"))
        self.assertFalse((self.root / "config" / "sable-server.toml").exists())

    def test_refuses_existing_target_without_changing_its_contents(self):
        target = self.root / "build" / COMPARISON_DIRECTORY
        target.mkdir()
        sentinel = target / "owner-data.txt"
        sentinel.write_text("preserve", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            prepare_run_directory(self.root, f"build/{COMPARISON_DIRECTORY}", 10)
        self.assertEqual("preserve", sentinel.read_text(encoding="utf-8"))
        self.assertFalse((target / "config").exists())

    def test_refuses_even_an_existing_empty_target(self):
        target = self.root / "build" / COMPARISON_DIRECTORY
        target.mkdir()
        with self.assertRaises(FileExistsError):
            prepare_run_directory(self.root, f"build/{COMPARISON_DIRECTORY}", 10)
        self.assertEqual([], list(target.iterdir()))

    def test_refuses_owner_path_and_nonrequested_substep_counts(self):
        with self.assertRaises(ValueError):
            prepare_run_directory(self.root, "run", 10)
        with self.assertRaises(ValueError):
            prepare_run_directory(self.root, f"build/{COMPARISON_DIRECTORY}", 2)
        with self.assertRaises(ValueError):
            prepare_run_directory(self.root, f"build/{COST_SUBSTEPS2_DIRECTORY}", 10)
        self.assertEqual([], list((self.root / "build").iterdir()))

    def test_creates_fresh_regression_target_and_preserves_original_approval(self):
        regression = prepare_run_directory(self.root,
                                            f"build/{REGRESSION_DIRECTORY}", 10)
        self.assertEqual("sub_level_substeps_per_tick = 10\n",
                         (regression / "config" / "sable-server.toml").read_text(encoding="utf-8"))
        self.assertIn(COMPARISON_DIRECTORY, APPROVED_DIRECTORIES)
        self.assertIn(REGRESSION_DIRECTORY, APPROVED_DIRECTORIES)

    def test_rejects_other_game_test_directory_names(self):
        with self.assertRaises(ValueError):
            prepare_run_directory(self.root,
                                  "build/gametest-shuttle-feedback-08-substeps10-regression-r3", 10)
        self.assertEqual([], list((self.root / "build").iterdir()))

    def test_approves_exact_fresh_targets_with_their_matched_counts(self):
        expected = {
            COMPARISON_DIRECTORY: 10,
            REGRESSION_DIRECTORY: 10,
            DIAGNOSTIC_DIRECTORY: 10,
            COST_SUBSTEPS2_DIRECTORY: 2,
            COST_SUBSTEPS10_DIRECTORY: 10,
            SHELL_SUBSTEPS10_DIRECTORY: 10,
            SIX_SUBSTEPS_TRIAL_DIRECTORY: 6,
            SIX_SUBSTEPS_REGRESSION_DIRECTORY: 6,
            COST_SUBSTEPS6_DIRECTORY: 6,
            SIX_SUBSTEPS_REGRESSION_R33_DIRECTORY: 6,
            SIX_SUBSTEPS_FAILURE_FOLLOWUP_R34_DIRECTORY: 6,
            SIX_SUBSTEPS_IDLE_PROBE_R35_DIRECTORY: 6,
            SIX_SUBSTEPS_REGRESSION_R36_DIRECTORY: 6,
            R37_COST_SUBSTEPS2_DIRECTORY: 2,
            R37_COST_SUBSTEPS6_DIRECTORY: 6,
            R38_FLIGHT_OFFLINE_UNLOAD_DIRECTORY: 2,
            R39_FLIGHT_OFFLINE_UNLOAD_DIRECTORY: 2,
            R40_CARGO_NATURAL_SAVE_DIRECTORY: 2,
            R41_CARGO_POSTJOIN_DIRECTORY: 2,
            R42_FRAME_ANCHOR_DIRECTORY: 2,
            R43_FRAME_ANCHOR_DIRECTORY: 2,
            R44_FRAME_ANCHOR_DIRECTORY: 2,
            R45_BODY_UNLOAD_ORDER_DIRECTORY: 2,
            R46_BODY_TICK_ADMISSION_DIRECTORY: 2,
            R47_FRAME_COMPOSITION_DIRECTORY: 2,
            R48_POST_RESTORE_MOTION_DIRECTORY: 2,
        }
        self.assertEqual(set(expected), APPROVED_DIRECTORIES)
        for directory, count in expected.items():
            with self.subTest(directory=directory):
                target = prepare_run_directory(self.root, f"build/{directory}", count)
                self.assertEqual(f"sub_level_substeps_per_tick = {count}\n",
                                 (target / "config" / "sable-server.toml").read_text(encoding="utf-8"))

    def test_rejects_unapproved_aliases_and_all_wrong_counts(self):
        for alias in (
                "gametest-shuttle-feedback-08-substeps10-r1",
                "gametest-shuttle-feedback-08-cost-substeps10-r1-extra"):
            with self.subTest(alias=alias), self.assertRaises(ValueError):
                prepare_run_directory(self.root, f"build/{alias}", 10)
        for directory, approved_count in (
                (COMPARISON_DIRECTORY, 10), (REGRESSION_DIRECTORY, 10),
                (DIAGNOSTIC_DIRECTORY, 10), (COST_SUBSTEPS2_DIRECTORY, 2),
                (COST_SUBSTEPS10_DIRECTORY, 10), (SHELL_SUBSTEPS10_DIRECTORY, 10),
                (SIX_SUBSTEPS_TRIAL_DIRECTORY, 6),
                (SIX_SUBSTEPS_REGRESSION_DIRECTORY, 6), (COST_SUBSTEPS6_DIRECTORY, 6),
                (SIX_SUBSTEPS_REGRESSION_R33_DIRECTORY, 6),
                (SIX_SUBSTEPS_FAILURE_FOLLOWUP_R34_DIRECTORY, 6),
                (SIX_SUBSTEPS_IDLE_PROBE_R35_DIRECTORY, 6),
                (SIX_SUBSTEPS_REGRESSION_R36_DIRECTORY, 6),
                (R37_COST_SUBSTEPS2_DIRECTORY, 2),
                (R37_COST_SUBSTEPS6_DIRECTORY, 6),
                (R38_FLIGHT_OFFLINE_UNLOAD_DIRECTORY, 2),
                (R39_FLIGHT_OFFLINE_UNLOAD_DIRECTORY, 2),
                (R40_CARGO_NATURAL_SAVE_DIRECTORY, 2),
                (R41_CARGO_POSTJOIN_DIRECTORY, 2),
                (R42_FRAME_ANCHOR_DIRECTORY, 2),
                (R43_FRAME_ANCHOR_DIRECTORY, 2),
                (R44_FRAME_ANCHOR_DIRECTORY, 2),
                (R45_BODY_UNLOAD_ORDER_DIRECTORY, 2),
                (R46_BODY_TICK_ADMISSION_DIRECTORY, 2),
                (R47_FRAME_COMPOSITION_DIRECTORY, 2),
                (R48_POST_RESTORE_MOTION_DIRECTORY, 2)):
            with self.subTest(directory=directory), self.assertRaises(ValueError):
                prepare_run_directory(self.root, f"build/{directory}", 2 if approved_count == 10 else 10)
        self.assertEqual([], list((self.root / "build").iterdir()))

    def test_existing_approved_targets_are_refused_without_changes(self):
        for directory, count in (
                (COMPARISON_DIRECTORY, 10), (REGRESSION_DIRECTORY, 10),
                (DIAGNOSTIC_DIRECTORY, 10), (COST_SUBSTEPS2_DIRECTORY, 2),
                (COST_SUBSTEPS10_DIRECTORY, 10), (SHELL_SUBSTEPS10_DIRECTORY, 10),
                (SIX_SUBSTEPS_TRIAL_DIRECTORY, 6),
                (SIX_SUBSTEPS_REGRESSION_DIRECTORY, 6), (COST_SUBSTEPS6_DIRECTORY, 6),
                (SIX_SUBSTEPS_REGRESSION_R33_DIRECTORY, 6),
                (SIX_SUBSTEPS_FAILURE_FOLLOWUP_R34_DIRECTORY, 6),
                (SIX_SUBSTEPS_IDLE_PROBE_R35_DIRECTORY, 6),
                (SIX_SUBSTEPS_REGRESSION_R36_DIRECTORY, 6),
                (R37_COST_SUBSTEPS2_DIRECTORY, 2),
                (R37_COST_SUBSTEPS6_DIRECTORY, 6),
                (R38_FLIGHT_OFFLINE_UNLOAD_DIRECTORY, 2),
                (R39_FLIGHT_OFFLINE_UNLOAD_DIRECTORY, 2),
                (R40_CARGO_NATURAL_SAVE_DIRECTORY, 2),
                (R41_CARGO_POSTJOIN_DIRECTORY, 2),
                (R42_FRAME_ANCHOR_DIRECTORY, 2),
                (R43_FRAME_ANCHOR_DIRECTORY, 2),
                (R44_FRAME_ANCHOR_DIRECTORY, 2),
                (R45_BODY_UNLOAD_ORDER_DIRECTORY, 2),
                (R46_BODY_TICK_ADMISSION_DIRECTORY, 2),
                (R47_FRAME_COMPOSITION_DIRECTORY, 2),
                (R48_POST_RESTORE_MOTION_DIRECTORY, 2)):
            with self.subTest(directory=directory):
                target = self.root / "build" / directory
                target.mkdir()
                sentinel = target / "owner-data.txt"
                sentinel.write_text("preserve", encoding="utf-8")
                with self.assertRaises(FileExistsError):
                    prepare_run_directory(self.root, f"build/{directory}", count)
                self.assertEqual("preserve", sentinel.read_text(encoding="utf-8"))
                sentinel.unlink()
                target.rmdir()

    def test_cli_accepts_six_and_refuses_mismatched_trial_count(self):
        from prepare_impact_substeps_comparison import main

        with patch("prepare_impact_substeps_comparison.prepare_run_directory") as prepare:
            with patch("sys.argv", ["tool", "--run-dir", f"build/{SIX_SUBSTEPS_TRIAL_DIRECTORY}", "--substeps", "6"]):
                self.assertEqual(0, main())
            prepare.assert_called_once()

        with self.assertRaises(ValueError):
            prepare_run_directory(self.root, f"build/{SIX_SUBSTEPS_TRIAL_DIRECTORY}", 2)
        with self.assertRaises(ValueError):
            prepare_run_directory(self.root, f"build/{SIX_SUBSTEPS_TRIAL_DIRECTORY}", 10)

    def test_prepares_both_fresh_cost_comparison_targets_with_matched_count(self):
        for directory, count in ((COST_SUBSTEPS2_DIRECTORY, 2),
                                 (COST_SUBSTEPS10_DIRECTORY, 10)):
            target = prepare_run_directory(self.root, f"build/{directory}", count)
            self.assertEqual(f"sub_level_substeps_per_tick = {count}\n",
                             (target / "config" / "sable-server.toml").read_text(encoding="utf-8"))
        self.assertEqual({"config"}, {p.name for p in (self.root / "build" / COST_SUBSTEPS2_DIRECTORY).iterdir()})

    def test_cost_targets_still_refuse_existing_and_unapproved_paths(self):
        target = self.root / "build" / COST_SUBSTEPS10_DIRECTORY
        target.mkdir()
        with self.assertRaises(FileExistsError):
            prepare_run_directory(self.root, f"build/{COST_SUBSTEPS10_DIRECTORY}", 10)
        with self.assertRaises(ValueError):
            prepare_run_directory(self.root, "build/gametest-shuttle-feedback-08-cost-substeps3-r1", 2)


if __name__ == "__main__":
    unittest.main()
