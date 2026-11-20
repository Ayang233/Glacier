#!/usr/bin/env bash
# MIUIX 基线维护脚本（Glacier）
#
# 用法：
#   tools/miuix-baseline.sh status            # 本地 MIUIX 与官方最新 main / tag 的差距
#   tools/miuix-baseline.sh pin <ref>         # 把 MIUIX 源码切到指定 tag / commit 并打本地补丁
#   tools/miuix-baseline.sh fetch             # 拉取官方 main 与全部 tag
#   tools/miuix-baseline.sh verify            # 校验：目录存在、commit 与基线文档一致、补丁已应用
#
# 环境变量：
#   MIUIX_DIR   MIUIX 源码目录；默认 ../miuix（相对仓库根目录）
#
# 说明：Glacier 以 composite build 引用 MIUIX 源码，不解析 Maven 坐标，
# 因此"基线"= 一个具体 commit + 一个本地补丁。基线数值记录在仓库根目录 MIUIX_BASELINE.md。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIUIX_DIR="${MIUIX_DIR:-$(cd "$ROOT/.." && pwd)/miuix}"
BASELINE_FILE="$ROOT/MIUIX_BASELINE.md"
PATCH_FILE="$ROOT/tools/miuix-composite-build.patch"

die() { echo "错误：$*" >&2; exit 1; }

require_miuix() {
    [ -d "$MIUIX_DIR/.git" ] || die "找不到 MIUIX 源码仓库：$MIUIX_DIR（设 MIUIX_DIR 指定）"
}

baseline_commit() {
    grep -E 'MIUIX commit' "$BASELINE_FILE" 2>/dev/null \
        | grep -oE '[0-9a-f]{40}' | head -1
}

cmd_fetch() {
    require_miuix
    git -C "$MIUIX_DIR" fetch origin main --tags
    echo "官方 main：$(git -C "$MIUIX_DIR" log -1 --format='%h %s' origin/main)"
    echo "最新 tag： $(git -C "$MIUIX_DIR" tag --sort=-creatordate | head -1)"
}

cmd_status() {
    require_miuix
    local head current baseline version
    head=$(git -C "$MIUIX_DIR" rev-parse HEAD)
    current=$(git -C "$MIUIX_DIR" describe --tags --always)
    baseline=$(baseline_commit || true)
    version=$(grep -oE 'LIBRARY_VERSION = "[^"]+"' "$MIUIX_DIR/build-logic/src/main/kotlin/BuildConfig.kt" \
        | grep -oE '[0-9][^"]*' || echo "?")

    echo "MIUIX 目录：  $MIUIX_DIR"
    echo "本地 HEAD：   $head ($current)"
    echo "LIBRARY_VERSION：$version"
    [ -n "$baseline" ] && echo "基线记录：    $baseline"
    if [ -n "$baseline" ] && [ "$baseline" != "$head" ]; then
        echo "⚠ 本地 HEAD 与 MIUIX_BASELINE.md 记录的 commit 不一致"
    fi

    if git -C "$MIUIX_DIR" rev-parse --verify -q origin/main >/dev/null; then
        echo
        echo "官方 main：$(git -C "$MIUIX_DIR" log -1 --format='%h %s' origin/main)"
        local ahead
        ahead=$(git -C "$MIUIX_DIR" rev-list --count "HEAD..origin/main")
        echo "落后 main：$ahead 个提交"
        if [ "$ahead" -gt 0 ]; then
            echo "--- 未纳入基线的提交 ---"
            git -C "$MIUIX_DIR" log --oneline "HEAD..origin/main"
        fi
    fi

    if git -C "$MIUIX_DIR" diff --quiet -- build-logic/src/main/kotlin/BuildConfig.kt; then
        echo
        echo "⚠ 本地补丁尚未应用（composite build 会因 git 命令工作目录 exit 128）"
    else
        echo
        echo "本地补丁：已应用（build-logic/BuildConfig.kt workingDir）"
    fi
}

cmd_pin() {
    local ref="${1:-}"
    [ -n "$ref" ] || die "用法：tools/miuix-baseline.sh pin <tag|commit>"
    require_miuix
    git -C "$MIUIX_DIR" fetch origin main --tags
    git -C "$MIUIX_DIR" checkout --detach "$ref"
    if ! git -C "$MIUIX_DIR" apply --check "$PATCH_FILE" 2>/dev/null; then
        echo "补丁无法直接应用（可能已被上游合并），请人工确认后再继续" >&2
    else
        git -C "$MIUIX_DIR" apply "$PATCH_FILE"
        echo "已应用本地补丁：$(basename "$PATCH_FILE")"
    fi
    echo "MIUIX 已切到：$(git -C "$MIUIX_DIR" log -1 --format='%H %s')"
    echo "记得同步更新 MIUIX_BASELINE.md 的「本次基线」表。"
}

cmd_verify() {
    require_miuix
    local head baseline
    head=$(git -C "$MIUIX_DIR" rev-parse HEAD)
    baseline=$(baseline_commit || true)
    [ -n "$baseline" ] || die "MIUIX_BASELINE.md 里没有找到 MIUIX commit 记录"
    [ "$head" = "$baseline" ] || die "MIUIX HEAD ($head) 与基线 ($baseline) 不一致"
    git -C "$MIUIX_DIR" diff --quiet -- build-logic/src/main/kotlin/BuildConfig.kt \
        && die "本地补丁未应用"
    echo "基线校验通过：$head"
}

case "${1:-status}" in
    status)  cmd_status ;;
    fetch)   cmd_fetch ;;
    pin)     shift; cmd_pin "$@" ;;
    verify)  cmd_verify ;;
    *)       die "未知子命令：$1（可用：status / fetch / pin / verify）" ;;
esac
