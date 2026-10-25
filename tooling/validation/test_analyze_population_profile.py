import unittest

from tooling.validation.analyze_population_profile import analyze, duration_seconds, SUSTAINED_PHASES, flyer_pose


def marker(phase, second, count, scenario="parrots"):
    return {"type": "ms14.PopulationPhase", "startTime": f"2026-01-01T00:00:{second:02d}Z",
            "values": {"phase": phase, "scenario": scenario, "animalCount": count}}


class PopulationAnalyzerTests(unittest.TestCase):
    def flyer_fixture(self, ident="a", sec="01", y=5, dimension="overworld", host="parrot"):
        s={"type":"ms14.ServerFlyerPose","startTime":f"2026-01-01T00:00:{sec}Z","values":{"entityId":ident,"dimension":dimension,"hostEntityType":host,"gameTick":1,"entityY":y,"expectedFixtureFloorY":5,"flightState":"FLYING","onGround":False,"poweredFlight":True}}
        c={"type":"ms14.ClientFlyerPose","startTime":f"2026-01-01T00:00:{sec}Z","values":{"entityId":ident,"dimension":dimension,"hostEntityType":host,"gameTick":2,"entityY":y,"renderY":y,"modelBottomPixels":24,"presentationScale":1,"estimatedMeshBottomY":y,"hasVisibleGeometry":True,"onGround":False,"poweredFlight":False,"resolvedFlightCapability":False,"flightState":"GROUNDED","sourceClip":"fly","destinationClip":"idle","blendWeight":.5}}
        return s,c

    def test_flyer_pairs_require_identity_and_utc_proximity_and_keep_bad_state(self):
        s,c=self.flyer_fixture()
        c['values']['entityId']='b'
        self.assertEqual(flyer_pose([s],[c])['pairedRowCount'],0)
        c['values']['entityId']='a'; c['startTime']='2026-01-01T00:00:02Z'
        self.assertEqual(flyer_pose([s],[c])['pairedRowCount'],0)
        c['startTime']='2026-01-01T00:00:01.1Z'
        result=flyer_pose([s],[c]); row=result['entities']['a']
        self.assertEqual(row['clientCapabilityFalseCount'],1); self.assertEqual(row['stateMismatchCount'],1)
        self.assertEqual(row['floorSkimmingCount'],1); self.assertIn('estimatedMeshBottomY', result['meshBottomNote'])

    def test_flyer_bad_rows_do_not_satisfy_required_coverage_and_rank_worst(self):
        s,c=self.flyer_fixture(); s=[s]; c=[c]
        bads=[]; badc=[]
        for i in range(20):
            a,b=self.flyer_fixture(sec=f"{i%60:02d}")
            a['values'].pop('expectedFixtureFloorY'); b['values']['estimatedMeshBottomY']=float('nan')
            bads.append(a); badc.append(b)
        fp=flyer_pose(bads,badc,20)
        self.assertEqual(fp['pairedRowCount'],0)
        self.assertEqual(analyze([],[], 'parrots', minimum=20, require_flyer_pose=True)['status'],'UNVERIFIED')
        for i,d in enumerate((1,5,2,9,3,7)):
            a,b=self.flyer_fixture(sec=f"{i:02d}", y=5+d)
            s0,c0=a,b
            c0['values']['entityY']=5
            s0['values']['entityId']='ranked'; c0['values']['entityId']='ranked'
            s.append(s0); c.append(c0)
        row=flyer_pose(s,c)['entities']['ranked']
        self.assertEqual(abs(row['worstPairs'][0]['yDifference']),9)

    def test_dimension_and_host_are_part_of_identity(self):
        s,c=self.flyer_fixture(); c['values']['dimension']='nether'
        self.assertEqual(flyer_pose([s],[c])['pairedRowCount'],0)

    def test_legacy_capture_optional_and_required_evidence(self):
        s,c=self.fixture(); report=analyze(s,c,'parrots')
        self.assertEqual(report['status'],'CAPTURE_COVERAGE_RECORDED')
        report=analyze([],[],'parrots',require_flyer_pose=True)
        self.assertEqual(report['status'],'UNVERIFIED')

    def sustained_fixture(self):
        server, client = [], []
        checks = {
            "population_idle": {"acceptedInputSamples": 1, "acceptedHorizontalDistance": 0, "nearBodySamples": 1100},
            "population_move": {"acceptedHorizontalDistance": 20, "nearBodySamples": 200},
            "population_jump": {"acceptedGroundedJumpRequests": 3, "positiveAscentSamples": 1, "nearBodySamples": 200},
            "population_away": {"farBodySamples": 200}, "population_return": {"nearBodySamples": 200},
        }
        for i, phase in enumerate(SUSTAINED_PHASES):
            start, end = i * 2 + 1, i * 2 + 2
            server.extend([
                {"type": "ms14.PopulationPhase", "startTime": f"2026-01-01T00:00:{start:02d}Z", "values": {"scenario": "parrots", "phase": phase, "phaseTicks": 0, "animalCount": 100}},
                {"type": "ms14.PopulationPhase", "startTime": f"2026-01-01T00:00:{end:02d}Z", "values": {"scenario": "parrots", "phase": phase, "phaseTicks": 1200, "animalCount": 100, **checks[phase]}},
            ])
            server.append({"type": "ms14.ServerTickDuration", "startTime": f"2026-01-01T00:00:{start:02d}Z", "duration": "PT10MS", "values": {}})
            client.append({"type": "ms14.ClientFrameDuration", "startTime": f"2026-01-01T00:00:{start:02d}Z", "values": {"intervalNanos": 16_000_000, "renderedAnimalCalls": 0 if phase == "population_away" else 3}})
        return server, client

    def test_sustained_five_phase_coverage_and_per_role_frames(self):
        server, client = self.sustained_fixture()
        report = analyze(server, client, "parrots", protocol="sustained")
        self.assertEqual(report["status"], "CAPTURE_COVERAGE_RECORDED")
        self.assertEqual(list(report["phases"]), list(SUSTAINED_PHASES))
        self.assertEqual(report["phases"]["population_away"]["client"]["count"], 1)
        self.assertEqual(report["phases"]["population_away"]["server"]["intervalMs"]["p50"], 10)

    def test_sustained_missing_phase_boundary_metrics_and_client_frames_fail_closed(self):
        server, client = self.sustained_fixture()
        server = [e for e in server if not (e.get("type") == "ms14.PopulationPhase" and e["values"]["phase"] == "population_move" and e["values"]["phaseTicks"] == 0)]
        client = [e for e in client if e.get("type") != "ms14.ClientFrameDuration" or e["startTime"] != "2026-01-01T00:00:07Z"]
        report = analyze(server, client, "parrots", protocol="sustained")
        self.assertEqual(report["status"], "INCOMPLETE")
        self.assertTrue(any("missing population_move start" in x for x in report["issues"]))
        self.assertTrue(any("population_away insufficient client time" in x for x in report["issues"]))

    def test_sustained_uses_only_server_authority_markers_and_rejects_client_only(self):
        server, client = self.sustained_fixture()
        client.extend(e for e in server if e.get("type") == "ms14.PopulationPhase")
        report = analyze(server, client, "parrots", protocol="sustained")
        self.assertEqual(report["status"], "CAPTURE_COVERAGE_RECORDED")
        report = analyze([], client, "parrots", protocol="sustained")
        self.assertEqual(report["status"], "INCOMPLETE")
        self.assertTrue(any("server missing population_idle start" in issue for issue in report["issues"]))

    def test_sustained_motion_gate_failures_and_stationary_scope(self):
        server, client = self.sustained_fixture()
        for e in server:
            v=e.get("values", {})
            if v.get("phase") == "population_move" and v.get("phaseTicks") == 1200: v["acceptedHorizontalDistance"] = 1
            if v.get("phase") == "population_jump" and v.get("phaseTicks") == 1200: v["positiveAscentSamples"] = 0
            if v.get("phase") == "population_away" and v.get("phaseTicks") == 1200: v["farBodySamples"] = 0
            if v.get("phase") == "population_return" and v.get("phaseTicks") == 1200: v.pop("nearBodySamples")
        report = analyze(server, client, "parrots", protocol="sustained")
        self.assertEqual(report["status"], "INCOMPLETE")
        self.assertTrue(any("population_move acceptedHorizontalDistance" in x for x in report["issues"]))
        self.assertTrue(any("population_jump positiveAscentSamples" in x for x in report["issues"]))
        self.assertTrue(any("population_away farBodySamples" in x for x in report["issues"]))
        self.assertTrue(any("population_return nearBodySamples" in x for x in report["issues"]))
        self.assertEqual(analyze([], [], "parrots")["status"], "UNVERIFIED")

    def fixture(self):
        server = [marker("baseline", 0, 0), marker("population", 1, 100), marker("complete", 5, 100)]
        client = []
        for sec in (0, 1, 2, 3, 4):
            server.append({"type": "ms14.ServerTickDuration", "startTime": f"2026-01-01T00:00:{sec:02d}Z",
                           "duration": "PT10MS", "values": {"phase": "baseline" if sec == 0 else "population", "animalCount": 0 if sec == 0 else 100}})
            client.append({"type": "ms14.ClientFrameDuration", "startTime": f"2026-01-01T00:00:{sec:02d}Z",
                           "values": {"intervalNanos": 16_000_000, "renderedAnimalCalls": (0, 3, 4, 5, 6)[sec]}})
        return server, client

    def test_distribution_units_alignment_and_gc_hotspots(self):
        server, client = self.fixture()
        server += [
            {"type": "jdk.GarbageCollection", "startTime": "2026-01-01T00:00:01Z", "duration": "PT2MS", "values": {}},
            {"type": "jdk.ExecutionSample", "startTime": "2026-01-01T00:00:01Z", "values": {"sampledThread": {"javaName": "Server thread"},
             "stackTrace": {"frames": [{"method": {"type": {"name": "pkg.Tick"}, "name": "run"}}]}}},
            {"type": "jdk.ExecutionSample", "startTime": "2026-01-01T00:00:01Z", "values": {"sampledThread": {"javaName": "Worker-1"},
             "stackTrace": {"frames": [{"method": {"type": {"name": "pkg.Work"}, "name": "run"}}]}}},
        ]
        report = analyze(server, client, "parrots")
        self.assertEqual(report["status"], "CAPTURE_COVERAGE_RECORDED")
        self.assertEqual(report["phases"]["early"]["server_mspt"]["p50"], 10)
        self.assertEqual(report["phases"]["late"]["client_frame_interval_ms"]["mean"], 16)
        self.assertEqual(report["phases"]["early"]["rendered_animal_calls"]["mean"], 3.5)
        self.assertEqual(report["visibility"]["renderingFrameFraction"], 1)
        self.assertEqual(report["visibility"]["medianRenderedAnimalCalls"], 4.5)
        self.assertEqual(report["gc"]["server"]["early"]["cycles"]["maxDurationSeconds"], .002)
        self.assertEqual(report["executionSampleHotspots"]["server"][0]["sampleCount"], 1)
        self.assertEqual(report["primaryThreadHotspots"]["server"]["baseline"], [])
        self.assertEqual([h["frame"] for h in report["primaryThreadHotspots"]["server"]["early"]], ["pkg.Tick.run"])

    def test_missing_marker_bad_count_and_insufficient_samples_unverified(self):
        server, client = self.fixture()
        server[1]["values"]["animalCount"] = 99
        server = [e for e in server if not (e.get("type") == "ms14.PopulationPhase" and e["values"]["phase"] == "complete")]
        report = analyze(server, client, "parrots", minimum=2)
        self.assertEqual(report["status"], "UNVERIFIED")
        self.assertTrue(any("animalCount" in issue for issue in report["issues"]))
        self.assertTrue(any("complete" in issue for issue in report["issues"]))
        self.assertEqual(report["phases"]["baseline"]["server_mspt"]["status"], "UNVERIFIED")

    def test_execution_samples_are_not_frame_durations(self):
        server, client = self.fixture()
        client.extend({"type": "jdk.ExecutionSample", "values": {"eventThread": {"javaName": "Render thread"}}} for _ in range(50))
        report = analyze(server, client, "parrots")
        self.assertEqual(report["phases"]["early"]["client_frame_interval_ms"]["count"], 2)
        self.assertEqual(report["executionSampleHotspots"]["client"], [])

    def test_iso_duration(self):
        self.assertEqual(duration_seconds("PT1.5MS"), .0015)

    def test_missing_visibility_field_is_unverified_not_zero(self):
        server, client = self.fixture()
        for event in client:
            event["values"].pop("renderedAnimalCalls")
        report = analyze(server, client, "parrots")
        self.assertEqual(report["status"], "UNVERIFIED")
        self.assertEqual(report["visibility"]["status"], "UNVERIFIED")
        self.assertIsNone(report["visibility"]["medianRenderedAnimalCalls"])

    def test_no_positive_population_animal_calls_is_unverified(self):
        server, client = self.fixture()
        for event in client:
            if event["startTime"].endswith(("01Z", "02Z", "03Z", "04Z")):
                event["values"]["renderedAnimalCalls"] = 0
        report = analyze(server, client, "parrots")
        self.assertEqual(report["visibility"]["status"], "UNVERIFIED")
        self.assertEqual(report["visibility"]["renderingFrameFraction"], 0)
        self.assertEqual(report["status"], "UNVERIFIED")

    def test_actual_three_marker_driver_protocol_splits_population_window(self):
        server, client = self.fixture()
        self.assertEqual([e["values"]["phase"] for e in server if e["type"] == "ms14.PopulationPhase"],
                         ["baseline", "population", "complete"])
        report = analyze(server, client, "parrots")
        self.assertEqual(report["status"], "CAPTURE_COVERAGE_RECORDED")
        self.assertEqual(report["phases"]["early"]["server_mspt"]["count"], 2)
        self.assertEqual(report["phases"]["late"]["server_mspt"]["count"], 2)
        self.assertEqual(report["phases"]["early"]["client_frame_interval_ms"]["count"], 2)
        self.assertEqual(report["phases"]["late"]["client_frame_interval_ms"]["count"], 2)

    def test_minimum_sample_threshold_retains_distribution_for_inspection(self):
        server, client = self.fixture()
        report = analyze(server, client, "parrots", minimum=20)
        self.assertEqual(report["status"], "UNVERIFIED")
        samples = report["phases"]["early"]["server_mspt"]
        self.assertEqual(samples["count"], 2)
        self.assertEqual(samples["p95"], 10)
        self.assertEqual(samples["status"], "UNVERIFIED")

    def test_missing_sampled_thread_is_not_misattributed_to_primary(self):
        server, client = self.fixture()
        server.append({"type": "jdk.ExecutionSample", "values": {"stackTrace": {"frames": [
            {"method": {"type": {"name": "pkg.Unknown"}, "name": "run"}}]}}})
        report = analyze(server, client, "parrots")
        self.assertTrue(all(not phase for phase in report["primaryThreadHotspots"]["server"].values()))
        self.assertEqual(report["executionSampleHotspots"]["server"][0]["threadNames"], ["unknown"])

    def test_primary_samples_are_phase_attributed_not_startup_or_aggregate(self):
        server, client = self.fixture()
        def sample(sec, thread, frame):
            return {"type": "jdk.ExecutionSample", "startTime": f"2026-01-01T00:00:{sec:02d}Z",
                    "values": {"sampledThread": {"javaName": thread}, "stackTrace": {"frames": [
                        {"method": {"type": {"name": "pkg"}, "name": frame}}]}}}
        server.extend([sample(0, "Server thread", "Startup"), sample(2, "Server thread", "Live"),
                       sample(2, "Worker", "Background")])
        report = analyze(server, client, "parrots")
        self.assertEqual(report["primaryThreadHotspots"]["server"]["baseline"][0]["frame"], "pkg.Startup")
        self.assertEqual(report["primaryThreadHotspots"]["server"]["early"][0]["frame"], "pkg.Live")
        self.assertNotIn("pkg.Background", [x["frame"] for x in report["primaryThreadHotspots"]["server"]["early"]])

    def test_gc_cycles_and_pause_events_are_separate_per_jvm(self):
        server, client = self.fixture()
        for target, duration in ((server, "PT20MS"), (client, "PT30MS")):
            target.extend([
                {"type": "jdk.GarbageCollection", "startTime": "2026-01-01T00:00:02Z", "duration": "PT1S", "values": {}},
                {"type": "jdk.GCPhasePause", "startTime": "2026-01-01T00:00:02Z", "duration": duration, "values": {}},
                {"type": "jdk.GCPhasePause", "startTime": "2026-01-01T00:00:02Z", "duration": "PT10MS", "values": {}}])
        report = analyze(server, client, "parrots")
        gc = report["gc"]["server"]["early"]
        self.assertEqual(gc["cycles"]["totalDurationSeconds"], 1)
        self.assertEqual(gc["pauses"]["count"], 2)
        self.assertEqual(gc["pauses"]["totalDurationSeconds"], .03)
        self.assertEqual(report["gc"]["client"]["early"]["pauses"]["totalDurationSeconds"], .04)

    def test_route_work_groups_phase_entity_outcome_and_excludes_startup(self):
        server, client = self.fixture()
        fields = {"entityType": "ms14:parrot", "outcome": "COMPLETE", "expandedNodes": 4,
                  "surfaceQueries": 3, "standableCalls": 2, "edgeClearCalls": 1, "noCollisionProbes": 5}
        for when, outcome in (("2025-12-31T23:59:59Z", "ERROR"), (1, "COMPLETE"), (2, "COMPLETE"), (3, "NO_PATH"), (4, "NO_PATH")):
            timestamp = when if isinstance(when, str) else f"2026-01-01T00:00:{when:02d}Z"
            server.append({"type": "ms14.NpcRouteWork", "startTime": timestamp,
                "duration": "PT2MS", "values": {**fields, "outcome": outcome}})
        report = analyze(server, client, "parrots")
        route = report["route_work"]
        self.assertNotIn("ms14:parrot", route["phases"]["baseline"])
        self.assertEqual(route["phases"]["early"]["ms14:parrot"]["COMPLETE"]["calls"], 2)
        self.assertEqual(route["phases"]["early"]["ms14:parrot"]["COMPLETE"]["expandedNodes"]["sum"], 8)
        self.assertEqual(route["phases"]["late"]["ms14:parrot"]["NO_PATH"]["calls"], 2)

    def test_route_work_absence_is_explicitly_unverified(self):
        server, client = self.fixture()
        self.assertEqual(analyze(server, client, "parrots")["route_work"]["status"], "UNVERIFIED_NO_EVENTS")

    def test_gc_and_route_duration_attribution_is_explicit_and_uses_event_start(self):
        server, client = self.fixture()
        server.extend([
            {"type": "jdk.GCPhasePause", "startTime": "2026-01-01T00:00:02.999Z",
             "duration": "PT2S", "values": {}},
            {"type": "ms14.NpcRouteWork", "startTime": "2026-01-01T00:00:02.999Z",
             "duration": "PT2S", "values": {"entityType": "ms14:parrot", "outcome": "COMPLETE",
                "expandedNodes": 1, "surfaceQueries": 1, "standableCalls": 1,
                "edgeClearCalls": 1, "noCollisionProbes": 1}},
        ])
        report = analyze(server, client, "parrots")
        note = "event_start_time; complete event durations are attributed to the phase containing startTime, not clipped to phase bounds."
        self.assertEqual(report["gcPhaseAttribution"], note)
        self.assertEqual(report["gc"]["server"]["early"]["pauses"]["count"], 1)
        self.assertEqual(report["route_work"]["phaseAttribution"], note)
        self.assertEqual(report["route_work"]["phases"]["early"]["ms14:parrot"]["COMPLETE"]["durationMs"]["p50"], 2000)

    def test_surface_reuse_counters_invariant_and_legacy_availability(self):
        server, client = self.fixture()
        def route(sec, outcome, queries, hits=None, computations=None):
            values = {"entityType": "ms14:parrot", "outcome": outcome, "expandedNodes": 10,
                      "surfaceQueries": queries, "standableCalls": 0, "edgeClearCalls": 0,
                      "noCollisionProbes": 0}
            if hits is not None: values["surfaceCacheHits"] = hits
            if computations is not None: values["surfaceComputations"] = computations
            server.append({"type": "ms14.NpcRouteWork", "startTime": f"2026-01-01T00:00:{sec:02d}Z",
                           "duration": "PT1MS", "values": values})
        route(1, "COMPLETE", 100, 60, 40)
        route(2, "COMPLETE", 80)  # Legacy R33 shape: unknown reuse measurements.
        route(3, "NO_PATH", 100, 60, 40)
        route(4, "NO_PATH", 80, 70, 10)
        phases = analyze(server, client, "parrots")["route_work"]["phases"]
        early = phases["early"]["ms14:parrot"]["COMPLETE"]
        self.assertEqual(early["surfaceQueries"]["sum"], 180)
        self.assertEqual(early["surfaceCacheHits"]["sum"], 60)
        self.assertEqual(early["surfaceComputations"]["sum"], 40)
        self.assertEqual(early["surface_reuse_measurement"], "PARTIAL")
        late = phases["late"]["ms14:parrot"]["NO_PATH"]
        self.assertEqual(late["surfaceQueries"]["sum"], 180)
        self.assertEqual(late["surfaceCacheHits"]["sum"], 130)
        self.assertEqual(late["surfaceComputations"]["sum"], 50)
        self.assertEqual(late["surface_reuse_measurement"], "RECORDED")

    def test_all_legacy_route_groups_mark_reuse_fields_unverified(self):
        server, client = self.fixture()
        values = {"entityType": "ms14:parrot", "outcome": "NO_PATH", "expandedNodes": 1,
                  "surfaceQueries": 2, "standableCalls": 0, "edgeClearCalls": 0, "noCollisionProbes": 0}
        server.append({"type": "ms14.NpcRouteWork", "startTime": "2026-01-01T00:00:02Z",
                       "duration": "PT1MS", "values": values})
        group = analyze(server, client, "parrots")["route_work"]["phases"]["early"]["ms14:parrot"]["NO_PATH"]
        self.assertEqual(group["surfaceComputations"]["sum"], 0)
        self.assertEqual(group["surface_reuse_measurement"], "UNVERIFIED_MISSING_FIELDS")


if __name__ == "__main__":
    unittest.main()
