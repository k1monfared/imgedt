# Merge PR

Create a pull request if one doesn't exist, then merge it into master.

## Steps

1. Verify you are NOT on the `master` branch.
2. Check if a PR already exists for this branch: `gh pr view --json number,title,state 2>&1`
3. If no PR exists, create one:
   - Read `git log master..HEAD --oneline` to understand all commits.
   - Create the PR with `gh pr create --head <branch> --title "<title>" --body "<summary>"`
4. Merge the PR: `gh pr merge --merge`
5. Report the merged PR number and URL.

## Rules

- Never merge directly without a PR (other people work on this repo).
- Use `--merge` strategy (not squash or rebase) unless the user says otherwise.
