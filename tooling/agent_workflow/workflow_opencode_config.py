"""Pure renderer/extractor for the shared OpenCode policy and local overlay."""

import copy
import json
import re

TEMPLATE_VERSION = 1
OVERLAY_VERSION = 1


def _loads(raw):
    raw = raw.encode("utf-8") if isinstance(raw, str) else bytes(raw)
    # Reuse the repository's JSONC scanner and duplicate-key parser; defer import
    # to avoid a module cycle with adjacent binding workflows.
    from workflow_coordinator_bindings import _jsonc_text, _strict_ordered_json
    try:
        text, _bom, _original = _jsonc_text(raw)
        return _strict_ordered_json(text.encode("utf-8"))
    except (UnicodeError, ValueError) as exc:
        raise ValueError("invalid JSONC configuration") from exc


def _template(value):
    doc = _loads(value) if isinstance(value, (str, bytes)) else copy.deepcopy(value)
    if (not isinstance(doc, dict) or set(doc) != {"version", "config"}
            or type(doc.get("version")) is not int or doc["version"] != TEMPLATE_VERSION
            or not isinstance(doc.get("config"), dict)):
        raise ValueError("unsupported shared template")
    return doc


def load_template(source):
    if hasattr(source, "read_bytes"):
        source = source.read_bytes()
    return _template(source)


def _overlay(value):
    doc = _loads(value) if isinstance(value, (str, bytes)) else copy.deepcopy(value)
    if (not isinstance(doc, dict) or set(doc) != {"version", "variables", "config"}
            or type(doc.get("version")) is not int or doc["version"] != OVERLAY_VERSION
            or not isinstance(doc["variables"], dict) or not isinstance(doc["config"], dict)):
        raise ValueError("invalid local overlay")
    return doc


def _substitute(value, variables):
    if isinstance(value, dict):
        return {k: _substitute(v, variables) for k, v in value.items()}
    if isinstance(value, list):
        return [_substitute(v, variables) for v in value]
    if isinstance(value, str):
        def replace(match):
            key = match.group(1)
            if key not in variables or not isinstance(variables[key], str) or not variables[key]:
                raise ValueError("missing or invalid variable: " + key)
            return variables[key]
        result = re.sub(r"\{\{([^{}]+)\}\}", replace, value)
        if "{{" in result or "}}" in result:
            raise ValueError("malformed variable placeholder")
        return result
    return value


def _merge(shared, local, key=None):
    if isinstance(shared, dict) and isinstance(local, dict):
        result = copy.deepcopy(shared)
        for key, value in local.items():
            if key in result:
                result[key] = _merge(result[key], value, key)
            else:
                result[key] = copy.deepcopy(value)
        return result
    if isinstance(shared, list) and isinstance(local, list) and key == "permissions":
        return copy.deepcopy(shared) + copy.deepcopy(local)
    return copy.deepcopy(local)


def validate_local_overlay(overlay, template):
    doc = _overlay(overlay)
    _substitute(_template(template)["config"], doc["variables"])
    return doc


def render_config(template, overlay):
    doc = validate_local_overlay(overlay, template)
    shared = _substitute(_template(template)["config"], doc["variables"])
    return _merge(shared, doc["config"])


class _Empty:
    pass


_EMPTY = _Empty()


def _subtract(shared, current, key=None):
    if isinstance(shared, dict):
        if not isinstance(current, dict):
            raise ValueError("current config misses shared settings")
        extra = {}
        for name, value in current.items():
            if name not in shared:
                extra[name] = copy.deepcopy(value)
            else:
                residue = _subtract(shared[name], value, name)
                if residue is not _EMPTY:
                    extra[name] = residue
        if any(name not in current for name in shared):
            raise ValueError("current config misses shared settings")
        return extra if extra else _EMPTY
    if isinstance(shared, list):
        if not isinstance(current, list):
            raise ValueError("current config misses shared list")
        if key != "permissions":
            if shared != current:
                raise ValueError("current config differs from shared setting")
            return _EMPTY
        remaining = list(copy.deepcopy(current))
        for rule in shared:
            try:
                remaining.remove(rule)
            except ValueError as exc:
                raise ValueError("current permissions omit a shared rule") from exc
        return remaining if remaining else _EMPTY
    if shared != current:
        raise ValueError("current config differs from shared setting")
    return _EMPTY


def _without_permissions(value):
    if isinstance(value, dict):
        return {k: _without_permissions(v) for k, v in value.items() if k != "permissions"}
    if isinstance(value, list):
        return [_without_permissions(v) for v in value]
    return value


def _permission_families(config):
    result = {}
    agents = config.get("agents", {}) if isinstance(config, dict) else {}
    if not isinstance(agents, dict):
        return None
    for role, record in agents.items():
        if not isinstance(record, dict) or not isinstance(record.get("permissions", []), list):
            return None
        families = {}
        for rule in record.get("permissions", []):
            if not isinstance(rule, dict) or not isinstance(rule.get("action"), str):
                return None
            action = rule["action"]
            family = "ms14_workflow_*" if action.startswith("ms14_workflow_") else action
            if "*" in action and action not in ("*", "ms14_workflow_*") and family != "ms14_workflow_*":
                return None
            families.setdefault(family, []).append(rule)
        result[role] = families
    return result


def configs_equivalent(left, right):
    if _without_permissions(left) != _without_permissions(right):
        return False
    left_families, right_families = _permission_families(left), _permission_families(right)
    if left_families is None or right_families is None or set(left_families) != set(right_families):
        return False
    for role, families in left_families.items():
        other = right_families[role]
        if set(families) != set(other) or any(families[k] != other[k] for k in families):
            return False
        lrules = left["agents"][role].get("permissions", [])
        rrules = right["agents"][role].get("permissions", [])
        if any(x.get("action") == "*" for x in lrules + rrules) and lrules != rrules:
            return False
    return True


def extract_local_overlay(template, current_config, variables):
    base = _substitute(_template(template)["config"], variables)
    current = _loads(current_config) if isinstance(current_config, (str, bytes)) else copy.deepcopy(current_config)
    residue = _subtract(base, current)
    overlay = {"version": OVERLAY_VERSION, "variables": copy.deepcopy(variables),
               "config": {} if residue is _EMPTY else residue}
    if not configs_equivalent(current, render_config(template, overlay)):
        raise ValueError("shared-rule extraction would change permission behavior")
    return overlay


def preserve_existing_bytes(template, overlay, current_bytes):
    current = _loads(current_bytes)
    generated = render_config(template, overlay)
    if configs_equivalent(current, generated):
        return current_bytes
    return serialize_config(generated).encode("utf-8")


def serialize_config(config):
    return json.dumps(config, indent=2, ensure_ascii=False) + "\n"
