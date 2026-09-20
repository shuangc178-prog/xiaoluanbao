#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把卡库里的渲染稿处理成能进 APK 的 drawable。

用法（需要 Pillow，本机在系统 Python 3.14 里）：

    C:/Python314/python.exe .workbuddy/cards/prepare_art.py

做两件事：

1. **收集卡**（cards.json 里 `drawables` 有值的卡）：从四角泛洪去背 → 按前景裁边 →
   量化成调色板 PNG → 写进 `app/src/main/res/drawable-nodpi/<drawable>.png`。
   设计稿是 1086×1448 的渲染稿，带米白底和内置投影；直接铺到深色主题上会是一块白方块，
   所以必须先泛洪去背（和休息卡当年同一套流程）。

2. **各弹抽卡按钮封面**：只做缩放与压缩，**不去背**（封面本来就铺满整张图），
   按清单里的 drawable 名写进 `app/src/main/res/drawable-nodpi/`。

同时在 `.workbuddy/cards/preview/` 下留一份预览（去背后的卡面放在浅色/深色两种底上），
方便肉眼检查有没有残留白边——这一步机器判断不了，得人看。
"""

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent                 # .workbuddy/cards
PROJECT_ROOT = ROOT.parents[1]
MANIFEST = ROOT / "cards.json"
DRAWABLE_DIR = PROJECT_ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
PREVIEW_DIR = ROOT / "preview"

# 去背参数：容差取自休息卡那次的经验值（10~12 是对纯色底），
# 这里是 JPEG 渲染稿，压缩噪点更大，取 36；四角都作为种子点。
FLOOD_TOLERANCE = 36
# 保留 256 色调色板：像素画本身颜色数远低于这个值，压缩比却很高。
PALETTE_COLORS = 256
# 封面输出宽度：按钮上最多显示约 110dp，3 倍屏也就 330px，800 足够且有富余。
COVER_WIDTH = 800
COVER_QUALITY = 88


def cut_background(image: Image.Image, tolerance: int = FLOOD_TOLERANCE) -> Image.Image:
    """从四个角泛洪去背，返回带透明通道的图。"""
    canvas = image.convert("RGBA")
    width, height = canvas.size
    transparent = (0, 0, 0, 0)
    for seed in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
        if canvas.getpixel(seed)[3] == 0:
            continue
        ImageDraw.floodfill(canvas, seed, transparent, thresh=tolerance)
    return canvas


def trim_to_content(image: Image.Image) -> Image.Image:
    box = image.getbbox()
    if box is None:
        return image
    return image.crop(box)


def report(label: str, image: Image.Image) -> None:
    alpha = image.getchannel("A")
    pixels = list(alpha.getdata())
    transparent_ratio = sum(1 for value in pixels if value == 0) / len(pixels)
    print(f"  {label}: {image.width}×{image.height}  透明 {transparent_ratio:.1%}")


def prepare_cards(data: dict) -> int:
    done = 0
    for series in data.get("series", []):
        for card in series.get("cards", []):
            if not card.get("prepare"):
                # 已经处理过的卡（比如休息卡）跳过：它的库内文件本身就是成品，
                # 再泛洪裁一次只会裁掉边、把宽高比改坏。
                continue
            drawables = card.get("drawables") or {}
            name = drawables.get("full")
            rel = (card.get("art") or {}).get("full")
            if not name or not rel:
                continue
            source = ROOT / rel
            if not source.exists():
                print(f"  [!] 缺图：{source}")
                return 1

            with Image.open(source) as raw:
                cut = trim_to_content(cut_background(raw))
            # 量化成带透明通道的调色板 PNG（FASTOCTREE 是唯一支持 alpha 的量化方式）：
            # 像素画颜色数本来就不高，量化顺带把 JPEG 的压缩噪点并掉，体积远小于 32 位 RGBA。
            quantized = cut.quantize(colors=PALETTE_COLORS, method=Image.FASTOCTREE)

            DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)
            target = DRAWABLE_DIR / f"{name}.png"
            quantized.save(target, optimize=True)
            print(f"  [{card['id']}] → {target.name}  {target.stat().st_size:,}B")
            report(card["id"], cut)
            _write_preview(card["id"], cut)
            done += 1
    return done


def _has_alpha(image: Image.Image) -> bool:
    return image.getchannel("A").getextrema()[0] < 255


def _write_preview(card_id: str, cut: Image.Image) -> None:
    """把去背结果贴在浅色与深色底上各放一份，供人工检查残留白边。"""
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    for label, background in (("light", (250, 249, 245, 255)), ("dark", (48, 48, 46, 255))):
        canvas = Image.new("RGBA", cut.size, background)
        canvas.alpha_composite(cut)
        canvas.convert("RGB").save(PREVIEW_DIR / f"{card_id}-{label}.jpg", quality=90)


def prepare_covers(data: dict) -> int:
    gacha = data.get("gacha") or {}
    covers = gacha.get("covers") or ([gacha["cover"]] if gacha.get("cover") else [])
    if not covers:
        print("  [!] cards.json 里没有 gacha.covers")
        return -1

    DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)
    for cover in covers:
        source = ROOT / cover["source"]
        if not source.exists():
            print(f"  [!] 缺封面：{source}")
            return -1
        with Image.open(source) as raw:
            image = raw.convert("RGB")
            scale = COVER_WIDTH / image.width
            resized = image.resize((COVER_WIDTH, round(image.height * scale)), Image.LANCZOS)
        target = DRAWABLE_DIR / f"{cover['drawable']}.jpg"
        resized.save(target, quality=COVER_QUALITY, optimize=True)
        print(
            f"  [{cover.get('series', '?')}] → {target.name}  "
            f"{resized.width}×{resized.height}  {target.stat().st_size:,}B"
        )
    return len(covers)


def main() -> int:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    print("收集卡：")
    count = prepare_cards(data)
    print("封面：")
    cover_count = prepare_covers(data)
    if cover_count < 0:
        return 1
    print(
        f"\n[ok] 处理了 {count} 张卡 + {cover_count} 张封面 → "
        f"{DRAWABLE_DIR.relative_to(PROJECT_ROOT)}"
    )
    print(f"[预览] 去背结果贴在浅/深底上的对照图在 {PREVIEW_DIR.relative_to(PROJECT_ROOT)}/（肉眼看有没有白边）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
