# Run Tests

Build the project and run all tests. Fail immediately if anything breaks.

## Steps

1. Verify you are NOT on the `master` branch. If you are, stop and tell the user to create a branch first.
2. Run `./gradlew assembleDebug test`
3. If the build or tests fail, show the errors clearly and stop.
4. If everything passes, report success.

## Rules

- Do NOT continue past a failure. The user must fix it before proceeding.
- Do NOT modify any code. This skill is read-only.
