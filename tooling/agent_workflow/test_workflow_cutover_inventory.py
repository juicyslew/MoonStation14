import unittest
import hashlib
import json
import os
import shlex
import tempfile
from pathlib import Path
from unittest.mock import patch

try:
    import workflow_cutover_inventory as inventory
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_cutover_inventory as inventory


class InventoryTests(unittest.TestCase):
    @staticmethod
    def _argv(command):
        return [part.strip('"') for part in shlex.split(command, posix=False)]

    def test_default_reader_is_bounded_readonly_powershell_cim(self):
        class Result: stdout='[]'
        with patch.object(inventory.subprocess, 'run', return_value=Result()) as run:
            self.assertEqual(inventory._query_processes(), [])
        args, kwargs = run.call_args
        self.assertIn('Get-CimInstance Win32_Process', args[0][-1])
        self.assertEqual(kwargs['timeout'], 8)
        self.assertTrue(kwargs['check'])

    def test_query_failure_holds_without_claiming_clear(self):
        class Barrier:
            root='C:/exchange'
            def recheck(self): return {'status':'OBSERVED_ZERO','jobs':[]}
        result = inventory.collect(Barrier(), 'missing', 'bad', {'pid':1,'created':1}, 'C:/shared',
                                   query_processes=lambda: (_ for _ in ()).throw(OSError('query failed')))
        self.assertEqual(result['status'], 'HELD')

    def test_snapshot_schema_rejects_non_rows(self):
        class Result: stdout='null'
        with patch.object(inventory.subprocess, 'run', return_value=Result()):
            with self.assertRaises(ValueError): inventory._query_processes()

    def test_inventory_digest_matches_stop_consumers_exclusion_rule(self):
        body = {'version':1,'exchange_root':'C:/exchange','observer_identity':{'pid':9,'created':77},
                'captured_at_ms':100,'known_owned_states':[],'eligible_active':[],'unknown':[],
                'checked_scope':'fixture','scope_owner':'fixture'}
        body['source_digest'] = inventory._digest(body)
        expected = inventory._digest({k:v for k,v in body.items() if k != 'source_digest'})
        self.assertEqual(body['source_digest'], expected)

    def _fixture(self, base, jobs=None, record=None):
        shared=base/'shared'; source=shared/inventory.stop.OBSERVER_RELATIVE
        source.parent.mkdir(parents=True); source.write_text('# observer fixture\n')
        exchange=base/'exchange'; exchange.mkdir()
        approved=base/'local'/'Temp'/'opencode'; approved.mkdir(parents=True)
        source_hash=hashlib.sha256(source.read_bytes()).hexdigest()
        receipt=approved/'receipt.json'
        receipt.write_text(json.dumps({'shared_root':str(shared),'files':[{'target':'shared','path':inventory.stop.OBSERVER_RELATIVE.as_posix(),'deleted':False,'new_sha256':source_hash}]}))
        receipt_hash=hashlib.sha256(receipt.read_bytes()).hexdigest()
        job_rows=jobs or []
        for row in job_rows:
            path=exchange/'gate-execution'/f"{row['job_id']}.json"; path.parent.mkdir(exist_ok=True)
            path.write_text(json.dumps(record or {'job_id':row['job_id'],'state':'interrupted','cleanup':{'status':'no_child_recorded'}}))
            row['sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
        class Barrier:
            root=exchange
            def __init__(self): self.snapshots=0
            def recheck(self):
                self.snapshots+=1
                return {'status':'OBSERVED_ZERO','jobs':[dict(r) for r in job_rows]}
        command=f'python.exe "{source}" --exchange "{exchange}" --watch'
        process={'ProcessId':501,'ParentProcessId':1,'CreationDate':'date','ExecutablePath':'python.exe','Name':'python.exe','CommandLine':command}
        return Barrier(),receipt,receipt_hash,shared,exchange,process

    def test_collector_clear_is_consumed_by_production_validator_and_historical_barrier_row(self):
        with tempfile.TemporaryDirectory() as d:
            base=Path(d); row={'job_id':'historical','sha256':'pending','limitation':'historical_no_child_recorded_not_cleanup_proof'}
            barrier,receipt,receipt_hash,shared,exchange,observer=self._fixture(base,[row])
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), \
                 patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}), \
                 patch.object(inventory.gate_execution,'read',return_value={'job_id':'historical','state':'interrupted','cleanup':{'status':'no_child_recorded'}}), \
                 patch.object(inventory.gate_execution,'identity_state') as identity_state:
                identity_state.side_effect=AssertionError('historical row has no process identity to query')
                result=inventory.collect(barrier,receipt,receipt_hash,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer],clock_ms=lambda:1000,
                    argv_parser=self._argv)
            self.assertEqual('OBSERVED_CLEAR',result['status'],result)
            self.assertEqual('historical_unrecorded',result['inventory']['known_owned_states'][0]['identity_state'])
            plan=result['observer_plan']; plan['pid']=501;plan['created']=88
            inventory.stop._validate_inventory(plan,barrier.recheck(),lambda:1000,30000)
            self.assertEqual(3,barrier.snapshots)
            identity_state.assert_not_called()

    def test_worker_java_and_name_only_native_rows_hold(self):
        variants=[
            {'ProcessId':602,'ExecutablePath':'python.exe','Name':'python.exe','CommandLine':f'python.exe {inventory.stop.OBSERVER_RELATIVE} --exchange C:/x --watch workflow_native_worker.py'},
            {'ProcessId':603,'ExecutablePath':'python.exe','Name':'python.exe','CommandLine':'python.exe C:/other/task.py'},
            {'ProcessId':604,'ExecutablePath':'other.exe','Name':'javaw.exe','CommandLine':''},
            {'ProcessId':605,'ExecutablePath':'python.exe','Name':'java.exe','CommandLine':'python.exe unrelated'},
            {'ProcessId':606,'ExecutablePath':'gradle.exe','Name':'gradle.exe','CommandLine':''},
            {'ProcessId':607,'ExecutablePath':'python.exe','Name':'python.exe','CommandLine':None},
        ]
        for row in variants:
            with self.subTest(row=row):
                with tempfile.TemporaryDirectory() as d:
                    base=Path(d); barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
                    with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}):
                        result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                            query_processes=lambda:[observer,row],clock_ms=lambda:1000,
                            argv_parser=self._argv)
                    expected='OBSERVED_CLEAR' if row['ProcessId'] in (602,603) else 'HELD'
                    self.assertEqual(expected,result['status'],result)

    def test_recorded_identity_alive_or_unknown_holds(self):
        for state in ('alive','unknown'):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as d:
                base=Path(d); row={'job_id':'job','sha256':'pending'}
                record={'job_id':'job','state':'interrupted','worker_identity':{'pid':900,'created':12}}
                barrier,receipt,rhash,shared,exchange,observer=self._fixture(base,[row],record)
                with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), \
                     patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}), \
                     patch.object(inventory.gate_execution,'read',return_value=record), \
                     patch.object(inventory.gate_execution,'identity_state',return_value=state):
                    result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                        query_processes=lambda:[observer],clock_ms=lambda:1000,
                        argv_parser=self._argv)
                self.assertEqual('HELD',result['status'])

    def test_reused_pid_ambiguity_and_jobset_change_hold(self):
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':87},shared,
                    query_processes=lambda:[observer],clock_ms=lambda:1000,
                    argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])

    def test_realistic_foreign_cim_metadata_does_not_block_valid_observer(self):
        with tempfile.TemporaryDirectory() as d:
            base=Path(d); barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            rows=[{'ProcessId':0,'Name':'System Idle Process','ExecutablePath':None,'CommandLine':None},
                  {'ProcessId':4,'Name':'System','ExecutablePath':None,'CommandLine':None},
                  {'ProcessId':120,'Name':'svchost.exe','ExecutablePath':None,'CommandLine':None},observer]
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), \
                 patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                    query_processes=lambda:rows,clock_ms=lambda:1000,argv_parser=self._argv)
            self.assertEqual('OBSERVED_CLEAR',result['status'],result)
            self.assertEqual(3,result['evidence']['foreign_process_count'])
            self.assertNotIn('CommandLine',json.dumps(result['evidence']))
            plan=result['observer_plan']; plan['pid']=501; plan['created']=88
            inventory.stop._validate_inventory(plan,barrier.recheck(),lambda:1000,30000)

    def test_only_executed_worker_entrypoint_is_classified_as_work(self):
        commands=[
            ('python.exe','python.exe',"python.exe -c \"open('workflow_native_worker.py').read()\""),
            ('python.exe','python.exe',"python.exe -c \"print('java.exe workflow_native_worker.py gradlew.bat')\""),
            ('python.exe','python.exe','python.exe C:/other/reader.py workflow_native_worker.py'),
            ('opencode.exe','opencode.exe','opencode.exe api POST {"message":"java.exe workflow_native_worker.py"}'),
        ]
        for name,executable,command in commands:
            with self.subTest(command=command), tempfile.TemporaryDirectory() as d:
                base=Path(d); barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
                incidental={'ProcessId':702,'Name':name,'ExecutablePath':executable,'CommandLine':command}
                with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}):
                    result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                        query_processes=lambda:[observer,incidental],clock_ms=lambda:1000,argv_parser=self._argv)
                self.assertEqual('OBSERVED_CLEAR',result['status'],result)
        with tempfile.TemporaryDirectory() as d:
            base=Path(d); barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            actual={'ProcessId':703,'Name':'python.exe','ExecutablePath':'python.exe',
                    'CommandLine':f'python.exe -E -u "{shared}/tooling/agent_workflow/workflow_native_worker.py" run --database "{exchange}/workflow.db"'}
            gate={'ProcessId':704,'Name':'python.exe','ExecutablePath':'python.exe',
                  'CommandLine':f'python.exe "{Path(inventory.gate_execution.__file__).resolve()}" --worker "{exchange}" candidate claim 1'}
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer,actual],clock_ms=lambda:1000,argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])
            # Either concrete owned worker entrypoint is recognized by argv position.
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer,gate],clock_ms=lambda:1000,argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])

    def test_stale_receipt_and_malformed_cim_rows_hold(self):
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}):
                result=inventory.collect(barrier,receipt,'0'*64,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer],clock_ms=lambda:1000,
                    argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer,{'ProcessId':22,'Name':'python.exe','ExecutablePath':'python.exe','CommandLine':None}],clock_ms=lambda:1000,
                    argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer,dict(observer)],clock_ms=lambda:1000,
                    argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);barrier,receipt,rhash,shared,exchange,observer=self._fixture(base)
            original=barrier.recheck
            def changing():
                snapshot=original()
                if barrier.snapshots==2: snapshot['jobs']=[{'job_id':'new','sha256':'0'*64}]
                return snapshot
            barrier.recheck=changing
            with patch.dict(os.environ,{'LOCALAPPDATA':str(base/'local')}), patch.object(inventory.gate_execution,'process_identity',return_value={'pid':501,'created':88}):
                result=inventory.collect(barrier,receipt,rhash,{'pid':501,'created':88},shared,
                    query_processes=lambda:[observer],clock_ms=lambda:1000,
                    argv_parser=self._argv)
            self.assertEqual('HELD',result['status'])


if __name__ == '__main__': unittest.main()
