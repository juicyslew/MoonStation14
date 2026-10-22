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
    return result
