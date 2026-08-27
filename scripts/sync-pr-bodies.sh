#!/usr/bin/env bash
#
# Push each docs/steps/step-0N.md into the body of its reference pull request.
#
# The lesson prose is authored once, on main, under docs/steps/. The pull request
# bodies are copies, so run this after editing a lesson.
#
# Requires gh, authenticated with push access. Run from the repository root on a
# checkout of main.

set -euo pipefail

declare -A PR_FOR_STEP=(
  [01]=""
  [02]=""
  [03]=""
  [04]=""
  [05]=""
)

for n in 01 02 03 04 05; do
  pr="${PR_FOR_STEP[$n]}"
  if [[ -z "$pr" ]]; then
    echo "no pull request number recorded for step-$n; fill in PR_FOR_STEP" >&2
    exit 1
  fi
  echo "==> syncing docs/steps/step-$n.md into PR #$pr"
  gh pr edit "$pr" --body-file "docs/steps/step-$n.md"
done

echo "done"
