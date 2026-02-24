#!/usr/bin/env python3
"""Fetch a URL with curl-cffi impersonation.

Usage:
  python curl_cffi_fetch.py <method> <url> <payload_json>
"""

import json
import sys


def main() -> int:
    if len(sys.argv) != 4:
        print("expected arguments: <method> <url> <payload_json>", file=sys.stderr)
        return 2

    method = sys.argv[1].upper()
    url = sys.argv[2]
    payload = json.loads(sys.argv[3])

    try:
        from curl_cffi import requests
    except Exception as exc:  # pragma: no cover
        print(f"curl_cffi import failed: {exc}", file=sys.stderr)
        return 3

    try:
        kwargs = {
            "url": url,
            "impersonate": "chrome124",
            "timeout": 20,
        }
        if method == "GET":
            kwargs["params"] = payload
        else:
            kwargs["json"] = payload

        response = requests.request(method, **kwargs)
        response.raise_for_status()
    except Exception as exc:  # pragma: no cover
        print(f"curl_cffi request failed: {exc}", file=sys.stderr)
        return 4

    print(response.text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
