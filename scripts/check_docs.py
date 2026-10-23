"""检查本仓库文档、正文摘要与资源引用，不访问外仓或网络。"""
import hashlib
import json
from pathlib import Path
import re
import struct
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def check():
    # 文档与三卷实际资源一起校验，避免新卷正文更改后遗留旧摘要。
    wiki = ROOT / "docs/wiki"
    required = {"Home", "Scroll", "Protocol", "Integration", "Development", "_Sidebar"}
    pages = sorted(wiki.glob("*.md"))
    if required - {page.stem for page in pages}:
        raise ValueError("Wiki 页面不完整")
    for path in [ROOT / "README.md", ROOT / "AGENTS.md", ROOT / "THIRD_PARTY_NOTICES.md", *pages]:
        text = path.read_text(encoding="utf-8")
        if not text.strip() or not text.endswith("\n") or path.is_symlink():
            raise ValueError(f"文档格式错误：{path}")
        if len(re.findall(r"^```", text, re.M)) % 2:
            raise ValueError(f"代码块未闭合：{path}")
        if any(line.rstrip() != line for line in text.splitlines()):
            raise ValueError(f"行尾空白：{path}")
        for target in re.findall(r"\]\(([^)]+)\)", text):
            parsed = urlsplit(target)
            if not parsed.scheme and parsed.path:
                linked = (path.parent / unquote(parsed.path)).resolve()
                if not linked.is_relative_to(ROOT) or not linked.exists():
                    raise ValueError(f"文档链接失效：{path}: {target}")
    for name in ("qi-notes", "gathering", "middle-jiao"):
        body = ROOT / "shared/src/main/resources/meridian_progression" / f"{name}.txt"
        revision = hashlib.sha256(body.read_bytes()).hexdigest()
        if revision not in (wiki / "Scroll.md").read_text(encoding="utf-8"):
            raise ValueError(f"正文修订与 Wiki 摘要不一致：{name}")
    resources = []
    for module in ("fabric", "client"):
        directory = ROOT / module / "src/main/resources"
        resources.extend(directory.rglob("*.json"))
        metadata = json.loads((directory / "fabric.mod.json").read_text(encoding="utf-8"))
        for values in metadata["entrypoints"].values():
            for entry in values:
                source = ROOT / module / "src/main/java" / (entry.replace(".", "/") + ".java")
                if not source.is_file():
                    raise ValueError(f"缺少入口实现：{entry}")
    for resource in resources:
        json.loads(resource.read_text(encoding="utf-8"))
    # 美术是必需资源：模型引用、PNG 尺寸和三卷独立图像一起校验，防止漏装或错绑。
    for category, module, folder, names, dimensions in (
        ("背景", "client", "gui/scroll", ("qi_notes", "gathering", "middle_jiao"), (1536, 1024)),
        ("图标", "fabric", "item", ("fragment_scroll", "gathering_scroll", "middle_jiao_scroll"), (32, 32)),
    ):
        checksums = set()
        assets = ROOT / module / "src/main/resources/assets/meridian_progression"
        for name in names:
            path = assets / "textures" / folder / f"{name}.png"
            data = path.read_bytes()
            if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != dimensions:
                raise ValueError(f"{category}尺寸或格式无效：{path}")
            checksums.add(hashlib.sha256(data).hexdigest())
            if category == "图标":
                model = json.loads((assets / "models/item" / f"{name}.json").read_text(encoding="utf-8"))
                if model["textures"]["layer0"] != f"meridian_progression:item/{name}":
                    raise ValueError(f"图标引用错误：{name}")
        if len(checksums) != len(names):
            raise ValueError(f"三卷{category}必须分别生成，不能复用同一图片")
    audio = ROOT / "client/src/main/resources/assets/meridian_progression"
    sounds = json.loads((audio / "sounds.json").read_text(encoding="utf-8"))
    for definition in sounds.values():
        for sound in definition["sounds"]:
            path = audio / "sounds" / (sound["name"].split(":", 1)[1] + ".ogg")
            if path.read_bytes()[:4] != b"OggS":
                raise ValueError(f"声音资源无效：{path}")
    print(f"文档、正文摘要、入口及 {len(resources)} 个 JSON 资源检查通过")


if __name__ == "__main__":
    check()
