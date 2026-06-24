#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./concurrent_same_file_uploads.sh <concurrency> <file_path>

Example:
  ./concurrent_same_file_uploads.sh 25 ~/Downloads/NoelRomero_Resume.pdf

Environment:
  API_BASE   Optional. Defaults to http://localhost:8080
EOF
}

if [[ $# -ne 2 ]]; then
  usage
  exit 1
fi

concurrency=$1
file_path=$2
api_base=${API_BASE:-http://localhost:8080}

if ! [[ "$concurrency" =~ ^[1-9][0-9]*$ ]]; then
  echo "Concurrency must be a positive integer: $concurrency" >&2
  exit 1
fi

if [[ ! -f "$file_path" ]]; then
  echo "File not found: $file_path" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

file_name=$(basename "$file_path")
file_size=$(wc -c < "$file_path" | tr -d ' ')
file_type=$(file -b --mime-type "$file_path" 2>/dev/null || printf 'application/octet-stream')
status_file=$(mktemp)

trap 'rm -f "$status_file"' EXIT

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

upload_one() {
  local sequence=$1
  local unique_name payload metadata file_id

  unique_name="${file_name%.*}-run-${sequence}"
  if [[ "$file_name" == *.* ]]; then
    unique_name="${unique_name}.${file_name##*.}"
  fi

  payload=$(cat <<EOF
{
  "fileName": "$(json_escape "$unique_name")",
  "fileSize": $file_size,
  "fileType": "$(json_escape "$file_type")"
}
EOF
)

  if ! metadata=$(curl -fsS \
    -X POST "$api_base/v1/files/actions/createUpload" \
    -H 'Content-Type: application/json' \
    -d "$payload"); then
    echo "FAIL run=$sequence phase=post file=$unique_name" >&2
    printf 'FAIL\n' >> "$status_file"
    return 1
  fi

  file_id=$(printf '%s' "$metadata" | sed -n 's/.*"fileId":"\([^"]*\)".*/\1/p')
  if [[ -z "$file_id" ]]; then
    echo "FAIL run=$sequence phase=parse file=$unique_name response=$metadata" >&2
    printf 'FAIL\n' >> "$status_file"
    return 1
  fi

  if curl -fsS -o /dev/null \
    -w "OK run=$sequence file=$unique_name fileId=$file_id status=%{http_code} total=%{time_total}s bytes=%{size_upload}\n" \
    -X PUT "$api_base/v1/files/$file_id?offset=0" \
    -H 'Content-Type: application/octet-stream' \
    --data-binary @"$file_path"; then
    printf 'OK\n' >> "$status_file"
  else
    echo "FAIL run=$sequence phase=put file=$unique_name fileId=$file_id" >&2
    printf 'FAIL\n' >> "$status_file"
    return 1
  fi
}

export -f json_escape
export -f upload_one
export api_base
export file_name
export file_path
export file_size
export file_type
export status_file

set +e
seq 1 "$concurrency" | xargs -n 1 -P "$concurrency" bash -c '
  upload_one "$1"
' _
xargs_status=$?
set -e

ok_count=$(grep -c '^OK$' "$status_file" || true)
fail_count=$(grep -c '^FAIL$' "$status_file" || true)

echo "summary file=$file_name total=$concurrency ok=$ok_count failed=$fail_count"

if (( fail_count > 0 )) || [[ "$xargs_status" -ne 0 ]]; then
  exit 1
fi
