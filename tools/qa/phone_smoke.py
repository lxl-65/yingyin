#!/usr/bin/env python3
"""以普通用户视角跑一遍手机端主路径：首页 → 分类 → 搜索。

只用 ADB，不依赖 PC 的 8899 服务。任何一步超时都会返回非 0，方便放进每次发版前的检查。
用法：
  python tools/qa/phone_smoke.py
  python tools/qa/phone_smoke.py --serial 83e3caf8
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

PKG = "app.yingyin.player"
ADB = os.environ.get("ADB", r"C:\Tools\android-sdk\platform-tools\adb.exe")


def sh(*args: str, timeout: int = 20) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    return (p.stdout or "") + (p.stderr or "")


def dump(serial: str | None = None) -> ET.Element:
    # 这里的 XML 来自本机 adb 的 uiautomator，不是网络输入，使用 stdlib 解析即可。
    last = ""
    for _ in range(5):
        out = sh("shell", "uiautomator", "dump", "/sdcard/yingyin-qa.xml")
        if "dumped" in out.lower():
            with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as f:
                path = f.name
            try:
                sh("pull", "/sdcard/yingyin-qa.xml", path)
                return ET.parse(path).getroot()
            finally:
                try:
                    os.unlink(path)
                except OSError:
                    pass
        last = out
        time.sleep(0.5)
    raise RuntimeError(f"uiautomator dump failed: {last}")


def texts(root: ET.Element) -> list[str]:
    return [n.attrib.get("text", "") for n in root.iter("node") if n.attrib.get("text")]


def wait_text(want: str, timeout: float = 20) -> ET.Element:
    end = time.time() + timeout
    while time.time() < end:
        try:
            root = dump()
            if any(want in t for t in texts(root)):
                return root
        except Exception:
            pass
        time.sleep(0.7)
    raise AssertionError(f"等待文本超时: {want!r}; 当前: {texts(dump())[:25]}")


def tap_text(root: ET.Element, want: str) -> None:
    for n in root.iter("node"):
        if n.attrib.get("text", "") == want:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                return
    raise AssertionError(f"找不到可点击文本: {want!r}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial")
    args = ap.parse_args()
    if args.serial:
        os.environ["ANDROID_SERIAL"] = args.serial
    sh("shell", "input", "keyevent", "224", timeout=10)
    time.sleep(0.5)
    sh("shell", "wm", "dismiss-keyguard", timeout=10)
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    home = wait_text("个源已接入", 35)
    print("HOME", [t for t in texts(home) if "源" in t][:3])
    assert not any("伦理" in t or "色情" in t for t in texts(home)), "首页泄露私密内容"

    tap_text(home, "分类")
    cat = wait_text("按类型找片", 10)
    tap_text(cat, "电影")
    movie = wait_text("部已加载", 20)
    mt = texts(movie)
    print("MOVIE", [t for t in mt if "部已加载" in t or t in ("动作", "爱情", "喜剧")][:8])
    assert not any("伦理" in t or "色情" in t for t in mt), "分类页泄露私密内容"
    assert any(t in ("动作", "爱情", "喜剧") for t in mt), "电影类型筛选没有正常翻译"

    # 分类详情页不显示底部导航：返回一次回到分类总览，再点底部“首页”。
    sh("shell", "input", "keyevent", "4")
    cat = wait_text("与网页端同款分类", 8)
    tap_text(cat, "首页")
    home2 = wait_text("个源已接入", 10)
    tap_text(home2, "搜索")
    search = wait_text("热门搜索", 10)
    tap_text(search, "庆余年")
    t0 = time.time()
    result = wait_text("搜索结果", 12)
    rt = texts(result)
    print("SEARCH", [t for t in rt if "部" in t][:3], f"{time.time() - t0:.1f}s")
    assert any("部" in t for t in rt), "搜索没有出结果"
    assert not any("伦理" in t or "色情" in t for t in rt), "搜索结果泄露私密内容"
    print("PASS phone smoke")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("FAIL", e)
        sys.exit(1)
