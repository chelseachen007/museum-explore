# -*- coding: utf-8 -*-
"""版本发布：改版本号、写更新说明、打包、打 tag、建 GitHub Release。

用法：
    python3 tools/release.py 0.6.0 --note "博物馆详情页可直接打开官方预约入口" \
        --note "新增参观时间线，打卡状态自动留痕"

规则：
    - 功能点至少一条，空说明直接拒绝发版。
    - 版本号同时写进 build.gradle、MainActivity.kt 和 CHANGELOG.md。
    - 先跑数据校验和单测，再打 APK；任何一步失败都不产生 tag 或 Release。
    - 默认只发布到 GitHub，不自动推送代码历史：代码提交由人确认。
    - 需要一次性完成 commit + push 时加 --commit。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
GRADLE_FILE = ROOT / "android" / "app" / "build.gradle"
MAIN_ACTIVITY = ROOT / "android" / "app" / "src" / "main" / "java" / "cn" / "chenjie" / "museumexplore" / "MainActivity.kt"
CHANGELOG_FILE = ROOT / "CHANGELOG.md"
APK_SOURCE = ROOT / "android" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
APK_NAME = "boji-{version}-debug.apk"
GRADLE = ROOT / ".toolchain" / "gradle-8.10.2" / "bin" / "gradle"
JAVA_HOME = ROOT / ".toolchain" / "jdk-17.0.20.1+1" / "Contents" / "Home"
SDK = ROOT / ".toolchain" / "android-sdk"


def run(command: list[str], *, env: dict[str, str] | None = None, capture: bool = False) -> subprocess.CompletedProcess:
    print("$ " + " ".join(command), flush=True)
    return subprocess.run(
        command,
        cwd=ROOT,
        env=env,
        text=True,
        capture_output=capture,
        check=True,
    )


def fail(message: str) -> None:
    print(f"\n发布中止：{message}", file=sys.stderr)
    sys.exit(1)


def git_output(*args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=True,
    ).stdout.strip()


def parse_version(raw: str) -> tuple[str, int, int, int]:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", raw.strip())
    if not match:
        fail(f"版本号必须形如 0.6.0，收到 {raw!r}")
    major, minor, patch = (int(part) for part in match.groups())
    return f"{major}.{minor}.{patch}", major, minor, patch


def version_code(major: int, minor: int, patch: int) -> int:
    return major * 10000 + minor * 100 + patch


def current_version() -> str:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    match = re.search(r"versionName\s+'([^']+)'", text)
    if not match:
        fail("读不到 build.gradle 里的 versionName")
    return match.group(1)


def read_notes(path: str | None, notes: list[str]) -> list[str]:
    lines: list[str] = []
    if path:
        file_path = Path(path)
        if not file_path.is_absolute():
            file_path = ROOT / file_path
        if not file_path.exists():
            fail(f"说明文件不存在: {file_path}")
        lines.extend(
            line.strip().lstrip("-*").strip()
            for line in file_path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")
        )
    lines.extend(note.strip() for note in notes if note.strip())
    unique: list[str] = []
    for line in lines:
        # 只去掉「1. 」「- 」「* 」这类列表前缀，不能连说明开头的数字一起吃掉。
        line = re.sub(r"^\s*(?:[-*]\s+|\d+[\.\)、]\s*)", "", line).strip()
        if line and line not in unique:
            unique.append(line)
    if not unique:
        fail("没有功能点。用 --note \"...\" 或 --notes-file 提供本次更新内容。")
    return unique


def ensure_clean(allow_dirty: bool) -> None:
    if allow_dirty:
        return
    dirty = git_output("status", "--porcelain")
    if dirty:
        fail(
            "工作区有未提交改动。先提交，或确认要带上全部改动时加 --allow-dirty。\n"
            + dirty[:1200]
        )


def ensure_branch() -> None:
    branch = git_output("rev-parse", "--abbrev-ref", "HEAD")
    if branch != "main":
        fail(f"发版只允许在 main 分支，当前是 {branch}")


def ensure_not_tagged(version: str) -> None:
    tags = git_output("tag", "--list", f"v{version}")
    if tags:
        fail(f"tag v{version} 已存在。改版本号，或先删除旧 tag。")


def bump_version(version: str, code: int) -> None:
    gradle = GRADLE_FILE.read_text(encoding="utf-8")
    gradle, code_count = re.subn(r"(versionCode\s+)\d+", rf"\g<1>{code}", gradle)
    gradle, name_count = re.subn(r"(versionName\s+)'[^']+'", rf"\g<1>'{version}'", gradle)
    if code_count != 1 or name_count != 1:
        fail("build.gradle 里 versionCode / versionName 的数量不符合预期")
    GRADLE_FILE.write_text(gradle, encoding="utf-8")

    activity = MAIN_ACTIVITY.read_text(encoding="utf-8")
    activity, agent_count = re.subn(r"BoJi/\d+\.\d+\.\d+", f"BoJi/{version}", activity)
    activity, about_count = re.subn(r"版本 \d+\.\d+\.\d+", f"版本 {version}", activity)
    if agent_count != 1 or about_count != 2:
        fail("MainActivity.kt 里版本号出现次数不符合预期，请检查页面文案")
    MAIN_ACTIVITY.write_text(activity, encoding="utf-8")


def write_changelog(version: str, notes: list[str], today: str) -> None:
    header = "# 更新日志\n\n本文件由 `tools/release.py` 维护。每条版本说明都对应 GitHub Release。\n"
    entry = f"\n## v{version} · {today}\n\n" + "\n".join(f"- {note}" for note in notes) + "\n"
    if CHANGELOG_FILE.exists():
        text = CHANGELOG_FILE.read_text(encoding="utf-8")
        if f"## v{version}" in text:
            fail(f"CHANGELOG.md 已有 v{version}，请改版本号")
        if text.startswith("# 更新日志"):
            # 旧版本每次发版都会把 header 再插一遍，留下十几行重复的说明。
            # 这里先把已有的说明行全部清掉，再重新拼一次，保证只留一份。
            body = re.sub(r"^# 更新日志[ \t]*\n", "", text, count=1)
            body = re.sub(
                r"^\s*\n?本文件由 `tools/release\.py` 维护。.*$", "", body, flags=re.MULTILINE
            ).lstrip("\n")
            CHANGELOG_FILE.write_text(header + "\n" + body.rstrip() + "\n" + entry, encoding="utf-8")
        else:
            CHANGELOG_FILE.write_text(header + entry + "\n" + text, encoding="utf-8")
    else:
        CHANGELOG_FILE.write_text(header + entry, encoding="utf-8")


def validate_and_test(env: dict[str, str]) -> None:
    run([sys.executable, "tools/text_clean.py", "--check"], env=env)
    run([sys.executable, "tools/validate_data.py"], env=env)
    run(
        [str(GRADLE), "-p", "android", "testDebugUnitTest"],
        env=env,
    )


def build_apk(env: dict[str, str]) -> Path:
    run([str(GRADLE), "-p", "android", "clean", "assembleDebug"], env=env)
    if not APK_SOURCE.exists():
        fail(f"没有生成 APK: {APK_SOURCE}")
    return APK_SOURCE


def verify_apk(apk: Path, version: str, code: int) -> None:
    aapt = SDK / "build-tools" / "35.0.0" / "aapt"
    signer = SDK / "build-tools" / "35.0.0" / "apksigner"
    if not aapt.exists() or not signer.exists():
        fail("找不到 Android build-tools 35.0.0")
    badging = run([str(aapt), "dump", "badging", str(apk)], capture=True).stdout
    if f"versionName='{version}'" not in badging or f"versionCode='{code}'" not in badging:
        fail(f"APK 版本与预期不符（期望 {version} / {code}）")
    run([str(signer), "verify", str(apk)], capture=True)


def security_check() -> None:
    tracked = git_output("ls-files").splitlines()
    pattern = re.compile(
        r"(?i)(?:sk-[A-Za-z0-9]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16})"
    )
    hits = []
    for name in tracked:
        path = ROOT / name
        if not path.is_file() or path.stat().st_size > 2_000_000:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if pattern.search(text):
            hits.append(name)
    if hits:
        fail("检测到疑似密钥文件：" + ", ".join(hits))


def commit_and_push(version: str, code: int, notes: list[str], push_code: bool) -> None:
    files = [str(GRADLE_FILE.relative_to(ROOT)), str(MAIN_ACTIVITY.relative_to(ROOT)), "CHANGELOG.md"]
    run(["git", "add", *files])
    dirty = git_output("diff", "--cached", "--name-only")
    if dirty:
        run(["git", "commit", "-m", f"发布 v{version}"])
    if push_code:
        run(["git", "push", "origin", "main"])


def create_release(version: str, apk: Path, notes: list[str]) -> None:
    tag = f"v{version}"
    title = f"博迹 v{version}"
    body = f"## 博迹 v{version}\n\n" + "\n".join(f"- {note}" for note in notes)
    run(["git", "tag", "-a", tag, "-m", title])
    run(["git", "push", "origin", tag])
    run(
        [
            "gh",
            "release",
            "create",
            tag,
            str(apk),
            "--title",
            title,
            "--notes",
            body,
        ]
    )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="发布新版本：改版本号、打包、写 Release")
    parser.add_argument("version", help="新版本号，例如 0.6.0")
    parser.add_argument("--note", action="append", default=[], help="一条更新说明，可重复")
    parser.add_argument("--notes-file", help="从文件读取更新说明，一行一条")
    parser.add_argument("--allow-dirty", action="store_true", help="允许带未提交改动发版（不推荐）")
    parser.add_argument("--commit", action="store_true", help="自动提交版本号和 CHANGELOG")
    parser.add_argument("--push", action="store_true", help="配合 --commit，把代码推送到 origin/main")
    args = parser.parse_args(argv)

    version, major, minor, patch = parse_version(args.version)
    code = version_code(major, minor, patch)
    old_version = current_version()
    if version == old_version:
        fail(f"版本号没有变化，当前已经是 {old_version}")
    if (major, minor, patch) < tuple(int(part) for part in old_version.split(".")):
        fail(f"新版本 {version} 低于当前版本 {old_version}")

    notes = read_notes(args.notes_file, args.note)
    ensure_branch()
    ensure_clean(args.allow_dirty)
    ensure_not_tagged(version)

    env = os.environ.copy()
    env["JAVA_HOME"] = str(JAVA_HOME)
    env["PATH"] = f"{JAVA_HOME / 'bin'}:{env.get('PATH', '')}"

    print(f"\n发布 v{version}（versionCode {code}）")
    for note in notes:
        print(f"  - {note}")
    print()

    security_check()
    validate_and_test(env)
    bump_version(version, code)
    write_changelog(version, notes, date.today().isoformat())
    apk = build_apk(env)
    verify_apk(apk, version, code)

    artifact = Path(tempfile.mkdtemp(prefix="boji-release-")) / APK_NAME.format(version=version)
    shutil.copy2(apk, artifact)
    print(f"\nAPK: {artifact} ({artifact.stat().st_size / 1024 / 1024:.0f} MB)")

    commit_and_push(version, code, notes, push_code=args.push)
    create_release(version, artifact, notes)

    print("\n发布完成：")
    print(f"  https://github.com/chelseachen007/museum-explore/releases/tag/v{version}")
    if not args.commit:
        print("\n注意：版本号和 CHANGELOG 的改动还没有提交。确认后执行：")
        print("  git add android/app/build.gradle android/app/src/main/java/cn/chenjie/museumexplore/MainActivity.kt CHANGELOG.md")
        print(f"  git commit -m \"发布 v{version}\" && git push origin main")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except subprocess.CalledProcessError as error:
        print(f"\n发布中止：命令失败（退出码 {error.returncode}）", file=sys.stderr)
        sys.exit(error.returncode or 1)
