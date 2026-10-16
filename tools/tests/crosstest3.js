const { chromium } = require('/Users/chenjie/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1000} });
  const p = await ctx.newPage();
  p.on('dialog', d=>d.accept());

  await p.goto('http://127.0.0.1:8765/%E6%9D%AD%E5%B7%9E%E5%9B%BD%E5%AE%9D%E5%9C%B0%E5%9B%BE.html');
  await p.waitForTimeout(3500);

  // 杭州页手动勾选 id=13（不拍照），看全国页能否看到
  const card13 = p.locator('.item[data-id="13"]').first();
  await card13.scrollIntoViewIfNeeded(); await p.waitForTimeout(800);
  console.log('杭州页卡片存在:', await card13.count());
  await card13.locator('.check').click();
  await p.waitForTimeout(1500);
  console.log('\n=== 杭州页手动勾选 id=13（河姆渡出土陶灶，不拍照）===');
  console.log(JSON.stringify({
    杭州visited: await p.evaluate(()=>localStorage.getItem('hz_guobao_visited_v1')),
    全国visited: await p.evaluate(()=>localStorage.getItem('museum195_visited_v1'))
  }, null, 1));

  await p.goto('http://127.0.0.1:8765/195%E4%BB%B6%E6%B0%B8%E7%A6%81%E6%96%87%E7%89%A9%C2%B7%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2.html');
  await p.waitForTimeout(4000);
  const nat13 = p.locator('article.card[data-id="13"]').first();
  console.log('\n=== 全国页看 id=13 ===');
  console.log(JSON.stringify({
    卡片名: await nat13.locator('.serif').innerText(),
    已打卡: await nat13.evaluate(e=>e.classList.contains('done')),
    全国visited: await p.evaluate(()=>localStorage.getItem('museum195_visited_v1'))
  }, null, 1));

  // 反向：全国页勾选后回杭州页看
  const nat12 = p.locator('article.card[data-id="12"]').first();
  await nat12.scrollIntoViewIfNeeded(); await p.waitForTimeout(600);
  await nat12.locator('.check').click();
  await p.waitForTimeout(1200);
  await p.goto('http://127.0.0.1:8765/%E6%9D%AD%E5%B7%9E%E5%9B%BD%E5%AE%9D%E5%9C%B0%E5%9B%BE.html');
  await p.waitForTimeout(3500);
  const hz12 = p.locator('.item[data-id="12"]').first();
  await hz12.scrollIntoViewIfNeeded(); await p.waitForTimeout(800);
  console.log('\n=== 反向：全国页勾选 id=12 → 杭州页看 ===');
  console.log(JSON.stringify({
    杭州页卡片名: await hz12.locator('.serif, .nm, h3').first().innerText().catch(()=>'(取不到)'),
    已打卡: await hz12.evaluate(e=>e.classList.contains('done')),
    杭州visited: await p.evaluate(()=>localStorage.getItem('hz_guobao_visited_v1')),
    全国visited: await p.evaluate(()=>localStorage.getItem('museum195_visited_v1'))
  }, null, 1));

  await b.close();
})();
