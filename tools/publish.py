#!/usr/bin/env python3
"""把本地提交增量推送到 GitHub（走 Git Data API）。

为什么需要它：本机网络只放行 api.github.com，github.com 被代理拦截，
`git push` 会报 `CONNECT tunnel failed, response 502`。
Git Data API 走 https://api.github.com，因此可以正常推送。

特点：
- 增量：按 tree sha 判断远端已有哪些提交，只推剩下的；
- 不依赖本地与远端 SHA 相同（首次经 API 推送会重建 SHA）；
- 需要已登录的 gh（`gh auth status` 通过）。

用法：
    python tools/publish.py <本地仓库目录> <owner/repo> <gh 可执行文件>
"""
import base64
import json
import subprocess
import sys

REPO_DIR = sys.argv[1]
SLUG = sys.argv[2]
GH = sys.argv[3]


def git(*args, binary=False):
    out = subprocess.run(
        ["git", "-C", REPO_DIR] + list(args), capture_output=True, check=True
    ).stdout
    return out if binary else out.decode("utf-8", "replace").strip()


def api(endpoint, payload=None, method="POST"):
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
        raise RuntimeError(
            f"{endpoint} failed: {res.stderr.decode('utf-8', 'replace')[:400]}"
        )
    body = res.stdout.decode("utf-8", "replace").strip()
    return json.loads(body) if body else {}


def remote_state():
    """返回 (远端 head sha, 远端已有 tree sha 集合)。空仓库返回 (None, set())。"""
    try:
        ref = api(f"repos/{SLUG}/git/ref/heads/main", method="GET")
    except RuntimeError:
        return None, set()
    head = ref["object"]["sha"]
    trees = set()
    page = 1
    while True:
        batch = api(
            f"repos/{SLUG}/commits?sha=main&per_page=100&page={page}", method="GET"
        )
        if not batch:
            break
        for item in batch:
            trees.add(item["commit"]["tree"]["sha"])
        if len(batch) < 100:
            break
        page += 1
    return head, trees


def initialize():
    created = api(
        f"repos/{SLUG}/contents/.gitkeep",
        {
            "message": "chore: initialize repository",
            "content": base64.b64encode(b"").decode("ascii"),
        },
        method="PUT",
    )
    return created["commit"]["sha"]


head, remote_trees = remote_state()
if head is None:
    head = initialize()
    print(f"initialized empty repo at {head[:10]}")

local = git("rev-list", "--reverse", "HEAD").splitlines()
print(f"local commits: {len(local)}, remote head: {head[:10]}")

# 跳过远端已存在的提交（按 tree 判定，不依赖 SHA 相同）
index = 0
while index < len(local) and git("rev-parse", f"{local[index]}^{{tree}}") in remote_trees:
    index += 1

pending = local[index:]
if not pending:
    print("already up to date")
    sys.exit(0)
print(f"to push: {len(pending)}")

blob_cache = {}
for commit in pending:
    message = git("log", "-1", "--pretty=%B", commit)
    entries = []
    for record in git("ls-tree", "-r", "-z", commit, binary=True).split(b"\0"):
        if not record:
            continue
        meta, path = record.split(b"\t", 1)
        mode, otype, obj = meta.decode().split()
        if otype != "blob":
            continue
        entries.append(
            {
                "path": path.decode("utf-8", "replace"),
                "mode": mode,
                "type": "blob",
                "sha": obj,
            }
        )

    for entry in entries:
        obj = entry["sha"]
        if obj in blob_cache:
            continue
        content = git("cat-file", "blob", obj, binary=True)
        blob = api(
            f"repos/{SLUG}/git/blobs",
            {"content": base64.b64encode(content).decode("ascii"), "encoding": "base64"},
        )
        if blob["sha"] != obj:
            raise RuntimeError(f"blob sha mismatch: {entry['path']}")
        blob_cache[obj] = blob["sha"]

    tree = api(f"repos/{SLUG}/git/trees", {"tree": entries})
    created = api(
        f"repos/{SLUG}/git/commits",
        {"message": message, "tree": tree["sha"], "parents": [head]},
    )
    head = created["sha"]
    print(f"  -> {head[:10]}  {message.splitlines()[0][:60]}")

api(
    f"repos/{SLUG}/git/refs/heads/main",
    {"sha": head, "force": True},
    method="PATCH",
)
print(f"DONE head={head}")
