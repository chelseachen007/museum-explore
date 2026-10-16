const { chromium } = require('/Users/chenjie/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1000} });
  const out = {};
  for (const [name, url] of [
    ['全国195页', encodeURIComponent('195件永禁文物·博物馆探索.html')],
    ['杭州国宝地图', encodeURIComponent('杭州国宝地图.html')]
  ]) {
    const p = await ctx.newPage();
    const errs=[]; p.on('pageerror',e=>errs.push(e.message.slice(0,100)));
    await p.goto('http://127.0.0.1:8765/'+url);
    await p.waitForTimeout(3500);
    out[name] = {
      卡片: await p.locator('article.card, .item, .mcard').count(),
      拍照按钮: await p.locator('.snap').count(),
      照片条: await p.locator('.pstrip').count(),
      页面异常: errs.length ? errs.join(' | ') : '无'
    };
    await p.close();
  }
  console.log(JSON.stringify(out, null, 1));
  await b.close();
})();
