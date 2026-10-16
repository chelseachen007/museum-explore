const { chromium } = require('/Users/chenjie/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1100}, deviceScaleFactor:2 });
  const p = await ctx.newPage();
  const errs=[]; p.on('pageerror',e=>errs.push(e.message.slice(0,120)));
  p.on('dialog', d => d.accept());
  await p.goto('http://127.0.0.1:8765/195%E4%BB%B6%E6%B0%B8%E7%A6%81%E6%96%87%E7%89%A9%C2%B7%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2.html');
  await p.waitForTimeout(3500);

  console.log('=== 初始状态 ===');
  console.log(JSON.stringify({
    卡片数: await p.locator('article.card').count(),
    拍照按钮: await p.locator('.snap').count(),
    照片条: await p.locator('.pstrip').count(),
    计数文案: (await p.locator('#count').innerText()).replace(/\n/g,' '),
    进度环: await p.locator('.bignum').innerText().catch(()=>'(无)')
  }, null, 1));

  // 截图当"用户拍的照片"
  const png = await p.screenshot({ clip:{x:0,y:0,width:600,height:400} });

  // 真实路径：点第一张卡的「拍照打卡」→ 唤起文件选择器 → 选 2 张
  const [chooser] = await Promise.all([
    p.waitForEvent('filechooser'),
    p.locator('article.card').first().locator('.snap').click()
  ]);
  await chooser.setFiles([
    { name:'my-relic-1.png', mimeType:'image/png', buffer:png },
    { name:'my-relic-2.png', mimeType:'image/png', buffer:png }
  ]);
  await p.waitForTimeout(4500);

  const first = p.locator('article.card').first();
  console.log('=== 上传 2 张后（第一张卡） ===');
  console.log(JSON.stringify({
    缩略图: await first.locator('.pthumb').count(),
    加号按钮: await first.locator('.padd').count(),
    按钮文案: await first.locator('.snap').innerText(),
    卡片已打卡: await first.evaluate(e=>e.classList.contains('done')),
    计数文案: (await p.locator('#count').innerText()).replace(/\n/g,' ')
  }, null, 1));

  // 切到「我的足迹」看照片墙
  await p.locator('#viewseg button', { hasText:'我的足迹' }).click();
  await p.waitForTimeout(2500);
  console.log('=== 我的足迹 · 照片墙 ===');
  console.log(JSON.stringify({
    照片墙格子: await p.locator('.pcell').count(),
    说明文案: (await p.locator('#pwmeta').innerText()).slice(0,70),
    总进度: await p.locator('.bignum').innerText()
  }, null, 1));
  await p.screenshot({ path:'_shots/nat_pwall.png', fullPage:false });

  // 刷新验证持久化
  await p.reload(); await p.waitForTimeout(3500);
  await p.locator('article.card').first().waitFor();
  console.log('=== 刷新后 ===');
  console.log(JSON.stringify({
    第一张卡缩略图: await p.locator('article.card').first().locator('.pthumb').count(),
    卡片已打卡: await p.locator('article.card').first().evaluate(e=>e.classList.contains('done')),
    计数文案: (await p.locator('#count').innerText()).replace(/\n/g,' ')
  }, null, 1));

  // 打开灯箱
  await p.locator('article.card').first().locator('.pthumb').first().click();
  await p.waitForTimeout(1500);
  console.log('=== 灯箱 ===');
  console.log(JSON.stringify(await p.evaluate(()=>{
    const i=document.getElementById('lbimg');
    return { 打开:document.getElementById('lb').classList.contains('open'),
             src存在:!!i.getAttribute('src'), 已解码:i.naturalWidth>0,
             说明:document.getElementById('lbcap').textContent.slice(0,50) };
  }), null, 1));
  await p.screenshot({ path:'_shots/nat_plb.png' });
  await p.keyboard.press('Escape'); await p.waitForTimeout(600);

  // 删除一张（确认对话框已自动 accept）
  await p.locator('article.card').first().locator('.pthumb .px').first().click({force:true});
  await p.waitForTimeout(2500);
  console.log('=== 删除一张后 ===');
  console.log(JSON.stringify({
    剩余缩略图: await p.locator('article.card').first().locator('.pthumb').count(),
    按钮文案: await p.locator('article.card').first().locator('.snap').innerText()
  }, null, 1));

  console.log('页面异常:', errs.length ? errs.join(' | ') : '无');
  await b.close();
})();
