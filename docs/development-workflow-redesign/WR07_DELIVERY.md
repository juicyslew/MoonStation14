# WR07: delivery, owner decisions and accepted-source regression

WR07 technically READY after independent review: 307 distinct current cases satisfied (306 full-run passes plus the corrected, independently verified factory assertion; production hashes unchanged). These are inactive development sources, not a live publication or real owner acceptance.

## Authority and delivery

`workflow_result_delivery.AuthorResultDelivery` consumes saved immutable result events and exact AUTHOR delivery rows. It reuses the existing OpenCode prompt acknowledgement validator and store delivery leases/receipts. The initial message has stable identity and complete author ACK arguments; API transport acceptance, author protocol ACK and owner acceptance remain distinct. Accepted transport or protocol ACK prevents another POST. Unknown transport retains a precise delivery incident, not permission to rerun checks or invent another author revision.

The author SDK adds `acknowledge_result`; its server-bound author identity cannot be appointed in request JSON. Exact event, generation, result ID/digest and command identity are checked through existing store authority. ACK can arrive before transport recording without regressing the final ACK state. No stopped-author reconstruction, replacement or nudging is included.

Owner REQUEST_CHANGES/CANCEL and regression FAIL/ERROR use their own control-feedback events, author deliveries and acknowledgements. Shared delivery insertion, leases and transport checks remain the owners; control feedback is not a fabricated replacement feature result. Owner ACCEPT/queued-regression and regression PASS need no author inference and are saved projection hints, not claims of human consumption.

## Preserved owner snapshot

New accepted submissions freeze `source-snapshot.zip` alongside the existing registered source manifest. Every FILE row is byte/hash checked; archive paths and timestamps are deterministic. Tracked changes and nonignored untracked source are preserved; deleted source stays absent. The ZIP is external, immutable, submission-produced provenance—not a second mutable job record.

`workflow_owner.OwnerService` verifies registered manifest, archive and accepted BUILD bytes and extracts a verified copy under an external acceptance cache. It never restores or reserves the author's working tree and does not infer acceptance from chat. The displayed result, input, generation and sequence are bound to an authenticated local owner action. The owner can accept the originally displayed build even after live source changes; replay uses the saved decision rather than re-reading later mutable files.

Manifest/archive candidates must be unambiguous and bound to the same submission producer/input. The canonical manifest's semantic digest must equal the accepted input, independently of its artifact-file hash. Reusing an acceptance under a new command is allowed only for the same bound decision/evidence/program inputs; conflicting content is rejected, not reported as an apparent replay success.

`WorkflowStore.record_owner_decision` commits owner decisions and, for ACCEPT only, one acceptance and one regression reservation/wake atomically. REQUEST_CHANGES and CANCEL are separate durable control feedback, not fabricated test FAILs. Feature-gate PASS history stays immutable. Authentic owner evidence is stored in these SQL records; no unsupported generic OWNER artifact producer is claimed.

Explicit configuration may supply `owners` with local bearer token files, callers and worktree scope; human session IDs may be null. `/v1/owner/snapshot?run_id=...` provides the saved displayed result/manual setup/evidence, and `/v1/owner/decision` accepts a typed decision with displayed result ID/digest. The server supplies owner identity. Author/reviewer credentials cannot appoint an owner. Merely viewing/testing a snapshot is not acceptance; no owner action requires an AI-provider query.

## Separate code-only regression

`admit_regression_job` creates a distinct `POST_ACCEPTANCE_REGRESSION` run and one native CHECK from the saved acceptance/program inputs. It does not reopen the focused run, create an OWNER_READY submission, or start reviewers. `RegressionAdmission` handles the saved wake; the existing native backend, shared finite runner, typed evidence parser and SQL resource ownership execute it.

Regression input resolution checks the accepted archive and copied source instead of the author's current Git/worktree state. The fixed program plan runs full GameTests with a separate fixture directory and no tag filter. It is not the ordinary pre-acceptance full-suite exception path. Tests use a cheap batch wrapper emitting real typed producer markers; no actual Gradle/Minecraft suite was run.

Initial execution requires the exact manifest FILE set: an additional source file is not silently accepted. Full-regression PASS additionally requires the existing producer's full-selection proof and matching selected/total counts. A filtered exit-zero run is ERROR, not full-suite success. The native task deadline is an absolute program-clock deadline, not a duration mistaken for an epoch timestamp.

The existing transition/finality guards produce a separate regression-job result and `REGRESSION_DECIDED` event. Feature result, owner acceptance and regression outcome remain independent. Regression FAIL is not retroactive owner rejection, and delivery repair cannot rerun validation. Snapshot copies and evidence remain retained for provenance; disposable validation directories alone may be removed.

## Validation and boundaries

Focused tests cover source archive contents/replay, real owned native execution from an accepted copy, terminal regression PASS/FAIL separation, owner storage transactions, author transport/ACK races and a real coordinator-thread acceptance-to-regression flow. Official SDK author/reviewer catalogs stay separately bound. Test counts and final review evidence are recorded in TASKS.md after acceptance, not inferred from worker reports.

Real OpenCode prompt/inbox/role semantics, provider and cancellation recovery remain WR08/WR11 boundaries; unknown state is held rather than assumed absent. WEBPAGE projection/human-seen state belongs to WR09. No live jobs, installed configuration, publication, native Minecraft tests, commits or owner acceptance are authorized by this unit.
