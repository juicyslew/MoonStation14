"""Create a current-source attribution packet for the intended R11 candidate."""
from __future__ import annotations

import difflib
import hashlib
import importlib.util
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
SHARED_TOOLS = Path(r"C:\Users\William\Documents\Minecraft Mod Dev Files\MoonStation14-shared\tooling\agent_workflow")
EXCHANGE = Path(r"C:\Users\William\Documents\Minecraft Mod Dev Files\Asset Building Workspace\work\agent_exchange\candidates")
OUT = ROOT / "docs/shuttle-relocation-sprint/scope-r11"
BASE = "620face76b8c1374019ce074380a451d698f05e1"
EXPECTED_HEAD = "5c09e426d055d18932e1896b865912d2716a8278"
INTENDED = "shuttle-feedback08-cargo-orientation-20261015-r11"
EXCLUDED = {
    "docs/shuttle-relocation-sprint/scope-r11/SUMMARY.md",
    "docs/shuttle-relocation-sprint/scope-r11/snapshot.json",
    "docs/shuttle-relocation-sprint/scope-r11/changed-source-vs-head.patch",
}
HISTORY = {
    "accepted_wall_r10": "shuttle-feedback07-side-y-20261014-r10",
    "r6": "shuttle-feedback08-cargo-orientation-20261015-r6",
    "r7": "shuttle-feedback08-cargo-orientation-20261015-r7",
    "r8": "shuttle-feedback08-cargo-orientation-20261015-r8",
    "r9": "shuttle-feedback08-cargo-orientation-20261015-r9",
    "cargo_r10": "shuttle-feedback08-cargo-orientation-20261015-r10",
}
R9_OUTCOME = {
    "lead_session": "ses_ec02df7efffehdPu2u2qwUSX6x",
    "lead_message": "msg_13fd5fb63001foydukTDeOVbPn",
    "decision": "needs_changes",
    "full_jvm": "3314 passed",
    "focused_native": "5 pass / 1 missing-hook failure of 6 expected out of 8895; clean shutdown",
    "full_native": "skipped after focused-hook failure",
}


def canonical_hash(value: object) -> str:
    raw = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def load_record(label: str) -> tuple[dict, str]:
    data = (EXCHANGE / f"{HISTORY[label]}.json").read_bytes()
    return json.loads(data), hashlib.sha256(data).hexdigest()


def classify(path: str, inherited: bool) -> str:
    p = path.replace("\\", "/").lower()
    name = p.rsplit("/", 1)[-1]
    if inherited:
        if p.startswith(("net/minecraft/", "net/neoforged/", "meta-inf/")):
            return "inherited-R10-extracted-source-or-jar-artifact"
        return "inherited-R10-workspace-or-prior-changes"
    if name == "lifecycleshuttlecargoreconnectgametests.java":
        return "R11-cargo-terminal-observation-fix"
    if p == "build.gradle":
        return "R10-GameTestServer-mixin-launch-only-config"
    if name in {
        "gametestprogressmixinbootstrap.java", "gametestprogressobserver.java",
        "gametestprogressstate.java", "gametesttickerprogressmixin.java",
        "gametestprogressstatetest.java", "gametestprogressgametests.java",
        "nativegametestprogressprobe.java", "moonstation14.gametest.mixins.json",
    }:
        return "R7-test-progress-tooling"
    if name in {
        "looseitementity.java", "modeventhooks.java", "examinetargetresolver.java",
        "examineworldselector.java", "examineclient.java", "selectedlooseoutlinerenderer.java",
        "bodyhanditemtransfer.java", "bodyitemtargetprobe.java", "bodyloosethrow.java",
        "nativehelditemtransfer.java", "looseitemquerygeometry.java",
        "looseitemqueryextenttest.java", "looseitemquerygeometrytest.java",
        "looseframeorientation.java", "looseframeorientationtest.java",
        "cargoexaminegeometrygametests.java", "cargopickupgeometrygametests.java",
        "cargoactordroporientationgametests.java", "shuttlecargosetupgametests.java",
        "shuttlefixturecommands.java",
    }:
        return "feedback08-cargo-orientation-setup"
    if name in {"movingcabinauthorityfixture.java", "sablefixturescene.java", "sableownedbodycarrygametests.java"}:
        return "feedback08-shared-fixture-extensions"
    if name in {"prepare_impact_substeps_comparison.cpython-313.pyc", "test_prepare_impact_substeps_comparison.cpython-313.pyc"}:
        return "preserved-prior-diagnostic-bytecode-artifact"
    if name in {
        "cargoreconnectwitnesshistory.java", "cargoreconnectwitnesshistorytest.java",
        "hullimpactsubstepsexpectation.java",
        "hullimpactsubstepsexpectationtest.java", "hullimpacttracestate.java",
        "hullimpacttracestatetest.java", "hullterraingeometry.java", "hullterraingeometrytest.java",
        "hullterrainimpactgametests.java", "hullterrainimpactprobe.java",
        "hullstatemismatchevidencetest.java", "servertickcostrecorder.java",
        "servertickcostrecordertest.java", "shuttleservercostgametests.java",
        "prepare_impact_substeps_comparison.py", "test_prepare_impact_substeps_comparison.py",
    }:
        return "prior-feedback08-hull-cost-reconnect-diagnostics"
    if p.startswith("docs/shuttle-relocation-sprint/scope-r") or p.startswith("docs/shuttle-relocation-sprint/tools/") or name.startswith("gate-plan-08-") or name == "owner-feedback-08.md":
        return "author-evidence-and-plan"
    if p.startswith("docs/") or p.startswith(".opencode/") or p in {"agents.md", "opencode.jsonc", ".gitignore"}:
        return "prior-feedback08-documentation"
    if p.startswith(("build.gradle", "gradle.properties", "src/gametest/resources/")):
        return "prior-feedback08-test-support"
    if p.startswith("tooling/"):
        return "prior-feedback08-tooling"
    if p.startswith("src/"):
        return "review-required-changed-source"
    return "prior-workspace-or-artifact"


def main() -> None:
    sys.path.insert(0, str(SHARED_TOOLS))
    spec = importlib.util.spec_from_file_location("shared_workflow_readonly", SHARED_TOOLS / "workflow.py")
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot import shared workflow helper")
    workflow = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = workflow
    spec.loader.exec_module(workflow)

    records = {label: load_record(label) for label in HISTORY}
    head = workflow.git(ROOT, "rev-parse", "HEAD").decode().strip()
    if head != EXPECTED_HEAD:
        raise RuntimeError(f"unexpected HEAD {head}; refusing cross-snapshot comparison")
    history_heads = {}
    manifests = {}
    for label, (record, _) in records.items():
        worktree = record.get("worktree") or {}
        recorded_head = worktree.get("head") or record.get("head_sha") or record.get("head")
        if record.get("base_sha") != BASE or record.get("base_ref") != BASE or recorded_head != head:
            raise RuntimeError(f"{label} base/HEAD mismatch; refusing historical comparison")
        history_heads[label] = recorded_head
        manifests[label] = {entry["path"]: entry for entry in record.get("dirty_changes", [])}

    base_sha, committed, dirty_all = workflow.git_changes(ROOT, BASE)
    if base_sha != BASE:
        raise RuntimeError(f"shared helper resolved unexpected base {base_sha}")
    dirty = [row for row in dirty_all if row["path"] not in EXCLUDED]
    dirty.sort(key=lambda row: row["path"])
    current_paths = {row["path"] for row in dirty}
    rows = []
    for entry in dirty:
        path = entry["path"]
        file = ROOT / path
        present = file.exists()
        is_file = file.is_file()
        live_hash = workflow.sha(file) if is_file else None
        matches_live = ((not present and entry["sha256"] is None) or (is_file and live_hash == entry["sha256"]))
        comparisons = {}
        for label in HISTORY:
            old = manifests[label].get(path)
            comparisons[label] = {
                "candidate_id": HISTORY[label],
                "candidate_snapshot_key": records[label][0].get("snapshot_key"),
                "candidate_record_sha256": records[label][1],
                "status": old.get("status") if old else None,
                "sha256": old.get("sha256") if old else None,
                "status_and_hash_match": bool(old and old.get("status") == entry["status"] and old.get("sha256") == entry["sha256"]),
                "manifest_entry": old is not None,
            }
        head_preimage_hash = None
        if all(not manifests[label].get(path) for label in HISTORY):
            try:
                head_preimage_hash = hashlib.sha256(workflow.git(ROOT, "show", f"{head}:{path}")).hexdigest()
            except Exception:
                head_preimage_hash = None
        r10_match = comparisons["accepted_wall_r10"]["status_and_hash_match"]
        category = classify(path, r10_match)
        rows.append({
            "path": path, "status": entry["status"], "sha256": entry["sha256"],
            "present": present, "is_file": is_file, "live_sha256": live_hash,
            "live_matches_manifest": matches_live, "category": category,
            "comparison_history": comparisons, "head_preimage_sha256_if_unlisted": head_preimage_hash,
        })

    removed_prior_paths = []
    for path, old in sorted(manifests["cargo_r10"].items()):
        file = ROOT / path
        if path not in current_paths and not file.exists():
            head_preimage_hash = None
            try:
                head_preimage_hash = hashlib.sha256(workflow.git(ROOT, "show", f"{head}:{path}")).hexdigest()
            except Exception:
                pass
            removed_prior_paths.append({
                "path": path, "r10_prior_status": old.get("status"), "r10_prior_sha256": old.get("sha256"),
                "current_manifest_entry": False, "current_filesystem_present": False,
                "current_git_status": "no entry; not represented as a tracked deletion",
                "head_preimage_sha256": head_preimage_hash,
            })

    source_digest = canonical_hash(rows)
    committed.sort(key=lambda row: (row.get("path", ""), row.get("old_path", ""), row.get("status", "")))
    committed_digest = canonical_hash(committed)
    OUT.mkdir(parents=True, exist_ok=True)
    patch_rows = [row for row in rows if row["category"] in {
        "feedback08-cargo-orientation-setup", "R7-test-progress-tooling",
        "feedback08-shared-fixture-extensions", "prior-feedback08-hull-cost-reconnect-diagnostics",
        "prior-feedback08-test-support", "R10-GameTestServer-mixin-launch-only-config",
        "R11-cargo-terminal-observation-fix",
    }]
    patch_lines = [
        "# Current scoped source patch for intended cargo-orientation R11\n\n",
        "Current HEAD-relative diff of explicitly classified R11 fixture, cargo/setup, progress, shared-fixture, prior-diagnostic and support files. R10 preimages are unavailable; this diff may include older lines and is not an exact R10 delta. Binary paths are named but never decoded. Verify source hashes in snapshot.json.\n\n",
    ]
    text_ext = {".java", ".json", ".gradle", ".properties", ".toml", ".md", ".py", ".ps1", ".jsonc"}
    for row in patch_rows:
        path = row["path"]
        file = ROOT / path
        if file.suffix.lower() not in text_ext or (file.is_file() and file.stat().st_size > 256 * 1024):
            patch_lines.append(f"\n# BINARY/UNSUPPORTED/OVERSIZE: source path + SHA-256 in snapshot.json: {path}\n")
            continue
        if workflow.git(ROOT, "ls-files", "--", path).strip():
            patch_lines.append(workflow.git(ROOT, "diff", "--no-ext-diff", "--no-color", head, "--", path).decode("utf-8", "strict"))
        elif file.is_file():
            lines = file.read_text(encoding="utf-8").splitlines(keepends=True)
            patch_lines.extend(difflib.unified_diff([], lines, fromfile="/dev/null", tofile=path))
    patch_text = "".join(patch_lines)
    for removed in removed_prior_paths:
        patch_text += (f"\n# Removed since cargo R10; absent from current dirty manifest and filesystem (not a tracked D): {removed['path']}\n"
                       f"# R10 prior status={removed['r10_prior_status']}; SHA-256={removed['r10_prior_sha256']}; HEAD preimage SHA-256={removed['head_preimage_sha256']}\n")
    patch_path = "docs/shuttle-relocation-sprint/scope-r11/changed-source-vs-head.patch"

    groups = {}
    for row in rows:
        groups[row["category"]] = groups.get(row["category"], 0) + 1
    mismatches = [row["path"] for row in rows if not row["live_matches_manifest"]]
    review_required = [row["path"] for row in rows if row["category"] == "review-required-changed-source"]
    retained_code_categories = {
        "feedback08-cargo-orientation-setup", "R7-test-progress-tooling",
        "feedback08-shared-fixture-extensions", "prior-feedback08-hull-cost-reconnect-diagnostics",
        "prior-feedback08-test-support", "R10-GameTestServer-mixin-launch-only-config",
    }
    retained_code = [row for row in rows if row["category"] in retained_code_categories]
    retained_code_r10_matches = [row for row in retained_code if row["comparison_history"]["cargo_r10"]["status_and_hash_match"]]
    retained_code_r10_mismatches = [row["path"] for row in retained_code if not row["comparison_history"]["cargo_r10"]["status_and_hash_match"]]
    history_metadata = {
        label: {"candidate_id": HISTORY[label], "snapshot_key": records[label][0].get("snapshot_key"),
                "candidate_record_sha256": records[label][1], "head": history_heads[label]}
        for label in HISTORY
    }
    packet = {
        "candidate_id": INTENDED,
        "candidate_stage": "intended-next-submission; no R11 candidate record or readiness receipt created",
        "current_head": head,
        "immutable_base": base_sha,
        "source_manifest_digest": source_digest,
        "source_manifest_digest_algorithm": "SHA-256(canonical JSON of sorted current dirty rows excluding exactly the three self-referential generated output paths)",
        "current_dirty_count_including_self_exclusions": len(dirty_all),
        "current_source_manifest_count": len(rows),
        "self_reference_exclusions": [{"path": path, "reason": "generated packet output would cause a self-hash cycle; ordinary R11 workflow submission binds this output"} for path in sorted(EXCLUDED)],
        "committed_changes_from_base": {"count": len(committed), "canonical_sha256": committed_digest, "rows": committed},
        "comparison_history": history_metadata,
        "source_manifest": rows,
        "removed_prior_paths": removed_prior_paths,
        "category_counts": groups,
        "retained_code_comparison_to_cargo_r10": {"count": len(retained_code), "status_and_hash_matches": len(retained_code_r10_matches), "mismatches": retained_code_r10_mismatches},
        "live_mismatches": mismatches,
        "review_required_changed_source": review_required,
        "readiness": {
            "status": "NOT_READY; R10 needs_changes; R11 exact plan checks not run by author",
            "prior_receipts_carry_forward": False,
            "gate_logs": "No R11 readiness logs created by this author tooling; integration lead runs exact R11 plan checks on fresh candidate.",
            "native_hook_probe": "R10 focused probe passed historically; fresh R11 execution still required",
            "known_failures": "known 40/80 failures remain unresolved evidence; review explicitly",
        },
        "snapshot_binding": "This pre-submission evidence is bound by source_manifest_digest. Normal R11 candidate submission will hash/bind the final three generated outputs; do not edit them after submission.",
        "lead_identity_clarification": {"session_id": "ses_ec032fb96ffeonAMbBgOxiuOrf", "message_id": "msg_13fce66a6001hz62bsAawrkkyO", "text": "Current-source manifest and independent digest, bound as normal R11 submission evidence then covered by submitted candidate snapshot, is achievable non-circular identity. Packet need not embed its own final snapshot key; do not edit post-submit."},
        "r10_prior_outcome": {"full_jvm": "3314 passed", "focused_native": "6/889 passed", "full_native": "timed out; historical result, not R11 readiness", "full_native_progress": "749 admitted/finished, 2 hull failures, 140 unadmitted; last cargo result DONE_FALLBACK_PASS rather than ordinary native PASS; cause unproven"},
        "r9_attribution_clarification": {"session_id": "ses_ec02df7efffehdPu2u2qwUSX6x", "message_id": "msg_13fd5fb63001foydukTDeOVbPn", "text": "Exact wall-R10 dirty preimage line delta is not required; complete candidate/current source manifest provides attribution, and HEAD diff is convenience only."},
        "patch": {"path": patch_path, "scoped_path_count": len(patch_rows), "utf8_bytes": len(patch_text.encode("utf-8")), "r10_preimage_limitation": True},
        "scope_note": "Current-source manifest is the attribution authority for intended R11. Historical accepted-wall-R10/R6/R7/R8/R9/cargo-R10 hashes and statuses remain nested comparison-only, never top-level candidate identity or readiness.",
    }
    (OUT / "snapshot.json").write_text(json.dumps(packet, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (OUT / "changed-source-vs-head.patch").write_text(patch_text, encoding="utf-8")

    lines = [
        "# Intended cargo-orientation R11 current-source attribution — NOT A READINESS RECEIPT\n\n",
        f"Intended candidate: `{INTENDED}`. Current HEAD `{head}`; immutable base `{BASE}`. This packet is not a workflow candidate or pass.\n\n",
        f"Current dirty paths from shared `git_changes(repo, base)` helper: {len(dirty_all)} total, {len(rows)} source-manifest rows after exactly three output exclusions. Digest: `{source_digest}`. Committed base-to-HEAD rows: {len(committed)} (digest `{committed_digest}`); HEAD remains the recorded historical HEAD.\n\n",
        f"Explicit category counts: `{json.dumps(groups, sort_keys=True)}`. Live mismatches: {len(mismatches)}. Unclassified changed source paths: {len(review_required)}. Scoped patch `{patch_path}`: {len(patch_rows)} paths, {len(patch_text.encode('utf-8'))} bytes. Retained code matches cargo R10 by status+hash: {len(retained_code_r10_matches)}/{len(retained_code)}; mismatches: {len(retained_code_r10_mismatches)}. Removed paths since cargo R10: {len(removed_prior_paths)}. Full source hashes/statuses and nested historical proof are in `snapshot.json`.\n\n",
        "R11 source change is limited to `LifecycleShuttleCargoReconnectGameTests.java`: a `CargoJournal` terminal-scheduled latch defers failure through `safeDelay`/existing `succeedAfterObservation`; existing observations, assertions, and 160-offline/60-post observations are preserved. Other current code/build/mixin/probe paths match cargo R10 by status+hash. Matching proves source identity, not correctness.\n\n",
        "R10 historical result: full JVM 3314 passed; focused native 6/889 passed; full 889 native timed out after 749 admitted/finished, two hull failures, and 140 unadmitted. Last cargo result was DONE_FALLBACK_PASS rather than ordinary native PASS; cause remains unproven until a batch advances. These results are not R11 readiness. The bootstrap had already been removed by R10; no new R10-to-R11 removed prior source is reported.\n\n",
        "The generated SUMMARY, snapshot JSON and patch are the only three exact self-reference exclusions. Generator, R11 plan, older plans, prior packets and other current dirty files remain in the manifest for normal submission binding. The ignored R1 cargo terminal-phase author log is not source/gate evidence in the shared non-ignored dirty-path manifest. Do not edit outputs after R11 submission.\n\n",
        "R9 lead clarification (session `ses_ec02df7efffehdPu2u2qwUSX6x`, message `msg_13fd5fb63001foydukTDeOVbPn`): exact wall-R10 dirty preimage line delta is not required; current full manifest and HEAD patch are adequate attribution. This is not a new approval. R10 passes do not carry forward to R11.\n\n",
        "R11 remains uncompiled and unrun by design. The lead runs the unchanged R11 plan: full JVM, focused 7/889 (including reconnect ordinary-listener PASS, not DONE_FALLBACK), then full 889 with admission/aggregate checked; genuine 40/80 hull failures remain failures. The full-run root cause remains a hypothesis pending observed batch progress. No logs are fabricated here.\n\n",
        "## Current scoped source list\n\n| Category | Current change | Cargo R10 status+hash | Path |\n|---|---|---|---|\n",
    ]
    for row in patch_rows:
        lines.append(f"| {row['category']} | current dirty | {'same' if row['comparison_history']['cargo_r10']['status_and_hash_match'] else 'differs'} | `{row['path']}` |\n")
    lines.append("\n## Removed prior cargo R10 paths\n\n| Path | R10 prior status/hash | Current manifest | Filesystem | HEAD preimage |\n|---|---|---|---|---|\n")
    for item in removed_prior_paths:
        lines.append(f"| `{item['path']}` | `{item['r10_prior_status']}` / `{item['r10_prior_sha256']}` | absent | absent | `{item['head_preimage_sha256']}` |\n")
    lines.extend(["\n## Complete current dirty-path index\n\n| Category | Git status | Wall R10 | R6 | R7 | R8 | R9 | Cargo R10 | Live match | Path |\n|---|---|---|---|---|---|---|---|---:|---|\n"])
    for row in rows:
        hist = row["comparison_history"]
        markers = ["same" if hist[k]["status_and_hash_match"] else ("other" if hist[k]["manifest_entry"] else "absent") for k in ("accepted_wall_r10", "r6", "r7", "r8", "r9", "cargo_r10")]
        lines.append(f"| {row['category']} | `{row['status']}` | {markers[0]} | {markers[1]} | {markers[2]} | {markers[3]} | {markers[4]} | {markers[5]} | {'yes' if row['live_matches_manifest'] else '**NO**'} | `{row['path']}` |\n")
    if review_required:
        lines.append("\nChanged source paths not in explicit sets require human scope review; they are not silently assigned to cargo implementation.\n")
    (OUT / "SUMMARY.md").write_text("".join(lines), encoding="utf-8")
    print(json.dumps({
        "candidate": INTENDED, "head": head, "source_manifest_count": len(rows),
        "dirty_count_with_exclusions": len(dirty_all), "source_manifest_digest": source_digest,
        "self_reference_exclusions": sorted(EXCLUDED), "category_counts": groups,
        "scoped_patch_paths": len(patch_rows), "scoped_patch_bytes": len(patch_text.encode("utf-8")),
        "changed_R11_fixture_paths": [row["path"] for row in rows if row["category"] == "R11-cargo-terminal-observation-fix"],
        "retained_code_cargo_R10_matches": len(retained_code_r10_matches),
        "retained_code_cargo_R10_count": len(retained_code),
        "retained_code_cargo_R10_mismatches": retained_code_r10_mismatches,
        "removed_prior_paths_since_cargo_R10": removed_prior_paths,
        "live_mismatches": mismatches, "review_required_changed_source": review_required,
    }, indent=2))


if __name__ == "__main__":
    main()
