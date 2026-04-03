# Push Branch

Print the git push command for the user to run. Do NOT execute it.

## Steps

1. Determine the current branch name.
2. Check if the branch already tracks a remote with `git rev-parse --abbrev-ref @{upstream}` (ignore errors if no upstream).
3. Print the appropriate command:
   - If no upstream: `git push -u origin <branch-name>`
   - If upstream exists and there are unpushed commits: `git push`
   - If already up to date: tell the user there is nothing to push.

## Rules

- NEVER execute git push. Only print the command.
- Wait for the user to confirm they have pushed before any subsequent skill continues.
