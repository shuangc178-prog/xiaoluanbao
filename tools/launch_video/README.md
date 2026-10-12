# 启动视频生成

四段启动视频（本日计划 / 日记随想 / 学习 / 统计）都由 `launch.html` 逐帧渲染：
每一帧是时间 `t` 的纯函数，`render.mjs` 用本机 Chrome 无头打开页面，逐帧调用
`window.__seek(t)` 截图，PNG 流直接交给 ffmpeg 编码成 H.264 mp4。

- 主角是 Claude 像素小卵包（`clawdInner`，8×6 身体 + 两侧手臂 + 四条腿），
  配色与 `ui/theme/Color.kt` 的 Claude Desktop 主题一致。
- 页面直接引用 `app/src/main/res/drawable-nodpi/` 里的 `ic_theme_light/dark.png`、`rest_card_full.png`。

## 预览

浏览器打开 `launch.html?s=1&play`（`s` 取 1–4），实时循环播放；`&t=1.5` 定格在某一时刻。

## 渲染

依赖：`playwright-core`（只用本机已装的 Chrome，不下载浏览器）与带 libx264 的 ffmpeg
（例如 `pip install imageio-ffmpeg` 自带的那份）。

```bash
npm install --prefix <临时目录> playwright-core
export PLAYWRIGHT_CORE=<临时目录>/node_modules/playwright-core
export FFMPEG=<ffmpeg 可执行文件>
node tools/launch_video/render.mjs --out previews/launch_videos_clawd
node tools/launch_video/render.mjs --out <目录> --scenes 2 --stills 0.5,1.5,2.9   # 只导出定格图
```

输出 `launch_video_{1..4}.mp4`：1080×2400、30 fps、3 秒（91 帧）。
