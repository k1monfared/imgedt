# Project Workflow Skills

Slash commands for the development workflow. Each `.md` file becomes a `/command-name` in Claude Code.

## Atomic skills

| Command | Purpose |
|---------|---------|
| `/test` | Build and run tests |
| `/commit-branch` | Stage and commit on current branch |
| `/push-branch` | Print the git push command (never executes) |
| `/merge-pr` | Create PR and merge into master |
| `/bump-version` | Bump version, update changelog, create tag |
| `/push-tag` | Print the tag push command (never executes) |

## Composite skills

| Command | Chains |
|---------|--------|
| `/prepare` | test -> commit-branch -> push-branch |
| `/release` | merge-pr -> bump-version -> push-tag |
| `/ship` | prepare -> (user pushes) -> release |
