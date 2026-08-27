#!/usr/bin/env bash
#
# Rebase the chain of reference-step branches onto main and push.
#
# Use this after any change to the baseline on main (a dependency bump, a fix to
# the vulnerable app, a docs edit) so every step branch carries it too. The step
# branches must stay in sync with main, because a step's pull request diff is
# base..head: if main moves ahead and a branch does not, the diff shows main's
# newer content being reverted.
#
# The whole chain is rebased in ONE pass with --update-refs, which moves every
# intermediate branch ref along with it. Do not loop over the branches rebasing
# them one at a time: once the first branch is rewritten, git merge-base for the
# next one still resolves to the old base, so the earlier step's patch gets
# replayed a second time and conflicts.
#
# This force-pushes. Open pull requests update in place, but inline review
# comments anchored to rewritten commits can go stale, so re-check any teaching
# annotations afterwards.
#
# Run from a clean working tree. Ends on main.

set -euo pipefail

TIP=step-05-observability
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

git fetch origin

# Where the chain currently sits on top of main. Every step commit is a
# descendant of this, so it is the upstream for the single rebase below.
old_base=$(git merge-base main "$TIP")

if [[ "$old_base" == "$(git rev-parse main)" ]]; then
  echo "chain is already on top of main; nothing to rebase"
else
  echo "==> rebasing the whole chain from $(git rev-parse --short "$old_base") onto main"
  git checkout --quiet "$TIP"
  git rebase --update-refs --onto main "$old_base"
fi

echo "==> verifying each branch builds"
for branch in "${BRANCHES[@]}"; do
  git checkout --quiet "$branch"
  echo "==> ./mvnw -B verify on $branch"
  ./mvnw -B -q verify
done

echo "==> checking that no branch drifted outside the application code"
parent=main
for branch in "${BRANCHES[@]}"; do
  drift=$(git diff --name-only "$parent".."$branch" \
            -- README.md docs/ pom.xml .github/ scripts/ .gitignore)
  if [[ -n "$drift" ]]; then
    echo "$branch differs from $parent outside the application code:" >&2
    echo "$drift" >&2
    echo "a step branch must carry only its code delta" >&2
    exit 1
  fi
  parent=$branch
done

echo "==> pushing"
for branch in "${BRANCHES[@]}"; do
  git push --force-with-lease origin "$branch"
done

git checkout --quiet main
echo "done"
