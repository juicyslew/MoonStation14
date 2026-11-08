import unittest
import threading
from unittest import mock

import workflow_board
from test_workflow_board_projection import record
from workflow_coordinator import ExplicitCoordinator


class WorkflowBoardActivityTests(unittest.TestCase):
    @staticmethod
    def coordinator(observer, clock):
        c=ExplicitCoordinator.__new__(ExplicitCoordinator)
        c.author_activity_observer=observer
        c.activity_refresh_interval=5
        c.activity_ttl=30
        c._activity_lock=threading.Lock()
        c._activity_state_lock=threading.Lock()
        c._activity_clock=clock
        c._activity_checked=None
        c._activity_result={'checked_at':None,'complete':False,'agents':[],'errors':[]}
        c._activity_semantic=None
        c._board_condition=threading.Condition()
        c._board_revision=0
        c._board_workflow_revision=0
        c._board_subscribers=0
        c._wake=threading.Event()
        return c

    def test_refresh_coalesces_and_only_semantic_changes_notify(self):
        now=[0.0]; calls=[]
        def observe():
            calls.append(1)
            return {'checked_at':100+now[0],'complete':True,'agents':[{'worktree':'/agent04','label':'Agent 04',
                'activity':'idle','summary':'No active work','session_id':''}],'errors':[]}
        c=self.coordinator(observe,lambda:now[0])
        self.assertTrue(c.refresh_author_activity())
        self.assertFalse(c.refresh_author_activity())
        self.assertEqual(len(calls),1)
        self.assertEqual(c._board_revision,1)
        self.assertEqual(c._board_workflow_revision,0)
        self.assertFalse(c._wake.is_set())
        now[0]=5
        self.assertFalse(c.refresh_author_activity()) # timestamp alone is not semantic
        self.assertEqual(c._board_revision,1)
        self.assertEqual(c._board_workflow_revision,0)
        c.notify_committed()
        self.assertEqual(c._board_revision,2)
        self.assertEqual(c._board_workflow_revision,1)
        now[0]=10
        observe_activity=observe
        def changed():
            row=observe_activity()['agents'][0].copy(); row['activity']='working'
            return {'checked_at':110,'complete':True,'agents':[row],'errors':[]}
        c.author_activity_observer=changed
        self.assertTrue(c.refresh_author_activity())
        self.assertEqual(c._board_revision,3)
        self.assertEqual(c._board_workflow_revision,1)
        self.assertEqual(c._board_revision_frame(3),b'event: board-revision\ndata: {"revision":3,"workflow_revision":1}\n\n')

    def test_expired_and_failed_observations_never_preserve_idle(self):
        now=[0.0]
        c=self.coordinator(lambda:{'checked_at':1,'complete':True,'agents':[{'worktree':'/a','label':'Agent 04',
            'activity':'idle','summary':'No active work','session_id':''}],'errors':[]},lambda:now[0])
        c.refresh_author_activity()
        self.assertEqual(c._cached_author_activity()[0]['activity'],'idle')
        now[0]=31
        self.assertEqual(c._cached_author_activity()[0]['activity'],'unknown')
        c.author_activity_observer=lambda:(_ for _ in ()).throw(RuntimeError('private detail'))
        now[0]=32
        c.refresh_author_activity()
        self.assertEqual(c._cached_author_activity()[0]['activity'],'unknown')
        self.assertNotIn('private detail',str(c._activity_result))

    def test_snapshot_reads_only_memory_activity_cache(self):
        c=self.coordinator(lambda:(_ for _ in ()).throw(AssertionError('observer called')),lambda:0)
        c.store=mock.Mock(); c.store.path='db'
        c.summary=lambda:{'ok':True}; c.admission_root=None
        with mock.patch('workflow_coordinator.workflow_store.WorkflowStore.readonly_snapshot',return_value={'available':True,'records':[]}):
            c._board_snapshot_payload('/agent04')
        self.assertIsNone(c._activity_checked)

    def test_activity_placeholders_and_unknown_never_becomes_idle(self):
        for activity, column, status in (("working", "Building", "Working"),
                                         ("waiting", "Building", "Waiting for your input"),
                                         ("idle", "Idle", "Idle"),
                                         ("unknown", "Idle", "Activity unavailable")):
            with self.subTest(activity=activity):
                board=workflow_board.project({"available":True,"records":[]},worktree="/w",
                    author_activity=[{"worktree":"/w","label":"Agent 04","activity":activity,"summary":"Implementing feature"}])
                card=board["columns"][column][0]
                self.assertEqual(card["status_text"],status)
                self.assertFalse(card["capabilities"])

    def test_submitted_workflow_precedes_agent_activity(self):
        item=record("CHECKING")
        board=workflow_board.project({"available":True,"records":[item]},worktree="/w",
            author_activity=[{"worktree":"/w","label":"Agent 04","activity":"idle"}])
        self.assertEqual(len(board["columns"]["Checking"]),1)
        self.assertFalse(board["columns"]["Idle"])
        self.assertEqual(board["columns"]["Checking"][0]["status_text"],"Running checks")

    def test_passed_manual_testing_coexists_with_live_and_idle_author(self):
        item=record("DECIDED",result="PASS")
        for activity,column in (("working","Building"),("idle","Idle")):
            board=workflow_board.project({"available":True,"records":[item]},worktree="/w",
                author_activity=[{"worktree":"/w","label":"Agent 04","activity":activity}])
            self.assertEqual(len(board["columns"]["Your testing"]),1)
            self.assertEqual(len(board["columns"][column]),1)

    def test_accepted_dismissal_requires_clean_current_acceptance(self):
        accepted={"decision_id":"d","run_id":"r","generation":2,"input_digest":"digest",
            "decision":{"decision":"ACCEPT","run_id":"r","evaluation_generation":2,"input_digest":"digest"}}
        acceptance={"acceptance_id":"a","decision_id":"d","run_id":"r","evaluation_generation":2,"input_digest":"digest"}
        item=record("DECIDED","PASS",decisions=(accepted,),extra={"acceptances":[acceptance]})
        card=workflow_board.project({"available":True,"records":[item]},worktree="/w")["columns"]["Accepted"][0]
        self.assertTrue(card["dismissible"])
        self.assertTrue(card["dismiss_key"])
        item["regression_jobs"]=[{"job":{"status":"RUNNING"}}]
        card=workflow_board.project({"available":True,"records":[item]},worktree="/w")["columns"]["Accepted"][0]
        self.assertFalse(card["dismissible"])


if __name__ == "__main__":
    unittest.main()
