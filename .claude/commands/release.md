# Release

Merge the current branch and create a versioned release. This chains three atomic skills.

## Prerequisites

- The current branch must already be pushed to the remote.
- There must be no uncommitted changes.

## Steps

1. Run `/merge-pr` — create a PR if needed and merge it into master.
2. After merge, switch to master and pull: `git checkout master && git pull`
3. Run `/bump-version` — ask the user for patch/minor/major, update files, commit, create tag.
4. Run `/push-tag` — print the tag push command for the user.

## Rules

- Stop if the branch has not been pushed yet. Tell the user to run `/push-branch` first.
- Stop if there are uncommitted changes. Tell the user to commit or stash first.
