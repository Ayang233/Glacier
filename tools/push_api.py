#!/usr/bin/env python3
"""增量推送本地提交到 GitHub（Git Data API，不依赖 gh）。

与 `tools/push.py` 的区别：那个脚本调用 `gh api`，本机没装 gh 时无法使用；
这里直接用 `urllib` + HTTPS 调同一个 Git Data API，token 取 `GITHUB_TOKEN`
或 git credential manager 里已保存的 github.com 凭据。

两个脚本的共同点：
- 只上传相对远端最新树**变化**的文件（`base_tree` 继承未变化路径）；
- 按 tree sha 找锚点，所以本地与远端 SHA 不同也能增量推送。

本脚本额外把本地提交的 author / committer（含时间与时区）原样带上，
因此远端生成的提交 SHA 通常与本地一致，不需要事后再 `git fetch` 对齐。

用法：
    python tools/push_api.py [仓库目录] [owner/repo] [分支]
默认：当前目录 / Ayang233/Glacier / main
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

REPO_DIR = sys.argv[1] if len(sys.argv) > 1 else "."
SLUG = sys.argv[2] if len(sys.argv) > 2 else "Ayang233/Glacier"
BRANCH = sys.argv[3] if len(sys.argv) > 3 else "main"
API = "https://api.github.com"
UA = "glacier-push-api"


def git(*args, binary=False):
    out = subprocess.run(["git", "-C", REPO_DIR] + list(args), capture_output=True, check=True).stdout
    return out if binary else out.decode("utf-8", "replace").strip()


def token():
    tok = os.environ.get("GITHUB_TOKEN")
    if tok:
        return tok.strip()
    res = subprocess.run(
        ["git", "credential", "fill"],
        input=b"protocol=https\nhost=github.com\n\n",
        capture_output=True,
    )
    for line in res.stdout.decode("utf-8", "replace").splitlines():
        if line.startswith("password="):
            return line[len("password="):].strip()
    raise SystemExit("找不到 GitHub token：设置 GITHUB_TOKEN 或先让 git 记住凭据")


TOKEN = token()


def api(endpoint, payload=None, method="GET"):
    url = endpoint if endpoint.startswith("http") else f"{API}/{endpoint}"
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"token {TOKEN}")
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "application/vnd.github+json")
    if data is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read()
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:600]
        raise SystemExit(f"{method} {url} -> HTTP {e.code}: {detail}") from None
    return json.loads(body) if body else {}


def tree_entries(rev):
    out = {}
    for rec in git("ls-tree", "-r", "-z", rev, binary=True).split(b"\0"):
        if not rec:
            continue
        meta, path = rec.split(b"\t", 1)
        mode, _type, sha = meta.decode().split()
        out[path.decode("utf-8", "replace")] = (mode, sha)
    return out


def commit_object(rev):
    """拆分原始 commit 对象：headers + message（message 保持原样，保证 SHA 可复现）。"""
    raw = git("cat-file", "commit", rev, binary=True)
    head, message = raw.split(b"\n\n", 1)
    headers = {}
    for line in head.decode("utf-8", "replace").splitlines():
        key, _, value = line.partition(" ")
        headers.setdefault(key, []).append(value)
    return headers, message.decode("utf-8", "replace")


def identity(commit, fmt):
    """author / committer：GitHub 要求 ISO 8601 日期，直接问 git 要。"""
    return {
        "name": git("show", "-s", "--format=%an", commit),
        "email": git("show", "-s", "--format=%ae", commit),
        "date": git("show", "-s", f"--format={fmt}", commit),
    }


remote_head = api(f"repos/{SLUG}/git/ref/heads/{BRANCH}")["object"]["sha"]
remote_tree_sha = api(f"repos/{SLUG}/git/commits/{remote_head}")["tree"]["sha"]
print(f"remote {remote_head[:10]} tree={remote_tree_sha[:10]}")

local_commits = git("rev-list", "--reverse", "HEAD").splitlines()
anchor = None
for c in local_commits:
    if git("rev-parse", f"{c}^{{tree}}") == remote_tree_sha:
        anchor = c
        break
if anchor and local_commits[-1] == anchor:
    print("already up to date")
    sys.exit(0)
pending = local_commits[local_commits.index(anchor) + 1:] if anchor else local_commits
print(f"pending commits: {len(pending)}")

head, base_tree = remote_head, remote_tree_sha
for commit in pending:
    headers, message = commit_object(commit)
    parent = git("rev-parse", f"{commit}^")
    changes = {}
    for line in git("diff-tree", "-r", "--no-commit-id", "--name-status", parent, commit).splitlines():
        status, path = line.split("\t", 1)
        changes[path] = status
    local_tree = tree_entries(commit)

    entries, uploaded = [], 0
    for path, status in changes.items():
        if status == "D":
            entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
            continue
        mode, sha = local_tree[path]
        if mode == "120000":
            print(f"  skip symlink {path}")
            continue
        content = git("cat-file", "blob", sha, binary=True)
        blob = api(
            f"repos/{SLUG}/git/blobs",
            {"content": base64.b64encode(content).decode("ascii"), "encoding": "base64"},
            method="POST",
        )
        entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
        uploaded += len(content)

    new_tree = api(f"repos/{SLUG}/git/trees", {"base_tree": base_tree, "tree": entries}, method="POST")
    created = api(
        f"repos/{SLUG}/git/commits",
        {
            "message": message,
            "tree": new_tree["sha"],
            "parents": [head],
            "author": identity(commit, "%aI"),
            "committer": identity(commit, "%cI"),
        },
        method="POST",
    )
    head, base_tree = created["sha"], new_tree["sha"]
    same = "sha 与本地一致" if created["sha"] == commit else f"本地 {commit[:10]}"
    print(f"  -> {head[:10]}  {message.splitlines()[0][:56]}  ({len(changes)} paths, {uploaded / 1024:.1f} KiB, {same})")

api(f"repos/{SLUG}/git/refs/heads/{BRANCH}", {"sha": head, "force": False}, method="PATCH")
print(f"DONE head={head}")
