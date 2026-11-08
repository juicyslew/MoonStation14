"""Inspect and, with explicit authorization, retire a settled validation ERROR."""
import argparse
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request
from deploy_workflow import _reviewed_loopback_endpoint


def run(server, run_id, execute=False, command_id=None, opener=None):
    server = _reviewed_loopback_endpoint(server)
    opener = opener or urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(path, data=None):
        req = urllib.request.Request(server.rstrip("/") + path, data=data,
            headers={"Origin": server, "Content-Type": "application/json"} if data else {"Origin": server})
        try:
            with opener.open(req, timeout=15) as response:
                if data is None and path == "/integration-status":
                    response.read()
                    return None
                return json.load(response)
        except urllib.error.HTTPError as exc:
            try: detail = json.loads(exc.read())
            except Exception: detail = {}
            if isinstance(detail.get("error"), dict): detail = detail["error"]
            raise RuntimeError("server HTTP %s code=%s message=%s recovery=%s" %
                (exc.code, detail.get("code", "UNKNOWN"), detail.get("message", ""), detail.get("recovery", "")))

    request("/integration-status")
    snap = request("/v1/owner/snapshot?" + urllib.parse.urlencode({"run_id": run_id}))
    attempt = snap.get("attempt") or {}
    result = snap.get("result") or {}
    state = result.get("decision", result.get("result", attempt.get("result", "UNKNOWN")))
    print("run=%s result=%s generation=%s sequence=%s" % (run_id, state, snap.get("generation"), snap.get("expected_sequence")))
    print("error_code=%s error_message=%s findings=%s" % (result.get("error_code", "NOT RECORDED"), result.get("error_message", "NOT RECORDED"), result.get("findings", "NOT RECORDED")))
    print("inputfreshness=NOT CHECKED; historicalcleanup=%s" % (attempt.get("cleanup_state") or "NOT REPORTED"))
    print("proposed action: %s" % ("retire settled ERROR" if state == "ERROR" else "no retirement; normal fix/owner action"))
    if not execute: return snap
    if state != "ERROR" or attempt.get("phase") != "DECIDED":
        raise RuntimeError("refusing retirement: only a settled terminal ERROR may be retired; FAIL/PASS require normal fix/owner action")
    if not command_id: raise ValueError("--command-id is required with --execute")
    command = {"contract_version": 1, "run_id": run_id, "evaluation_generation": snap.get("generation"),
        "expected_sequence": snap.get("expected_sequence"), "command_id": command_id,
        "actor": "OWNER", "reason": "CANCELLED"}
    receipt = request("/v1/cancel", json.dumps(command).encode())
    if not isinstance(receipt, dict) or receipt.get("accepted") is not True or not isinstance(receipt.get("receipt"), dict):
        raise RuntimeError("cancellation response was not an accepted receipt; inspect the owner UI before retrying")
    settled = receipt["receipt"]
    if settled.get("state") != "RETIRED_HEAD" or settled.get("historical_result") != "ERROR":
        raise RuntimeError("cancellation receipt did not confirm settled ERROR retirement; inspect the owner UI")
    print("Recovery: RETIRED_HEAD; historical result ERROR preserved. Next: prepare a fresh submission through normal validation.")
    return receipt


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", required=True); parser.add_argument("--run-id", required=True)
    parser.add_argument("--execute", action="store_true"); parser.add_argument("--command-id")
    args = parser.parse_args()
    if args.execute and not args.command_id:
        parser.error("--command-id is required with --execute")
    try: run(args.server, args.run_id, args.execute, args.command_id)
    except (ValueError, RuntimeError) as exc:
        parser.exit(2, "error: %s\n" % exc)


if __name__ == "__main__": main()
