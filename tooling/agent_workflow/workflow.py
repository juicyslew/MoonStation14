"""Local entrypoint template binding this worktree to the single shared CLI."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


SHARED_ROOT = Path(__file__).resolve().parents[3] / "MoonStation14-shared"
SHARED_PACKAGE = SHARED_ROOT / "tooling" / "agent_workflow"
SHARED_WORKFLOW = SHARED_PACKAGE / "workflow.py"
REQUIRED_RUNTIME = (SHARED_WORKFLOW, SHARED_PACKAGE / "validation_evidence.py", SHARED_PACKAGE / "gate_dispatch.py")

missing = [path for path in REQUIRED_RUNTIME if not path.is_file()]
if missing:
    raise SystemExit(
        f"Shared workflow runtime is incomplete; missing: {', '.join(map(str, missing))}. "
        "Ask the integration owner to publish the shared workflow; this shim has no local fallback."
    )

# Keep the caller's cwd and arguments intact; the shared CLI's --repo defaults
# relative to that caller context. Put shared imports ahead of the local shim.
sys.path[:0] = [str(SHARED_ROOT), str(SHARED_PACKAGE)]
_SPEC = importlib.util.spec_from_file_location("moonstation14_shared_workflow", SHARED_WORKFLOW)
if _SPEC is None or _SPEC.loader is None:
    raise SystemExit(f"Could not load shared workflow runtime at {SHARED_WORKFLOW}")
_MODULE = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _MODULE
_SPEC.loader.exec_module(_MODULE)

if __name__ == "__main__":
    raise SystemExit(_MODULE.main())
