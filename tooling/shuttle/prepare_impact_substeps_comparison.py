#!/usr/bin/env python3
"""Prepare approved fresh isolated substep GameTest directories; never launch or copy run data."""

from __future__ import annotations

import argparse
from pathlib import Path

COMPARISON_DIRECTORY = "gametest-shuttle-feedback-08-impact-substeps10-r1"
REGRESSION_DIRECTORY = "gametest-shuttle-feedback-08-substeps10-regression-r1"
DIAGNOSTIC_DIRECTORY = "gametest-shuttle-feedback-08-substeps10-regression-r2"
COST_SUBSTEPS2_DIRECTORY = "gametest-shuttle-feedback-08-cost-substeps2-r1"
COST_SUBSTEPS10_DIRECTORY = "gametest-shuttle-feedback-08-cost-substeps10-r1"
SHELL_SUBSTEPS10_DIRECTORY = "gametest-shuttle-feedback-08-shell-placement-substeps10-r17"
SIX_SUBSTEPS_TRIAL_DIRECTORY = "gametest-shuttle-feedback-08-impact-substeps6-r31"
SIX_SUBSTEPS_REGRESSION_DIRECTORY = "gametest-shuttle-feedback-08-substeps6-regression-r32"
COST_SUBSTEPS6_DIRECTORY = "gametest-shuttle-feedback-08-cost-substeps6-r32"
SIX_SUBSTEPS_REGRESSION_R33_DIRECTORY = "gametest-shuttle-feedback-08-substeps6-regression-r33"
SIX_SUBSTEPS_FAILURE_FOLLOWUP_R34_DIRECTORY = "gametest-shuttle-feedback-08-substeps6-failure-followup-r34"
SIX_SUBSTEPS_IDLE_PROBE_R35_DIRECTORY = "gametest-shuttle-feedback-08-substeps6-idle-probe-r35"
SIX_SUBSTEPS_REGRESSION_R36_DIRECTORY = "gametest-shuttle-feedback-08-substeps6-regression-r36"
R37_COST_SUBSTEPS2_DIRECTORY = "gametest-shuttle-feedback-08-cost-substeps2-r37"
R37_COST_SUBSTEPS6_DIRECTORY = "gametest-shuttle-feedback-08-cost-substeps6-r37"
R38_FLIGHT_OFFLINE_UNLOAD_DIRECTORY = "gametest-shuttle-feedback-08-flight-offline-unload-r38"
R39_FLIGHT_OFFLINE_UNLOAD_DIRECTORY = "gametest-shuttle-feedback-08-flight-offline-unload-r39"
R40_CARGO_NATURAL_SAVE_DIRECTORY = "gametest-shuttle-feedback-08-cargo-natural-save-r40"
R41_CARGO_POSTJOIN_DIRECTORY = "gametest-shuttle-feedback-08-cargo-postjoin-r41"
R42_FRAME_ANCHOR_DIRECTORY = "gametest-shuttle-feedback-08-frame-anchor-r42"
R43_FRAME_ANCHOR_DIRECTORY = "gametest-shuttle-feedback-08-frame-anchor-r43"
R44_FRAME_ANCHOR_DIRECTORY = "gametest-shuttle-feedback-08-frame-anchor-r44"
R45_BODY_UNLOAD_ORDER_DIRECTORY = "gametest-shuttle-feedback-08-body-unload-order-r45"
R46_BODY_TICK_ADMISSION_DIRECTORY = "gametest-shuttle-feedback-08-body-tick-admission-r46"
R47_FRAME_COMPOSITION_DIRECTORY = "gametest-shuttle-feedback-08-frame-composition-r47"
R48_POST_RESTORE_MOTION_DIRECTORY = "gametest-shuttle-feedback-08-post-restore-motion-r48"
APPROVED_SUBSTEPS = {COMPARISON_DIRECTORY: 10, REGRESSION_DIRECTORY: 10,
                     DIAGNOSTIC_DIRECTORY: 10, COST_SUBSTEPS2_DIRECTORY: 2,
                     COST_SUBSTEPS10_DIRECTORY: 10, SHELL_SUBSTEPS10_DIRECTORY: 10,
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
                         R48_POST_RESTORE_MOTION_DIRECTORY: 2}
APPROVED_DIRECTORIES = frozenset(APPROVED_SUBSTEPS)


def prepare_run_directory(repository_root: Path, requested_directory: str | Path,
                          substeps: int) -> Path:
    root = repository_root.resolve()
    build_dir = (root / "build").resolve()
    if not build_dir.is_dir():
        raise ValueError("repository build directory must already exist")
    requested = Path(requested_directory)
    target = (root / requested).resolve() if not requested.is_absolute() else requested.resolve()
    if target.parent != build_dir or target.name not in APPROVED_DIRECTORIES:
        raise ValueError(f"refusing non-approved run directory: {target}")
    if APPROVED_SUBSTEPS[target.name] != substeps:
        raise ValueError(f"approved directory {target.name} requires {APPROVED_SUBSTEPS[target.name]} substeps")
    if target.exists() or target.is_symlink():
        raise FileExistsError(f"refusing to reuse existing run directory: {target}")

    target.mkdir(exist_ok=False)
    config_dir = target / "config"
    config_dir.mkdir(exist_ok=False)
    config_file = config_dir / "sable-server.toml"
    with config_file.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(f"sub_level_substeps_per_tick = {substeps}\n")
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", required=True,
                        help="must be build/ one of: " + ", ".join(sorted(APPROVED_DIRECTORIES)))
    parser.add_argument("--substeps", required=True, type=int, choices=(2, 6, 10))
    args = parser.parse_args()
    repository_root = Path(__file__).resolve().parents[2]
    prepared = prepare_run_directory(repository_root, args.run_dir, args.substeps)
    print(f"Prepared new isolated GameTest directory: {prepared}")
    print("Wrote config/sable-server.toml only; no run data copied and no server launched.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
