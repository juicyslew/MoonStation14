# Opt-in connected-wall diagnostic

In a connected owned CHARACTER client, run `/ms14_connectedwall arm`, perform one deliberate wall jump, then run `/ms14_connectedwall stop` and export the client log. Capture is default-off, at most 100 distinct client ticks (one render row per tick), and clears on ownership/session loss. This records observations only; it does not establish a cause, fix, or owner acceptance. It does not certify client behavior without an actual connected-client run.
