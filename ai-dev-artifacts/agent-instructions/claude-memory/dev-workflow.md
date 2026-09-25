---
name: dev-workflow
description: "How the user wants development done on the movie booking project — branches, no commits without review, incremental style code, quality checklist"
metadata:
  node_type: memory
  type: feedback
  originSessionId: 197530cb-ad91-4d63-aa6f-8facec417fba
  modified: 2026-09-23T06:35:47.215Z
---

- Every feature/step gets its own new branch cut from an up-to-date main. Do the work on that branch.
- At each checkpoint, stop and ask the user to review. Wait until they've merged and pulled main before starting the next step.
- Build incrementally, the way a person would: small steps, not a whole end-to-end flow at once.

**Why:** the user wants the repo to read like a human wrote it step by step, and to control git history themselves.
**How to apply:** before coding anything, create the branch. Keep each diff reviewable. End each checkpoint with "ready for you to commit on branch X".

- The design docs (PROJECT_DECISIONS.md, LLD.md, IMPLEMENTATION_PLAN.md) stay untracked until the end of the project. Keep them updated locally alongside the code, never include them in a branch's file list, and the user commits them at the very end.
- Don't run `git checkout main` / `git pull` myself either, even to catch up. If main isn't pulled yet, tell the user and wait. The user rejected this once (2026-09-23).
