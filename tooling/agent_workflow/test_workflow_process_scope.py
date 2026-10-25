import json
import os
import subprocess
import sys
import time
import unittest

from workflow_process_scope import OwnedProcessScope, ProcessScopeError

CHILD_CODE = r'''import json,os,subprocess,sys,time
def ident(pid):
 import ctypes
 from ctypes import wintypes
 k=ctypes.WinDLL('kernel32',use_last_error=True); k.OpenProcess.restype=wintypes.HANDLE; k.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
 h=k.OpenProcess(0x1000,False,pid)
 if not h: raise OSError(ctypes.get_last_error())
 a,b,c,d=(wintypes.FILETIME() for _ in range(4)); k.GetProcessTimes.argtypes=[wintypes.HANDLE,*([ctypes.POINTER(wintypes.FILETIME)]*4)]
 try:
  if not k.GetProcessTimes(h,*[ctypes.byref(x) for x in (a,b,c,d)]): raise OSError(ctypes.get_last_error())
  return {'pid':pid,'created':(a.dwHighDateTime<<32)|a.dwLowDateTime}
 finally: k.CloseHandle(h)
print(json.dumps({'child':ident(os.getpid())}),flush=True)
if sys.stdin.readline().strip()!='GO': raise SystemExit(4)
g=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
print(json.dumps({'grandchild':ident(g.pid)}),flush=True)
time.sleep(60)'''

CREATOR_CODE = r'''import json,os,subprocess,sys,time
from workflow_process_scope import OwnedProcessScope
scope=OwnedProcessScope.create('crash-test')
child=subprocess.Popen([sys.executable,'-u','-c',sys.argv[1]],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
child_id=json.loads(child.stdout.readline())['child']
scope.attach(child_id)
child.stdin.write('GO\n'); child.stdin.flush()
grand_id=json.loads(child.stdout.readline())['grandchild']
import gate_execution
print(json.dumps({'scope_name':scope.name,'creator_identity':gate_execution.process_identity(os.getpid()),'child_identity':child_id,'grandchild_identity':grand_id}),flush=True)
if sys.stdin.readline().strip()!='CRASH': raise SystemExit(5)
os._exit(17)'''


@unittest.skipUnless(os.name == 'nt', 'Windows Job Objects required')
class ProcessScopeTests(unittest.TestCase):
    def test_attached_interpreter_and_later_grandchild_terminate(self):
        import gate_execution
        scope=OwnedProcessScope.create('scope-test')
        unrelated=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
        child=subprocess.Popen([sys.executable,'-u','-c',CHILD_CODE],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
        ids=[]
        try:
            child_id=json.loads(child.stdout.readline())['child']; ids.append(child_id)
            scope.attach(child_id)
            child.stdin.write('GO\n'); child.stdin.flush()
            grand_id=json.loads(child.stdout.readline())['grandchild']; ids.append(grand_id)
            proof=scope.terminate_and_wait(5)
            self.assertEqual((proof['status'],proof['active_processes']),('CONFIRMED',0))
            self.assertTrue(all(gate_execution.identity_state(i)=='dead' for i in ids))
            child.wait(timeout=2)
            self.assertIsNone(unrelated.poll())
        finally:
            scope.close()
            if child.poll() is None: child.kill(); child.wait(timeout=2)
            child.stdout.close(); child.stdin.close()
            if unrelated.poll() is None: unrelated.kill(); unrelated.wait(timeout=2)

    def test_wrong_creation_identity_refused(self):
        import gate_execution
        scope=OwnedProcessScope.create('scope-test'); p=subprocess.Popen([sys.executable,'-c','import time;time.sleep(30)'])
        try:
            ident=gate_execution.process_identity(p.pid)
            with self.assertRaises(ProcessScopeError): scope.attach({'pid':ident['pid'],'created':ident['created']+1})
            self.assertIsNone(p.poll())
        finally:
            scope.close()
            if p.poll() is None: p.kill(); p.wait(timeout=2)

    def test_creator_crash_closes_noninherited_job_and_kills_tree(self):
        import gate_execution
        creator=subprocess.Popen([sys.executable,'-u','-c',CREATOR_CODE,CHILD_CODE],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
        record=None
        try:
            record=json.loads(creator.stdout.readline())
            creator.stdin.write('CRASH\n'); creator.stdin.flush()
            self.assertEqual(creator.wait(timeout=5),17)
            deadline=time.monotonic()+5
            while time.monotonic()<deadline:
                if all(gate_execution.identity_state(record[k])=='dead' for k in ('child_identity','grandchild_identity')): break
                time.sleep(.025)
            self.assertEqual(gate_execution.identity_state(record['child_identity']),'dead')
            self.assertEqual(gate_execution.identity_state(record['grandchild_identity']),'dead')
        finally:
            creator.stdout.close(); creator.stdin.close()
            if creator.poll() is None:
                # The creator owns this scope; only the recorded scope may be opened for cleanup.
                if record:
                    scope=OwnedProcessScope.open_existing(record['scope_name'])
                    if scope:
                        try: scope.terminate_and_wait(5)
                        finally: scope.close()
                creator.kill(); creator.wait(timeout=2)
            if record and creator.returncode != 17:
                scope=OwnedProcessScope.open_existing(record['scope_name'])
                if scope:
                    try: scope.terminate_and_wait(5)
                    finally: scope.close()

    def test_open_existing(self):
        scope=OwnedProcessScope.create('scope-test'); other=OwnedProcessScope.open_existing(scope.name)
        try: self.assertEqual(other.snapshot()['active_processes'],0)
        finally: other.close(); scope.close()

    def test_open_existing_snapshot_terminate_close_preserves_original_handle(self):
        import gate_execution
        from ctypes import wintypes
        scope=OwnedProcessScope.create('scope-reopen-test')
        child=subprocess.Popen([sys.executable,'-u','-c',CHILD_CODE],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
        reopened=None
        try:
            identity=json.loads(child.stdout.readline())['child']
            scope.attach(identity)
            reopened=OwnedProcessScope.open_existing(scope.name)
            self.assertIsNotNone(reopened)
            self.assertEqual(reopened._kernel.QueryInformationJobObject.argtypes[0],wintypes.HANDLE)
            self.assertEqual(reopened._kernel.CloseHandle.argtypes,[wintypes.HANDLE])
            self.assertEqual(reopened._kernel.TerminateJobObject.argtypes,[wintypes.HANDLE,wintypes.UINT])
            self.assertGreaterEqual(reopened.snapshot()['active_processes'],1)
            proof=reopened.terminate_and_wait(5)
            self.assertEqual((proof['status'],proof['active_processes']),('CONFIRMED',0))
            self.assertEqual(scope.snapshot()['active_processes'],0)
            child.wait(timeout=2)
            self.assertEqual(gate_execution.identity_state(identity),'dead')
        finally:
            if reopened is not None: reopened.close()
            scope.close()
            if child.poll() is None: child.kill(); child.wait(timeout=2)
            child.stdout.close(); child.stdin.close()


if __name__ == '__main__': unittest.main()
