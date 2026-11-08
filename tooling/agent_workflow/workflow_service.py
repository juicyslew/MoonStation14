"""Safe operator launcher for the currently published workflow coordinator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import sys
import time
import urllib.error
import urllib.request


def installation(main):
    path = main / '.opencode/workflow/current-installation.json'
    value = json.loads(path.read_text(encoding='utf-8'))
    receipt_path = Path(value['receipt']).resolve()
    receipt_bytes = receipt_path.read_bytes()
    if hashlib.sha256(receipt_bytes).hexdigest() != value['receipt_sha256']:
        raise ValueError('current installation receipt hash mismatch')
    receipt = json.loads(receipt_bytes)
    shared = Path(receipt['shared_root']).resolve()
    return value, receipt, shared


def verify_service(record, config_data, deploy, *, health_file=None, request_factory=None):
    config = Path(record['config']).resolve()
    health_file = Path(health_file or record['health_file']).resolve()
    port = int(record['port'])
    health = json.loads(health_file.read_text(encoding='utf-8'))
    deploy._validate_current_host(health, config, config.read_bytes(), require_sole_member=False)
    with socket.create_connection(('127.0.0.1', port), timeout=1):
        pass
    binding = (config_data.get('owners') or [{}])[0]
    token = Path(binding['token_file']).read_text(encoding='utf-8').strip()
    request = urllib.request.Request(f'http://127.0.0.1:{port}/v1/health', headers={'Authorization': 'Bearer ' + token})
    opener = request_factory or urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=3) as response:
        if response.status != 200:
            raise ValueError('authenticated health request failed')
        snapshot = json.load(response)
    if snapshot.get('status') != 'running':
        raise ValueError('authenticated coordinator health is not running')
    return health, snapshot


def ensure_running(check, start, *, inspect_only=False, recorded_process_alive=False, port_occupied=False):
    try:
        return check(), False
    except (OSError, ValueError, KeyError, urllib.error.URLError):
        if inspect_only:
            raise ValueError('workflow service is not verified')
        if recorded_process_alive:
            raise ValueError('a recorded workflow service process is still alive but unhealthy; refusing duplicate startup')
        if port_occupied:
            raise ValueError('workflow port is occupied by an unverified service; refusing replacement')
        start()
        return check(), True


def main_cli(argv=None):
    parser = argparse.ArgumentParser(description='Inspect or start the published workflow status service')
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--status', action='store_true', help='inspect only; do not start a stopped service')
    parser.add_argument('--open', action='store_true', help='open the verified board in the default browser')
    args = parser.parse_args(argv)
    try:
        root = args.repo.resolve()
        record, receipt, shared = installation(root)
        # deploy_workflow is the MAIN deployment owner in this installation; use
        # its existing startup/validation functions when the shared runtime does
        # not ship that deployment module.
        owner_root = shared if (shared / 'tooling/agent_workflow/deploy_workflow.py').is_file() else root
        sys.path.insert(0, str(owner_root / 'tooling/agent_workflow'))
        import deploy_workflow
        import gate_execution
        if Path(deploy_workflow.__file__).resolve() != (owner_root / 'tooling/agent_workflow/deploy_workflow.py').resolve():
            raise ValueError('startup owner did not load from the verified installation source')
        config, health_file = Path(record['config']).resolve(), Path(record['health_file']).resolve()
        config_data = json.loads(config.read_text(encoding='utf-8'))
        port = int(record['port'])
        python = shared / '.venv-workflow-coordinator/Scripts/python.exe'
        if not python.is_file():
            raise ValueError('installed coordinator interpreter is unavailable')

        def check():
            return verify_service(record, config_data, deploy_workflow)

        def start_host():
            service_path = Path.home() / '.local/state/opencode/service.json'
            service = json.loads(service_path.read_text(encoding='utf-8'))
            if service.get('url') != config_data.get('opencode_server'):
                raise ValueError('OpenCode service endpoint differs from coordinator configuration')
            password = service.get('password')
            if not isinstance(password, str) or not password:
                raise ValueError('OpenCode service credential unavailable')
            env = dict(os.environ)
            env['OPENCODE_PASSWORD'] = password
            deploy_workflow.start_host(config, python, health_file, port, env)

        if args.status:
            health, snapshot = check()
            started = False
        else:
            recorded_alive = False
            try:
                previous = json.loads(health_file.read_text(encoding='utf-8'))
                for identity in (previous.get('supervisor', {}).get('identity'),
                                 (previous.get('child') or {}).get('coordinator_identity')):
                    if isinstance(identity, dict) and gate_execution.process_identity(identity.get('pid')) == identity:
                        recorded_alive = True
            except FileNotFoundError:
                pass
            with socket.socket() as probe:
                occupied = probe.connect_ex(('127.0.0.1', port)) == 0
            service_result = None
            try:
                service_result, started = ensure_running(check, start_host,
                    recorded_process_alive=recorded_alive, port_occupied=occupied)
            except ValueError as exc:
                # Endpoint and credential checks are needed only when startup is actually required.
                if str(exc).startswith('workflow service is not verified'):
                    raise
                if 'recorded workflow service process' in str(exc) or 'port is occupied' in str(exc):
                    raise
                raise
            health, snapshot = service_result
        if not started:
            url = f'http://127.0.0.1:{port}/integration-status'
            print(f"Workflow service {health['state']} at {url}")
            if snapshot.get('orchestration_paused_for_maintenance'):
                print('Service is paused for maintenance; launcher will not unpause it.')
                return 1
        else:
            print(f"Workflow service started ({health['state']}) at http://127.0.0.1:{port}/integration-status")
        if args.open:
            import webbrowser
            webbrowser.open(f'http://127.0.0.1:{port}/integration-status')
        return 0
    except Exception as exc:
        print(f'Workflow service launcher: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main_cli())
