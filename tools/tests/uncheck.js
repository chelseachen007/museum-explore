const { chromium } = require('/Users/chenjie/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1000} });
  const p = await ctx.newPage();
  p.on('dialog', d=>d.accept());
  await p.goto('http://127.0.0.1:8765/195%E4%BB%B6%E6%B0%B8%E7%A6%81%E6%96%87%E7%89%A9%C2%B7%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2.html'); await p.waitForTimeout(2500);
  // 造老数据
  await p.evaluate(()=>{ localStorage.clear();
    localStorage.setItem('museum195_visited_v1','[1,2,3]');
    localStorage.setItem('hz_guobao_visited_v1','[14,15]'); });
  await p.reload(); await p.waitForTimeout(3000);
  console.log('迁移后:', await p.evaluate(()=>localStorage.getItem('guobao_visited_v1')),
              '| 旧键已清:', await p.evaluate(()=>[localStorage.getItem('museum195_visited_v1'),localStorage.getItem('hz_guobao_visited_v1')]));

  // 取消卡片 1 的打卡
  const c1 = p.locator('article.card[data-id="1"]').first();
  await c1.locator('.check').click(); await p.waitForTimeout(1200);
  console.log('取消后:', await p.evaluate(()=>localStorage.getItem('guobao_visited_v1')));

  // 刷新，看会不会被迁移回来
  await p.reload(); await p.waitForTimeout(3000);
  console.log('刷新后:', await p.evaluate(()=>localStorage.getItem('guobao_visited_v1')),
              '| 卡片1已打卡:', await p.locator('article.card[data-id="1"]').evaluate(e=>e.classList.contains('done')));
  console.log('\n=== 双页照片库共享 + 打卡同步 ===');
  // 杭州页拍一张
  await p.goto('http://127.0.0.1:8765/%E6%9D%AD%E5%B7%9E%E5%9B%BD%E5%AE%9D%E5%9C%B0%E5%9B%BE.html'); await p.waitForTimeout(3000);
  const hz14 = p.locator('.item[data-id="14"]').first();
  const png = await p.screenshot({ clip:{x:0,y:0,width:400,height:260} });
  const [ch] = await Promise.all([ p.waitForEvent('filechooser'), hz14.locator('.snap').click() ]);
  await ch.setFiles([{ name:'y.png', mimeType:'image/png', buffer:png }]);
  await p.waitForTimeout(4000);
  console.log('杭州页 id14:', JSON.stringify({
    缩略图: await hz14.locator('.pthumb').count(),
    已打卡: await hz14.evaluate(e=>e.classList.contains('done')),
    进度环: await p.locator('#ringTxt').innerText() }));
  await p.goto('http://127.0.0.1:8765/195%E4%BB%B6%E6%B0%B8%E7%A6%81%E6%96%87%E7%89%A9%C2%B7%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2.html'); await p.waitForTimeout(4000);
  const n14 = p.locator('article.card[data-id="14"]').first();
  console.log('全国页 id14:', JSON.stringify({
    缩略图: await n14.locator('.pthumb').count(),
    已打卡: await n14.evaluate(e=>e.classList.contains('done')) }));
  // 杭州页照片墙应只含杭州照片
  await p.goto('http://127.0.0.1:8765/%E6%9D%AD%E5%B7%9E%E5%9B%BD%E5%AE%9D%E5%9C%B0%E5%9B%BE.html'); await p.waitForTimeout(3500);
  await p.locator('#viewseg button, .tab', { hasText:'我的足迹' }).first().click().catch(()=>{});
  await p.waitForTimeout(2000);
  console.log('杭州页照片墙:', JSON.stringify({
    格子: await p.locator('.pcell').count(),
    说明: (await p.locator('#pwmeta').innerText().catch(()=>'(无)')).slice(0,60) }));
  await b.close();
})();
