#!/usr/bin/env bash
set -euo pipefail

version=1.7.7
archive="actionlint_${version}_linux_amd64.tar.gz"
expected_sha256=023070a287cd8cccd71515fedc843f1985bf96c436b7effaecce67290e7e0757
url="https://github.com/rhysd/actionlint/releases/download/v${version}/${archive}"

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT
curl --fail --silent --show-error --location "$url" --output "$tmp_dir/$archive"
printf '%s  %s\n' "$expected_sha256" "$tmp_dir/$archive" | sha256sum --check --status
tar -xzf "$tmp_dir/$archive" -C "$tmp_dir" actionlint
"$tmp_dir/actionlint" -color
