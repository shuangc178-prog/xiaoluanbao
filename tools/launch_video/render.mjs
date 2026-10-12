// 逐帧渲染 launch.html：每帧调用 window.__seek(t) 后截图，PNG 流直接喂给 ffmpeg 编码成 mp4。
//
//   node render.mjs --out <dir> [--scenes 1,2,3,4] [--fps 30] [--ffmpeg <exe>]
//   node render.mjs --out <dir> --stills 0.5,1.2,2.9      只导出几张定格图，用来检查画面
//
// 依赖 playwright-core（用本机安装的 Chrome，不下载浏览器）与 ffmpeg（需带 libx264）。
// playwright-core 不在默认解析路径时，用环境变量 PLAYWRIGHT_CORE 指向它的目录。

import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const args = { scenes: '1,2,3,4', fps: '30', ffmpeg: process.env.FFMPEG || 'ffmpeg', crf: '18' };
  for (let i = 0; i < argv.length; i += 2) args[argv[i].replace(/^--/, '')] = argv[i + 1];
  if (!args.out) throw new Error('missing --out <dir>');
  return args;
}

async function loadChromium() {
  const dir = process.env.PLAYWRIGHT_CORE;
  const mod = dir ? await import(pathToFileURL(path.join(dir, 'index.mjs')).href) : await import('playwright-core');
  return mod.chromium ?? mod.default.chromium;
}

async function openScene(browser, scene) {
  const page = await browser.newPage({ viewport: { width: 1080, height: 2400 }, deviceScaleFactor: 1 });
  const url = pathToFileURL(path.join(here, 'launch.html')).href + `?s=${scene}`;
  page.on('pageerror', err => console.error(`[scene ${scene}] ${err.message}`));
  await page.goto(url);
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 30_000 });
  return page;
}

async function frameAt(page, t) {
  await page.evaluate(time => window.__seek(time), t);
  return page.screenshot({ type: 'png', animations: 'disabled', caret: 'hide' });
}

async function renderVideo(page, scene, { fps, out, ffmpeg, crf }) {
  const duration = await page.evaluate(() => window.__duration);
  const frames = Math.round(duration * fps) + 1; // 含 t = duration 的最后一帧
  const target = path.join(out, `launch_video_${scene}.mp4`);
  const proc = spawn(ffmpeg, [
    '-y', '-loglevel', 'error',
    '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'png', '-i', '-',
    // 显式按 BT.709 转换并打标签：不打标签时 Android 解码器对 1080p 的猜法不一，橙色会偏。
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p,setparams=colorspace=bt709:color_primaries=bt709:color_trc=bt709:range=tv',
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-c:v', 'libx264', '-preset', 'slow', '-tune', 'animation', '-crf', String(crf),
    '-movflags', '+faststart', target,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });
  const done = once(proc, 'close');
  for (let i = 0; i < frames; i++) {
    const png = await frameAt(page, Math.min(duration, i / fps));
    if (!proc.stdin.write(png)) await once(proc.stdin, 'drain');
    if (i % 15 === 0) process.stdout.write(`\r[scene ${scene}] frame ${i + 1}/${frames}`);
  }
  proc.stdin.end();
  const [code] = await done;
  if (code !== 0) throw new Error(`ffmpeg exited with ${code}`);
  console.log(`\r[scene ${scene}] ${frames} frames -> ${target}`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const out = path.resolve(args.out);
  mkdirSync(out, { recursive: true });
  const chromium = await loadChromium();
  const browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--allow-file-access-from-files'] });
  try {
    for (const scene of args.scenes.split(',').map(Number)) {
      const page = await openScene(browser, scene);
      if (args.stills) {
        const { writeFile } = await import('node:fs/promises');
        for (const t of args.stills.split(',').map(Number)) {
          await writeFile(path.join(out, `scene${scene}_t${t.toFixed(2)}.png`), await frameAt(page, t));
        }
        console.log(`[scene ${scene}] stills -> ${out}`);
      } else {
        await renderVideo(page, scene, { fps: Number(args.fps), out, ffmpeg: args.ffmpeg, crf: args.crf });
      }
      await page.close();
    }
  } finally {
    await browser.close();
  }
}

main().catch(err => { console.error(err); process.exit(1); });
