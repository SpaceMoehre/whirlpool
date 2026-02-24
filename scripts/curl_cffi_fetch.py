#!/usr/bin/env python3
"""Fetch a URL with curl-cffi impersonation.

Usage:
  python curl_cffi_fetch.py <url> <query_json>
"""

import json
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print("expected arguments: <url> <query_json>", file=sys.stderr)
        return 2

    url = sys.argv[1]
    query_payload = json.loads(sys.argv[2])

    try:
        from curl_cffi import requests
    except Exception as exc:  # pragma: no cover
        print(f"curl_cffi import failed: {exc}", file=sys.stderr)
        return 3

    try:
        response = requests.get(
            url,
            params=query_payload,
            impersonate="chrome124",
            timeout=20,
        )
        response.raise_for_status()
    except Exception as exc:  # pragma: no cover
        print(f"curl_cffi request failed: {exc}", file=sys.stderr)
        return 4

    print(response.text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
