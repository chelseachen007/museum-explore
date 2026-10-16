const { chromium } = require('/Users/chenjie/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1100}, deviceScaleFactor:2 });
  const p = await ctx.newPage();
  const errs=[]; p.on('pageerror',e=>errs.push(e.message.slice(0,110)));
  p.on('dialog', d => d.accept());
  await p.goto('http://127.0.0.1:8765/%E6%9D%AD%E5%B7%9E%E5%9B%BD%E5%AE%9D%E5%9C%B0%E5%9B%BE.html');
  await p.waitForTimeout(3000);

  const step = {};
  step.snap按钮数 = await p.locator('.snap').count();

  // 用截图当"用户拍的照片"
  const png = await p.screenshot({ clip:{x:0,y:0,width:600,height:400} });
  // 真实路径：点「拍照打卡」→ 唤起文件选择器 → 选图
  const [chooser] = await Promise.all([
    p.waitForEvent('filechooser'),
    p.locator('.snap').first().click()
  ]);
  await chooser.setFiles([
    { name:'relic-a.png', mimeType:'image/png', buffer:png },
    { name:'relic-b.png', mimeType:'image/png', buffer:png }
  ]);
  await p.waitForTimeout(4000);

  step.第一条照片条 = await p.locator('.item').first().locator('.pthumb').count();
  step.第一条加号 = await p.locator('.item').first().locator('.padd').count();
  step.按钮文案 = await p.locator('.snap').first().innerText();
  step.已打卡类 = await p.locator('.item').first().evaluate(e=>e.classList.contains('done'));
  step.进度 = await p.locator('#ringTxt').innerText();
  step.照片墙格子 = await p.locator('.pcell').count();
  step.照片墙说明 = await p.locator('#pwmeta').innerText();
  console.log('--- 上传后 ---'); console.log(JSON.stringify(step,null,1));

  // 刷新验证持久化
  await p.reload(); await p.waitForTimeout(3000);
  console.log('--- 刷新后 ---');
  console.log(JSON.stringify({
    照片条: await p.locator('.item').first().locator('.pthumb').count(),
    进度: await p.locator('#ringTxt').innerText(),
    照片墙: await p.locator('.pcell').count()
  }));

  // 打开灯箱
  await p.locator('.item').first().locator('.pthumb').first().click();
  await p.waitForTimeout(1200);
  console.log('灯箱:', JSON.stringify(await p.evaluate(()=>{
    const i=document.getElementById('lbimg');
    return { open:document.getElementById('lb').classList.contains('open'),
             srcOk:!!i.getAttribute('src'), loaded:i.naturalWidth>0,
             cap:document.getElementById('lbcap').textContent };
  })));
  await p.screenshot({ path:'_shots/p_lb.png' });
  await p.keyboard.press('Escape'); await p.waitForTimeout(500);

  // 删除一张
  await p.locator('.item').first().locator('.pthumb .px').first().click({force:true});
  await p.waitForTimeout(2500);
  console.log('删除后剩余:', await p.locator('.item').first().locator('.pthumb').count(),
              '| 按钮:', await p.locator('.snap').first().innerText());

  await p.locator('#pwall').scrollIntoViewIfNeeded();
  await p.waitForTimeout(800);
  await p.screenshot({ path:'_shots/p_wall.png' });
  console.log('页面异常:', errs.length?errs.join(' | '):'无');
  await b.close();
})();
