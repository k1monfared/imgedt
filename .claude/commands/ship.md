# Ship

The full workflow: test, commit, push, merge, version bump, and tag. Chains `/prepare` and `/release` with a pause in between for the user to push.

## Steps

1. Run `/prepare` — test, commit, print push command.
2. **STOP and wait.** Tell the user:
   > Run the push command above, then tell me when it's done.
3. Once the user confirms the push is done, run `/release` — merge PR, bump version, print tag push command.
4. **STOP and wait.** Tell the user:
   > Run the tag push command above to trigger the GitHub release.

## Rules

- There are exactly TWO pause points where you must wait for the user:
  1. After `/prepare` prints the branch push command.
  2. After `/release` prints the tag push command.
- NEVER skip the pauses. The user must run the push commands themselves.
- If any step fails, stop and report the error. Do not continue.
