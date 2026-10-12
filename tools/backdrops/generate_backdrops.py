"""生成四个 Tab 的像素背景插画（明 / 暗各一套）。

每个场景两张透明 PNG：顶部一条（天空 / 纸面）、底部一条（地面 / 桌面 / 书架 / 图表）。
画布宽 240 个「像素格」，App 里按整数倍最近邻放大铺满屏宽（1080 / 1200 / 1440 都能整除或
只裁两侧几格），所以像素边永远是锐利的。中间留给内容，插画只在上下两端。

配色全部取自 ui/theme/Color.kt 的 Claude Desktop 明暗两套，亮度只比底色深一点点，
压在文字下面也不抢可读性；唯一的饱和色是主角小卵包本身。

用法：python tools/backdrops/generate_backdrops.py [输出目录]
默认输出到 app/src/main/res/drawable-nodpi/。
"""

from __future__ import annotations

import math
import random
import sys
from pathlib import Path

from PIL import Image

W = 240
TOP_H = 120
BOTTOM_H = 100

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

BAYER4 = [
    [0, 8, 2, 10],
    [12, 4, 14, 6],
    [3, 11, 1, 9],
    [15, 7, 13, 5],
]


def hex_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


class Art:
    """一张像素画布：所有坐标都是像素格，越界自动忽略。"""

    def __init__(self, height: int) -> None:
        self.h = height
        self.im = Image.new("RGBA", (W, height), (0, 0, 0, 0))
        self.px = self.im.load()

    def dot(self, x: int, y: int, color: str, alpha: float = 1.0) -> None:
        if not (0 <= x < W and 0 <= y < self.h) or alpha <= 0:
            return
        r, g, b = hex_rgb(color)
        a = max(0, min(255, round(alpha * 255)))
        if a >= 255:
            self.px[x, y] = (r, g, b, 255)
            return
        # 与已有像素按 alpha 叠加，保证半透明的光晕、纸纹叠在形状上时颜色正确。
        br, bg, bb, ba = self.px[x, y]
        fa = a / 255
        out_a = fa + ba / 255 * (1 - fa)
        if out_a <= 0:
            return
        mix = lambda s, d: round((s * fa + d * (ba / 255) * (1 - fa)) / out_a)
        self.px[x, y] = (mix(r, br), mix(g, bg), mix(b, bb), round(out_a * 255))

    def rect(self, x: int, y: int, w: int, h: int, color: str, alpha: float = 1.0) -> None:
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.dot(xx, yy, color, alpha)

    def bitmap(self, rows: list[str], x: int, y: int, palette: dict[str, str], scale: int = 1) -> None:
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in palette:
                    self.rect(x + i * scale, y + j * scale, scale, scale, palette[ch])

    def disc(self, cx: float, cy: float, r: float, color: str, alpha: float = 1.0) -> None:
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.dot(x, y, color, alpha)

    def crescent(self, cx: float, cy: float, r: float, color: str, cut: tuple[float, float, float]) -> None:
        dx, dy, cr = cut
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                px, py = x + 0.5, y + 0.5
                inside = (px - cx) ** 2 + (py - cy) ** 2 <= r * r
                cutout = (px - cx - dx) ** 2 + (py - cy - dy) ** 2 <= cr * cr
                if inside and not cutout:
                    self.dot(x, y, color)

    def glow(self, cx: float, cy: float, r: float, color: str, strength: float) -> None:
        """像素抖动的光晕：四档透明度 + Bayer 抖动，边缘是颗粒状而不是平滑渐变。"""
        for y in range(max(0, int(cy - r)), min(self.h, int(cy + r) + 1)):
            for x in range(max(0, int(cx - r)), min(W, int(cx + r) + 1)):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
                if d >= 1:
                    continue
                level = (1 - d) ** 1.6 * 4
                base = int(level)
                frac = level - base
                if frac * 16 > BAYER4[y % 4][x % 4]:
                    base += 1
                if base > 0:
                    self.dot(x, y, color, strength * min(base, 4) / 4)

    def ridge(self, base: float, amps: list[tuple[float, float, float]], color: str, top_color: str | None = None) -> list[int]:
        """一条山脊 / 地面剪影：每列从轮廓线一直填到画布底。返回每列的轮廓高度。"""
        tops = []
        for x in range(W):
            y = base
            for amp, freq, phase in amps:
                y -= amp * math.sin(x / W * math.tau * freq + phase)
            top = int(round(y))
            tops.append(top)
            for yy in range(top, self.h):
                self.dot(x, yy, color)
            if top_color:
                self.dot(x, top, top_color)
        return tops

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.im.save(path, optimize=True)


# ---------------------------------------------------------------- 调色板

LIGHT = {
    "subtle": "#F0EEE6", "pressed": "#E8E6DC", "line": "#DEDBD0", "deep": "#D3CFC2",
    "mark": "#F4DDB5", "peach": "#F2D9CC", "accent": "#C6613F",
    "green": "#DCE4DE", "green2": "#CCD8D0", "blue": "#D9E3E9", "violet": "#E3DCE7",
    "amber": "#E6DDCD", "paper": "#FFFFFF", "ink": "#8C8A82", "star": "#E8C98E",
}
DARK = {
    "subtle": "#363634", "pressed": "#3D3D3A", "line": "#454543", "deep": "#4E4E4B",
    "mark": "#6E5F42", "peach": "#59473F", "accent": "#D97757",
    "green": "#3E453F", "green2": "#4C534C", "blue": "#3F474B", "violet": "#4A444C",
    "amber": "#4D483E", "paper": "#4A4A47", "ink": "#9C9A92", "star": "#DFBB72",
}

CLAWD = "#D67857"
CLAWD_SHADE = "#B9603F"
EYE = "#1F1E1C"

CLAWD_ROWS = [
    "..BBBBBBBB..",
    "..BEBBBBEB..",
    "BBBBBBBBBBBB",
    "BBBBBBBBBBBB",
    "..BBBBBBBB..",
    "..SSSSSSSS..",
    "..B.B..B.B..",
    "..B.B..B.B..",
]
CLAWD_SITTING_ROWS = [
    "..BBBBBBBB..",
    "..BEBBBBEB..",
    "BBBBBBBBBBBB",
    "BBBBBBBBBBBB",
    "..BBBBBBBB..",
    "..SSSSSSSS..",
]
CLOUD = [
    "....####......",
    "..########.##.",
    ".#############",
    "SSSSSSSSSSSSSS",
]
CLOUD_SMALL = [
    "..###...",
    ".######.",
    "SSSSSSSS",
]
PLUS = [".#.", "###", ".#."]
SPARK = ["..#..", "..#..", "#####", "..#..", "..#.."]
GLYPHS = {
    "A": [".##.", "#..#", "####", "#..#", "#..#"],
    "B": ["###.", "#..#", "###.", "#..#", "###."],
    "C": [".###", "#...", "#...", "#...", ".###"],
    "a": ["....", ".##.", "..##", "#..#", ".###"],
    "?": ["###.", "...#", ".##.", "....", ".#.."],
    "!": ["#", "#", "#", ".", "#"],
}


def clawd(art: Art, x: int, feet_y: int, scale: int = 2, sitting: bool = False) -> None:
    """小卵包；(x, feet_y) 是左边缘与脚底。"""
    rows = CLAWD_SITTING_ROWS if sitting else CLAWD_ROWS
    art.bitmap(rows, x, feet_y - len(rows) * scale, {"B": CLAWD, "S": CLAWD_SHADE, "E": EYE}, scale)


def cloud(art: Art, x: int, y: int, fill: str, shade: str, small: bool = False) -> None:
    art.bitmap(CLOUD_SMALL if small else CLOUD, x, y, {"#": fill, "S": shade})


def flag(art: Art, x: int, foot_y: int, pole: str, cloth: str) -> None:
    """一根旗杆加一面朝右的三角小旗。"""
    art.rect(x, foot_y - 18, 1, 18, pole)
    for i in range(7):
        art.rect(x + 1, foot_y - 18 + i, 7 - abs(i - 3) * 2, 1, cloth)


def scatter(art: Art, seed: int, count: int, box: tuple[int, int, int, int], colors: list[str],
            alpha: tuple[float, float] = (0.5, 1.0), shapes: tuple[str, ...] = ("dot", "plus")) -> None:
    rng = random.Random(seed)
    x0, y0, x1, y1 = box
    for _ in range(count):
        x, y = rng.randint(x0, x1), rng.randint(y0, y1)
        color = rng.choice(colors)
        a = rng.uniform(*alpha)
        shape = rng.choice(shapes)
        if shape == "dot":
            art.dot(x, y, color, a)
        elif shape in ("plus", "spark"):
            rows = PLUS if shape == "plus" else SPARK
            half = len(rows) // 2
            for j, row in enumerate(rows):
                for i, ch in enumerate(row):
                    if ch == "#":
                        art.dot(x - half + i, y - half + j, color, a)
        elif shape == "confetti":
            art.rect(x, y, 2, 2, color, a)


# ---------------------------------------------------------------- 本日计划：晨光与小山坡

def plan_top(p: dict[str, str], dark: bool) -> Art:
    art = Art(TOP_H)
    if dark:
        art.glow(120, -4, 78, p["star"], 0.16)
        art.crescent(120, 2, 15, p["star"], (6, 5, 13))
        scatter(art, 11, 46, (4, 4, 236, 104), [p["star"], "#FAF9F5"], (0.35, 0.95), ("dot", "dot", "plus"))
        cloud(art, 12, 44, p["pressed"], p["subtle"])
        cloud(art, 188, 64, p["pressed"], p["subtle"], small=True)
        cloud(art, 70, 92, p["pressed"], p["subtle"], small=True)
    else:
        art.glow(120, -4, 86, p["mark"], 0.55)
        art.disc(120, -2, 15, p["mark"])
        for angle in range(20, 170, 25):
            rad = math.radians(angle)
            for k in range(20, 25):
                art.dot(round(120 + math.cos(rad) * k), round(-2 + math.sin(rad) * k), p["mark"])
        cloud(art, 12, 42, p["subtle"], p["pressed"])
        cloud(art, 186, 62, p["subtle"], p["pressed"], small=True)
        cloud(art, 70, 90, p["subtle"], p["pressed"], small=True)
        for bx, by in ((160, 30), (170, 26), (166, 36)):
            art.dot(bx, by, p["deep"])
            art.dot(bx + 1, by + 1, p["deep"])
            art.dot(bx + 2, by, p["deep"])
    return art


def plan_bottom(p: dict[str, str], dark: bool) -> Art:
    art = Art(BOTTOM_H)
    art.ridge(54, [(6, 1.3, 0.4), (2, 3.1, 1.2)], p["subtle"])
    art.ridge(68, [(5, 1.0, 2.2), (2, 2.7, 0.3)], p["green"])
    ground = art.ridge(82, [(3, 0.8, 1.0), (1, 3.3, 2.0)], p["green2"], top_color=p["green"])
    # 一串打卡石：从左下往右上延伸到小卵包脚边。
    for i, (sx, sy) in enumerate(((34, 94), (54, 89), (74, 86), (96, 84), (118, 83), (140, 83))):
        art.rect(sx, sy, 8, 3, p["amber"])
        art.rect(sx + 1, sy - 1, 6, 1, p["amber"])
        art.dot(sx + 3, sy, p["mark"])
        art.dot(sx + 4, sy + 1, p["mark"])
        art.dot(sx + 5, sy, p["mark"])
        art.dot(sx + 6, sy - 1, p["mark"])
    # 小树
    art.rect(26, ground[26] - 8, 2, 9, p["line"])
    art.disc(27, ground[26] - 13, 7, p["green2"])
    art.disc(25, ground[26] - 15, 4, p["green"])
    # 小卵包和小旗
    feet = ground[186] + 1
    clawd(art, 174, feet)
    flag(art, 202, feet, p["deep"], p["accent"])
    # 野花 / 萤火
    rng = random.Random(7)
    for _ in range(18):
        x = rng.randint(4, 235)
        y = rng.randint(ground[x] + 2, BOTTOM_H - 2)
        art.dot(x, y, p["star"] if dark else rng.choice([p["peach"], p["mark"], "#FFFFFF"]), 0.9)
    if dark:
        scatter(art, 21, 10, (8, 30, 232, 70), [p["star"]], (0.4, 0.9), ("dot",))
    return art


# ---------------------------------------------------------------- 日记随想：稿纸与台灯

def journal_top(p: dict[str, str], dark: bool) -> Art:
    art = Art(TOP_H)
    line = p["accent"]
    base_alpha = 0.16 if dark else 0.13
    cell, gap = 12, 5
    y = 6
    while y < TOP_H:
        fade = max(0.0, 1 - y / 104) ** 1.2
        if fade <= 0.02:
            break
        for x in range(0, W):
            art.dot(x, y, line, base_alpha * fade)
            art.dot(x, y + cell, line, base_alpha * fade)
        for x in range(6, W, cell):
            for yy in range(y, y + cell + 1):
                art.dot(x, yy, line, base_alpha * fade * 0.8)
        y += cell + gap
    if dark:
        art.crescent(214, 18, 8, p["star"], (4, -3, 7))
        scatter(art, 31, 18, (4, 4, 236, 70), [p["star"]], (0.4, 0.9), ("dot", "plus"))
    else:
        scatter(art, 31, 10, (8, 8, 232, 70), [p["mark"]], (0.6, 1.0), ("plus", "spark"))
    return art


def journal_bottom(p: dict[str, str], dark: bool) -> Art:
    art = Art(BOTTOM_H)
    desk_top = 74
    # 台灯的光（先画，桌面和物件都压在光上）
    art.glow(206, 54, 46, p["star"] if dark else p["mark"], 0.42 if dark else 0.6)
    art.rect(0, desk_top, W, BOTTOM_H - desk_top, p["amber"])
    art.rect(0, desk_top, W, 2, p["deep"])
    for x in range(0, W, 9):
        art.dot(x, desk_top + 6 + (x * 7) % 9, p["line"])
    # 左侧一摞本子
    for i, color in enumerate((p["peach"], p["blue"], p["violet"], p["green"])):
        y = desk_top - 5 * (i + 1)
        x = 16 + (i % 2) * 3
        art.rect(x, y, 38, 5, color)
        art.rect(x, y + 4, 38, 1, p["line"])
        art.rect(x + 3, y + 1, 1, 3, p["line"])
    # 摊开的本子
    art.rect(62, desk_top - 6, 44, 6, p["paper"])
    art.rect(83, desk_top - 6, 1, 6, p["line"])
    for yy in (desk_top - 4, desk_top - 2):
        art.rect(65, yy, 15, 1, p["line"])
        art.rect(87, yy, 15, 1, p["line"])
    # 小卵包站在本子右边，左手的铅笔落在本子上
    clawd(art, 112, desk_top)
    art.rect(108, desk_top - 17, 2, 12, "#F2C46B")
    art.rect(108, desk_top - 19, 2, 2, "#E39A86")
    art.rect(108, desk_top - 5, 2, 1, "#EBCB9F")
    art.dot(108, desk_top - 4, "#3D3D3A")
    # 墨水瓶
    art.rect(150, desk_top - 8, 8, 8, p["blue"] if not dark else p["violet"])
    art.rect(152, desk_top - 11, 4, 3, p["ink"])
    # 台灯
    art.rect(196, desk_top - 3, 18, 3, p["deep"])
    for k in range(18):
        art.rect(204 - k // 3, desk_top - 3 - k, 2, 1, p["deep"])
    art.rect(188, desk_top - 26, 18, 2, p["deep"])
    art.rect(190, desk_top - 24, 14, 4, p["line"] if not dark else p["deep"])
    art.rect(194, desk_top - 20, 6, 1, p["star"])
    return art


# ---------------------------------------------------------------- 学习：书架与漂浮的字母

def study_top(p: dict[str, str], dark: bool) -> Art:
    art = Art(TOP_H)
    colors = [p["blue"], p["violet"], p["amber"], p["green2"]]
    placements = [("A", 22, 28, 2), ("a", 64, 66, 2), ("?", 196, 24, 2), ("C", 214, 74, 2),
                  ("B", 150, 96, 1), ("!", 104, 40, 1), ("a", 176, 52, 1)]
    for i, (g, x, y, s) in enumerate(placements):
        art.bitmap(GLYPHS[g], x, y, {"#": colors[i % len(colors)]}, s)
    # 一本小书在飘
    art.rect(120, 82, 12, 7, p["paper"])
    art.rect(126, 82, 1, 7, p["line"])
    art.rect(119, 89, 14, 1, p["deep"])
    if dark:
        scatter(art, 41, 24, (4, 4, 236, 110), [p["star"]], (0.35, 0.9), ("dot", "plus"))
    else:
        scatter(art, 41, 10, (8, 8, 232, 110), [p["mark"]], (0.6, 1.0), ("plus", "spark"))
    return art


def study_bottom(p: dict[str, str], dark: bool) -> Art:
    art = Art(BOTTOM_H)
    shelf = 84
    art.rect(0, shelf, W, 4, p["deep"])
    art.rect(0, shelf + 4, W, BOTTOM_H - shelf - 4, p["subtle"])
    art.rect(0, shelf + 4, W, 1, p["line"])
    # 一排立着的书
    rng = random.Random(5)
    colors = [p["blue"], p["violet"], p["amber"], p["green2"], p["peach"]]
    x = 76
    while x < 190:
        w = rng.choice((5, 6, 7, 8))
        h = rng.randint(18, 32)
        color = colors[rng.randrange(len(colors))]
        art.rect(x, shelf - h, w, h, color)
        art.rect(x, shelf - h + h // 3, w, 1, p["line"])
        art.rect(x, shelf - h + h // 3 + 3, w, 1, p["line"])
        art.rect(x + w - 1, shelf - h, 1, h, p["line"])
        x += w + (1 if rng.random() < 0.3 else 0)
    # 一本斜靠着的书
    for k in range(26):
        art.rect(x + 1 + k // 4, shelf - 1 - k, 6, 1, p["peach"])
    # 平放的一摞书 + 坐在上面读书的小卵包
    stack_x, stack_top = 22, shelf
    for i, color in enumerate((p["green2"], p["blue"], p["amber"])):
        stack_top -= 6
        art.rect(stack_x + (i % 2) * 2, stack_top, 40, 6, color)
        art.rect(stack_x + (i % 2) * 2, stack_top + 5, 40, 1, p["line"])
    clawd(art, stack_x + 8, stack_top, sitting=True)
    art.rect(stack_x + 10, stack_top - 6, 20, 6, p["paper"])
    art.rect(stack_x + 19, stack_top - 6, 2, 6, p["line"])
    # 盆栽
    art.rect(214, shelf - 9, 14, 9, p["amber"] if not dark else p["peach"])
    art.rect(213, shelf - 10, 16, 2, p["deep"])
    for lx, ly, r in ((218, shelf - 16, 4), (224, shelf - 19, 4), (221, shelf - 24, 3)):
        art.disc(lx, ly, r, p["green2"])
    return art


# ---------------------------------------------------------------- 统计：柱状图小城

def stats_top(p: dict[str, str], dark: bool) -> Art:
    art = Art(TOP_H)
    for gy, a in ((34, 1.0), (64, 0.7), (94, 0.4)):
        for x in range(4, W - 4):
            if (x // 4) % 2 == 0:
                art.dot(x, gy, p["line"], a)
    if dark:
        scatter(art, 51, 34, (4, 4, 236, 112), [p["star"], "#FAF9F5"], (0.35, 0.9), ("dot", "dot", "plus"))
    else:
        scatter(art, 51, 26, (6, 6, 234, 112), [p["peach"], p["green2"], p["mark"], p["violet"]],
                (0.8, 1.0), ("confetti",))
        scatter(art, 52, 6, (10, 10, 230, 100), [p["mark"]], (0.8, 1.0), ("spark",))
    return art


def stats_bottom(p: dict[str, str], dark: bool) -> Art:
    art = Art(BOTTOM_H)
    base = 92
    art.rect(0, base, W, BOTTOM_H - base, p["subtle"])
    art.rect(0, base, W, 2, p["line"])
    heights = [16, 24, 22, 34, 44, 58]
    window = p["star"] if dark else "#FFFFFF"
    for i, h in enumerate(heights):
        x = 12 + i * 26
        top = base - h
        art.rect(x, top, 18, h, p["green"] if i < len(heights) - 1 else p["green2"])
        art.rect(x, top, 18, 2, p["green2"])
        rng = random.Random(i * 13 + 3)
        for wy in range(top + 5, base - 3, 5):
            for wx in (x + 3, x + 8, x + 13):
                if rng.random() < (0.45 if dark else 0.6):
                    art.rect(wx, wy, 2, 2, window, 0.55 if dark else 0.9)
    # 最高那根柱子上站着小卵包和小旗
    top_x = 12 + (len(heights) - 1) * 26
    feet = base - heights[-1]
    clawd(art, top_x - 3, feet)
    flag(art, top_x + 20, feet, p["deep"], p["accent"])
    # 奖杯
    cup = p["mark"] if not dark else p["star"]
    art.rect(180, base - 4, 14, 4, p["deep"])
    art.rect(185, base - 9, 4, 5, cup)
    art.rect(180, base - 20, 14, 11, cup)
    art.rect(177, base - 19, 3, 2, cup)
    art.rect(177, base - 17, 2, 4, cup)
    art.rect(194, base - 19, 3, 2, cup)
    art.rect(195, base - 17, 2, 4, cup)
    art.rect(183, base - 18, 2, 6, "#FFFFFF" if not dark else "#F4DDB5")
    return art


SCENES = {
    "plan": (plan_top, plan_bottom),
    "journal": (journal_top, journal_bottom),
    "study": (study_top, study_bottom),
    "stats": (stats_top, stats_bottom),
}


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    for name, (top, bottom) in SCENES.items():
        for theme, palette in (("light", LIGHT), ("dark", DARK)):
            dark = theme == "dark"
            top(palette, dark).save(out / f"backdrop_{name}_top_{theme}.png")
            bottom(palette, dark).save(out / f"backdrop_{name}_bottom_{theme}.png")
            print(f"backdrop_{name}_*_{theme}.png")


if __name__ == "__main__":
    main()
