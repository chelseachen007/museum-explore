const { chromium } = require('/Users/chenjie/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1000} });
  const p = await ctx.newPage();
  p.on('dialog', d=>d.accept());

  // 模拟"老用户"：先用旧键写入数据，看能否迁移过来
  await p.goto('http://127.0.0.1:8765/195%E4%BB%B6%E6%B0%B8%E7%A6%81%E6%96%87%E7%89%A9%C2%B7%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2.html');
  await p.waitForTimeout(2500);
  await p.evaluate(()=>{
    localStorage.clear();
    localStorage.setItem('museum195_visited_v1','[1,2,3]');
    localStorage.setItem('hz_guobao_visited_v1','[14,15]');
  });
  await p.reload(); await p.waitForTimeout(3500);
  console.log('=== 旧键迁移 ===');
  console.log(JSON.stringify({
    新键内容: await p.evaluate(()=>localStorage.getItem('guobao_visited_v1')),
    计数: (await p.locator('#count').innerText()).replace(/\n/g,' '),
    卡片1已打卡: await p.locator('article.card[data-id="1"]').evaluate(e=>e.classList.contains('done')),
    卡片14已打卡: await p.locator('article.card[data-id="14"]').evaluate(e=>e.classList.contains('done')),
    卡片9已打卡: await p.locator('article.card[data-id="9"]').evaluate(e=>e.classList.contains('done'))
  }, null, 1));

  // 杭州页也应该看到
  await p.goto('http://127.0.0.1:8765/%E6%9D%AD%E5%B7%9E%E5%9B%BD%E5%AE%9D%E5%9C%B0%E5%9B%BE.html');
  await p.waitForTimeout(3500);
  console.log('\n=== 杭州页读同一个键 ===');
  console.log(JSON.stringify({
    进度环: await p.locator('#ringTxt').innerText(),
    键内容: await p.evaluate(()=>localStorage.getItem('guobao_visited_v1'))
  }, null, 1));
  await b.close();
})();
