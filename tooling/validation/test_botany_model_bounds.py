import importlib.util
import math
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("botany_model_bounds.py")
SPEC = importlib.util.spec_from_file_location("botany_model_bounds", SCRIPT)
BOUNDS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BOUNDS)


class BoundsTests(unittest.TestCase):
    def test_legal_json_rotations_rescale_only_perpendicular_axes(self):
        element = {"from": [0, 0, 0], "to": [16, 16, 16]}
        self.assertEqual(BOUNDS.bounds(BOUNDS.element_points(element)), [0, 0, 0, 1, 1, 1])
        rotated = dict(element, rotation={"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True})
        self.assertEqual(BOUNDS.bounds(BOUNDS.element_points(rotated)), [-0.5, 0, -0.5, 1.5, 1, 1.5])
        shallow = dict(element, rotation={"origin": [8, 8, 8], "axis": "y", "angle": 22.5, "rescale": True})
        self.assertAlmostEqual(BOUNDS.bounds(BOUNDS.element_points(shallow))[4], 1)
        illegal = dict(element, rotation={"origin": [8, 8, 8], "axis": "y", "angle": 90, "rescale": True})
        with self.assertRaises(ValueError):
            BOUNDS.element_points(illegal)

    def test_parent_geometry_only_when_child_has_no_elements(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "assets/moonstation14/models/block/parent.json"
            parent.parent.mkdir(parents=True)
            parent.write_text('{"elements":[{"from":[0,0,0],"to":[16,16,16]}]}', encoding="utf-8")
            child = root / "assets/moonstation14/models/block/child.json"
            child.write_text('{"parent":"moonstation14:block/parent"}', encoding="utf-8")
            self.assertEqual(BOUNDS.bounds(BOUNDS.model_points(child, root)), [0, 0, 0, 1, 1, 1])
            child.write_text('{"parent":"moonstation14:block/parent","elements":[{"from":[0,0,0],"to":[8,8,8]}]}', encoding="utf-8")
            self.assertEqual(BOUNDS.bounds(BOUNDS.model_points(child, root)), [0, 0, 0, 0.5, 0.5, 0.5])

    def test_obj_vertex_units_are_already_blocks(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "assets/moonstation14/models").mkdir(parents=True)
            (root / "assets/moonstation14/models/test.obj").write_text("# mesh\nv 0.5 0.3125 0\nvt 1 1\nv 1 2 3\n", encoding="utf-8")
            wrapper = root / "wrapper.json"
            wrapper.write_text('{"loader":"neoforge:obj","model":"moonstation14:models/test.obj"}', encoding="utf-8")
            self.assertEqual(BOUNDS.bounds(BOUNDS.model_points(wrapper, root)), [0.5, 0.3125, 0, 1, 2, 3])

    def test_unknown_loader_and_nonfinite_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "model.json"
            path.write_text('{"loader":"unknown:thing"}', encoding="utf-8")
            with self.assertRaises(ValueError):
                BOUNDS.model_points(path, Path(temporary))
        with self.assertRaises(ValueError):
            BOUNDS.element_points({"from": [0, 0, 0], "to": [float("nan"), 1, 1]})


if __name__ == "__main__":
    unittest.main()
