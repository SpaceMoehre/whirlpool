#!/usr/bin/env python3
import json
import sys

if len(sys.argv) != 4:
    raise SystemExit("expected args: <method> <url> <payload_json>")

from curl_cffi import requests

method = sys.argv[1].upper()
url = sys.argv[2]
payload = json.loads(sys.argv[3])

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
print(response.text)
