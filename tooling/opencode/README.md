# Shared OpenCode configuration

`shared-config.jsonc` is the tracked shared policy template. Edit that file,
then run `python tooling/opencode/config.py check`. After all five configurations
are bound to the current installation receipt, use `python tooling/opencode/config.py init`
once to create receipt-owned overlays; later shared-template updates use
`python tooling/opencode/config.py apply`. Both commands use the existing
receipt-backed deployment preflight and deployer. The ignored `.opencode/workflow/opencode-local.json`
file is a generated local binding overlay, not shared policy; do not edit or
stage it. Do not edit the generated root `opencode.jsonc` files directly.
`check` reports repairable generated-output drift as `STALE`; initialized
`apply` regenerates the root file from the canonical template and its
receipt-owned overlay. It does not adopt the current root-file contents and
does not create a backup of that generated file. `init` remains strict and
requires the root config to match its receipt before extracting an overlay.
Use `opencode reload` to refresh configuration and MCP connections in an already
running OpenCode service after applying changed configuration.

Only the shared template is merged. A new checkout must first receive its
existing local binding through the workflow binding owner; do not copy private
OpenCode configuration or MCP credentials between checkouts. Provisioning and
deployment require the existing receipt-backed publisher/deployer workflow.
No fake commit is part of migration; commit migration changes through normal
review. The legacy `publish_workflow.ps1` is not the canonical entry point for
this configuration workflow.
