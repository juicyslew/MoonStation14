import unittest

import workflow_review_history
from test_workflow_review_cancellation import Harness as _Harness


class PagedHistoryTests(unittest.TestCase):
    def _case(self, foreign):
        h=_Harness('unused')
        observed=1
        owned={'id':'owned-assistant','sessionID':h.sid,'type':'assistant','time':{'created':30},'text':'assistant response','metadata':{}}
        original={'id':h.mid,'sessionID':h.sid,'type':'user','time':{'created':20},'text':h.message()['payload']['text'],'metadata':h.message()['payload']['metadata']}
        later={'id':'later-user','sessionID':h.sid,'type':'user','time':{'created':25},'text':'unrelated valid user prompt','metadata':{}}
        older={'id':'older-assistant','sessionID':h.sid,'type':'assistant','time':{'created':observed},'text':'before baseline','metadata':{}}
        if foreign:
            h.history=[owned,original,later,older]
        else:
            h.history=[owned,original,older]
        # Exercise the actual paged reader independently of the adapter's guarded use.
        calls=[]
        def request(server,method,path,body=None):
            if method=='GET' and path.endswith('/message?limit=50&order=desc'):
                calls.append(path); return 200,{'data':[owned],'cursor':{'next':'opaque-token'}}
            if method=='GET' and path.endswith('/message?limit=50&cursor=opaque-token'):
                calls.append(path); return 200,{'data':[later if foreign else original,older],'cursor':{}}
            return h.request(server,method,path,body)
        try:
            rows=workflow_review_history.read_since(request,'http://127.0.0.1:4096',h.sid,observed)
            h.request=request
            result=h.adapter.cancel('cancel-x',h.payload())
            if foreign:
                self.assertEqual('UNKNOWN',result)
                self.assertNotEqual('RELEASED',h.store.resource['state'])
                self.assertNotEqual('CANCELLED',h.store.task['state'])
                self.assertFalse(any(m=='DELETE' or (m=='POST' and p.endswith('/interrupt?resume=false')) for m,p in h.calls))
            else:
                self.assertEqual('APPLIED',result)
                self.assertEqual('RELEASED',h.store.resource['state'])
                self.assertEqual('CANCELLED',h.store.task['state'])
            self.assertEqual(2,len(calls))
            self.assertIn('cursor=opaque-token',calls[1]); self.assertNotIn('order=',calls[1])
        except (workflow_review_history.HistoryUnavailable,TypeError,ValueError) as exc:
            self.fail(f'paged history unexpectedly malformed: {exc}')

    def test_foreign_user_on_later_page_blocks_cleanup(self): self._case(True)
    def test_owned_rows_across_pages_allow_cleanup(self): self._case(False)


if __name__=='__main__': unittest.main()
