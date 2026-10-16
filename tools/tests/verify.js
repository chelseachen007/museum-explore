// 搬迁后验证：首页 + 全国页 + 杭州页在真实浏览器里是否正常
// 用法：node verify.js    （需先双击「启动博物馆探索.command」起服务）
const { chromium } = require('/Users/chenjie/node_modules/playwright');
const BASE = 'http://127.0.0.1:8765';

(async () => {
  const b = await chromium.launch({ proxy: { server: 'http://127.0.0.1:7890' } });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', e => errs.push(e.message.slice(0, 120)));

  // ---------- 1. 首页 ----------
  await p.goto(BASE + '/');
  await p.waitForTimeout(1500);
  console.log('=== 首页 ===');
  console.log(JSON.stringify({
    标题: await p.locator('h1').innerText().catch(() => '(无 h1)'),
    入口数: await p.locator('a.entry').count(),
    入口名: await p.locator('a.entry h2').allInnerTexts()
  }, null, 1));

  // ---------- 2. 全国页 ----------
  await p.locator('a.entry').first().click();
  await p.waitForTimeout(4000);
  await p.evaluate(() => { document.querySelectorAll('img[loading="lazy"]').forEach(i => i.loading = 'eager'); });
  for (let i = 0; i < 14; i++) { await p.mouse.wheel(0, 1400); await p.waitForTimeout(280); }
  // 等所有图片 load/error settle —— 不等就断言会误报「解码成功 3」（懒加载还没跑完）
  await p.evaluate(async () => {
    const imgs = [...document.querySelectorAll('article.card .art img')];
    await Promise.all(imgs.map(i => i.complete ? 0 : new Promise(r => {
      i.addEventListener('load', r, { once: true });
      i.addEventListener('error', r, { once: true });
      setTimeout(r, 15000);
    })));
  });
  await p.waitForTimeout(1500);

  const nat = await p.evaluate(() => {
    const imgs = [...document.querySelectorAll('article.card .art img')];
    return {
      卡片数: document.querySelectorAll('article.card').length,
      拍照按钮: document.querySelectorAll('.snap').length,
      实物照片: imgs.length,
      解码成功: imgs.filter(i => i.complete && i.naturalWidth > 0).length,
      计数文案: (document.getElementById('count') || {}).textContent || '(无)'
    };
  });
  console.log('\n=== 全国 195 页 ===');
  console.log(JSON.stringify(nat, null, 1));

  // ---------- 3. 杭州页 ----------
  await p.goto(BASE + '/杭州国宝地图.html');
  await p.waitForTimeout(6000);
  const hz = await p.evaluate(() => {
    const tip = document.getElementById('mapTip');
    const box = document.getElementById('map');
    return {
      文物条目: document.querySelectorAll('.item').length,
      拍照按钮: document.querySelectorAll('.snap').length,
      腾讯地图脚本: typeof TMap !== 'undefined',
      地图区文本: (box ? box.innerText.replace(/\s+/g, ' ').trim().slice(0, 50) : ''),
      提示条可见: tip ? getComputedStyle(tip).display !== 'none' : false,
      鉴权失败字样: document.body.innerText.includes('鉴权失败')
    };
  });
  console.log('\n=== 杭州页 ===');
  console.log(JSON.stringify(hz, null, 1));

  console.log('\n页面异常:', errs.length ? errs.join(' | ') : '无');
  await b.close();
})();
