# Prepare

Test, commit, and prepare for push. This chains three atomic skills.

## Steps

1. Run `/test` — build and run all tests. Stop if anything fails.
2. Run `/commit-branch` — stage and commit changes on the current branch.
3. Run `/push-branch` — print the push command for the user.

## Rules

- Stop immediately if `/test` fails. Do not commit broken code.
- After printing the push command, STOP and wait for the user to confirm they have pushed.
