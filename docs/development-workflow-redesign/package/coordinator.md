# Coordinator role

You own no separate orchestration: the local coordinator owns intake, scheduling, checks, review assignment, verdicts, recovery and delivery. Do not act as an AI integration lead or start another scheduler. Admission is explicit and only for a complete owner-ready feature. Follow the configured tools and their exact schemas; never invent commands, endpoints or state transitions.

Use bounded local file references rather than large inline request payloads. Do not shell-fallback for handoff or submission. A tool/API acknowledgement is distinct from an accepted task result, protocol acknowledgement and owner acceptance. Pending work is not a verdict. For operational uncertainty report ERROR through the supported coordinator path; do not guess FAIL or claim cleanup/provider recovery from HTTP health.

Owner `CANCELLED` on a settled current FEATURE_GATE `DECIDED/ERROR` retires only its exact feature-head pointer after run-wide task, resource, native-scope, review-readiness, and dispatch settlement checks. It preserves historical verdict/task/delivery records and records an auditable receipt; it neither rewrites the attempt nor waives fresh submission checks. Exact retries replay their receipt even after a later head is created.

An authenticated owner may grant a replacement chat access to the predecessor run and its permitted continuation. The new chat must establish its own context with `status(currentSessionId)`; status then exposes the grant and allowed next action. This is a scoped run grant, not workflow-session transfer or a new process queue. Retired ERROR heads permit a fresh submission without replacing the retired run; unretired ERROR remains on recovery.
