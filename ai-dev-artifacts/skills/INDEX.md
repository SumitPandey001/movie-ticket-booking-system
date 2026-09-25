# Skills index

I parsed every `tool_use` in the four transcripts. **None of them made a Skill tool call.** The only slash command was `/model`. There were no Agent/subagent calls either. So no skill folders are copied here.

Two plugins were loaded into every session automatically by SessionStart hooks. The model saw their instructions, but never invoked them explicitly. They're listed so the reader knows they were in context. The plugins weren't called, so only name, version and source are given instead of copying the folders.

| name | source | version | purpose | where it was used |
|---|---|---|---|---|
| ponytail | plugin, author [DietrichGebert](https://github.com/DietrichGebert) (repo URL not in manifest) | 4.9.0 | "lazy senior dev" style rules: YAGNI, stdlib first, shortest diff | Injected by a hook into all 4 sessions (2026-09-23 to 2026-09-25). Never invoked. **Unsure** how much it shaped output. |
| superpowers (`using-superpowers`) | plugin, [obra/superpowers](https://github.com/obra/superpowers) via `claude-plugins-official` | 6.2.0 / 6.3.0 / 6.4.1 cached | Process skills (brainstorming, TDD, debugging) | Its intro text was injected by a hook into all 4 sessions. No sub-skill was ever invoked. **Unsure.** |

Installed but **not used** in this project:
- Synced user skills: `docs`, `docx`, `pdf`, `pptx`, `xlsx`, `skill-creator`, `morning`, `import-memory`
- User agents in `~/.claude/agents`: `bff-schema-architect`, `code-flow-analyzer`, `crm-campaign-dashboard-builder`, `requirement-to-code`, `software-architect`, `test-case-eng`
- Codex skills: `hatch-pet`, `.system`

No project-level `.claude/skills`, `.claude/commands` or `.claude/agents` exist.
