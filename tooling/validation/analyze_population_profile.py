"""Summarize JFR JSON exports for population profiling (no JFR/JVM invocation).

Protocol: server and client are exported with ``jfr print --json`` and contain
ms14.ServerTickDuration (phase, animalCount, gameTick),
ms14.ClientFrameDuration (intervalNanos), and ms14.PopulationPhase markers
(scenario, phase, animalCount). Marker startTime values use the same-host UTC
clock. Markers identify baseline, population, and complete; population is
split at its time midpoint.
Expected animals: parrots=100, kobolds=100, mixed=40; baseline=0.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import math
import re
import statistics
import sys
from collections import Counter
from pathlib import Path

EXPECTED = {"parrots": 100, "kobolds": 100, "mixed": 40}
PHASES = ("baseline", "early", "late")
SUSTAINED_PHASES = ("population_idle", "population_move", "population_jump", "population_away", "population_return")
FLYER_TOLERANCE_SECONDS = .350


def flyer_pose(server, client, minimum=1):
    def valid_num(x): return isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x)
    def valid_int(x): return isinstance(x, int) and not isinstance(x, bool)
    def valid_bool(x): return isinstance(x, bool)
    def reasons(v, kind):
        required = ({"entityY":valid_num,"expectedFixtureFloorY":valid_num,"gameTick":valid_int,"onGround":valid_bool,"poweredFlight":valid_bool,"flightState":lambda x:isinstance(x,str) and bool(x)} if kind=="Server" else
                    {"entityY":valid_num,"renderY":valid_num,"modelBottomPixels":valid_num,"presentationScale":valid_num,"estimatedMeshBottomY":valid_num,"gameTick":valid_int,"onGround":valid_bool,"poweredFlight":valid_bool,"resolvedFlightCapability":valid_bool,"hasVisibleGeometry":valid_bool,"sourceClip":lambda x:isinstance(x,str),"destinationClip":lambda x:isinstance(x,str),"blendWeight":valid_num,"flightState":lambda x:isinstance(x,str) and bool(x)})
        bad=[k for k,check in required.items() if not check(v.get(k))]
        if kind=="Client" and v.get("hasVisibleGeometry") is not True: bad.append("hasVisibleGeometry")
        return bad
    def rows(events, kind):
        result = []
        for e in events:
            if event_type(e) != f"ms14.{kind}FlyerPose": continue
            v = values(e); t = timestamp(e)
            if t is None: continue
            key = tuple(v.get(k) for k in ("entityId", "dimension", "hostEntityType"))
            if not all(isinstance(x, str) and x for x in key): continue
            bad=reasons(v,kind)
            result.append((t, key, v, bad))
        return result
    sr, cr = rows(server, "Server"), rows(client, "Client")
    pairs, used = [], set()
    rejectedServer=sum(bool(x[3]) for x in sr); rejectedClient=sum(bool(x[3]) for x in cr)
    sr=sorted(sr,key=lambda x:x[0]); cr=sorted(cr,key=lambda x:x[0])
    for st, sk, sv, sbad in sr:
        if sbad: continue
        candidates = [(abs(ct-st), i, ct, cv) for i, (ct, ck, cv, cbad) in enumerate(cr)
                      if not cbad and i not in used and ck == sk and abs(ct-st) <= FLYER_TOLERANCE_SECONDS]
        if not candidates: continue
        drift, i, ct, cv = min(candidates)
        used.add(i)
        pairs.append((sk, st, ct, sv, cv, drift))
    entities = {}
    for key, st, ct, sv, cv, drift in pairs:
        name = key[0]; row = entities.setdefault(name, {"entityId":key[0],"dimension":key[1],"hostEntityType":key[2],"pairCount": 0, "pairs": [], "serverClearance": [], "estimatedMeshClearance": [], "yDifference": [], "rawFlightStateCounts": Counter(), "serverPoweredCount":0,"clientPoweredCount":0,"clientCapabilityFalseCount": 0, "clipBlendCount":0,"stateMismatchCount":0, "floorSkimmingCount": 0})
        row["pairCount"] += 1
        floor = sv.get("expectedFixtureFloorY"); sy=sv.get("entityY"); cy=cv.get("entityY"); mesh=cv.get("estimatedMeshBottomY")
        valid=lambda x: isinstance(x,(int,float)) and not isinstance(x,bool) and math.isfinite(x)
        if valid(floor) and valid(sy): row["serverClearance"].append(sy-floor)
        if valid(floor) and valid(mesh): row["estimatedMeshClearance"].append(mesh-floor)
        if valid(sy) and valid(cy): row["yDifference"].append(cy-sy)
        state=sv.get("flightState"); row["rawFlightStateCounts"][str(state) if state is not None else "UNAVAILABLE"] += 1
        row["serverPoweredCount"] += sv.get("poweredFlight") is True
        row["clientPoweredCount"] += cv.get("poweredFlight") is True
        row["clientCapabilityFalseCount"] += cv.get("resolvedFlightCapability") is False
        row["clipBlendCount"] += cv.get("sourceClip") != cv.get("destinationClip")
        row["stateMismatchCount"] += sv.get("flightState") != cv.get("flightState")
        row["floorSkimmingCount"] += valid(floor) and valid(sy) and abs(sy-floor) <= .1
        row["pairs"].append({"tickDelta": cv["gameTick"]-sv["gameTick"], "timeDeltaMs": round((ct-st)*1000,3), "serverClearance": sy-floor, "clientLogicalClearance":cy-floor,"estimatedMeshClearance": mesh-floor, "yDifference": cy-sy, "flightState": state, "clientFlightState":cv.get("flightState"), "clientCapability":cv.get("resolvedFlightCapability"), "sourceClip":cv.get("sourceClip"), "destinationClip":cv.get("destinationClip")})
    for row in entities.values():
        for field in ("serverClearance", "estimatedMeshClearance", "yDifference"):
            data=row[field]; row[field]={"status":"RECORDED" if data else "UNAVAILABLE/INCOMPLETE", "distribution":distribution(data) if data else None}
        row["rawFlightStateCounts"]=dict(row["rawFlightStateCounts"]); pairs0=row.pop("pairs"); row["worstPairs"]=sorted(pairs0,key=lambda p:abs(p["yDifference"]),reverse=True)[:5]
    return {"status":"RECORDED" if len(pairs)>=minimum and entities else "UNAVAILABLE/INCOMPLETE", "pairedRowCount":len(pairs), "matchedUUIDCount":len(entities), "entities":entities, "rejectedServerRows":rejectedServer,"rejectedClientRows":rejectedClient,"rejectionReasons":{"server":"missing/invalid required schema field","client":"missing/invalid required schema field or invisible geometry"}, "unpairedServerRows":len(sr)-len(pairs), "unpairedClientRows":len(cr)-len(pairs), "matching":"same entityId+dimension+hostEntityType; nearest same-host JFR UTC timestamp within 350ms; not exact-same-tick proof", "meshBottomNote":"estimatedMeshBottomY is an estimate, not camera-matrix capture or visual proof."}


def load_events(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    events = data.get("recording", {}).get("events")
    if not isinstance(events, list):
        raise ValueError(f"{path}: expected recording.events array")
    return [e for e in events if isinstance(e, dict)]


def values(event: dict) -> dict:
    result = event.get("values", {})
    return result if isinstance(result, dict) else {}


def event_type(event: dict) -> str:
    return str(event.get("type", ""))


def timestamp(event: dict) -> float | None:
    raw = event.get("startTime") or values(event).get("startTime")
    if not isinstance(raw, str):
        return None
    try:
        result = dt.datetime.fromisoformat(raw.replace("Z", "+00:00")).timestamp()
        return result if math.isfinite(result) else None
    except ValueError:
        return None


def duration_seconds(raw) -> float | None:
    if isinstance(raw, (int, float)):
        return float(raw) / 1e9
    if not isinstance(raw, str):
        return None
    # ISO-8601 duration emitted by jfr print --json (e.g. PT1.5MS).
    match = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?(?:(\d+(?:\.\d+)?)(MS|US|NS))?", raw.upper())
    if not match:
        return None
    h, m, s, sub, unit = match.groups()
    scale = {"MS": 1e-3, "US": 1e-6, "NS": 1e-9}
    return float(h or 0) * 3600 + float(m or 0) * 60 + float(s or 0) + float(sub or 0) * scale.get(unit, 1)


def distribution(data: list[float]) -> dict:
    ordered = sorted(data)
    def percentile(p: float) -> float:
        index = (len(ordered) - 1) * p
        lo = math.floor(index)
        hi = math.ceil(index)
        return ordered[lo] + (ordered[hi] - ordered[lo]) * (index - lo)
    return {"count": len(ordered), "p50": percentile(.5), "p95": percentile(.95),
            "p99": percentile(.99), "max": ordered[-1], "mean": statistics.fmean(ordered)}


def analyze(server: list[dict], client: list[dict], scenario: str, minimum: int = 1, protocol: str = "stationary", require_flyer_pose: bool = False) -> dict:
    if protocol == "sustained": report=analyze_sustained(server, client, scenario, minimum)
    elif protocol != "stationary": return {"status": "INCOMPLETE", "protocol": protocol, "scenario": scenario, "issues": [f"unknown protocol: {protocol}"]}
    else: report=analyze_stationary(server, client, scenario, minimum)
    fp=flyer_pose(server, client, minimum)
    report["flyer_pose"]=fp
    if require_flyer_pose and (fp["pairedRowCount"] < minimum or fp["matchedUUIDCount"] < 1):
        report["status"]="UNVERIFIED"
        report.setdefault("issues", []).append("required flyer-pose evidence unavailable or below minimum paired rows")
    return report


def analyze_sustained(server: list[dict], client: list[dict], scenario: str, minimum: int = 1) -> dict:
    """Validate operator-driven phase evidence; this is coverage, not performance acceptance."""
    issues = []
    expected = EXPECTED.get(scenario)
    if expected is None:
        return {"status": "INCOMPLETE", "protocol": "sustained", "scenario": scenario, "issues": [f"unknown scenario: {scenario}"]}
    boundaries = {}
    phase_metrics = {}
    marker_issues = []
    # Phase markers are server-authoritative. The client emits frame samples,
    # not phase markers; use the server's host timestamps for both recordings.
    role_boundaries = {}
    for event in server:
        if event_type(event) != "ms14.PopulationPhase": continue
        v = values(event); phase = str(v.get("phase", "")).lower(); when = timestamp(event)
        if v.get("scenario") != scenario or phase not in SUSTAINED_PHASES: continue
        ticks = v.get("phaseTicks")
        if when is None or isinstance(ticks, bool) or not isinstance(ticks, int):
            marker_issues.append(f"server {phase}: missing timestamp or integer phaseTicks"); continue
        if ticks == 0:
            if phase in role_boundaries and "start" in role_boundaries[phase]: marker_issues.append(f"server {phase}: duplicate start boundary")
            role_boundaries.setdefault(phase, {})["start"] = when
        elif ticks >= 1200:
            if phase in role_boundaries and "end" in role_boundaries[phase]: marker_issues.append(f"server {phase}: duplicate end boundary")
            role_boundaries.setdefault(phase, {})["end"] = when
            phase_metrics[phase] = v
            count = v.get("animalCount")
            if isinstance(count, bool) or not isinstance(count, int) or count != expected:
                marker_issues.append(f"server {phase}: animalCount {count!r} != {expected}")
        else: marker_issues.append(f"server {phase}: phaseTicks {ticks} below 1200")
    for phase in SUSTAINED_PHASES:
        pair = role_boundaries.get(phase, {})
        if "start" not in pair or "end" not in pair:
            marker_issues.append(f"server missing {phase} {'start' if 'start' not in pair else 'end'} boundary")
        elif pair["end"] <= pair["start"]: marker_issues.append(f"server {phase}: invalid boundary ordering")
    complete = [role_boundaries.get(p, {}) for p in SUSTAINED_PHASES]
    for previous, current in zip(complete, complete[1:]):
        if "end" in previous and "start" in current and current["start"] < previous["end"]:
            marker_issues.append("server phase boundary overlap/order invalid")
    boundaries["server"] = role_boundaries
    issues.extend(marker_issues)
    requirements = {
        "population_idle": {"nearBodySamples": (">=", 1000), "acceptedInputSamples": (">", 0), "acceptedHorizontalDistance": ("<=", 2)},
        "population_move": {"acceptedHorizontalDistance": (">=", 16), "nearBodySamples": (">=", 100)},
        "population_jump": {"acceptedGroundedJumpRequests": (">=", 3), "positiveAscentSamples": (">", 0), "nearBodySamples": (">=", 100)},
        "population_away": {"farBodySamples": (">=", 100)},
        "population_return": {"nearBodySamples": (">=", 100)},
    }
    metrics = {}
    for phase, checks in requirements.items():
        v = phase_metrics.get(phase, {}); metrics[phase] = {}
        for field, (op, limit) in checks.items():
            n = v.get(field); valid = isinstance(n, (int, float)) and not isinstance(n, bool) and math.isfinite(n)
            if field.endswith("Samples") or field == "acceptedGroundedJumpRequests": valid = valid and isinstance(n, int)
            passed = valid and {">=": n >= limit, ">": n > limit, "<=": n <= limit}[op]
            metrics[phase][field] = {"value": n if valid else None, "requirement": f"{op}{limit}", "status": "RECORDED" if passed else "INCOMPLETE"}
            if not passed: issues.append(f"{phase} {field}: missing or fails {op}{limit}")
    phase_frames = {}
    for phase in SUSTAINED_PHASES:
        phase_frames[phase] = {}
        for role, events in (("server", server), ("client", client)):
            pair = boundaries["server"].get(phase, {}); samples = []
            for e in events:
                t = timestamp(e)
                if t is None or not (pair.get("start", float("inf")) <= t < pair.get("end", float("-inf"))): continue
                if role == "client" and event_type(e) == "ms14.ClientFrameDuration":
                    value = values(e).get("intervalNanos")
                    if isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and value >= 0: samples.append(float(value) / 1e6)
                if role == "server" and event_type(e) == "ms14.ServerTickDuration":
                    value = duration_seconds(e.get("duration", values(e).get("duration")))
                    if value is not None and math.isfinite(value) and value >= 0: samples.append(value * 1000)
            phase_frames[phase][role] = {"count": len(samples), "intervalMs": distribution(samples) if samples else None}
            if len(samples) < minimum: issues.append(f"{phase} insufficient {role} time samples: {len(samples)} < {minimum}")
    return {"status": "INCOMPLETE" if issues else "CAPTURE_COVERAGE_RECORDED", "protocol": "sustained", "scenario": scenario,
        "phases": phase_frames, "motionCoverage": metrics, "issues": issues,
            "interpretation": "Coverage only; not performance acceptance, successful jump launch, camera visibility, or physical-input proof."}


def analyze_stationary(server: list[dict], client: list[dict], scenario: str, minimum: int = 1) -> dict:
    issues: list[str] = []
    markers = []
    for event in server + client:
        if event_type(event) == "ms14.PopulationPhase":
            v = values(event)
            when = timestamp(event)
            if when is not None and isinstance(v.get("phase"), str):
                markers.append((when, v.get("phase", "").lower(), v.get("animalCount"), v.get("scenario")))
    markers.sort()
    resolved = {}
    for phase in ("baseline", "population", "complete"):
        matches = [m for m in markers if m[1] == phase and m[3] == scenario]
        if not matches:
            issues.append(f"missing {phase} phase marker for scenario {scenario}")
        else:
            resolved[phase] = matches[0]
    if not any(m[1] == "complete" and m[3] == scenario for m in markers):
        issues.append("missing complete phase marker")
    expected = 0 if scenario == "baseline" else EXPECTED.get(scenario)
    if expected is None:
        issues.append(f"unknown scenario: {scenario}")
    if "baseline" in resolved and resolved["baseline"][2] != 0:
        issues.append(f"baseline marker animalCount {resolved['baseline'][2]!r} != expected 0")
    if "population" in resolved and expected is not None and resolved["population"][2] != expected:
        issues.append(f"population marker animalCount {resolved['population'][2]!r} != expected {expected}")
    if "complete" in resolved and expected is not None and resolved["complete"][2] != expected:
        issues.append(f"complete marker animalCount {resolved['complete'][2]!r} != expected {expected}")

    boundaries = sorted((m[0], phase) for phase, m in resolved.items())
    population_start = resolved.get("population", (None,))[0]
    complete_start = resolved.get("complete", (None,))[0]
    midpoint = (population_start + complete_start) / 2 if population_start is not None and complete_start is not None and complete_start > population_start else None
    if midpoint is None:
        issues.append("invalid population window: population must precede complete")
    buckets = {phase: {"server_mspt": [], "client_frame_interval_ms": [], "rendered_animal_calls": []} for phase in PHASES}
    visibility_missing = False
    def phase_at(when):
        if when is None:
            return None
        result = None
        for start, phase in boundaries:
            if start <= when:
                result = phase
            else:
                break
        if result == "population" and midpoint is not None:
            return "early" if when < midpoint else "late"
        return result if result in PHASES else None
    for event in server:
        if event_type(event) != "ms14.ServerTickDuration":
            continue
        v = values(event); phase = str(v.get("phase", "")).lower()
        if phase == "population" or phase not in PHASES:
            phase = phase_at(timestamp(event))
        dur = duration_seconds(event.get("duration", v.get("duration")))
        if phase in buckets and dur is not None:
            buckets[phase]["server_mspt"].append(dur * 1000)
    for event in client:
        if event_type(event) != "ms14.ClientFrameDuration":
            continue
        when = timestamp(event); phase = phase_at(when) if when is not None else None
        raw = values(event).get("intervalNanos")
        if phase in buckets and isinstance(raw, (int, float)):
            buckets[phase]["client_frame_interval_ms"].append(float(raw) / 1e6)
        if phase in buckets:
            calls = values(event).get("renderedAnimalCalls")
            if isinstance(calls, bool) or not isinstance(calls, (int, float)) or calls < 0:
                visibility_missing = True
            else:
                buckets[phase]["rendered_animal_calls"].append(float(calls))
    report_phases = {}
    for phase, group in buckets.items():
        report_phases[phase] = {}
        for key, samples in group.items():
            stats = distribution(samples) if samples else {"count": 0}
            if len(samples) < minimum:
                issues.append(f"insufficient {phase} {key} samples: {len(samples)} < {minimum}")
                stats["status"] = "UNVERIFIED"
            report_phases[phase][key] = stats

    population_frames = buckets["early"]["rendered_animal_calls"] + buckets["late"]["rendered_animal_calls"]
    visible_frames = [n for n in population_frames if n > 0]
    visibility_issues = []
    if visibility_missing:
        visibility_issues.append("client frames missing valid renderedAnimalCalls field")
    if not population_frames:
        visibility_issues.append("no population client frames with renderedAnimalCalls measurements")
    elif not visible_frames:
        visibility_issues.append("no population frames recorded animal render calls")
    if visibility_issues:
        issues.extend(visibility_issues)
        visibility = {"status": "UNVERIFIED", "populationFrameCount": len(population_frames),
                      "framesWithAnimalCalls": len(visible_frames),
                      "renderingFrameFraction": len(visible_frames) / len(population_frames) if population_frames else None,
                      "medianRenderedAnimalCalls": None, "issues": visibility_issues}
    else:
        visibility = {"status": "RECORDED", "populationFrameCount": len(population_frames),
                      "framesWithAnimalCalls": len(visible_frames),
                      "renderingFrameFraction": len(visible_frames) / len(population_frames),
                      "medianRenderedAnimalCalls": distribution(population_frames)["p50"],
                      "renderedAnimalCalls": {phase: distribution(buckets[phase]["rendered_animal_calls"])
                                               if buckets[phase]["rendered_animal_calls"] else {"count": 0, "status": "UNVERIFIED"}
                                               for phase in PHASES}}

    def gc_entry():
        return {"count": 0, "totalDurationSeconds": 0.0, "maxDurationSeconds": 0.0, "eventTypes": Counter()}
    gc = {jvm: {p: {"cycles": gc_entry(), "pauses": gc_entry()} for p in PHASES}
          for jvm in ("server", "client")}
    for jvm, events in (("server", server), ("client", client)):
        for event in events:
            typ = event_type(event)
            category = {"jdk.GarbageCollection": "cycles", "jdk.GCPhasePause": "pauses"}.get(typ)
            when = timestamp(event)
            phase = phase_at(when) if when is not None else None
            if category and phase:
                d = duration_seconds(event.get("duration", values(event).get("duration")))
                entry = gc[jvm][phase][category]
                entry["count"] += 1
                entry["totalDurationSeconds"] += d or 0
                entry["maxDurationSeconds"] = max(entry["maxDurationSeconds"], d or 0)
                entry["eventTypes"][typ] += 1
    for phases in gc.values():
        for phase in PHASES:
            for category in ("cycles", "pauses"):
                entry = phases[phase][category]
                entry["eventTypes"] = dict(entry["eventTypes"])
    route_events = [event for event in server if event_type(event) == "ms14.NpcRouteWork"]
    if route_events:
        route_work = {"status": "RECORDED", "phases": {p: {} for p in PHASES},
                      "durationNote": "Per-call route durations are reported; they are not exclusive CPU time.",
                      "phaseAttribution": "event_start_time; complete event durations are attributed to the phase containing startTime, not clipped to phase bounds."}
        counter_fields = ("expandedNodes", "surfaceQueries", "surfaceCacheHits", "surfaceComputations",
                          "standableCalls", "edgeClearCalls", "noCollisionProbes")
        for event in route_events:
            v = values(event); phase = phase_at(timestamp(event))
            if phase not in PHASES:
                continue
            group = route_work["phases"][phase].setdefault(str(v.get("entityType", "unknown")), {})
            outcome = str(v.get("outcome", "unknown"))
            stats = group.setdefault(outcome, {"calls": 0, "durationMs": [],
                "surfaceReuseMeasuredCalls": 0, **{field: [] for field in counter_fields}})
            stats["calls"] += 1
            duration = duration_seconds(event.get("duration", v.get("duration")))
            if duration is not None:
                stats["durationMs"].append(duration * 1000)
            for field in counter_fields:
                value = v.get(field)
                if isinstance(value, (int, float)) and not isinstance(value, bool):
                    stats[field].append(value)
            if all(isinstance(v.get(field), (int, float)) and not isinstance(v.get(field), bool)
                   for field in ("surfaceCacheHits", "surfaceComputations")):
                stats["surfaceReuseMeasuredCalls"] += 1
        for groups in route_work["phases"].values():
            for outcomes in groups.values():
                for stats in outcomes.values():
                    stats["durationMs"] = distribution(stats["durationMs"]) if stats["durationMs"] else {"count": 0}
                    for field in counter_fields:
                        vals = stats[field]
                        stats[field] = {"sum": sum(vals), "distribution": distribution(vals) if vals else {"count": 0}}
                    measured = stats.pop("surfaceReuseMeasuredCalls")
                    stats["surface_reuse_measurement"] = (
                        "RECORDED" if measured == stats["calls"] else
                        "PARTIAL" if measured else "UNVERIFIED_MISSING_FIELDS")
    else:
        route_work = {"status": "UNVERIFIED_NO_EVENTS", "phases": {},
                      "note": "No ms14.NpcRouteWork events; route work cost is unknown, not zero."}
    hotspots = {"server": [], "client": []}
    primary_hotspots = {"server": {p: [] for p in PHASES}, "client": {p: [] for p in PHASES}}
    primary_names = {"server": "Server thread", "client": "Render thread"}
    for target, events, needles in (("server", server, ("Server", "server")), ("client", client, ("Render", "render"))):
        counts = Counter(); thread_names = {}
        for event in events:
            if event_type(event) != "jdk.ExecutionSample":
                continue
            v = values(event); thread = v.get("sampledThread") or v.get("eventThread") or v.get("thread") or {}
            name = thread.get("javaName", thread.get("name", "unknown")) if isinstance(thread, dict) else str(thread)
            stack = v.get("stackTrace") or v.get("stack", {})
            frames = stack.get("frames", []) if isinstance(stack, dict) else []
            for frame in frames:
                method = frame.get("method", {}) if isinstance(frame, dict) else {}
                desc = method.get("type", {}).get("name", "") + "." + method.get("name", "") if isinstance(method, dict) else str(method)
                if desc != ".":
                    counts[desc] += 1; thread_names.setdefault(desc, set()).add(str(name))
        hotspots[target] = [{"frame": frame, "sampleCount": count, "threadNames": sorted(thread_names[frame])}
                            for frame, count in counts.most_common(20)]
        phased = {p: Counter() for p in PHASES}
        for event in events:
            if event_type(event) != "jdk.ExecutionSample":
                continue
            v = values(event); thread = v.get("sampledThread") or {}
            name = thread.get("javaName", thread.get("name", "unknown")) if isinstance(thread, dict) else str(thread)
            phase = phase_at(timestamp(event))
            if name != primary_names[target] or phase not in PHASES:
                continue
            stack = v.get("stackTrace") or v.get("stack", {})
            for frame in stack.get("frames", []) if isinstance(stack, dict) else []:
                method = frame.get("method", {}) if isinstance(frame, dict) else {}
                desc = method.get("type", {}).get("name", "") + "." + method.get("name", "") if isinstance(method, dict) else str(method)
                if desc != ".": phased[phase][desc] += 1
        for phase in PHASES:
            primary_hotspots[target][phase] = [{"frame": frame, "sampleCount": count,
                "threadNames": [primary_names[target]]} for frame, count in phased[phase].most_common(20)]
    return {"status": "UNVERIFIED" if issues else "CAPTURE_COVERAGE_RECORDED", "scenario": scenario,
            "phases": report_phases, "visibility": visibility,
            "gc": gc, "gcDurationNote": "GarbageCollection cycle duration is not stop-the-world pause duration; only GCPhasePause durations represent pauses.",
            "gcPhaseAttribution": "event_start_time; complete event durations are attributed to the phase containing startTime, not clipped to phase bounds.",
            "gcAttributionNote": "phaseAttribution: event_start_time; complete event durations are attributed to the phase containing startTime, not clipped to phase bounds.",
            "executionSampleHotspots": hotspots, "primaryThreadHotspots": primary_hotspots,
            "route_work": route_work, "issues": issues}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True, type=Path)
    parser.add_argument("--client", required=True, type=Path)
    parser.add_argument("--scenario", required=True, choices=tuple(EXPECTED))
    parser.add_argument("--protocol", choices=("stationary", "sustained"), default="stationary")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--minimum-samples", type=int, default=20)
    parser.add_argument("--require-flyer-pose", action="store_true")
    args = parser.parse_args(argv)
    if args.minimum_samples < 1:
        parser.error("--minimum-samples must be at least one")
    try:
        report = analyze(load_events(args.server), load_events(args.client), args.scenario, args.minimum_samples, args.protocol, args.require_flyer_pose)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"UNVERIFIED: {exc}", file=sys.stderr)
        return 2
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if report["status"] == "CAPTURE_COVERAGE_RECORDED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
