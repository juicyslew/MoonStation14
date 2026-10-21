# Future workflow instructions (staged merge input only)

This is a workflow-only section intended for a future, reviewed merge into AGENTS.md. It is not a replacement root instruction file and does not change active AGENTS.md.

- Submit only complete owner-ready features, with criterion-to-evidence matrix and usable manual setup. Use focused checks; full GameTests are post-owner-acceptance except an explicit bound owner exception.
- Coordinator owns scheduling/checks/recovery/delivery. Use only role-bound MCP tools and bounded local file references; never shell-fallback handoffs, invent commands, or create a second scheduler.
- Pending is not a verdict. PASS/FAIL/ERROR are terminal and immutable. ERROR recovers on the same run with retained valid evidence, not a new author revision. API ACK, protocol ACK, accepted task result and owner acceptance are distinct.
- Authors wait for results; no automatic stopped-author restart/nudge. Reviewers inspect only assigned scope and report typed findings or precise ERROR.
- All non-inference state and execution remain local. Activation waits for WR12 fresh-baseline controlled cutover and explicit owner acceptance remains separate.
