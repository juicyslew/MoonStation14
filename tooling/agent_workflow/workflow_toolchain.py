"""Deterministic executable/toolchain evidence for native workflow checks."""
import hashlib
import os
import shutil
import sys
from pathlib import Path


def _file(path):
    if not path:
        return {'path': 'MISSING', 'sha256': None, 'size_bytes': None}
    path = Path(path).resolve()
    try:
        data = path.read_bytes()
    except OSError:
        return {'path': str(path), 'sha256': None, 'size_bytes': None}
    return {'path': str(path), 'sha256': hashlib.sha256(data).hexdigest(), 'size_bytes': len(data)}


def _resolve(command, repo):
    name = Path(command).name.lower()
    if name == 'gradlew' and os.name == 'nt':
        candidate = Path(repo) / 'gradlew.bat'
        if candidate.is_file():
            return candidate
    if os.path.isabs(command):
        return Path(command)
    found = shutil.which(command)
    return Path(found) if found else None


def capture(check, repo):
    argv = check.get('argv', []) if isinstance(check, dict) else []
    command = argv[0] if argv else ''
    resolved = Path(sys.executable) if command == sys.executable else _resolve(command, repo)
    result = {'executable': _file(resolved)}
    if Path(command).name.lower() in ('gradle', 'gradlew', 'gradlew.bat'):
        java_home = os.environ.get('JAVA_HOME')
        java = Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java') if java_home else Path(shutil.which('java') or '')
        result['java'] = _file(java if str(java) else None)
        if java_home:
            result['java_home'] = str(Path(java_home).resolve())
        props = Path(repo) / 'gradle' / 'wrapper' / 'gradle-wrapper.properties'
        jar = Path(repo) / 'gradle' / 'wrapper' / 'gradle-wrapper.jar'
        result['gradle_wrapper_properties'] = _file(props)
        result['gradle_wrapper_jar'] = _file(jar)
        # Keep this evidence tied to the exact launcher adaptation used for
        # execution rather than inferring policy from the frozen plan.
        try:
            from workflow_runtime import execution_argv
        except ImportError:
            from tooling.agent_workflow.workflow_runtime import execution_argv
        executed = execution_argv(repo, argv)
        if 'runGameTestServer' in executed:
            script = Path(__file__).resolve().with_name('validation-logging.gradle')
            if not script.is_file() or script.is_symlink():
                raise ValueError('canonical managed validation logging init script is missing or linked')
            debug = [arg.split('=', 1)[1] for arg in executed if arg.startswith('-Pms14ValidationDebug=')]
            if len(debug) != 1 or debug[0] not in ('true', 'false'):
                raise ValueError('managed validation logging mode is missing or invalid')
            result['validation_logging'] = {
                'policy_id': 'ms14-gametest-logging-v1',
                'mode': 'DEBUG' if debug[0] == 'true' else 'INFO',
                'init_script': _file(script),
            }
    return result
