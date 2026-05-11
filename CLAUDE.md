# CLAUDE.md

## Arrow conventions

This is a sailing app — vector directions are easy to flip and three sessions
in a row have rediscovered the same rule. Lock it in:

- **Current arrows point in the direction the current is flowing *toward*.**
  A buoy released in current drifts toward the arrow tip.
- **Wind arrows point in the direction the wind is coming *from*.** This is the
  sailing/meteorology convention — a north wind blows from the north, so the
  arrow points to the north.
- Boats point head-to-wind by default (no manual rotation).

If a fix involves an arrow direction, double-check which of the two
conventions applies before swapping signs. The two reasons direction bugs
show up here: (1) confusing the two conventions, (2) mixing the geographic
frame with the wind-rotated canvas frame (see `CourseScreen.kt` — most
drawing happens in a wind-aligned local frame).

## Builds

There is **no local gradle wrapper** and no system gradle. Builds happen via
the GitHub Actions workflow that produces the APK. Don't claim "verified the
build" locally — do a careful read-through, lean on the type system / IDE
analysis if available, and let CI confirm.

## Git

The remote uses the `github-personal` SSH alias
(`git@github-personal:apantzare/sailing-coaching-app.git`), which keys to
`~/.ssh/id_ed25519_github_personal`. `git fetch / push` works without any
account switching.

For `gh` CLI commands (PR/issue/check operations) the active account still
matters — if `gh auth status` shows `axelviking` active, run
`gh auth switch --user apantzare` first, since `axelviking` can't see this
repo.
