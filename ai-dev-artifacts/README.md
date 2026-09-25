# AI development artifacts

This folder holds everything that shows how AI was used to build the Movie Ticket Booking System (Spring Boot 4.1, **Java 21**, PostgreSQL + Flyway, Redis, Spring Modulith).
All files are copies; the originals are still in place. The only edits are redactions, marked `[REDACTED: <reason>]`.

## Tools and models
- **Claude Code** (CLI), model `claude-opus-5-5` for the whole project. That's 4 sessions, 2026-09-23 to 2026-09-25.
- **Codex**: installed, but none of its 34 local sessions touch this repo, so none are included. `~/.codex/AGENTS.md` is empty.
- No Cursor or Copilot configuration exists.

## How AI was used
1. **Design (user-provided, then AI-maintained).** The user started the main session with three design docs: `PROJECT_DECISIONS.md`, `LLD.md` and `IMPLEMENTATION_PLAN.md`. The user also set the rules: no AI commits, one branch per step, human-style code. Claude kept those docs in sync with the code as it went ([raw/design](raw/design)).
2. **Implementation (AI-written, human-directed).** The user drove the build step by step ("start M2 step 3" … "start M8 step 4"), eight milestones in all. Claude wrote the code, migrations, tests and scripts for each step on a fresh branch. The user reviewed, committed, pushed and merged every step: all 99 commits and PRs #1–#47 are authored by the user. At the user's request, the commits carry no AI trailers, so the transcripts are the record of AI authorship, not git.
3. **Human steering.** The user reported compile/runtime errors (e.g. `TheaterAdminController`), asked design questions (idempotency keys, event listeners, the outbox/`event_publication`, module boundaries), and made calls on structure (DTO packages, `allowedDependencies`, removing Lombok). Claude answered and made those changes. The transcripts show no hand-written code, but the user may have edited files outside the sessions; that can't be verified from here.
4. **Docs and tooling (AI-written).** `docs/USAGE.md`, `docs/DIAGRAMS.md` (ER and flow diagrams), the Postman collection, the `.http` files, the `seed-local.sh`/`race.sh` scripts and the HTML admin/shop prototypes.
5. **Review passes (AI).** Session `d61ba290`: Claude rated the repo against a quality checklist, then fixed the logging and duplicate-code issues and removed Lombok, over several rounds.

## Instruction files and skills
- **During development there was no `CLAUDE.md` or `AGENTS.md`.** The working rules lived in Claude Code's auto-memory, which loads into every session. Those four files are copied verbatim in [agent-instructions/claude-memory](agent-instructions/claude-memory).
- The root [`CLAUDE.md`](../CLAUDE.md) was **written at submission time (2026-09-25), at the user's request**, as a condensed version of those memory files. It wasn't used during development and is untracked, so there's no git history for it or any other repo-level instruction file.
- **Skills:** none were invoked. Two plugins (ponytail, superpowers) were auto-injected by hooks. See [skills/INDEX.md](skills/INDEX.md).

## Redactions
Transcripts were copied from `~/.claude/projects/<repo>/`. Redacted:
- the local DB password default
- the user's work email, employer name and OS username
- an internal tool name and internal account names
- workplace connector/tool listings and org/session-context attachments, which are out of scope for this project

The `.md` rendering of each transcript shows prompts, replies and one-line tool-call summaries. Tool outputs are omitted.

## Artifact table
| artifact | path |
|---|---|
| Claude memory index | [agent-instructions/claude-memory/MEMORY.md](agent-instructions/claude-memory/MEMORY.md) |
| Memory: dev workflow | [agent-instructions/claude-memory/dev-workflow.md](agent-instructions/claude-memory/dev-workflow.md) |
| Memory: setup decisions | [agent-instructions/claude-memory/project-setup-decisions.md](agent-instructions/claude-memory/project-setup-decisions.md) |
| Memory: no Lombok | [agent-instructions/claude-memory/no-lombok.md](agent-instructions/claude-memory/no-lombok.md) |
| Codex user AGENTS.md (empty) | [agent-instructions/codex/AGENTS.md](agent-instructions/codex/AGENTS.md) |
| Submission-time CLAUDE.md | [../CLAUDE.md](../CLAUDE.md) |
| Skills index | [skills/INDEX.md](skills/INDEX.md) |
| Session 08709c25 (09-23, `/model` only) | [jsonl](raw/transcripts/08709c25-1210-4b87-8b71-a3490906c3cf.jsonl) · [md](raw/transcripts/08709c25-1210-4b87-8b71-a3490906c3cf.md) |
| Session 197530cb (09-23→25, main build M1–M8, docs, prototypes) | [jsonl](raw/transcripts/197530cb-ad91-4d63-aa6f-8facec417fba.jsonl) · [md](raw/transcripts/197530cb-ad91-4d63-aa6f-8facec417fba.md) |
| Project decisions | [raw/design/PROJECT_DECISIONS.md](raw/design/PROJECT_DECISIONS.md) |
| Low-level design | [raw/design/LLD.md](raw/design/LLD.md) |
| Implementation plan | [raw/design/IMPLEMENTATION_PLAN.md](raw/design/IMPLEMENTATION_PLAN.md) |
| ER and flow diagrams | [raw/design/DIAGRAMS.md](raw/design/DIAGRAMS.md) |
| API usage guide | [raw/design/USAGE.md](raw/design/USAGE.md) |
| Postman collection + envs | [raw/api/postman](raw/api/postman) |
| `.http` request files | [raw/api/http](raw/api/http) |
| Seed and race-test scripts | [raw/scripts](raw/scripts) |
| Admin/shop HTML prototypes | [raw/prototype](raw/prototype) |
| Flyway migrations (not copied, they're source) | [../src/main/resources/db/migration](../src/main/resources/db/migration) |
