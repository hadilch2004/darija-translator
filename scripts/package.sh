#!/usr/bin/env bash
# Creates the submission archive  <StudentName>_project2.zip  next to the project folder.
#   ./scripts/package.sh Jane_Doe
set -euo pipefail

if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "Usage: $0 <StudentName>     (e.g. $0 Jane_Doe  ->  Jane_Doe_project2.zip)" >&2
  exit 1
fi
name="$1"
case "$name" in
  *[!A-Za-z0-9_-]*) echo "Use only letters, digits, '_' and '-' in the name." >&2; exit 1 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
parent="$(dirname "$root")"
folder="$(basename "$root")"
out="$parent/${name}_project2.zip"

cd "$parent"
rm -f "$out"
zip -r -q "$out" "$folder" \
  -x "*/node_modules/*" "*/target/*" "*/.git/*" "*/.expo/*" "*/.bundle-check/*" \
     "*/.env" "*.DS_Store" "*.log" "*.zip"

echo "Created $out"
unzip -l "$out" | tail -1
