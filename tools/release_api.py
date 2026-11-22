#!/usr/bin/env python3
"""创建 GitHub Release 并上传附件（只走 api.github.com / uploads.github.com）。

用法：
    python tools/release_api.py <tag> <target_commitish> <notes_file> <asset> [asset...]
token 取 GITHUB_TOKEN 或 git credential manager 里存好的 github.com 凭据。
"""
import json
import mimetypes
import subprocess
import sys
import urllib.request

SLUG = "Ayang233/Glacier"
API = "https://api.github.com"


def token():
    res = subprocess.run(
        ["git", "credential", "fill"],
        input=b"protocol=https\nhost=github.com\n\n",
        capture_output=True,
    )
    for line in res.stdout.decode("utf-8", "replace").splitlines():
        if line.startswith("password="):
            return line[len("password="):].strip()
    raise SystemExit("找不到 GitHub token")


TOKEN = token()


def api(url, payload=None, method="GET", content_type="application/json"):
    data = None
    if payload is not None:
        data = payload if isinstance(payload, bytes) else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "glacier-release-api")
    if data is not None:
        req.add_header("Content-Type", content_type)
    with urllib.request.urlopen(req, timeout=180) as resp:
        body = resp.read()
        return json.loads(body) if body else {}


tag, target, notes_file = sys.argv[1], sys.argv[2], sys.argv[3]
assets = sys.argv[4:]
body = open(notes_file, encoding="utf-8").read()

release = api(
    f"{API}/repos/{SLUG}/releases",
    {
        "tag_name": tag,
        "target_commitish": target,
        "name": f"Glacier {tag.lstrip('v')}",
        "body": body,
        "draft": False,
        "prerelease": False,
    },
    method="POST",
)
print("release:", release["html_url"], "tag:", release["tag_name"])

for path in assets:
    name = path.rsplit("/", 1)[-1]
    ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
    if name.endswith(".apk"):
        ctype = "application/vnd.android.package-archive"
    with open(path, "rb") as fh:
        data = fh.read()
    asset = api(
        f"https://uploads.github.com/repos/{SLUG}/releases/{release['id']}/assets?name={name}",
        data,
        method="POST",
        content_type=ctype,
    )
    print("asset:", asset["name"], asset["size"], asset.get("digest"))
