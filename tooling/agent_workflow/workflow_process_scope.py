"""Windows Job Object ownership for an exactly identified worker process tree."""
import ctypes
import os
import secrets
import time
from ctypes import wintypes


class ProcessScopeError(RuntimeError):
    pass


class OwnedProcessScope:
    """Owns a non-inheritable kill-on-close Windows Job Object handle."""
    def __init__(self, handle, name):
        self._handle = handle
        self.name = name
        self._identities = []
        self._kernel = self._kernel_api()

    @staticmethod
    def _kernel_api():
        k=ctypes.WinDLL('kernel32', use_last_error=True)
        k.CreateJobObjectW.argtypes=[ctypes.c_void_p,wintypes.LPCWSTR]; k.CreateJobObjectW.restype=wintypes.HANDLE
        k.OpenJobObjectW.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.LPCWSTR]; k.OpenJobObjectW.restype=wintypes.HANDLE
        k.CloseHandle.argtypes=[wintypes.HANDLE]; k.CloseHandle.restype=wintypes.BOOL
        k.SetInformationJobObject.argtypes=[wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD]; k.SetInformationJobObject.restype=wintypes.BOOL
        k.QueryInformationJobObject.argtypes=[wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD,ctypes.c_void_p]; k.QueryInformationJobObject.restype=wintypes.BOOL
        k.TerminateJobObject.argtypes=[wintypes.HANDLE,wintypes.UINT]; k.TerminateJobObject.restype=wintypes.BOOL
        k.AssignProcessToJobObject.argtypes=[wintypes.HANDLE,wintypes.HANDLE]; k.AssignProcessToJobObject.restype=wintypes.BOOL
        k.IsProcessInJob.argtypes=[wintypes.HANDLE,wintypes.HANDLE,ctypes.POINTER(wintypes.BOOL)]; k.IsProcessInJob.restype=wintypes.BOOL
        k.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]; k.OpenProcess.restype=wintypes.HANDLE
        return k

    @classmethod
    def _windows(cls):
        if os.name != 'nt':
            raise ProcessScopeError('native process scopes are supported only on Windows')

    @classmethod
    def create(cls, unique_program_name):
        cls._windows()
        if not isinstance(unique_program_name, str) or not unique_program_name or any(c in unique_program_name for c in '\\/:'):
            raise ValueError('program name must be a non-empty opaque name')
        name = f'Local\\MoonStation14-{unique_program_name}-{secrets.token_hex(16)}'
        k = cls._kernel_api()
        k.CreateJobObjectW.restype = wintypes.HANDLE
        k.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        k.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
        k.SetInformationJobObject.restype = wintypes.BOOL
        k.CloseHandle.argtypes = [wintypes.HANDLE]; k.CloseHandle.restype = wintypes.BOOL
        h = k.CreateJobObjectW(None, name)
        if not h: raise OSError(ctypes.get_last_error(), 'CreateJobObjectW failed')
        try:
            info = _EXTENDED_LIMIT_INFORMATION()
            info.BasicLimitInformation.LimitFlags = 0x00002000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            if not k.SetInformationJobObject(h, 9, ctypes.byref(info), ctypes.sizeof(info)):
                raise OSError(ctypes.get_last_error(), 'SetInformationJobObject failed')
            return cls(h, name)
        except Exception:
            k.CloseHandle(h)
            raise

    @classmethod
    def open_existing(cls, name):
        cls._windows()
        k = cls._kernel_api()
        k.OpenJobObjectW.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.LPCWSTR]
        k.CloseHandle.argtypes = [wintypes.HANDLE]; k.CloseHandle.restype = wintypes.BOOL
        h = k.OpenJobObjectW(0x0004 | 0x0008, False, name)
        if not h:
            if ctypes.get_last_error() == 2: return None
            raise OSError(ctypes.get_last_error(), 'OpenJobObjectW failed')
        return cls(h, name)

    def attach(self, raw_identity):
        if not isinstance(raw_identity, dict) or not isinstance(raw_identity.get('pid'), int) or raw_identity.get('created') is None:
            raise ValueError('raw PID and creation identity required')
        try:
            from gate_execution import process_identity
        except ModuleNotFoundError:
            from tooling.agent_workflow.gate_execution import process_identity
        k = self._kernel
        k.OpenProcess.restype = wintypes.HANDLE
        k.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        k.GetProcessTimes.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME)]; k.GetProcessTimes.restype = wintypes.BOOL
        k.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]; k.GetExitCodeProcess.restype = wintypes.BOOL
        k.CloseHandle.argtypes = [wintypes.HANDLE]; k.CloseHandle.restype = wintypes.BOOL
        k.AssignProcessToJobObject.restype = wintypes.BOOL
        k.QueryInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p]; k.QueryInformationJobObject.restype = wintypes.BOOL
        k.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]; k.TerminateJobObject.restype = wintypes.BOOL
        proc = k.OpenProcess(0x0001 | 0x0100 | 0x1000, False, raw_identity['pid'])
        if not proc: raise ProcessScopeError('exact process could not be opened')
        try:
            current = process_identity(raw_identity['pid'], handle=proc)
            if current != raw_identity: raise ProcessScopeError('process creation identity mismatch or process exited')
            k.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
            if not k.AssignProcessToJobObject(self._handle, proc):
                raise OSError(ctypes.get_last_error(), 'AssignProcessToJobObject failed; no unsafe fallback')
            self._identities.append(dict(raw_identity))
        finally: k.CloseHandle(proc)

    def snapshot(self):
        info = _BASIC_ACCOUNTING_INFORMATION()
        if not self._kernel.QueryInformationJobObject(self._handle, 1, ctypes.byref(info), ctypes.sizeof(info), None):
            raise OSError(ctypes.get_last_error(), 'QueryInformationJobObject failed')
        return {'name': self.name, 'active_processes': int(info.ActiveProcesses), 'identities': list(self._identities)}

    def contains(self, raw_identity):
        """Verify an exact process identity is currently a member of this job."""
        try:
            from gate_execution import process_identity
        except ModuleNotFoundError:
            from tooling.agent_workflow.gate_execution import process_identity
        k=self._kernel
        k.OpenProcess.restype=wintypes.HANDLE
        k.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
        k.IsProcessInJob.argtypes=[wintypes.HANDLE,wintypes.HANDLE,ctypes.POINTER(wintypes.BOOL)]
        k.IsProcessInJob.restype=wintypes.BOOL
        k.CloseHandle.argtypes=[wintypes.HANDLE]; k.CloseHandle.restype=wintypes.BOOL
        p=k.OpenProcess(0x0100|0x1000,False,raw_identity['pid'])
        if not p:return False
        try:
            if process_identity(raw_identity['pid'],handle=p)!=raw_identity:return False
            member=wintypes.BOOL()
            if not k.IsProcessInJob(p,self._handle,ctypes.byref(member)):
                raise OSError(ctypes.get_last_error(),'IsProcessInJob failed')
            return bool(member.value)
        finally:k.CloseHandle(p)

    def terminate_and_wait(self, timeout=5):
        if timeout < 0 or timeout > 5: raise ValueError('timeout must be between zero and five seconds')
        k=self._kernel
        if not k.TerminateJobObject(self._handle, 1): raise OSError(ctypes.get_last_error(), 'TerminateJobObject failed')
        end=time.monotonic()+timeout
        while True:
            state=self.snapshot()
            if state['active_processes']==0: return {'status':'CONFIRMED', **state}
            if time.monotonic()>=end: return {'status':'UNRESOLVED', **state}
            time.sleep(min(.025,end-time.monotonic()))

    def close(self):
        if self._handle:
            h,self._handle=self._handle,None
            if not self._kernel.CloseHandle(h): raise OSError(ctypes.get_last_error(), 'CloseHandle failed')


class _BASIC_LIMIT_INFORMATION(ctypes.Structure):
    _fields_=[('PerProcessUserTimeLimit',ctypes.c_longlong),('PerJobUserTimeLimit',ctypes.c_longlong),('LimitFlags',wintypes.DWORD),('MinimumWorkingSetSize',ctypes.c_size_t),('MaximumWorkingSetSize',ctypes.c_size_t),('ActiveProcessLimit',wintypes.DWORD),('Affinity',ctypes.c_size_t),('PriorityClass',wintypes.DWORD),('SchedulingClass',wintypes.DWORD)]
class _IO_COUNTERS(ctypes.Structure):
    _fields_=[('ReadOperationCount',ctypes.c_ulonglong),('WriteOperationCount',ctypes.c_ulonglong),('OtherOperationCount',ctypes.c_ulonglong),('ReadTransferCount',ctypes.c_ulonglong),('WriteTransferCount',ctypes.c_ulonglong),('OtherTransferCount',ctypes.c_ulonglong)]
class _EXTENDED_LIMIT_INFORMATION(ctypes.Structure):
    _fields_=[('BasicLimitInformation',_BASIC_LIMIT_INFORMATION),('IoInfo',_IO_COUNTERS),('ProcessMemoryLimit',ctypes.c_size_t),('JobMemoryLimit',ctypes.c_size_t),('PeakProcessMemoryUsed',ctypes.c_size_t),('PeakJobMemoryUsed',ctypes.c_size_t)]
class _BASIC_ACCOUNTING_INFORMATION(ctypes.Structure):
    _fields_=[('TotalUserTime',ctypes.c_longlong),('TotalKernelTime',ctypes.c_longlong),('ThisPeriodTotalUserTime',ctypes.c_longlong),('ThisPeriodTotalKernelTime',ctypes.c_longlong),('TotalPageFaultCount',wintypes.DWORD),('TotalProcesses',wintypes.DWORD),('ActiveProcesses',wintypes.DWORD),('TotalTerminatedProcesses',wintypes.DWORD)]
