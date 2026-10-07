// node training/film/render.mjs <video> [fps=30] -> out/<video>_video.mp4: every frame screenshotted and piped straight
// into ffmpeg (no image files on disk). The sound is mixed apart (training/audio/mix.py).
import { chromium } from 'playwright';
import { spawn } from 'child_process';
import { open } from './pages.mjs';
const [v, fpsArg = '30'] = process.argv.slice(2);
const fps = Number(fpsArg), outFile = `out/${v}_video.mp4`;
const browser = await chromium.launch();
const { page, errors } = await open(browser, v);
const total = await page.evaluate(() => window.TIMELINE.total);
const ff = spawn('/opt/homebrew/bin/ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'png', '-i', '-',
  '-c:v', 'libx264', '-preset', 'medium', '-crf', '16', '-pix_fmt', 'yuv420p', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709', outFile], { stdio: ['pipe', 'inherit', 'inherit'] });
const n = Math.round(total * fps), t0 = Date.now();
for (let i = 0; i < n; i++) {
  await page.evaluate((t) => window.renderAt(t), i / fps);
  const png = await page.screenshot({ type: 'png' });
  if (!ff.stdin.write(png)) await new Promise((r) => ff.stdin.once('drain', r));
  if (i % 300 === 0) console.log(`frame ${i}/${n} ${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
ff.stdin.end();
await new Promise((r) => ff.on('close', r));
await browser.close();
console.log('done', outFile, total.toFixed(2) + 's', errors.length ? 'ERRORS ' + errors.join(' | ') : '');
