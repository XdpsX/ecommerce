# Backend commands (Windows PowerShell)

From the repository root:

```powershell
.\\tooling\\scripts\\backend.ps1 apply
.\\tooling\\scripts\\backend.ps1 check
.\\tooling\\scripts\\backend.ps1 verify
```

The script runs Maven Wrapper sequentially for `ecommerce`, `identity`, `notification`, and `media`. It stops on the first failure and returns a nonzero exit code. `verify` includes the Spotless check bound to Maven's `validate` phase. This is a local convenience script, not a Maven multi-module reactor.

For CI, run the relevant service's Maven Wrapper independently so jobs can execute in parallel and failures stay isolated.
