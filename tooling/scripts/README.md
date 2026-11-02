# Backend Maven runner (Windows PowerShell)

From the repository root:

```powershell
.\tooling\scripts\backend.ps1 apply
.\tooling\scripts\backend.ps1 verify -Parallel 2
.\tooling\scripts\backend.ps1 check -Parallel 4 -LogDir .logs/backend
```

- `apply` runs `spotless:apply`; `check` runs `spotless:check`; `verify` runs `mvn verify` (including Spotless check).
- `-Parallel` defaults to 1; supported values: 1–4. Services have independent Maven wrappers and `target` directories.
- Output is written to a timestamped run folder under `.logs/backend` (or the supplied `-LogDir`), with separate `<service>.stdout.log` and `<service>.stderr.log` files and a `summary.log`. These local logs are Git-ignored.
- Terminal output shows starts, completions, and the summary, not Maven's full output.
- The script attempts all services, even if one fails; its final exit code is nonzero if any service fails.
- `-LogDir` accepts an absolute path or a path relative to the repository root.

For CI, prefer a separate job per service so builds are isolated and can run in parallel.
