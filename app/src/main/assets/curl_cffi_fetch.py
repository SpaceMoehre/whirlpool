#!/usr/bin/env python3
import json
import sys

if len(sys.argv) != 3:
    raise SystemExit("expected args: <url> <query_json>")

from curl_cffi import requests

url = sys.argv[1]
params = json.loads(sys.argv[2])
response = requests.get(url, params=params, impersonate="chrome124", timeout=20)
response.raise_for_status()
print(response.text)
