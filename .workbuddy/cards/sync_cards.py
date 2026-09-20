#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把卡库清单同步到 App 的 assets，并做一次一致性自检。

用法（在仓库任意位置执行都可以，Python 3 即可）：

    python .workbuddy/cards/sync_cards.py

做四件事：
1. 校验 cards.json 是合法 JSON，并把各系列拍平；
2. 逐张卡检查卡面文件是否存在、宽高比是否与清单里写的数字一致（png / jpg 都支持）；
3. 如果这张卡声明了 drawables，检查 res/drawable-nodpi/ 下真有对应文件；
4. 把清单写到 app/src/main/assets/cards/cards.json —— 唯一来源永远是 .workbuddy/cards/ 那一份，
   这份不要手改。assets 版会把图片路径换成文件名（图片本体不进 assets）。

任何一项不对都会打印出来并以非 0 退出，但**不会**自动改文件——卡面尺寸对不上是设计问题，得人来定。
"""

import json
import struct
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent          # .workbuddy/cards
PROJECT_ROOT = SCRIPT_DIR.parents[1]                  # 仓库根
SOURCE = SCRIPT_DIR / "cards.json"
ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "cards"
DRAWABLE_DIR = PROJECT_ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
DRAWABLE_SUFFIXES = (".png", ".jpg", ".jpeg", ".webp")


def image_size(path: Path):
    """读 PNG / JPEG 的像素尺寸，不依赖第三方库。"""
    raw = path.read_bytes()
    if raw[:8] == b"\x89PNG\r\n\x1a\n":
        return struct.unpack(">II", raw[16:24])
    if raw[:2] == b"\xff\xd8":
        i = 2
        while i < len(raw) - 9:
            if raw[i] != 0xFF:
                i += 1
                continue
            marker = raw[i + 1]
            if marker in (0xC0, 0xC1, 0xC2, 0xC3):
                height, width = struct.unpack(">HH", raw[i + 5 : i + 9])
                return width, height
            if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
                i += 2
                continue
            i += 2 + struct.unpack(">H", raw[i + 2 : i + 4])[0]
    raise ValueError(f"{path.name} 既不是 PNG 也不是 JPEG")


def iter_cards(data):
    for series in data.get("series", []):
        for card in series.get("cards", []):
            yield series, card


def main() -> int:
    if not SOURCE.exists():
        print(f"[x] 找不到清单：{SOURCE}")
        return 1

    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    problems = []
    stats = {}

    for series, card in iter_cards(data):
        card_id = card.get("id", "?")
        label = f"{series.get('id', '?')}/{card_id}"
        art = card.get("art") or {}
        found = []

        for role in ("full", "chip"):
            rel = art.get(role)
            if not rel:
                continue                                   # 缺 chip 不算错（缩略图常常后补）
            image = SCRIPT_DIR / rel
            if not image.exists():
                problems.append(f"{label}: {role} 卡面不存在 → {rel}")
                continue
            width, height = image_size(image)
            actual = round(width / height, 4)
            declared = round(float(art.get(f"aspectRatio{role.capitalize()}", 0)), 4)
            if declared and abs(actual - declared) > 0.001:
                problems.append(
                    f"{label}: {role} 宽高比不符 — 清单写 {declared}，实际 {width}×{height}={actual}"
                )
            found.append(f"{role} {width}×{height} {image.stat().st_size:,}B")

        if not found:
            problems.append(f"{label}: 一个可用的卡面都没有")

        for role, name in (card.get("drawables") or {}).items():
            if not name:
                continue                                   # chip 常常还没裁，留 null
            hits = [DRAWABLE_DIR / f"{name}{suffix}" for suffix in DRAWABLE_SUFFIXES]
            if not any(hit.exists() for hit in hits):
                problems.append(f"{label}: drawable {name} 在 res/drawable-nodpi/ 下没有对应文件")

        prepared = art.get("prepared")
        if prepared:
            target = DRAWABLE_DIR / prepared["file"]
            if not target.exists():
                problems.append(f"{label}: prepared 指向的文件不存在 → {prepared['file']}")
            else:
                width, height = image_size(target)
                if (width, height) != (prepared["width"], prepared["height"]):
                    problems.append(
                        f"{label}: prepared 尺寸不符 — 清单写 "
                        f"{prepared['width']}×{prepared['height']}，实际 {width}×{height}"
                    )

        detail = card.get("detail")
        if detail and not (SCRIPT_DIR / detail).exists():
            problems.append(f"{label}: detail 指向的 meta.json 不存在 → {detail}")

        series_id = series.get("id", "?")
        stats[series_id] = stats.get(series_id, 0) + 1
        mark = "packaged" if (card.get("drawables") or {}) else "art-only"
        rarity = card.get("rarity") or "-"
        print(f"  [{mark:9}] {label:34} {rarity:>5}  {'; '.join(found) or '无图'}")

    if problems:
        print(f"\n[!] 发现 {len(problems)} 处问题：")
        for item in problems:
            print("    -", item)
        return 1

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    payload = json.loads(json.dumps(data, ensure_ascii=False))
    payload["generatedBy"] = "sync_cards.py"
    payload["sourceOfTruth"] = ".workbuddy/cards/cards.json"
    payload["note"] = (
        "由 .workbuddy/cards/sync_cards.py 生成，不要手改。"
        "这里 art 只留文件名，图片本体不在 assets：已打包的卡走 res/drawable-nodpi（见 drawables 字段），"
        "未打包的卡图片留在 .workbuddy/cards/ 里。"
    )
    for _, card in iter_cards(payload):
        art = card.get("art") or {}
        for role in ("full", "chip"):
            if art.get(role):
                art[role] = Path(art[role]).name

    out = ASSETS_DIR / "cards.json"
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total = sum(stats.values())
    summary = "、".join(f"{key} {value} 张" for key, value in stats.items())
    print(f"\n[ok] 共 {total} 张卡（{summary}）已同步 → {out.relative_to(PROJECT_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
