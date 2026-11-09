# CampXSync — GSD Start Here

## Purpose
Use this pack to continue the existing CampXSync work without losing context. The current task is to investigate and resolve the observed ~6-second login delay. This is **not** permission to restart the project, repeat completed audits, change unrelated modules, or run database writes.

## Important project context
- Repository: `campxsync/CampXSync_V2.0`
- Working branch: `ReviewBranch`
- Next.js frontend: `frontend/`
- Vercel project/root: `camp-xs-ync/frontend` / root directory `frontend`
- Staging Supabase project ref: `kpwdvvcschankgggitnj`
- Production Supabase project ref: `ekspahbsbwdpraaqfqui` — **never access or modify it**
- Current sequence: **performance investigation first**, independent Claude gap analysis afterward, student login/dashboard later.
- Preserve all existing local changes, tests, docs, checkpoints, and working login fixes.

## How to start GSD in Antigravity
GSD has more than one distribution/runtime, so do not assume every installation uses the same command syntax.

1. Open the existing CampXSync repository/workspace in Antigravity. Make sure the active workspace is the existing repo and branch, not a new empty project or worktree.
2. Open the Antigravity agent/chat panel for that workspace.
3. In the input, type `/gsd:help` and inspect the response. The Antigravity port of `get-shit-done-cc` documents `/gsd:help` as its help command. If it is not recognized, do **not** install or initialize anything yet; use the IDE's command palette/extension details to verify the exact installed GSD extension name, version, and command syntax.
4. Ask the agent to report which GSD commands/workflow are available and locate the existing GSD state/checkpoint files. Do not run a new-project/bootstrap command.
5. Start this bounded task using the installed runtime's equivalent of GSD **quick/task execution with state tracking**, or its step-by-step mode. Prefer step-by-step for this sensitive task. If the extension uses `/gsd:quick`, use that; if it exposes a different command, follow its own help output. Do not use autonomous mode for this task.
6. Paste the prompt from `ANTIGRAVITY_EXECUTION_PROMPT.md` into the agent. Tell it to use `PERFORMANCE_TASK_BRIEF.md` as the task definition.
7. Let it inspect first. Review any proposed database writes, credential changes, security-sensitive changes, or deploy actions against the existing approval rules before approving.

## GSD safety reminders
- **Do not run `/gsd:new-project`, `/gsd-new-project`, or any bootstrap/reset command** for this existing project.
- Do not reinstall/update GSD merely because a command is not recognized.
- Do not assume this task is complete because an agent produces a report; require measurements and test evidence.
- Do not commit, push, or deploy without the project's existing approval requirements.
- Never expose secrets in logs or Markdown. Do not paste passwords or `.env.local` contents into prompts/reports.
- If GSD is unavailable, proceed only after inspecting existing project instructions and tracking the task in the supplied Markdown files; do not replace the existing workflow.

## Official references
- GSD commands reference: https://getshitdone.help/user-docs/commands/
- GSD Antigravity port/help: https://github.com/vietdev99/GSDAntigravity
- Antigravity IDE documentation: https://www.antigravity.google/docs/getting-started?tab=ide
