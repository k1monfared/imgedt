# Commit Branch

Stage and commit all changes on the current feature branch.

## Steps

1. Verify you are NOT on the `master` branch. If you are, stop and tell the user.
2. Run `git status` and `git diff` to review all changes.
3. Stage the relevant changed files. Do NOT stage files that look like secrets (.env, credentials, local.properties).
4. Read recent commit messages with `git log --oneline -10` to match the project's style.
5. Write a concise commit message that describes what changed and why. Use this format:

```
Short summary line (imperative mood)

Optional longer description if the change is non-trivial.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

6. Commit the changes.
7. Show the commit hash and summary.

## Rules

- Never commit on master.
- Never stage local.properties, .env, or credential files.
- Always use a HEREDOC for the commit message to preserve formatting.
