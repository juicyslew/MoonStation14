import hashlib
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_store

from test_workflow_native_worker import NativeInputLoaderTests


class NativeCheckRecordTests(NativeInputLoaderTests):
    def test_completed_first_check_is_linked_before_second_fails(self):
        producer="from pathlib import Path; p=Path('build/output.zip'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'first artifact')"
        root=self.root/'check-records'
        db=root/'db.sqlite'; marker=root/'second-saw-record'
        second=("import sqlite3,sys; from pathlib import Path; c=sqlite3.connect('file:"+str(db).replace('\\','/')+"?mode=ro',uri=True); "
                "task=c.execute(\"select id from tasks where json_extract(task_json,'$.state')='CLAIMED'\").fetchone()[0]; "
                "n=c.execute('select count(*) from native_check_records where task_id=?',(task,)).fetchone()[0]; "
                "assert n==1,n; Path("+repr(str(marker))+ ").write_text('linked'); sys.exit(127)")
        checks=[{'argv':[sys.executable,'-c',producer],'rationale':'produce package','covers':['c1'],'kind':'build','artifacts':['build/output.zip']},
                {'argv':[sys.executable,'-c',second],'rationale':'verify prior record then infrastructure failure','covers':['c1'],'kind':'command'}]
        store,result,runroot=self.run_case('check-records','pass\n',checks=checks)
        self.assertEqual(result['decision'],'ERROR')
        self.assertTrue(marker.is_file(), 'second check did not observe first linked record')
        connection=store._connect()
        try: task_id=connection.execute('select id from tasks').fetchone()[0]
        finally: connection.close()
        records=store.list_native_check_records(task_id)
        self.assertEqual([row['check_index'] for row in records],[0,1])
        link=store.get_artifact(records[0]['artifact_ref'])['artifact']
        data=Path(link['path']).read_bytes()
        self.assertEqual(hashlib.sha256(data).hexdigest(),link['sha256'])
        payload=workflow_store.strict_loads(data.decode())
        self.assertEqual(payload['kind'],'NATIVE_CHECK_COMPLETED')
        self.assertEqual(payload['check_index'],0)
        self.assertEqual(payload['record']['exit_code'],0)
        saved=store.get_run_submission(store.get_task(task_id)['run_id'])
        self.assertEqual(payload['plan_digest'],saved['request']['plan_digest'])
        self.assertEqual(payload['policy_digest'],saved['request']['policy_digest'])
        self.assertEqual(payload['input_digest'],saved['request']['input_digest'])
        for item in [{'path':payload['record']['log'],'sha256':payload['record']['log_sha256']},*payload['record']['evidence']['artifacts']]:
            path=Path(item['path']); self.assertTrue(path.is_file())
            contents=path.read_bytes()
            self.assertEqual(len(contents),item.get('size',len(contents)))
            self.assertEqual(hashlib.sha256(contents).hexdigest(),item['sha256'])
        self.assertFalse(any(store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT' for ref in result['artifact_refs']))
        failure=workflow_store.strict_loads(Path(store.get_artifact(records[1]['artifact_ref'])['artifact']['path']).read_text())
        self.assertEqual(failure['record']['exit_code'],127)
