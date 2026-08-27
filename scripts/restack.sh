#!/usr/bin/env bash
#
# Rebase the chain of reference-step branches onto main, in order, and push.
#
# Use this after any change to the baseline on main (a dependency bump, a fix to
# the vulnerable app, a README edit) so every step branch carries it too.
#
# This force-pushes. Open pull requests update in place, but inline review
# comments anchored to rewritten commits can go stale, so re-check any teaching
# annotations afterwards.
#
# Run from a clean working tree on any branch. Ends on main.

set -euo pipefail

BRANCHES=(
  step-01-prompt-injection
  step-02-token-propagation
  step-03-excessive-agency
  step-04-sensitive-disclosure
  step-05-observability
)

if [[ -n "$(git status --porcelain)" ]]; then
  echo "working tree is dirty; commit or stash first" >&2
  exit 1
fi

start_branch=$(git rev-parse --abbrev-ref HEAD)
git fetch origin

parent=main
for branch in "${BRANCHES[@]}"; do
  echo "==> rebasing $branch onto $parent"
  base=$(git merge-base "$branch" "$parent")
  git rebase --onto "$parent" "$base" "$branch"
  parent=$branch
done

echo "==> chain rebased; verifying each branch builds"
for branch in "${BRANCHES[@]}"; do
  git checkout --quiet "$branch"
  echo "==> ./mvnw -B verify on $branch"
  ./mvnw -B -q verify
done

echo "==> pushing"
for branch in "${BRANCHES[@]}"; do
  git push --force-with-lease origin "$branch"
done

git checkout --quiet "$start_branch"
echo "done"
