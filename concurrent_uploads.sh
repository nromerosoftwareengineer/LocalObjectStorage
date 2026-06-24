#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./concurrent_uploads.sh <directory> <concurrency> [api_base]

Example:
  ./concurrent_uploads.sh ~/Downloads/test-files 10 http://localhost:8080
EOF
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 1
fi

directory=$1
concurrency=$2
api_base=${3:-http://localhost:8080}

if [[ ! -d "$directory" ]]; then
  echo "Directory not found: $directory" >&2
  exit 1
fi

if ! [[ "$concurrency" =~ ^[1-9][0-9]*$ ]]; then
  echo "Concurrency must be a positive integer: $concurrency" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

status_file=$(mktemp)
trap 'rm -f "$status_file"' EXIT

total_files=$(find "$directory" -type f | wc -l | tr -d ' ')

if [[ "$total_files" -eq 0 ]]; then
  echo "No files found in: $directory" >&2
  exit 1
fi

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

upload_one() {
  local file=$1
  local file_name file_size file_type payload metadata file_id

  file_name=$(basename "$file")
  file_size=$(wc -c < "$file" | tr -d ' ')
  file_type=$(file -b --mime-type "$file" 2>/dev/null || printf 'application/octet-stream')

  payload=$(cat <<EOF
{
  "fileName": "$(json_escape "$file_name")",
  "fileSize": $file_size,
  "fileType": "$(json_escape "$file_type")"
}
EOF
)

  if ! metadata=$(curl -fsS \
    -X POST "$api_base/v1/files/actions/createUpload" \
    -H 'Content-Type: application/json' \
    -d "$payload"); then
    echo "FAIL post file=$file_name" >&2
    printf 'FAIL\n' >> "$status_file"
    return 1
  fi

  file_id=$(printf '%s' "$metadata" | sed -n 's/.*"fileId":"\([^"]*\)".*/\1/p')
  if [[ -z "$file_id" ]]; then
    echo "FAIL parse fileId file=$file_name response=$metadata" >&2
    printf 'FAIL\n' >> "$status_file"
    return 1
  fi

  if curl -fsS -o /dev/null \
    -w "OK file=$file_name fileId=$file_id status=%{http_code} total=%{time_total}s bytes=%{size_upload}\n" \
    -X PUT "$api_base/v1/files/$file_id?offset=0" \
    -H 'Content-Type: application/octet-stream' \
    --data-binary @"$file"; then
    printf 'OK\n' >> "$status_file"
  else
    echo "FAIL put file=$file_name fileId=$file_id" >&2
    printf 'FAIL\n' >> "$status_file"
    return 1
  fi
}

export -f json_escape
export -f upload_one
export api_base
export status_file

find "$directory" -type f -print0 | xargs -0 -n 1 -P "$concurrency" bash -c '
  upload_one "$1"
' _

ok_count=$(grep -c '^OK$' "$status_file" || true)
fail_count=$(grep -c '^FAIL$' "$status_file" || true)

echo "summary total=$total_files ok=$ok_count failed=$fail_count concurrency=$concurrency"

if (( fail_count > 0 )); then
  exit 1
fi
