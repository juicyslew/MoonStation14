"""Offline geometry bounds audit for imported botany plant models.

JSON block elements are authored in 0..16 model units; OBJ vertex coordinates
are already block-local units. Output is an audit report, not runtime code.
"""
import argparse
import json
import math
from pathlib import Path


def finite_vector(values, count, label):
    if not isinstance(values, list) or len(values) != count:
        raise ValueError(f"{label}: expected {count} numeric values")
    result = tuple(float(value) for value in values)
    if not all(math.isfinite(value) for value in result):
        raise ValueError(f"{label}: non-finite coordinate")
    return result


def element_points(element):
    start = finite_vector(element.get("from"), 3, "element.from")
    end = finite_vector(element.get("to"), 3, "element.to")
    rotation = element.get("rotation")
    origin, axis, angle, rescale = (None, None, 0.0, False)
    if rotation is not None:
        if not isinstance(rotation, dict) or rotation.get("axis") not in ("x", "y", "z"):
            raise ValueError("unsupported element rotation")
        origin = finite_vector(rotation.get("origin"), 3, "rotation.origin")
        axis = rotation["axis"]
        angle = float(rotation.get("angle"))
        rescale = rotation.get("rescale", False)
        if not math.isfinite(angle) or abs(angle) not in (0, 22.5, 45) or not isinstance(rescale, bool):
            raise ValueError("invalid rotation angle/rescale")
    points = []
    for x in (start[0], end[0]):
        for y in (start[1], end[1]):
            for z in (start[2], end[2]):
                point = [x, y, z]
                if rotation is not None:
                    offset = [point[i] - origin[i] for i in range(3)]
                    if rescale:
                        factor = 1.0 / math.cos(math.radians(angle))
                        if not math.isfinite(factor):
                            raise ValueError("rotation rescale is unbounded")
                        for i, component_axis in enumerate("xyz"):
                            if component_axis != axis:
                                offset[i] *= factor
                    radians = math.radians(angle)
                    c, s = math.cos(radians), math.sin(radians)
                    if axis == "x":
                        offset = [offset[0], offset[1] * c - offset[2] * s, offset[1] * s + offset[2] * c]
                    elif axis == "y":
                        offset = [offset[0] * c + offset[2] * s, offset[1], -offset[0] * s + offset[2] * c]
                    else:
                        offset = [offset[0] * c - offset[1] * s, offset[0] * s + offset[1] * c, offset[2]]
                    point = [origin[i] + offset[i] for i in range(3)]
                points.append(tuple(v / 16.0 for v in point))
    return points


def model_points(path, resources, active=()):
    path = path.resolve()
    if path in active:
        raise ValueError(f"parent model cycle: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if "loader" in data:
        if data["loader"] != "neoforge:obj":
            raise ValueError(f"unsupported model loader {data['loader']!r}: {path}")
        if "transform" in data or "display" in data or "parent" in data:
            raise ValueError(f"unsupported OBJ wrapper spatial transform: {path}")
        model = data.get("model")
        if not isinstance(model, str) or not model.startswith("moonstation14:"):
            raise ValueError(f"unsupported OBJ resource {model!r}")
        obj = resources / "assets" / "moonstation14" / model.split(":", 1)[1]
        points = []
        for line in obj.read_text(encoding="utf-8").splitlines():
            fields = line.split()
            if fields and fields[0] == "v":
                points.append(finite_vector(fields[1:], 3, f"{obj}:v"))
        if not points:
            raise ValueError(f"OBJ has no positions: {obj}")
        return points
    points = []
    parent = data.get("parent")
    if parent not in (None, "minecraft:block/block"):
        if not isinstance(parent, str) or not parent.startswith("moonstation14:"):
            raise ValueError(f"unsupported parent {parent!r}: {path}")
        parent_name = parent.split(":", 1)[1]
        if parent_name.startswith("block/"):
            parent_name = "models/" + parent_name
        parent_path = resources / "assets" / "moonstation14" / (parent_name + ".json")
        if "elements" not in data:
            points.extend(model_points(parent_path, resources, active + (path,)))
    for element in data.get("elements", []):
        points.extend(element_points(element))
    return points


def bounds(points):
    if not points:
        return None
    return [min(p[i] for p in points) for i in range(3)] + [max(p[i] for p in points) for i in range(3)]


def audit(resources):
    manifest_path = resources / "assets/moonstation14/botany/plant-models.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    report = {"states": {}, "visuals": {}, "missing": []}
    for family, states in manifest["plants"].items():
        visual_points = {}
        for state, model_id in states.items():
            namespace, model_name = model_id.split(":", 1)
            if namespace != "moonstation14":
                raise ValueError(f"unsupported model namespace {namespace!r}")
            if model_name.startswith("block/"):
                model_name = "models/" + model_name
            model_path = resources / "assets" / namespace / (model_name + ".json")
            points = model_points(model_path, resources)
            if not points:
                report["missing"].append(f"{family}/{state}")
                continue
            b = bounds(points)
            report["states"][f"{family}/{state}"] = b
            visual_points.setdefault(family, []).extend(points)
        if visual_points:
            report["visuals"][family] = bounds(visual_points[family])
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resources", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = audit(args.resources)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"states={len(report['states'])} visuals={len(report['visuals'])} missing={len(report['missing'])}")


if __name__ == "__main__":
    main()
