const { chromium } = require('/Users/chenjie/node_modules/playwright');
const FILE = 'file:///Users/chenjie/WorkBuddy%20AI/%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2/%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2/195%E4%BB%B6%E6%B0%B8%E7%A6%81%E6%96%87%E7%89%A9%C2%B7%E5%8D%9A%E7%89%A9%E9%A6%86%E6%8E%A2%E7%B4%A2.html';

(async () => {
  const b = await chromium.launch({ proxy:{server:'http://127.0.0.1:7890'} });
  const ctx = await b.newContext({ viewport:{width:1280,height:1000} });
  const p = await ctx.newPage();
  const errs=[]; p.on('pageerror',e=>errs.push(e.message.slice(0,140)));
  p.on('dialog', d=>d.accept());

  await p.goto(FILE);
  await p.waitForTimeout(4000);

  console.log('=== file:// 环境探测 ===');
  console.log(JSON.stringify(await p.evaluate(async ()=>{
    const r = { 协议: location.protocol };
    // localStorage
    try { localStorage.setItem('__t','1'); localStorage.removeItem('__t'); r.localStorage='可用'; }
    catch(e){ r.localStorage='被禁: '+e.name; }
    // IndexedDB
    try {
      if (typeof indexedDB==='undefined') { r.indexedDB='不存在'; }
      else {
        await new Promise((res,rej)=>{
          const q=indexedDB.open('__probe',1);
          q.onsuccess=()=>{ q.result.close(); indexedDB.deleteDatabase('__probe'); res(); };
          q.onerror=()=>rej(q.error);
          q.onblocked=()=>rej(new Error('blocked'));
          setTimeout(()=>rej(new Error('超时')),5000);
        });
        r.indexedDB='可用';
      }
    } catch(e){ r.indexedDB='被禁: '+(e.name||e.message); }
    // 页面自己的降级提示
    const meta=document.getElementById('pwmeta');
    r.照片墙提示 = meta ? meta.textContent.slice(0,60) : '(无)';
    r.拍照按钮禁用 = document.querySelector('.snap')?.disabled ?? '(无按钮)';
    // 卡片渲染
    r.卡片数 = document.querySelectorAll('article.card').length;
    r.进度文案 = document.getElementById('count')?.textContent?.trim().slice(0,40);
    return r;
  }), null, 1));

  // 试着真的走一次打卡：能不能点开文件选择器
  console.log('\n=== 尝试打卡 ===');
  try {
    const [chooser] = await Promise.all([
      p.waitForEvent('filechooser', { timeout: 6000 }),
      p.locator('article.card').first().locator('.snap').click()
    ]);
    const png = await p.screenshot({ clip:{x:0,y:0,width:400,height:260} });
    await chooser.setFiles([{ name:'t.png', mimeType:'image/png', buffer:png }]);
    await p.waitForTimeout(4000);
    console.log(JSON.stringify({
      结果: '文件选择器被唤起',
      缩略图: await p.locator('article.card').first().locator('.pthumb').count(),
      按钮文案: (await p.locator('article.card').first().locator('.snap').innerText()).replace(/\n/g,' '),
      卡片已打卡: await p.locator('article.card').first().evaluate(e=>e.classList.contains('done')),
      照片墙提示: (await p.locator('#pwmeta').innerText().catch(()=>'(无)')).slice(0,60)
    }, null, 1));
  } catch(e) {
    console.log('打卡失败:', e.message.split('\n')[0]);
    console.log('按钮状态:', await p.locator('article.card').first().locator('.snap').evaluate(
      b=>({ disabled:b.disabled, title:b.title })));
  }

  // 复选框打卡（不依赖 IndexedDB 的功能）是否正常
  console.log('\n=== 纯勾选打卡 ===');
  const before = await p.locator('#count').innerText();
  await p.locator('article.card').first().locator('.check').click();
  await p.waitForTimeout(1200);
  const after = await p.locator('#count').innerText();
  console.log(JSON.stringify({ 点击前: before.replace(/\n/g,' '), 点击后: after.replace(/\n/g,' ') }));

  // 刷新看是否持久化
  await p.reload(); await p.waitForTimeout(3500);
  console.log('\n=== 刷新后（持久化） ===');
  console.log(JSON.stringify({
    卡片已打卡: await p.locator('article.card').first().evaluate(e=>e.classList.contains('done')),
    计数: (await p.locator('#count').innerText()).replace(/\n/g,' ')
  }, null, 1));

  console.log('\n页面异常:', errs.length?errs.join(' | '):'无');
  await b.close();
})();
