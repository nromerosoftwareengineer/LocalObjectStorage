#!/usr/bin/env python3

import json
import mimetypes
import pathlib
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request


def usage() -> None:
    print(
        "Usage:\n"
        "  ./concurrent_same_file_uploads.py <concurrency> <file_path> [api_base]\n\n"
        "Example:\n"
        "  ./concurrent_same_file_uploads.py 25 ~/Downloads/NoelRomero_Resume.pdf\n",
        file=sys.stderr,
    )


def build_request(url: str, method: str, data: bytes, content_type: str) -> urllib.request.Request:
    request = urllib.request.Request(url=url, data=data, method=method)
    request.add_header("Content-Type", content_type)
    request.add_header("Accept", "application/json")
    return request


def create_upload(api_base: str, file_name: str, file_size: int, file_type: str) -> str:
    payload = json.dumps(
        {
            "fileName": file_name,
            "fileSize": file_size,
            "fileType": file_type,
        }
    ).encode("utf-8")

    request = build_request(
        url=f"{api_base}/v1/files/actions/createUpload",
        method="POST",
        data=payload,
        content_type="application/json",
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        body = json.loads(response.read().decode("utf-8"))
        return body["fileId"]


def upload_bytes(api_base: str, file_id: str, file_bytes: bytes) -> tuple[int, dict]:
    url = f"{api_base}/v1/files/{file_id}?offset=0"
    request = build_request(
        url=url,
        method="PUT",
        data=file_bytes,
        content_type="application/octet-stream",
    )

    with urllib.request.urlopen(request, timeout=300) as response:
        body = json.loads(response.read().decode("utf-8"))
        return response.status, body


def main() -> int:
    if len(sys.argv) not in (3, 4):
        usage()
        return 1

    try:
        concurrency = int(sys.argv[1])
    except ValueError:
        print(f"Concurrency must be an integer: {sys.argv[1]}", file=sys.stderr)
        return 1

    if concurrency <= 0:
        print(f"Concurrency must be positive: {concurrency}", file=sys.stderr)
        return 1

    file_path = pathlib.Path(sys.argv[2]).expanduser().resolve()
    api_base = (sys.argv[3] if len(sys.argv) == 4 else "http://localhost:8080").rstrip("/")

    if not file_path.is_file():
        print(f"File not found: {file_path}", file=sys.stderr)
        return 1

    file_bytes = file_path.read_bytes()
    file_name = file_path.name
    file_size = len(file_bytes)
    file_type = mimetypes.guess_type(file_name)[0] or "application/octet-stream"

    upload_targets: list[tuple[int, str, str]] = []
    stem = file_path.stem
    suffix = file_path.suffix

    print(f"Preparing {concurrency} upload records for {file_name} against {api_base}")
    for sequence in range(1, concurrency + 1):
        unique_name = f"{stem}-run-{sequence}{suffix}"
        try:
            file_id = create_upload(api_base, unique_name, file_size, file_type)
        except urllib.error.HTTPError as exc:
            details = exc.read().decode("utf-8", errors="replace")
            print(
                f"FAIL prepare run={sequence} status={exc.code} body={details}",
                file=sys.stderr,
            )
            return 1
        except urllib.error.URLError as exc:
            print(f"FAIL prepare run={sequence} error={exc}", file=sys.stderr)
            return 1

        upload_targets.append((sequence, unique_name, file_id))

    barrier = threading.Barrier(concurrency)
    results: list[dict | None] = [None] * concurrency

    def worker(index: int, sequence: int, unique_name: str, file_id: str) -> None:
        try:
            barrier.wait()
            started_at = time.perf_counter()
            status_code, body = upload_bytes(api_base, file_id, file_bytes)
            elapsed = time.perf_counter() - started_at
            results[index] = {
                "ok": True,
                "sequence": sequence,
                "file_name": unique_name,
                "file_id": file_id,
                "status_code": status_code,
                "stored_size": body.get("storedSize"),
                "elapsed": elapsed,
            }
        except urllib.error.HTTPError as exc:
            details = exc.read().decode("utf-8", errors="replace")
            results[index] = {
                "ok": False,
                "sequence": sequence,
                "file_name": unique_name,
                "file_id": file_id,
                "status_code": exc.code,
                "error": details,
            }
        except urllib.error.URLError as exc:
            results[index] = {
                "ok": False,
                "sequence": sequence,
                "file_name": unique_name,
                "file_id": file_id,
                "error": str(exc),
            }
        except Exception as exc:  # pragma: no cover - defensive fallback
            results[index] = {
                "ok": False,
                "sequence": sequence,
                "file_name": unique_name,
                "file_id": file_id,
                "error": repr(exc),
            }

    threads = []
    batch_started_at = time.perf_counter()
    for index, (sequence, unique_name, file_id) in enumerate(upload_targets):
        thread = threading.Thread(
            target=worker,
            args=(index, sequence, unique_name, file_id),
            name=f"upload-{sequence}",
        )
        thread.start()
        threads.append(thread)

    for thread in threads:
        thread.join()
    batch_elapsed = time.perf_counter() - batch_started_at

    ok_count = 0
    fail_count = 0
    for result in results:
        if result is None:
            fail_count += 1
            print("FAIL unknown result", file=sys.stderr)
            continue

        if result["ok"]:
            ok_count += 1
            print(
                "OK "
                f"run={result['sequence']} "
                f"file={result['file_name']} "
                f"fileId={result['file_id']} "
                f"status={result['status_code']} "
                f"stored={result['stored_size']} "
                f"time={result['elapsed']:.3f}s"
            )
        else:
            fail_count += 1
            status_text = f"status={result['status_code']} " if "status_code" in result else ""
            print(
                "FAIL "
                f"run={result['sequence']} "
                f"file={result['file_name']} "
                f"fileId={result['file_id']} "
                f"{status_text}"
                f"error={result['error']}",
                file=sys.stderr,
            )

    print(
        f"summary file={file_name} total={concurrency} ok={ok_count} failed={fail_count} "
        f"batch_time={batch_elapsed:.3f}s"
    )
    return 0 if fail_count == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
