#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把命令的输出排版成图片，贴到实验报告里当运行截图用。

用法：
    python3 scripts/render-shot.py --title "标题" --out out.png < captured.txt

设计说明：
  * 只做排版和着色，不改动文字，图里的内容就是命令实际打印出来的；
  * 高度按内容行数估算，避免固定高度导致截断或大片空白；
  * 只依赖本机已安装的 Chrome，不引入额外依赖。
"""
import argparse
import html
import os
import re
import subprocess
import sys
import tempfile

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")

TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<style>
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0; padding: 18px; background: #eef0f3;
    font-family: -apple-system, "PingFang SC", "Helvetica Neue", sans-serif;
  }}
  .window {{
    background: #ffffff; border: 1px solid #d0d4da; border-radius: 10px;
    overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,.08);
    width: {width}px;
  }}
  .bar {{
    height: 34px; background: linear-gradient(#fdfdfd, #eceef1);
    border-bottom: 1px solid #d8dbe0; display: flex; align-items: center;
    padding: 0 12px; gap: 8px;
  }}
  .dot {{ width: 11px; height: 11px; border-radius: 50%; flex: none; }}
  .r {{ background: #ff5f57; }} .y {{ background: #febc2e; }} .g {{ background: #28c840; }}
  .title {{
    flex: 1; text-align: center; font-size: 12px; color: #4a5057;
    font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }}
  pre {{
    margin: 0; padding: 12px 14px; background: #ffffff; color: #1b1f24;
    font-family: "SF Mono", Menlo, Consolas, monospace;
    font-size: {font_size}px; line-height: 1.5; white-space: pre-wrap;
    word-break: break-word;
  }}
</style></head>
<body>
  <div class="window">
    <div class="bar"><span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
      <span class="title">{title}</span></div>
    <pre>{body}</pre>
  </div>
</body></html>
"""


def highlight(line: str) -> str:
    """给关键行着色。只影响颜色，不增删任何字符。"""
    escaped = html.escape(line)
    stripped = line.strip()
    if stripped.startswith(("$ ", "mysql", "mvn ", "git ", "bash ", "java ", "python3")):
        return f'<span style="color:#0a6b3d;font-weight:600">{escaped}</span>'
    if "BUILD SUCCESS" in line or "Tests run: 116" in line or "OK (" in line:
        return f'<span style="color:#0a6b3d;font-weight:700">{escaped}</span>'
    if "BUILD FAILURE" in line or "FAILURES" in line or stripped.startswith("[ERROR]"):
        return f'<span style="color:#b3261e;font-weight:700">{escaped}</span>'
    if "[SLOW-SQL]" in line:
        return f'<span style="color:#9a6700;font-weight:600">{escaped}</span>'
    if stripped.startswith("[INFO]"):
        return f'<span style="color:#0b5cad">{escaped}</span>'
    if stripped.startswith("==>"):
        return f'<span style="color:#0b5cad;font-weight:600">{escaped}</span>'
    return escaped


def estimate_height(lines: int, font_size: int, width: int, raw: str) -> int:
    """估算图片高度：按行数算，长行按换行折算。"""
    line_height = int(font_size * 1.5)
    # 每行可容纳的字符数（等宽字体近似：字号 * 0.62 像素/字符）
    per_line = max(10, int((width - 28) / (font_size * 0.62)))
    visual_lines = 0
    for line in raw.split("\n"):
        visual_lines += max(1, -(-len(line) // per_line))
    return 34 + 24 + visual_lines * line_height + 36


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", default="Terminal")
    parser.add_argument("--out", required=True)
    parser.add_argument("--width", type=int, default=1180)
    parser.add_argument("--font-size", type=int, default=12)
    args = parser.parse_args()

    raw = ANSI.sub("", sys.stdin.read()).rstrip("\n")
    if not raw.strip():
        print("没有输入内容", file=sys.stderr)
        return 1

    body = "\n".join(highlight(line) for line in raw.split("\n"))
    doc = TEMPLATE.format(title=html.escape(args.title), body=body,
                          width=args.width, font_size=args.font_size)
    height = estimate_height(0, args.font_size, args.width, raw)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "shot.html")
        with open(page, "w", encoding="utf-8") as fh:
            fh.write(doc)
        subprocess.run([
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            f"--screenshot={args.out}",
            f"--window-size={args.width + 36},{height}",
            f"file://{page}",
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"已生成 {args.out}  ({height}px)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
