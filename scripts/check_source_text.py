"""核对设计稿中的唯一正文；只去掉 Markdown 引用和粗体，不改写文字。"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def check(design_path):
    section = design_path.read_text(encoding="utf-8").split("### 正文\n\n", 1)[1]
    lines = []
    for line in section.splitlines():
        if line.startswith("> "):
            line = line[2:]
        elif line.startswith(">"):
            line = line[1:]
        elif line:
            raise ValueError("正文中遇到未识别的设计稿结构")
        lines.append(line.replace("**", ""))
    expected = "\n".join(lines) + "\n"
    actual = (ROOT / "shared/src/main/resources/meridian_progression/qi-notes.txt").read_text(encoding="utf-8")
    if actual != expected:
        raise ValueError("残卷正文与设计稿不一致，包含空格、标点或模糊位置差异")
    print("残卷正文与设计稿逐字一致")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("用法：python3 scripts/check_source_text.py <07-单机流程.md>")
    check(Path(sys.argv[1]))
