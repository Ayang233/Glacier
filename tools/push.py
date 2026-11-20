#!/usr/bin/env python3
"""增量推送本地提交到 GitHub（Git Data API，只上传变化的文件）。

为什么不用 `git push`：本机到 github.com 的 443 连接经常被拦（Connection reset / 超时），
但 api.github.com 可用。

为什么比 tools/publish.py 快：publish.py 对每个待推提交都重新上传整棵树的全部 blob，
一次 4 个文件的文档提交也要传几十 MB。这里改用 Git Data API 的 `base_tree`：
只上传相对远端最新树的**变化文件**，未变化的路径由 base_tree 继承。

用法：
    python tools/push.py <本地仓库目录> <owner/repo> <gh 可执行文件>
"""
import base64
import json
import subprocess
import sys

REPO_DIR, SLUG, GH = sys.argv[1], sys.argv[2], sys.argv[3]
BRANCH = "main"


def git(*args, binary=False):
    out = subprocess.run(["git", "-C", REPO_DIR] + list(args), capture_output=True, check=True).stdout
    return out if binary else out.decode("utf-8", "replace").strip()


def api(endpoint, payload=None, method="GET"):
    cmd = [GH, "api", "--method", method, endpoint]
    data = None
    if payload is not None:
        cmd.extend(["--input", "-"])
        data = json.dumps(payload).encode("utf-8")
    elif method in ("POST", "PATCH", "PUT"):
        cmd.extend(["--input", "-"])
        data = b"{}"
    res = subprocess.run(cmd, input=data, capture_output=True)
    if res.returncode != 0:
        raise RuntimeError(f"{endpoint} failed: {res.stderr.decode('utf-8', 'replace')[:400]}")
    body = res.stdout.decode("utf-8", "replace").strip()
    return json.loads(body) if body else {}


def tree_entries(rev):
    """{path: (mode, sha)}，用于本地比较。"""
    out = {}
    for rec in git("ls-tree", "-r", "-z", rev, binary=True).split(b"\0"):
        if not rec:
            continue
        meta, path = rec.split(b"\t", 1)
        mode, _type, sha = meta.decode().split()
        out[path.decode("utf-8", "replace")] = (mode, sha)
    return out


remote_head = api(f"repos/{SLUG}/git/ref/heads/{BRANCH}")["object"]["sha"]
remote_tree_sha = api(f"repos/{SLUG}/git/commits/{remote_head}")["tree"]["sha"]
remote_entries = {
    e["path"]: (e["mode"], e["sha"])
    for e in api(f"repos/{SLUG}/git/trees/{remote_tree_sha}?recursive=1", method="GET").get("tree", [])
    if e["type"] == "blob"
}
print(f"remote {remote_head[:10]} tree={remote_tree_sha[:10]} blobs={len(remote_entries)}")

local_commits = git("rev-list", "--reverse", "HEAD").splitlines()
# 找到与远端树一致的本地提交作为锚点
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

head = remote_head
base_tree = remote_tree_sha
for commit in pending:
    message = git("log", "-1", "--pretty=%B", commit)
    parent = git("rev-parse", f"{commit}^")
    changes = {}
    raw = git("diff-tree", "-r", "--no-commit-id", "--name-status", parent, commit)
    for line in raw.splitlines():
        status, path = line.split("\t", 1)
        changes[path] = status
    local_tree = tree_entries(commit)

    entries = []
    uploaded = 0
    for path, status in changes.items():
        if status == "D":
            entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
            continue
        mode, sha = local_tree[path]
        if mode == "120000":
            print(f"skip symlink {path}")
            continue
        content = git("cat-file", "blob", sha, binary=True)
        blob = api(
            f"repos/{SLUG}/git/blobs",
            {"content": base64.b64encode(content).decode("ascii"), "encoding": "base64"},
            method="POST",
        )
        entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
        uploaded += len(content)

    new_tree = api(
        f"repos/{SLUG}/git/trees",
        {"base_tree": base_tree, "tree": entries},
        method="POST",
    )
    created = api(
        f"repos/{SLUG}/git/commits",
        {"message": message, "tree": new_tree["sha"], "parents": [head]},
        method="POST",
    )
    head, base_tree = created["sha"], new_tree["sha"]
    print(f"  -> {head[:10]}  {message.splitlines()[0][:60]}  ({len(changes)} paths, {uploaded/1024:.1f} KiB)")

api(f"repos/{SLUG}/git/refs/heads/{BRANCH}", {"sha": head, "force": True}, method="PATCH")
print(f"DONE head={head}")
