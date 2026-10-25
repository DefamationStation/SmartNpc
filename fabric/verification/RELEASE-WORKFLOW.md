# Fast survival iteration and release

Use a workflow, rather than maintaining a static installer inventory. Discover
the actual installation and target metadata for each upgrade; these small helpers
operate on the chosen instance, artifact and receipts.

1. Keep one incremental Gradle staging directory and a warm daemon. Run `build`
   there after changes. Freeze each tested jar by SHA-256; do not overwrite a
   running case's jar. `run-headless.py` copies the artifact into each case and
   binds its replays to those exact bytes. A later build can proceed independently.
   `build-candidate.ps1 -Workspace ... -JavaHome ... -StagingDirectory ...`
   waits for successful build/checks, verifies the unit results and embedded mod
   ID/version, and returns an
   immutable jar path plus receipt. Unit XML and the build log are frozen into
   a unique check directory as well, so the next build cannot replace evidence.
   Pass that returned path to tests; do not race
   a still-running build by copying its previous output.
   Older jars may remain in the compiler cache; only the declared release version
   can be selected. Gradle tracks version changes as a resource-processing input,
   refreshing the embedded metadata as well as the jar filename.
2. Reproduce the failing behaviour first, using `run-headless.py --workspace ...
   --jar ... --world ... --name ... --wait`. It copies a closed world, checks the
   source did not change during copying, retains the installed common mod stack
   and automatically reopens saved checkpoints. Never run tests on the live save.
   `--resume-case <closed-case>` copies a saved actor and its ledger into a fresh
   diagnostic case. Survival resumes its saved work; a blocked journey uses the
   real requested-home operation. Label continuation separately from a fresh
   start and retain the original failure. Copied cache paths and BlueMap ports
   are isolated as well as saves.
   Fresh standard scenarios start at declared dawn/clear conditions for a
   repeatable day. Use `--keep-world-conditions` for a fresh copied-condition
   trial; continuations never reset conditions. Record which was used.
   `--max-ticks` can shorten a focused observation, with the chosen cap recorded
   in the case metadata and inherited by replays. It cannot extend the standard
   scenario deadline. A deadline remains a failed outcome, never a pass.
3. Use accelerated tick rates for iteration, explicitly recorded in receipts.
   Repeat critical final cases at a lower rate. Generation and asynchronous
   loading can change observed timing; do not present accelerated throughput as
   a normal-game FPS or performance benchmark. Record simulation ticks and MSPT.
4. Run `release-plan.py --base COMMIT` to identify the affected integration gates.
   During iteration run the failing focused cases. Once production code is frozen,
   run the required normal-selector survival, journey/replay and regression cases
   on that artifact. Avoid repeating unaffected graphics suites for every AI edit.
   A final client/Buddy smoke covers the combined installation and changed mixins.
5. Review and commit the source, excluding private worlds, logs, binaries and
   paths. Build a private release receipt with `sourceCommit`, `sha256`,
   `previousSha256`, `unitResultDirectory`, and `runtimeCases` entries containing
   `name` and required `markers`. Include `originalWorld` and `worldCopyReceipt`
   to recheck the original save is unchanged. Report negative controls separately.
6. Close all game/test clients. `deploy-verified.py --workspace ... --jar ...
   --receipt ... --instance player=... --instance companion=...` validates exact
   artifacts, tests, game dependencies and the installed baseline. Review its dry
   run, then use `--execute`. It backs up and replaces only Smart NPC and checks
   unrelated jars/options/configuration. It never copies a test world into play.
7. Update the setup repository's installed manifest, validation and customization
   preservation notes. Freeze the same tested input for its target-version adapter
   workflow and reproduce its hash. Reconcile both installed roles, push both
   repositories and check the source CI build. Do not silently replace the tested
   bytes with a fresh build or an upstream author's stock jar.

No check is a pass merely because it launched. Preserve completion/failure
receipts and identify which scenarios actually ran on each artifact. A failed
test is retained as a failure, even when its fixture or candidate is superseded.
