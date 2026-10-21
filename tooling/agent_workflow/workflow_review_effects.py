"""Shared cross-process serialization for review-session side effects."""
import hashlib
from pathlib import Path

try:
    import workflow_runtime
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_runtime


def review_effect_guard(store, server, session_id, *, nonblocking=True):
    """Lock a store/server/session tuple using the workflow's common mutex owner."""
    identity = '\0'.join((str(Path(store.path).expanduser().resolve()).casefold(),
                           str(server).rstrip('/').casefold(), str(session_id)))
    key = hashlib.sha256(identity.encode()).hexdigest()
    return workflow_runtime.gate_lock(Path(store.path).resolve().parent / 'review-effect-locks' / key,
                                      nonblocking=nonblocking)
