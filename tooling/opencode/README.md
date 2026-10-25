# Shared OpenCode configuration

`shared-config.jsonc` is the tracked shared policy template. Edit that file,
then run `python tooling/opencode/config.py check`. After all five configurations
are bound to the current installation receipt, use `python tooling/opencode/config.py init`
once to create receipt-owned overlays; later shared-template updates use
`python tooling/opencode/config.py apply`. Both commands use the existing
receipt-backed deployment preflight and deployer. The ignored `.opencode/workflow/opencode-local.json`
file is a generated local binding overlay, not shared policy; do not edit or
stage it. Do not edit the generated root `opencode.jsonc` files directly.

Only the shared template is merged. A new checkout must first receive its
existing local binding through the workflow binding owner; do not copy private
OpenCode configuration or MCP credentials between checkouts. Provisioning and
deployment require the existing receipt-backed publisher/deployer workflow.
No fake commit is part of migration; commit migration changes through normal
review. The legacy `publish_workflow.ps1` is not the canonical entry point for
this configuration workflow.
