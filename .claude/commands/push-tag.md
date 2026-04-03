# Push Tag

Print the git push command for the latest version tag. Do NOT execute it.

## Steps

1. Find the most recent tag: `git describe --tags --abbrev=0`
2. Check if that tag has already been pushed: `git ls-remote --tags origin <tag>`
3. If not yet pushed, print: `git push origin <tag>`
4. If already pushed, tell the user the tag is already on the remote.
5. Remind the user that pushing the tag will trigger the GitHub Actions release workflow, which builds the APK and creates a GitHub Release.

## Rules

- NEVER execute git push. Only print the command.
