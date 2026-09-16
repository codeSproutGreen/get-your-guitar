#!/usr/bin/env bash
# Links Claude Code's per-project auto-memory directory to <repo>/.claude/memory
# so memories are versioned with the code and shared across environments.
# Safe to re-run. Existing memories in the old directory are merged into the repo first.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
target="$repo_root/.claude/memory"
# Claude Code encodes the project path by replacing every non-alphanumeric char with '-'
encoded="$(printf '%s' "$repo_root" | sed 's/[^A-Za-z0-9]/-/g')"
proj_dir="$HOME/.claude/projects/$encoded"
link="$proj_dir/memory"

mkdir -p "$target" "$proj_dir"

if [ -L "$link" ]; then
  if [ "$(cd "$link" && pwd -P)" = "$target" ]; then echo "Already linked: $link -> $target"; exit 0; fi
  echo "Removing stale symlink $link"; rm "$link"
elif [ -d "$link" ]; then
  # Real directory: copy any memories the repo doesn't have yet, then move it aside.
  (cd "$link" && find . -type f) | sed 's#^\./##' | while read -r f; do
    if [ ! -e "$target/$f" ]; then
      mkdir -p "$(dirname "$target/$f")"; cp "$link/$f" "$target/$f"; echo "Merged $f into repo memory"
    fi
  done
  backup="$link.bak-$(date +%Y%m%d%H%M%S)"
  mv "$link" "$backup"
  echo "Backed up old memory dir to $backup (delete it once you've confirmed the link works)"
fi

ln -s "$target" "$link"
echo "Linked: $link -> $target"
