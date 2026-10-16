// 全国 195 页：无 IndexedDB + 无 TMap 的降级检查
const fs = require('fs'); const els = {};
function mk(id){
  const o = {id, innerHTML:'', textContent:'', style:{}, dataset:{}, children:[],
    appendChild(c){this.children.push(c)}, setAttribute(k,v){this[k]=v},
    classList:{toggle(){},add(){},remove(){},contains:()=>false},
    addEventListener(){}, removeEventListener(){}, disabled:false,
    closest(){return o}, querySelector(){return mk('q')}, querySelectorAll(){return []}};
  return o;
}
global.localStorage = {getItem:()=>null, setItem:()=>{}};
global.document = {
  getElementById:id=>els[id]||(els[id]=mk(id)),
  querySelectorAll:()=>[],
  createElement:t=>mk('el:'+t),
  addEventListener:()=>{},
  head:mk('head'), body:mk('body')
};
global.URL.createObjectURL = global.URL.createObjectURL || (()=>'blob:stub');
global.URL.revokeObjectURL = global.URL.revokeObjectURL || (()=>{});
global.IDBKeyRange = {only:v=>({v})};
global.window = {}; global.navigator = {};
global.TMap = undefined;           // 无腾讯地图
// 不定义 indexedDB —— 模拟最差环境

// 直接从页面里抽出最后一个内联 <script> 来跑，不用手工维护 check.js
const path = require('path');
const HTML = path.join(__dirname, '..', '..', '195件永禁文物·博物馆探索.html');
const _src = fs.readFileSync(HTML, 'utf8');
const _blocks = [..._src.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]);
if (!_blocks.length) { console.error('页面里没找到内联脚本'); process.exit(1); }
eval(_blocks[_blocks.length - 1]);

setTimeout(()=>{
  const view = els['view'] ? els['view'].innerHTML : '';
  const stat = els['statcards'] ? els['statcards'].innerHTML : '';
  console.log('=== 全国 195 页 · 降级环境 ===');
  console.log('文物卡片 .card :', (view.match(/class="card/g)||[]).length);
  console.log('拍照按钮 .snap :', (view.match(/class="snap"/g)||[]).length);
  console.log('照片条 .pstrip :', (view.match(/class="pstrip"/g)||[]).length);
  console.log('「为什么入选」:', (view.match(/为什么入选/g)||[]).length);
  console.log('官网链接 官网 ↗ :', (view.match(/官网 ↗/g)||[]).length);
  console.log('微信按钮 wxbtn :', (view.match(/wxbtn/g)||[]).length);
  console.log('统计卡 statcards 长度:', stat.length);
  console.log('计数 #count :', els['count'] ? els['count'].textContent : '(无)');
  console.log('照片墙说明 #pwmeta :', els['pwmeta'] ? String(els['pwmeta'].textContent).slice(0,60) : '(无)');
  console.log('页面未抛异常 ✔');
}, 2500);
