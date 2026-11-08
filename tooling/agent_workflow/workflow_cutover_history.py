"""Explicit, offline preservation of the legacy Integration Status HTML board."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import stat
import time

INDEX = "workflow-cutover-history.json"
BOARD = "integration-status.html"
LABEL = "Saved pre-cutover board (read-only, not live)"
MAX_JSON = 64 * 1024


def _package():
    if __package__:
        from . import workflow_coordinator_package as package
    else:
        import workflow_coordinator_package as package
    return package


def _regular_bytes(path: Path) -> bytes:
    package = _package()
    package.assert_no_reparse(path.parent, path)
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode):
        raise ValueError("history source must be a regular file")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError("history source must be a regular file")
        with os.fdopen(fd, "rb", closefd=False) as stream:
            return stream.read()
    finally:
        os.close(fd)


def _private_root(path: Path, *, create: bool) -> Path:
    base = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
    lexical = Path(os.path.abspath(path))
    if lexical == base or base not in lexical.parents or not lexical.name.startswith("wr12-legacy-history-"):
        raise ValueError("history output must be a unique child of private OpenCode temp")
    package = _package()
    package.assert_no_reparse(base, lexical)
    if create:
        lexical.mkdir(parents=True, exist_ok=False)
    if not lexical.is_dir():
        raise ValueError("history output root must be a new directory")
    package.assert_no_reparse(base, lexical)
    return lexical


def capture(exchange, private_new_output_root, previous_receipt_path, expected_receipt_sha, operation_id):
    """Capture only the known board and publish an exclusive control index.

    This is an explicit administrative primitive. It is not called by normal
    runtime startup and must only be invoked against an authorized fixture or
    an explicitly approved cutover operation.
    """
    package = _package()
    exchange = Path(os.path.abspath(exchange))
    index_path = exchange / INDEX
    package.assert_no_reparse(exchange, index_path)
    if index_path.exists():
        raise ValueError("history control index already exists")
    output = _private_root(Path(private_new_output_root), create=True)
    board_path = exchange / BOARD
    board = _regular_bytes(board_path)
    receipt_path = Path(os.path.abspath(previous_receipt_path))
    receipt = _regular_bytes(receipt_path)
    receipt_sha = hashlib.sha256(receipt).hexdigest()
    if not isinstance(expected_receipt_sha, str) or receipt_sha != expected_receipt_sha:
        raise ValueError("previous publication receipt SHA mismatch")
    json.loads(receipt.decode("utf-8"))
    digest = hashlib.sha256(board).hexdigest()
    saved = output / BOARD
    package.assert_no_reparse(output, saved)
    package._atomic_write(saved, board, exclusive=True)
    captured = time.time()
    manifest = {
        "schema_version": 1, "operation_id": str(operation_id), "label": LABEL,
        "captured_at": captured, "captured_at_ms": int(captured * 1000),
        "source": "legacy integration-status.html", "read_only": True,
        "board": {"path": str(saved), "sha256": digest},
        "previous_receipt": {"path": str(receipt_path), "sha256": receipt_sha},
    }
    manifest_path = output / "manifest.json"
    manifest_bytes = (json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")
    package._atomic_write(manifest_path, manifest_bytes, exclusive=True)
    index = {"schema_version": 1, "manifest": str(manifest_path),
             "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest()}
    package.assert_no_reparse(exchange, index_path)
    package._atomic_write(index_path,
                          (json.dumps(index, sort_keys=True, indent=2) + "\n").encode(), exclusive=True)
    return index


def read_projection(admission_root):
    """Return bounded, privacy-minimal optional legacy-board metadata."""
    root = Path(os.path.abspath(admission_root))
    index_path = root / INDEX
    try:
        package = _package()
        package.assert_no_reparse(root, index_path)
        if not index_path.exists():
            return None
        raw_index = _regular_bytes(index_path)
        if len(raw_index) > MAX_JSON:
            raise ValueError("control index exceeds size limit")
        index = json.loads(raw_index.decode("utf-8"))
        if not isinstance(index, dict) or index.get("schema_version") != 1:
            raise ValueError("invalid control index")
        manifest_path = Path(index.get("manifest", ""))
        if not manifest_path.is_absolute():
            raise ValueError("manifest path must be absolute")
        private_base = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
        package.assert_no_reparse(private_base, manifest_path)
        manifest_bytes = _regular_bytes(manifest_path)
        if len(manifest_bytes) > MAX_JSON or hashlib.sha256(manifest_bytes).hexdigest() != index.get("manifest_sha256"):
            raise ValueError("manifest hash or size invalid")
        manifest = json.loads(manifest_bytes.decode("utf-8"))
        if not isinstance(manifest, dict) or manifest.get("schema_version") != 1 or manifest.get("read_only") is not True:
            raise ValueError("invalid history manifest")
        if manifest.get("source") != "legacy integration-status.html" or not isinstance(manifest.get("operation_id"), str):
            raise ValueError("invalid history provenance")
        board_info = manifest.get("board")
        board_path = Path(board_info.get("path", "")) if isinstance(board_info, dict) else Path()
        if not board_path.is_absolute():
            raise ValueError("board path must be absolute")
        package.assert_no_reparse(private_base, board_path)
        board = _regular_bytes(board_path)
        board_sha = hashlib.sha256(board).hexdigest()
        if board_sha != board_info.get("sha256"):
            raise ValueError("saved board hash mismatch")
        receipt = manifest.get("previous_receipt")
        if not isinstance(receipt, dict) or not isinstance(receipt.get("sha256"), str) or len(receipt["sha256"]) != 64:
            raise ValueError("invalid receipt provenance")
        captured = manifest.get("captured_at_ms")
        if not isinstance(captured, int) or isinstance(captured, bool) or captured < 0:
            raise ValueError("invalid capture time")
        return {"available": True, "label": LABEL, "path": str(board_path),
                "sha256": board_sha, "captured_at_ms": captured}
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError):
        return {"available": False, "reason": "Saved history files are unavailable or invalid.",
                "control_index": str(index_path)}
