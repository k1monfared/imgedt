# Bump Version

Bump the project version, update the changelog, commit, and create a git tag.

## Steps

1. Read the current version from `app/build.gradle` (the `versionName` field).
2. Read the current `versionCode` from `app/build.gradle`.
3. Ask the user whether this is a **patch**, **minor**, or **major** bump. Show what the new version would be for each option.
4. Once the user chooses:

### Update `app/build.gradle`
- Increment `versionCode` by 1.
- Set `versionName` to the new version string.

### Update `CHANGELOG.md`
- Read `git log` for commits since the last tag to build the changelog entry.
- Add a new `## [X.Y.Z] - YYYY-MM-DD` section above `## [Unreleased]` content.
- Move any `[Unreleased]` items into the new version section.
- Update the comparison links at the bottom of the file.
- Reset the `[Unreleased]` section to empty.

### Commit and tag
- Stage `app/build.gradle` and `CHANGELOG.md`.
- Commit with message: `Release vX.Y.Z`
- Create an annotated tag: `git tag -a vX.Y.Z -m "Release vX.Y.Z"`

5. Report the new version and tag name.

## Rules

- Always ask the user for patch/minor/major. Never assume.
- The tag format is always `vX.Y.Z` (with the `v` prefix).
- Do NOT push the tag. The `/push-tag` skill handles that.
