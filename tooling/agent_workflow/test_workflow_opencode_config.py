import copy
import json
import pathlib
import unittest

import workflow_opencode_config as config


TEMPLATE = {
    "version": 1,
    "config": {"agents": {"review": {"permissions": [
        {"action": "external_directory", "resource": "{{shared_root}}/**", "effect": "allow"},
        {"action": "edit", "resource": "{{shared_root}}/**", "effect": "deny"}
    ]}}},
}
VARS = {"shared_root": "Z:/portable/shared"}


class WorkflowOpenCodeConfigTests(unittest.TestCase):
    def make_config(self, extra="local"):
        return {"agents": {"review": {"permissions": [
            {"action": "external_directory", "resource": "Z:/portable/shared/**", "effect": "allow"},
            {"action": "edit", "resource": "Z:/portable/shared/**", "effect": "deny"},
            {"action": "shell", "resource": extra, "effect": "allow"}
        ], "model": "private-model"}}, "mcp": {"servers": {"private": {"url": "token-ref"}}}}

    def test_five_local_shapes_roundtrip_and_order(self):
        for n in range(5):
            original = self.make_config(f"local-{n}")
            overlay = config.extract_local_overlay(TEMPLATE, original, VARS)
            self.assertEqual(config.render_config(TEMPLATE, overlay), original)
            self.assertEqual(overlay["config"]["agents"]["review"]["permissions"][-1]["resource"], f"local-{n}")

    def test_template_policy_change_reaches_every_render(self):
        overlays = [config.extract_local_overlay(TEMPLATE, self.make_config(str(n)), VARS) for n in range(5)]
        changed = copy.deepcopy(TEMPLATE)
        changed["config"]["agents"]["review"]["permissions"][0]["effect"] = "deny"
        for overlay in overlays:
            rendered = config.render_config(changed, overlay)
            self.assertEqual(rendered["agents"]["review"]["permissions"][0]["effect"], "deny")

    def test_portable_template_and_local_data_retained(self):
        text = config.serialize_config(config.load_template(TEMPLATE))
        self.assertNotIn("C:/Users", text)
        rendered = config.render_config(TEMPLATE, config.extract_local_overlay(TEMPLATE, self.make_config(), VARS))
        self.assertEqual(rendered["mcp"]["servers"]["private"]["url"], "token-ref")
        self.assertEqual(rendered["agents"]["review"]["model"], "private-model")

    def test_bad_variables_prefix_and_duplicate_keys_rejected(self):
        with self.assertRaises(ValueError):
            config.render_config(TEMPLATE, {"version": 1, "variables": {}, "config": {}})
        broken = self.make_config()
        broken["agents"]["review"]["permissions"][0]["effect"] = "deny"
        with self.assertRaises(ValueError):
            config.extract_local_overlay(TEMPLATE, broken, VARS)
        with self.assertRaises(ValueError):
            config.load_template('{"version":1,"version":1,"config":{}}')

    def test_five_real_configs_extract_and_preserve_exact_bytes(self):
        main = pathlib.Path(__file__).resolve().parents[2]
        roots = [main] + [main.parent / f"MoonStation14-agent-0{i}" for i in range(1, 5)]
        template = config.load_template(main / "tooling/opencode/shared-config.jsonc")
        variables = {
            "shared_root": "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-shared",
            "workspace_parent": "C:/Users/William/Documents/Minecraft Mod Dev Files",
            "ss14_root": "C:/Users/William/Documents/SS14-dev/space-station-14",
        }
        for root in roots:
            original = (root / "opencode.jsonc").read_bytes()
            overlay = config.extract_local_overlay(template, original, variables)
            rendered = config.render_config(template, overlay)
            self.assertTrue(config.configs_equivalent(config._loads(original), rendered), root.name)
            self.assertEqual(config.preserve_existing_bytes(template, overlay, original), original)

    def test_shared_permission_edit_reaches_all_five_outputs(self):
        main = pathlib.Path(__file__).resolve().parents[2]
        roots = [main] + [main.parent / f"MoonStation14-agent-0{i}" for i in range(1, 5)]
        template = config.load_template(main / "tooling/opencode/shared-config.jsonc")
        variables = {"shared_root": "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-shared",
                     "workspace_parent": "C:/Users/William/Documents/Minecraft Mod Dev Files",
                     "ss14_root": "C:/Users/William/Documents/SS14-dev/space-station-14"}
        revised = copy.deepcopy(template)
        revised["config"]["agents"]["implement"]["permissions"][1]["effect"] = "allow"
        outputs = []
        for root in roots:
            overlay = config.extract_local_overlay(template, (root / "opencode.jsonc").read_bytes(), variables)
            outputs.append(config.render_config(revised, overlay)["agents"]["implement"]["permissions"])
        self.assertTrue(all(rules[1]["effect"] == "allow" for rules in outputs))

    def test_unknown_cross_action_wildcards_fail_closed(self):
        left = {"agents": {"x": {"permissions": [
            {"action": "custom*", "resource": "*"}, {"action": "edit", "resource": "x"}]}}}
        right = {"agents": {"x": {"permissions": list(reversed(left["agents"]["x"]["permissions"]))}}}
        self.assertFalse(config.configs_equivalent(left, right))

    def test_deterministic_serialization(self):
        value = config.render_config(TEMPLATE, config.extract_local_overlay(TEMPLATE, self.make_config(), VARS))
        self.assertEqual(config.serialize_config(value), config.serialize_config(value))


if __name__ == "__main__":
    unittest.main()
