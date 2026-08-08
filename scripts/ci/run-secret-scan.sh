#!/usr/bin/env bash
set -euo pipefail

version=8.30.1
archive="gitleaks_${version}_linux_x64.tar.gz"
expected_sha256=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb
url="https://github.com/gitleaks/gitleaks/releases/download/v${version}/${archive}"

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT
curl --fail --silent --show-error --location "$url" --output "$tmp_dir/$archive"
printf '%s  %s\n' "$expected_sha256" "$tmp_dir/$archive" | sha256sum --check --status
tar -xzf "$tmp_dir/$archive" -C "$tmp_dir" gitleaks

# The Git scanner covers every checked-out commit. CI fetches full history so a
# secret cannot be hidden by deleting it in the pull request's final tree.
"$tmp_dir/gitleaks" git --redact --no-banner --verbose .

# A temporary index captures tracked plus untracked, non-ignored candidate files
# without staging the real worktree or traversing ignored local environment files.
candidate_index="$tmp_dir/candidate.index"
candidate_tree="$tmp_dir/candidate"
rm -f "$candidate_index"
mkdir -p "$candidate_tree"
GIT_INDEX_FILE="$candidate_index" git read-tree HEAD
GIT_INDEX_FILE="$candidate_index" git add -A
GIT_INDEX_FILE="$candidate_index" git checkout-index --all --prefix="$candidate_tree/"
"$tmp_dir/gitleaks" dir --redact --no-banner --verbose "$candidate_tree"
