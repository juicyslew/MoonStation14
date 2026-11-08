"""Durable, non-generating dependency circuit policy primitives."""
from __future__ import annotations

from dataclasses import dataclass


BACKOFF_SECONDS = (5, 15, 60)
MAX_BACKOFF_SECONDS = 120


@dataclass(frozen=True)
class ProbePermit:
    dependency_key: str
    generation: int
    proof_id: str
    due_ms: int


def task_deadline_due(task, now_ms, *, paused_ms=0, paused=False):
    """Evaluate a review deadline against active time only; pause is not a verdict."""
    if paused:
        return False
    deadline = task.get("deadline_ms")
    return deadline is not None and int(now_ms) >= int(deadline) + max(0, int(paused_ms))


def pause(store, *, task_id, dependency_key, failure_id, binding, now_ms, evidence_refs=()):
    """Durably open the dependency circuit and pause its exact review assignment."""
    if not all((task_id, dependency_key, failure_id)):
        raise ValueError("task, dependency and failure identifiers are required")
    return store.pause_dependency_task(task_id=task_id, dependency_key=dependency_key,
                                       failure_id=failure_id, binding=binding, now_ms=now_ms, evidence_refs=evidence_refs)


def resume(store, *, task_id, dependency_key, binding, now_ms):
    """Explicitly resume the same assignment after caller-authorized recovery."""
    return store.resume_dependency_task(task_id=task_id, dependency_key=dependency_key,
                                        binding=binding, now_ms=now_ms)
