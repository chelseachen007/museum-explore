# -*- coding: utf-8 -*-
import json, os, sys
# 全部路径相对脚本自身定位，整个工作区搬到哪里都能跑
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
from common import art_svg, art_color

OUT = os.path.dirname(_HERE)          # 工作区根目录（tools 的上一级）
with open(os.path.join(OUT, '195件永禁文物.json'), encoding='utf-8') as f:
    data = json.load(f)
with open(os.path.join(OUT, 'museums.json'), encoding='utf-8') as f:
    museum_data = json.load(f)
data['museums'] = museum_data['museums']

CAT_MAP = {
    '青铜器': '青铜器', '杂项·青铜器': '青铜器',
    '陶瓷': '陶瓷', '陶器': '陶器', '陶塑': '陶器',
    '玉器': '玉器', '玉器·水晶': '玉器', '玉器·玛瑙': '玉器',
    '书法': '书画', '绘画': '书画', '书画': '书画',
    '漆木器': '漆木器', '杂项·漆木器': '漆木器',
    '丝织': '丝织刺绣', '杂项·丝织': '丝织刺绣', '杂项·刺绣': '丝织刺绣',
    '壁画': '壁画石刻', '杂项·壁画': '壁画石刻', '石刻': '壁画石刻',
    '杂项·石刻': '壁画石刻', '画像砖': '画像砖',
    '金银器': '金银器', '杂项·金银器': '金银器', '杂项·金器': '金银器',
}
for it in data['items']:
    it['catMain'] = CAT_MAP.get(it['cat'], '文献杂项')
    it['art'] = art_svg(it['catMain'], 92)
    it['artColor'] = art_color(it['catMain'])

payload = json.dumps(data, ensure_ascii=False, separators=(',', ':'))

HTML = r'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>195 件永禁文物 · 博物馆探索地图</title>
<style>
:root{
  --bg:#f5f2ec; --panel:#fffdfa; --panel2:#faf7f2;
  --ink:#221f1b; --ink2:#6b645b; --ink3:#9a938a;
  --line:#e7e1d6; --line2:#f0ebe2;
  --red:#9c3b2e; --jade:#2e6b5e; --blue:#33567e; --gold:#a9803a;
  --shadow:0 1px 2px rgba(60,45,30,.05),0 8px 24px rgba(60,45,30,.06);
}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);
  font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  font-size:15px;line-height:1.7;-webkit-font-smoothing:antialiased}
.serif{font-family:"Songti SC","Noto Serif SC","STSong",serif}
a{color:var(--red);text-decoration:none}
.wrap{max-width:1180px;margin:0 auto;padding:0 22px}

.hero{background:radial-gradient(1200px 380px at 12% -30%,#efe6d6 0%,transparent 62%),
  radial-gradient(900px 320px at 92% -10%,#e6ece6 0%,transparent 58%),linear-gradient(180deg,#fffdfa,#f7f3ec);
  border-bottom:1px solid var(--line);padding:52px 0 34px}
.kicker{display:inline-flex;align-items:center;gap:8px;font-size:12.5px;letter-spacing:.14em;color:var(--red);
  background:#fbf0ec;border:1px solid #f0dcd5;border-radius:999px;padding:5px 14px;margin-bottom:20px;font-weight:600}
h1{font-family:"Songti SC",serif;font-size:clamp(34px,5.4vw,54px);line-height:1.12;margin:0 0 6px;letter-spacing:.02em;font-weight:700}
h1 .num{color:var(--red)}
.hero .lede{font-size:16.5px;color:var(--ink2);max-width:760px;margin:14px 0 0}
.batchbar{display:flex;flex-wrap:wrap;gap:10px;margin-top:26px}
.bchip{display:flex;align-items:baseline;gap:9px;background:var(--panel);border:1px solid var(--line);
  border-radius:12px;padding:10px 16px;box-shadow:var(--shadow)}
.bchip b{font-size:19px;font-family:"Songti SC",serif}
.bchip small{color:var(--ink3);font-size:12px}
.bchip .eq{color:var(--ink3);font-size:12px}
.dot{width:9px;height:9px;border-radius:50%;display:inline-block;flex:none}

section{padding:34px 0}
.sec-head{display:flex;align-items:baseline;gap:12px;margin-bottom:16px;flex-wrap:wrap}
.sec-head h2{font-family:"Songti SC",serif;font-size:22px;margin:0;font-weight:700;letter-spacing:.02em}
.sec-head .hint{color:var(--ink3);font-size:13px}

.why{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:24px 26px;box-shadow:var(--shadow)}
.why .law{border-left:3px solid var(--red);background:#fdf7f5;border-radius:0 10px 10px 0;padding:13px 18px;
  color:#5c4a44;font-size:14px;margin:0 0 20px}
.why .law b{color:var(--red)}
.why h3{font-size:15px;margin:22px 0 12px;font-weight:700;display:flex;align-items:center;gap:8px}
.why h3 .tag{font-size:11px;font-weight:600;color:var(--ink3);border:1px solid var(--line);border-radius:5px;padding:1px 7px}
.redline{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:6px}
.redline span{font-size:13px;background:#f6f1e9;border:1px solid var(--line);border-radius:8px;padding:6px 12px;color:#5f584f}
.redline span b{color:var(--red);margin-right:5px}
.logic{display:grid;grid-template-columns:repeat(auto-fit,minmax(215px,1fr));gap:12px;margin-top:6px}
.lcard{background:var(--panel2);border:1px solid var(--line2);border-radius:11px;padding:14px 16px}
.lcard b{display:block;font-size:14px;margin-bottom:5px}
.lcard p{margin:0;font-size:13px;color:var(--ink2);line-height:1.62}

.controls{position:sticky;top:0;z-index:30;background:rgba(245,242,236,.93);backdrop-filter:blur(10px);
  border-bottom:1px solid var(--line);padding:12px 0;margin-bottom:8px}
.ctl-row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
.search{flex:1;min-width:210px;position:relative}
.search input{width:100%;border:1px solid var(--line);background:var(--panel);border-radius:10px;
  padding:9px 13px 9px 35px;font-size:14px;color:var(--ink);outline:none;font-family:inherit}
.search input:focus{border-color:#c9b8a4;box-shadow:0 0 0 3px rgba(156,59,46,.07)}
.search::before{content:"⌕";position:absolute;left:12px;top:50%;transform:translateY(-52%);color:var(--ink3);font-size:17px}
select{border:1px solid var(--line);background:var(--panel);border-radius:10px;padding:9px 11px;font-size:13.5px;
  color:var(--ink);font-family:inherit;outline:none;cursor:pointer;max-width:190px}
.seg{display:flex;background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:3px}
.seg button{border:0;background:transparent;font-family:inherit;font-size:13.5px;color:var(--ink2);
  padding:6px 13px;border-radius:7px;cursor:pointer;transition:.15s}
.seg button.on{background:var(--ink);color:#fff;font-weight:600}
.chips{display:flex;flex-wrap:wrap;gap:7px;margin-top:10px}
.chip{border:1px solid var(--line);background:var(--panel);border-radius:999px;font-size:12.5px;
  padding:4px 13px;cursor:pointer;color:var(--ink2);transition:.15s;user-select:none}
.chip:hover{border-color:#cbbfae}
.chip.on{background:var(--ink);border-color:var(--ink);color:#fff;font-weight:600}
.chip .n{opacity:.55;margin-left:4px;font-size:11.5px}
.count{color:var(--ink3);font-size:13px;margin:14px 0 4px}
.count b{color:var(--red);font-size:15px}

.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:16px;margin-top:14px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:14px;overflow:hidden;
  box-shadow:var(--shadow);display:flex;flex-direction:column;transition:.18s;position:relative}
.card:hover{transform:translateY(-2px);box-shadow:0 2px 4px rgba(60,45,30,.06),0 14px 32px rgba(60,45,30,.10)}
.card.done{background:#f8faf8;border-color:#d9e5dd}
.card .art{height:112px;display:flex;align-items:center;justify-content:center;position:relative;
  background:linear-gradient(180deg,#fbf9f5,#f3efe6);border-bottom:1px solid var(--line2)}
.card .art img{width:100%;height:100%;object-fit:cover}
.card .art model-viewer{width:100%;height:100%;--poster-color:transparent;background:#f5f1e9}
.card .art[data-3d]{cursor:zoom-in}
.arttag{position:absolute;right:10px;bottom:7px;font-size:10.5px;color:var(--ink3);letter-spacing:.05em;
  background:rgba(255,253,250,.8);border-radius:4px;padding:1px 6px;z-index:2}
.arttag.d3{color:#7a5a2e;background:rgba(247,238,222,.92);font-weight:600}
/* 3D 放大查看 */
.viewer{position:fixed;inset:0;display:none;align-items:center;justify-content:center;z-index:50;
  background:rgba(38,32,26,.62);backdrop-filter:blur(3px);padding:24px}
.viewer.open{display:flex}
.vbox{position:relative;background:var(--panel);border-radius:18px;padding:16px 16px 12px;
  width:min(760px,100%);box-shadow:0 24px 60px rgba(0,0,0,.3)}
.vstage{width:100%;height:min(62vh,520px);border-radius:12px;background:#f5f1e9;overflow:hidden}
.vstage model-viewer{width:100%;height:100%;--poster-color:transparent}
.vname{font-family:"Songti SC",serif;font-size:17px;font-weight:700;margin-top:12px;color:var(--ink)}
.vhint{font-size:12px;color:var(--ink3);margin-top:3px}
.vclose{position:absolute;top:10px;right:12px;width:30px;height:30px;border-radius:50%;border:none;
  background:rgba(0,0,0,.06);color:var(--ink2);font-size:19px;line-height:1;cursor:pointer;z-index:2}
.vclose:hover{background:rgba(0,0,0,.12)}
.card .body{padding:16px 18px 16px;display:flex;flex-direction:column;flex:1}
.card-top{display:flex;justify-content:space-between;align-items:flex-start;gap:10px;margin-bottom:9px}
.card h3{font-family:"Songti SC",serif;font-size:17.5px;margin:0;line-height:1.35;font-weight:700}
.badge{flex:none;font-size:11px;font-weight:700;border-radius:6px;padding:3px 8px;color:#fff}
.b1{background:var(--red)} .b2{background:var(--jade)} .b3{background:var(--blue)}
.meta{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:12.5px;color:var(--ink2);margin-bottom:11px}
.meta .mu{color:var(--ink);font-weight:600}
.tags{display:flex;flex-wrap:wrap;gap:5px;margin-bottom:11px}
.tag{font-size:11.5px;background:#f4efe7;border:1px solid #ebe3d6;border-radius:6px;padding:2px 8px;color:#6a6055}
.whyline{font-size:13.5px;line-height:1.68;color:#3d3730;background:#fbf8f3;border-left:3px solid var(--gold);
  border-radius:0 8px 8px 0;padding:9px 13px;margin-bottom:11px}
.whyline b{color:var(--red)}
.story{font-size:13.5px;line-height:1.75;color:var(--ink2);margin:0;
  display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
.card.open .story{-webkit-line-clamp:unset;display:block}
.more{margin-top:0;font-size:12.5px;color:var(--red);cursor:pointer;
  border:0;background:transparent;padding:0;font-family:inherit;font-weight:600}
/* ---- 拍照打卡 ---- */
.ifoot{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-top:10px}
.snap{display:inline-flex;align-items:center;gap:5px;font-size:12px;color:var(--ink2);cursor:pointer;
  border:1px solid var(--line);background:var(--panel2);border-radius:8px;padding:4px 9px;
  font-family:inherit;transition:.15s;white-space:nowrap}
.snap:hover{border-color:#c9b8a4;color:var(--ink)}
.snap.has{border-color:#bcd6c9;color:var(--jade);background:#f2f8f5;font-weight:600}
.snap[disabled]{opacity:.45;cursor:not-allowed}
.pstrip{display:flex;gap:6px;flex-wrap:wrap;margin-top:9px}
.pstrip:empty{display:none}
.pthumb{width:52px;height:52px;border-radius:9px;overflow:hidden;position:relative;cursor:zoom-in;
  border:1px solid var(--line2);background:#f5f1e9;flex:none}
.pthumb img{width:100%;height:100%;object-fit:cover;display:block}
.pthumb .px{position:absolute;right:2px;top:2px;width:16px;height:16px;border-radius:50%;border:0;
  background:rgba(0,0,0,.5);color:#fff;font-size:11px;line-height:1;cursor:pointer;display:none;padding:0}
.pthumb:hover .px{display:block}
.padd{width:52px;height:52px;border-radius:9px;border:1px dashed var(--line);background:transparent;
  color:var(--ink3);font-size:19px;cursor:pointer;flex:none;font-family:inherit}
.padd:hover{border-color:#c9b8a4;color:var(--ink2)}
/* 照片墙 */
.pwallcard{grid-column:1/-1}
.pwmeta{font-size:12.5px;color:var(--ink3);line-height:1.8;margin:0 0 12px}
.pwall{display:grid;grid-template-columns:repeat(auto-fill,minmax(88px,1fr));gap:8px}
.pwall:empty{display:none}
.pcell{position:relative;aspect-ratio:1;border-radius:10px;overflow:hidden;cursor:zoom-in;
  border:1px solid var(--line2);background:#f5f1e9}
.pcell img{width:100%;height:100%;object-fit:cover;display:block}
.pcell .pcn{position:absolute;left:0;right:0;bottom:0;font-size:10px;color:#fff;padding:3px 5px;
  background:linear-gradient(transparent,rgba(0,0,0,.62));white-space:nowrap;overflow:hidden;
  text-overflow:ellipsis}
/* 灯箱 */
.lb{position:fixed;inset:0;display:none;align-items:center;justify-content:center;z-index:60;
  background:rgba(24,20,16,.9);padding:24px}
.lb.open{display:flex}
.lb figure{margin:0;max-width:min(880px,100%);display:flex;flex-direction:column;align-items:center;gap:11px}
.lb img{max-width:100%;max-height:72vh;border-radius:12px;display:block;background:#2b2620}
.lb figcaption{color:#e6e0d6;font-size:13px;text-align:center;line-height:1.7}
.lb button{position:absolute;border:0;background:rgba(255,255,255,.15);color:#fff;cursor:pointer;
  border-radius:999px;font-family:inherit;font-size:13px;padding:7px 14px}
.lb button:hover{background:rgba(255,255,255,.28)}
.lb .lbx{right:20px;top:18px;width:34px;height:34px;padding:0;font-size:17px}
.lb .lbd{left:20px;top:18px;color:#ffb4a8}
.lb .lbprev{left:20px;top:50%;transform:translateY(-50%);width:38px;height:38px;padding:0;font-size:17px}
.lb .lbnext{right:20px;top:50%;transform:translateY(-50%);width:38px;height:38px;padding:0;font-size:17px}
.offi{margin-top:11px;padding-top:11px;border-top:1px dashed var(--line);display:flex;gap:8px;
  align-items:center;flex-wrap:wrap;font-size:12.5px}
.offi a,.offi button{display:inline-flex;align-items:center;gap:5px;border:1px solid var(--line);
  background:var(--panel2);border-radius:8px;padding:5px 11px;font-family:inherit;font-size:12.5px;
  color:var(--ink2);cursor:pointer;transition:.15s}
.offi a:hover,.offi button:hover{border-color:#c9b8a4;color:var(--ink)}
.offi .hintline{color:var(--ink3);font-size:12px}
.credit{margin-top:7px;font-size:11.5px;color:var(--ink3);line-height:1.6}
.credit a{color:var(--ink3);text-decoration:underline;text-decoration-color:var(--line)}
.credit a:hover{color:var(--ink2)}
.credit span{color:#b3aca1}
.check{position:absolute;right:14px;top:14px;width:26px;height:26px;border-radius:8px;border:1.5px solid #fff;
  background:rgba(255,253,250,.85);cursor:pointer;display:flex;align-items:center;justify-content:center;
  font-size:14px;color:transparent;transition:.15s;backdrop-filter:blur(3px)}
.check:hover{border-color:#b9cdc0}
.card.done .check{background:var(--jade);border-color:var(--jade);color:#fff}

.mgroup{background:var(--panel);border:1px solid var(--line);border-radius:16px;margin-bottom:16px;
  overflow:hidden;box-shadow:var(--shadow)}
.mhead{display:flex;align-items:center;gap:14px;padding:16px 20px;cursor:pointer;
  background:linear-gradient(180deg,#fffdfa,#faf6f0);border-bottom:1px solid var(--line2)}
.mhead .mname{font-family:"Songti SC",serif;font-size:18px;font-weight:700;margin:0;flex:1}
.mhead .mcity{font-size:12.5px;color:var(--ink3);background:#f3eee6;border-radius:6px;padding:2px 9px}
.mhead .mcount{font-size:13px;color:var(--ink2);font-weight:600}
.mmeta{display:flex;flex-wrap:wrap;gap:7px;margin-top:8px;color:var(--ink2);font-size:12.5px}
.mmeta span{background:#f8f4ed;border:1px solid var(--line2);border-radius:6px;padding:2px 8px}
.mmeta .pending{color:#8b6a2d;background:#fff8e9;border-color:#f0dfb5}
.prog{height:5px;background:#efe9df;border-radius:3px;overflow:hidden;width:100%;margin-top:9px}
.prog i{display:block;height:100%;background:linear-gradient(90deg,#3f8b74,#2e6b5e);transition:.3s}
.mbody{padding:16px 20px 20px}
.mbody.hide{display:none}
.mlist{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}
.mitem{border:1px solid var(--line2);border-radius:11px;padding:13px 15px;background:var(--panel2);
  position:relative;display:flex;gap:12px;align-items:flex-start}
.mitem .mar{flex:none;opacity:.75}
.mitem .mar img{width:46px;height:46px;object-fit:cover;border-radius:7px;opacity:1;
  border:1px solid var(--line2)}
.mitem h4{font-family:"Songti SC",serif;font-size:15px;margin:0 0 6px;font-weight:700;padding-right:26px}
.mitem .mwhy{font-size:12.5px;color:var(--ink2);line-height:1.6;margin:0}
.mitem .mtags{display:flex;gap:5px;flex-wrap:wrap;margin-top:8px}
.mitem .check{right:12px;top:11px;width:22px;height:22px;font-size:12px;border-color:var(--line);
  background:var(--panel);backdrop-filter:none}
.mitem.done{background:#f6faf7;border-color:#d9e5dd}

/* 足迹 */
.foot{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}
.fcard{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:20px 22px;box-shadow:var(--shadow)}
.fcard h3{font-family:"Songti SC",serif;font-size:16px;margin:0 0 14px;font-weight:700}
.bignum{font-family:"Songti SC",serif;font-size:44px;line-height:1;color:var(--red);font-weight:700}
.bignum small{font-size:16px;color:var(--ink3);margin-left:6px;font-family:inherit}
.frow{display:flex;align-items:center;gap:10px;margin-bottom:9px;font-size:13px}
.frow .fname{flex:none;width:76px;color:var(--ink2)}
.frow .fbar{flex:1;height:8px;background:#efe9df;border-radius:4px;overflow:hidden}
.frow .fbar i{display:block;height:100%;background:linear-gradient(90deg,#c0603f,#9c3b2e);transition:.4s}
.frow .fnum{flex:none;width:52px;text-align:right;color:var(--ink3);font-size:12.5px}
.frow.full .fbar i{background:linear-gradient(90deg,#3f8b74,#2e6b5e)}
.empty{text-align:center;color:var(--ink3);padding:60px 0;font-size:14px}
.sharebtn{display:inline-flex;align-items:center;gap:8px;background:var(--ink);color:#fff;border:0;
  border-radius:10px;padding:11px 20px;font-family:inherit;font-size:14px;cursor:pointer;font-weight:600;margin-top:6px}
.sharebtn:hover{background:#3a352e}
.sharebtn:disabled{background:#c9c2b7;cursor:not-allowed}
footer{padding:44px 0 60px;color:var(--ink3);font-size:12.5px;text-align:center;line-height:1.9}
footer b{color:var(--ink2)}
@media(max-width:640px){
  .hero{padding:38px 0 26px}
  .grid{grid-template-columns:1fr}
  .why{padding:18px}
  select{max-width:100%;flex:1}
}
</style>
</head>
<body>

<header class="hero">
  <div class="wrap">
    <div class="kicker">禁止出国（境）展览文物 · 第一至第三批</div>
    <h1 class="serif">中国的 <span class="num">195</span> 件<br>永禁出境文物</h1>
    <p class="lede">国家文物局在 2002、2012、2013 年分三批发布《禁止出国（境）展览文物目录》，共 195 件（组）。
      它们是一级文物中的「孤品」与「易损品」——中国最好的东西里，最不能再冒风险的那一批。</p>
    <div class="batchbar" id="batchbar"></div>
  </div>
</header>

<div class="wrap">
  <section id="whysec">
    <div class="sec-head"><h2>为什么是这 195 件？</h2><span class="hint">入选不是「最有名」，而是「最不可替代 + 最脆弱」</span></div>
    <div class="why">
      <p class="law" id="lawtext"></p>
      <h3>一、法律依据 <span class="tag">硬门槛</span></h3>
      <p style="margin:0;font-size:13.5px;color:var(--ink2)">目录只从<b>一级文物</b>中遴选，且必须同时满足「孤品」或「易损品」之一。
        换句话说：光是珍贵不够，还得「毁了就再也没有」或「经不起折腾」。</p>
      <h3>二、五类「一律禁出」的红线 <span class="tag">类别硬规定</span></h3>
      <div class="redline">
        <span><b>1</b>历代出土古尸</span><span><b>2</b>宗教场所的主尊造像</span>
        <span><b>3</b>质地为象牙、犀角的文物</span><span><b>4</b>元以前的书画、丝作品</span>
        <span><b>5</b>宋、元有代表性的孤品瓷器</span>
      </div>
      <p style="margin:10px 0 0;font-size:13.5px;color:var(--ink2)">所以第二批整批都是书画——因为「元以前书画」本身就是红线；第三批里大量简牍、帛书、丝织、象牙器，也直接对应第 3、4 条。</p>
      <h3>三、五条底层逻辑 <span class="tag">为什么偏偏是它</span></h3>
      <div class="logic">
        <div class="lcard"><b>① 不可替代 · 孤品</b><p>存世唯一，毁一件就永久少一件。如后母戊鼎、大盂鼎、曾侯乙编钟、青铜神树。</p></div>
        <div class="lcard"><b>② 脆弱易损 · 材质</b><p>书画、丝帛、漆木、竹简、壁画、象牙、玻璃，运输与光照都可能造成不可逆损伤。</p></div>
        <div class="lcard"><b>③ 历史坐标 · 铭文证史</b><p>能坐实某个事件或制度的器物。利簋定武王伐纣之日，何尊留下最早的「中国」二字。</p></div>
        <div class="lcard"><b>④ 文明源头 · 起源物证</b><p>证明中华文明起源与独特性。良渚玉琮王、红山玉龙、太阳神鸟、陶寺蟠龙盘。</p></div>
        <div class="lcard"><b>⑤ 艺术巅峰 · 时代之最</b><p>代表某时代工艺的最高水平。越王勾践剑、千里江山图、汝窑天青釉、商四羊方尊。</p></div>
      </div>
      <p style="margin:18px 0 0;font-size:13px;color:var(--ink3);border-top:1px dashed var(--line);padding-top:14px">
        <b style="color:var(--ink2)">两个常见误解：</b>①「永禁」指永久禁止<b>出境展览</b>，不是不能看，国内照常展出；
        ②页面文物影像<b>优先取 Wikimedia Commons 的公有领域 / CC 授权实物照片</b>（每张下方已标注许可与作者），
        没匹配到的才用按材质类别绘制的<b>示意图</b>——更多实物影像请走各馆官方渠道。</p>
    </div>
  </section>

  <section id="statssec">
    <div class="sec-head"><h2>这份清单的分布</h2><span class="hint">按你的目标，先看「去哪看」最划算</span></div>
    <div class="logic" id="statcards"></div>
  </section>

  <section style="padding-top:8px">
    <div class="sec-head"><h2>探索工具</h2><span class="hint">勾选右上角方框记录「已打卡」，进度会自动保存</span></div>
  </section>
</div>

<div class="controls">
  <div class="wrap">
    <div class="ctl-row">
      <div class="search"><input id="q" type="search" placeholder="搜索文物名 / 博物馆 / 城市 / 关键词，如：青铜、杭州、帛画"></div>
      <select id="selCat"></select>
      <select id="selProv"></select>
      <select id="selMuseum"></select>
      <div class="seg" id="viewseg">
        <button data-v="list" class="on">清单</button>
        <button data-v="museum">按博物馆</button>
        <button data-v="city">按城市</button>
        <button data-v="foot">我的足迹</button>
      </div>
      <button class="chip" id="resetBtn" style="padding:8px 14px">重置筛选</button>
    </div>
    <div class="chips" id="batchchips"></div>
  </div>
</div>

<div class="wrap">
  <div class="count" id="count"></div>
  <div id="view"></div>
</div>

<footer class="wrap">
  <div><b>数据来源</b>：国家文物局三批《禁止出国（境）展览文物目录》官方文件（文物办发〔2002〕5 号 / 文物博函〔2012〕1345 号 / 文物博函〔2013〕1320 号）</div>
  <div>文物影像：优先采用 <b>Wikimedia Commons</b> 的公有领域 / CC 授权实物照片（卡片下方已标注许可与作者）；未匹配到的用按材质类别绘制的<b>示意图，非实物照片</b></div>
  <div>实物影像与展厅信息请以各馆官网 / 官方小程序为准</div>
  <div style="margin-top:10px">为「奔跑的小胖」的博物馆探索主线而做 · 数据可自由用于后续小程序开发</div>
</footer>

<div id="viewer" class="viewer" aria-hidden="true">
  <div class="vbox">
    <button class="vclose" id="vclose" title="关闭（Esc）">×</button>
    <div class="vstage" id="vstage"></div>
    <div class="vname" id="vname"></div>
    <div class="vhint">拖动旋转 · 滚轮缩放 · 双指手势可用</div>
  </div>
</div>

<div id="lb" class="lb" aria-hidden="true">
  <button class="lbx" id="lbx" title="关闭（Esc）">×</button>
  <button class="lbd" id="lbd">删除这张</button>
  <button class="lbprev" id="lbprev" title="上一张">‹</button>
  <button class="lbnext" id="lbnext" title="下一张">›</button>
  <figure>
    <img id="lbimg" alt="">
    <figcaption id="lbcap"></figcaption>
  </figure>
</div>

<script>
const DATA = __PAYLOAD__;
const items = DATA.items;
const museums = Object.fromEntries((DATA.museums||[]).map(m=>[m.name,m]));
const BATCH_CLS = {1:'b1',2:'b2',3:'b3'};
const LSKEY='guobao_visited_v1';
// 两页共用同一个键，这样在杭州页勾的打卡在全国页也认，反之亦然。
// 旧版本两页各存各的（museum195_visited_v1 / hz_guobao_visited_v1），启动时合并过来，别丢数据。
const OLD_LSKEYS=['museum195_visited_v1','hz_guobao_visited_v1'];
let visited = new Set(JSON.parse(localStorage.getItem(LSKEY)||'[]'));
const saveVisited = () => localStorage.setItem(LSKEY, JSON.stringify([...visited]));
(function migrateVisited(){
  let moved=false;
  for(const k of OLD_LSKEYS){
    try{
      const old=JSON.parse(localStorage.getItem(k)||'[]');
      if(Array.isArray(old)) old.forEach(id=>{
        if(typeof id==='number' && !visited.has(id)){ visited.add(id); moved=true; }
      });
      localStorage.removeItem(k);   // 迁移完必须删掉旧键，否则取消打卡后刷新又会被迁移回来
    }catch(e){}
  }
  if(moved) saveVisited();
})();
const state = {q:'', batch:0, cat:'', prov:'', museum:'', view:'list'};

/* ---------- 头部批次条 ---------- */
(function(){
  const bar=document.getElementById('batchbar');
  DATA.meta.batches.forEach((b,i)=>{
    const d=document.createElement('div');d.className='bchip';
    d.innerHTML='<span class="dot" style="background:'+({1:'#9c3b2e',2:'#2e6b5e',3:'#33567e'})[b.batch]+'"></span>'+
      '<span><b>'+b.count+'</b> <small>件 · '+b.name+' '+b.year+'</small></span>';
    bar.appendChild(d);
    if(i<2){const e=document.createElement('div');e.className='bchip';e.innerHTML='<span class="eq">+</span>';bar.appendChild(e);}
  });
  const t=document.createElement('div');t.className='bchip';
  t.innerHTML='<span class="eq">=</span><span><b style="color:#9c3b2e">195</b> <small>件（组）</small></span>';
  bar.appendChild(t);
})();
document.getElementById('lawtext').innerHTML='<b>法律依据：</b>'+DATA.meta.law;

/* ---------- 统计卡 ---------- */
(function(){
  const mus=new Set(items.map(i=>i.museum)), city=new Set(items.map(i=>i.city));
  const cards=[
    ['195','件（组）文物','三批合计，全部为一级文物'],
    [mus.size+'','家收藏单位','含博物馆、考古所、图书馆、高校'],
    [city.size+'','座城市','从北京到敦煌、乌鲁木齐'],
    ['68','件在北京','故宫 39 + 国博 23，占三分之一强'],
    ['60%+','集中在前 5 城','北京、西安、长沙、武汉、上海'],
    ['39','件在故宫博物院','全国第一，书画类大户']
  ];
  document.getElementById('statcards') && (document.getElementById('statcards').innerHTML = cards.map(c=>
    '<div class="lcard"><b style="font-family:\'Songti SC\',serif;font-size:26px;color:#9c3b2e;line-height:1.2">'+c[0]+
    '</b><b style="font-size:13px;color:#6b645b;font-weight:600;margin:2px 0 4px">'+c[1]+
    '</b><p>'+c[2]+'</p></div>').join(''));
})();

/* ---------- 筛选控件 ---------- */
function fillSel(el, arr, allLabel){
  el.innerHTML='<option value="">'+allLabel+'</option>'+arr.map(v=>'<option>'+v+'</option>').join('');
}
const cats=[...new Set(items.map(i=>i.catMain))].sort((a,b)=>
  items.filter(i=>i.catMain===b).length-items.filter(i=>i.catMain===a).length);
fillSel(document.getElementById('selCat'), cats, '全部类别');
const provs=[...new Set(items.map(i=>i.province))].sort((a,b)=>
  items.filter(i=>i.province===b).length-items.filter(i=>i.province===a).length);
fillSel(document.getElementById('selProv'), provs, '全部省份');
const musList=[...new Set(items.map(i=>i.museum))].sort((a,b)=>
  items.filter(i=>i.museum===b).length-items.filter(i=>i.museum===a).length);
fillSel(document.getElementById('selMuseum'), musList, '全部博物馆');

(function(){
  const box=document.getElementById('batchchips');
  [[0,'全部批次',195],[1,'第一批 2002',64],[2,'第二批 2012',37],[3,'第三批 2013',94]].forEach(([v,label,n])=>{
    const c=document.createElement('div');c.className='chip'+(v===0?' on':'');
    c.innerHTML=label+'<span class="n">'+n+'</span>';
    c.onclick=()=>{state.batch=v;[...box.children].forEach(x=>x.classList.remove('on'));c.classList.add('on');render();};
    box.appendChild(c);
  });
})();

document.getElementById('q').oninput=e=>{state.q=e.target.value.trim();render();};
document.getElementById('selCat').onchange=e=>{state.cat=e.target.value;render();};
document.getElementById('selProv').onchange=e=>{state.prov=e.target.value;render();};
document.getElementById('selMuseum').onchange=e=>{state.museum=e.target.value;render();};
document.querySelectorAll('#viewseg button').forEach(b=>{
  b.onclick=()=>{state.view=b.dataset.v;
    document.querySelectorAll('#viewseg button').forEach(x=>x.classList.remove('on'));b.classList.add('on');render();};
});
document.getElementById('resetBtn').onclick=()=>{
  state.q='';state.batch=0;state.cat='';state.prov='';state.museum='';
  document.getElementById('q').value='';document.getElementById('selCat').value='';
  document.getElementById('selProv').value='';document.getElementById('selMuseum').value='';
  document.querySelectorAll('#batchchips .chip').forEach((x,i)=>x.classList.toggle('on',i===0));
  render();
};

/* ---------- 过滤 ---------- */
function filtered(){
  const q=state.q.toLowerCase();
  return items.filter(it=>{
    if(state.batch && it.batch!==state.batch) return false;
    if(state.cat && it.catMain!==state.cat) return false;
    if(state.prov && it.province!==state.prov) return false;
    if(state.museum && it.museum!==state.museum) return false;
    if(q){
      const hay=(it.name+' '+it.museum+' '+it.city+' '+it.province+' '+it.era+' '+it.cat+' '+
        it.catMain+' '+it.tags.join(' ')+' '+it.reason+' '+it.story).toLowerCase();
      if(!hay.includes(q)) return false;
    }
    return true;
  });
}

function esc(s){return String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));}

/* ---------- 官方入口 ---------- */
function creditHTML(it){
  const c=it.imageCredit;
  if(!it.image||!c) return '';
  const bits=[];
  if(c.license) bits.push(esc(c.license));
  if(c.author){
    // Commons 的 Artist 字段有时是空的，回退到 Credit / 上传者。
    // CC BY-SA 要求署名，但得让人知道这个名字代表什么身份。
    const tag={attribution:'署名',credit:'来源',uploader:'上传者'}[c.authorSrc]||'';
    bits.push(esc(c.author)+(tag?'（'+tag+'）':''));
  }
  const txt=bits.join(' · ')||'Wikimedia Commons';
  return '<div class="credit">📷 '+(c.page
    ? '<a href="'+esc(c.page)+'" target="_blank" rel="noopener">'+txt+'</a>'
    : txt)+' <span>Wikimedia Commons</span></div>';
}

function officialHTML(it){
  const w='微信搜一搜「'+esc(it.museumWechat||it.museum)+'」';
  const link = it.museumUrl
    ? '<a href="'+esc(it.museumUrl)+'" target="_blank" rel="noopener">官网 ↗</a>'
    : '';
  return '<div class="offi">'+link+
    '<button class="wxbtn" data-m="'+esc(it.museumWechat||it.museum)+'">复制馆名 · 去微信找官方</button>'+
    '<span class="hintline">实物图片见官方渠道</span></div>';
}

/* ---------- 卡片 ---------- */
function cardHTML(it){
  const done=visited.has(it.id);
  const cat=' data-cat="'+esc(it.catMain)+'"';
  // 影像三级回退：model（3D）→ image（实物照片）→ 类别示意图
  let media;
  if(it.model && !degraded.has(it.id)){
    media='<div class="art" data-3d="1"'+cat+'><model-viewer src="'+esc(it.model)+'" alt="'+esc(it.name)+'" '+
      'camera-controls auto-rotate rotation-per-second="22deg" interaction-prompt="none" '+
      'loading="lazy" reveal="auto"></model-viewer>'+
      '<span class="arttag d3">3D · 点击放大</span></div>';
  }else if(it.image && !degraded.has(it.id)){
    const lic=(it.imageCredit&&it.imageCredit.license)||'Wikimedia Commons';
    media='<div class="art"'+cat+'><img src="'+esc(it.image)+'" alt="'+esc(it.name)+'" loading="lazy">'+
      '<span class="arttag">实物照片 · '+esc(lic)+'</span></div>';
  }else{
    media='<div class="art"'+cat+'>'+it.art+'<span class="arttag">示意图 · 非实物照片</span></div>';
  }
  return '<article class="card'+(done?' done':'')+'" data-id="'+it.id+'">'+
    media+'<button class="check" title="标记已打卡">✓</button>'+
    '<div class="body">'+
    '<div class="card-top"><h3 class="serif">'+esc(it.name)+'</h3>'+
    '<span class="badge '+BATCH_CLS[it.batch]+'">'+it.batchName+'</span></div>'+
    '<div class="meta"><span class="mu">🏛 '+esc(it.museum)+'</span>'+
    '<span>📍 '+esc(it.city)+'</span><span>⏳ '+esc(it.era)+'</span><span>◈ '+esc(it.catMain)+'</span></div>'+
    '<div class="tags">'+it.tags.map(t=>'<span class="tag">'+esc(t)+'</span>').join('')+'</div>'+
    '<div class="whyline"><b>为什么入选：</b>'+esc(it.reason)+'</div>'+
    '<p class="story">'+esc(it.story)+'</p>'+
    '<div class="ifoot"><button class="more">展开前世今生 ▾</button>'+
    '<button class="snap" data-id="'+it.id+'" title="拍照 / 从相册选图，存在本机不上传">📷 <span class="snaptx">拍照打卡</span></button></div>'+
    '<div class="pstrip" data-id="'+it.id+'"></div>'+
    officialHTML(it)+creditHTML(it)+'</div></article>';
}

function miniHTML(it){
  const done=visited.has(it.id);
  const mar=(it.image&&!degraded.has(it.id))
    ? '<div class="mar"><img src="'+esc(it.image)+'" alt="'+esc(it.name)+'" loading="lazy"></div>'
    : '<div class="mar">'+it.art.replace('width="92" height="92"','width="46" height="46"')+'</div>';
  return '<div class="mitem'+(done?' done':'')+'" data-id="'+it.id+'">'+
    mar+
    '<div style="flex:1"><h4 class="serif">'+esc(it.name)+'</h4>'+
    '<p class="mwhy">'+esc(it.reason)+'</p>'+
    '<div class="mtags"><span class="badge '+BATCH_CLS[it.batch]+'" style="font-size:10px;padding:2px 7px">'+
    it.batchName+'</span>'+it.tags.slice(0,3).map(t=>'<span class="tag">'+esc(t)+'</span>').join('')+'</div></div>'+
    '<button class="check" title="标记已打卡">✓</button></div>';
}

/* ---------- 足迹 ---------- */
function footHTML(){
  const total=items.length;
  const doneIds=[...visited].filter(id=>items.some(i=>i.id===id));
  const done=items.filter(i=>doneIds.includes(i.id));
  const cityAll={}, cityDone={}, musAll={}, musDone={};
  items.forEach(i=>{cityAll[i.city]=(cityAll[i.city]||0)+1; musAll[i.museum]=(musAll[i.museum]||0)+1;});
  done.forEach(i=>{cityDone[i.city]=(cityDone[i.city]||0)+1; musDone[i.museum]=(musDone[i.museum]||0)+1;});
  const pct=Math.round(done.length/total*100);

  const cityRows=Object.keys(cityAll).sort((a,b)=>cityAll[b]-cityAll[a]).map(c=>{
    const d=cityDone[c]||0, p=Math.round(d/cityAll[c]*100);
    return '<div class="frow'+(d===cityAll[c]?' full':'')+'"><span class="fname">'+esc(c)+'</span>'+
      '<span class="fbar"><i style="width:'+p+'%"></i></span>'+
      '<span class="fnum">'+d+'/'+cityAll[c]+'</span></div>';
  }).join('');

  const topMus=Object.keys(musDone).sort((a,b)=>musDone[b]-musDone[a]).slice(0,10).map(m=>{
    const d=musDone[m], t=musAll[m], p=Math.round(d/t*100);
    return '<div class="frow'+(d===t?' full':'')+'"><span class="fname" title="'+esc(m)+'">'+esc(m)+'</span>'+
      '<span class="fbar"><i style="width:'+p+'%"></i></span>'+
      '<span class="fnum">'+d+'/'+t+'</span></div>';
  }).join('') || '<p style="color:#9a938a;font-size:13px;margin:0">还没有打卡记录，先去清单里勾几件</p>';

  return '<div class="foot">'+
    '<div class="fcard"><h3>总进度</h3>'+
      '<div class="bignum">'+done.length+'<small>/ '+total+' 件</small></div>'+
      '<div style="margin-top:14px;font-size:13.5px;color:#6b645b;line-height:2">'+
      '已打卡博物馆 <b style="color:#221f1b">'+Object.keys(musDone).length+'</b> / '+Object.keys(musAll).length+' 家<br>'+
      '已点亮城市 <b style="color:#221f1b">'+Object.keys(cityDone).length+'</b> / '+Object.keys(cityAll).length+' 座<br>'+
      '完成度 <b style="color:#9c3b2e">'+pct+'%</b></div>'+
      '<button class="sharebtn" id="shareBtn">生成我的足迹图</button>'+
      '<p style="font-size:12px;color:#9a938a;margin:10px 0 0">生成一张 PNG，可保存或分享</p></div>'+
    '<div class="fcard"><h3>按城市点亮</h3>'+cityRows+'</div>'+
    '<div class="fcard"><h3>打卡最多的博物馆</h3>'+topMus+'</div>'+
    '<div class="fcard pwallcard"><h3>我的照片墙</h3>'+
      '<p class="pwmeta" id="pwmeta">还没有照片。在清单里任意一件文物上点「📷 拍照打卡」</p>'+
      '<div class="pwall" id="pwall"></div></div>'+
  '</div>';
}

/* ---------- 足迹分享图 ---------- */
function drawShare(){
  const total=items.length;
  const done=items.filter(i=>visited.has(i.id));
  const cityDone={};
  done.forEach(i=>cityDone[i.city]=(cityDone[i.city]||0)+1);
  const W=840,H=1040,c=document.createElement('canvas');
  c.width=W;c.height=H;const g=c.getContext('2d');
  const F='"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif';
  const S='"Songti SC","STSong",serif';

  g.fillStyle='#f5f2ec';g.fillRect(0,0,W,H);
  const grd=g.createLinearGradient(0,0,W,340);
  grd.addColorStop(0,'#efe6d6');grd.addColorStop(1,'#e8ece6');
  g.fillStyle=grd;g.fillRect(0,0,W,340);

  g.fillStyle='#9c3b2e';g.font='600 20px '+F;g.textAlign='center';
  g.fillText('博 物 馆 探 索 主 线',W/2,88);

  g.fillStyle='#221f1b';g.font='700 52px '+S;
  g.fillText('我的国宝足迹',W/2,158);

  g.fillStyle='#6b645b';g.font='17px '+F;
  g.fillText('中国 195 件禁止出国（境）展览文物',W/2,196);

  g.font='700 84px '+S;g.fillStyle='#9c3b2e';
  g.fillText(done.length,W/2-40,300);
  g.font='20px '+F;g.fillStyle='#6b645b';
  g.fillText('/ 195 件',W/2+72,300);

  // 进度条
  const bx=90,by=340,bw=W-180,bh=10;
  g.fillStyle='#e6e0d5';g.beginPath();g.roundRect(bx,by,bw,bh,5);g.fill();
  g.fillStyle='#9c3b2e';g.beginPath();g.roundRect(bx,by,Math.max(bw*done.length/total,4),bh,5);g.fill();

  // 统计
  const stats=[['博物馆',new Set(done.map(i=>i.museum)).size+' / '+new Set(items.map(i=>i.museum)).size],
               ['城市',new Set(done.map(i=>i.city)).size+' / '+new Set(items.map(i=>i.city)).size],
               ['完成度',Math.round(done.length/total*100)+'%']];
  stats.forEach((s,i)=>{
    const x=W/2+(i-1)*220;
    g.fillStyle='#221f1b';g.font='700 30px '+S;g.fillText(s[1],x,430);
    g.fillStyle='#9a938a';g.font='14px '+F;g.fillText(s[0],x,456);
  });

  // 城市列表
  g.textAlign='left';
  g.fillStyle='#221f1b';g.font='700 20px '+S;
  g.fillText('已点亮的城市',90,516);
  const list=Object.keys(cityDone).sort((a,b)=>cityDone[b]-cityDone[a]).slice(0,10);
  if(!list.length){
    g.fillStyle='#9a938a';g.font='16px '+F;
    g.fillText('还没有打卡记录 —— 从离家最近的一家博物馆开始吧',90,560);
  } else {
    list.forEach((city,i)=>{
      const y=560+i*40;
      g.fillStyle='#f0ebe2';g.beginPath();g.roundRect(90,y-20,W-180,32,8);g.fill();
      g.fillStyle='#3d3730';g.font='16px '+F;g.fillText(city,108,y+2);
      g.fillStyle='#9c3b2e';g.font='600 16px '+F;g.textAlign='right';
      g.fillText(cityDone[city]+' 件',W-108,y+2);g.textAlign='left';
    });
  }

  // 页脚
  g.fillStyle='#c9c2b7';g.fillRect(90,H-120,W-180,1);
  g.fillStyle='#9a938a';g.font='14px '+F;g.textAlign='center';
  const d=new Date();
  g.fillText('数据来源：国家文物局《禁止出国（境）展览文物目录》· 生成于 '+d.getFullYear()+'-'+
    String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'),W/2,H-80);
  g.fillText('文物图为按材质类别绘制的示意图，非实物照片',W/2,H-54);
  return c;
}

/* ---------- 交互绑定 ---------- */
function bindCommon(root){
  root.querySelectorAll('.check').forEach(btn=>{
    btn.onclick=e=>{
      e.stopPropagation();
      const id=+btn.closest('[data-id]').dataset.id;
      if(visited.has(id)) visited.delete(id); else visited.add(id);
      saveVisited();
      btn.closest('[data-id]').classList.toggle('done',visited.has(id));
      updateCount(); if(state.view!=='list') render();
    };
  });
  root.querySelectorAll('.more').forEach(b=>{
    b.onclick=e=>{e.stopPropagation();
      const card=b.closest('.card');card.classList.toggle('open');
      b.textContent=card.classList.contains('open')?'收起 ▴':'展开前世今生 ▾';};
  });
  root.querySelectorAll('.wxbtn').forEach(b=>{
    b.onclick=e=>{
      e.stopPropagation();
      const name=b.dataset.m;
      navigator.clipboard && navigator.clipboard.writeText(name).catch(()=>{});
      const old=b.textContent; b.textContent='已复制「'+name+'」，去微信粘贴搜索';
      setTimeout(()=>b.textContent=old,2200);
    };
  });
}

function updateCount(){
  const n=filtered().length, v=filtered().filter(i=>visited.has(i.id)).length;
  document.getElementById('count').innerHTML='共 <b>'+n+'</b> 件'+
    (v?' · 已打卡 <b>'+v+'</b> 件':'')+(n<195?' <span style="color:#9a938a">（已筛选）</span>':'');
}

function renderInner(){
  const view=document.getElementById('view');
  if(state.view==='foot'){
    document.getElementById('count').innerHTML='';
    view.innerHTML=footHTML();
    const sb=document.getElementById('shareBtn');
    sb && (sb.onclick=()=>{
      try{
        const c=drawShare();
        const a=document.createElement('a');
        a.download='我的国宝足迹.png';a.href=c.toDataURL('image/png');a.click();
      }catch(err){ alert('生成图片失败：'+err.message); }
    });
    return;
  }
  const list=filtered();
  updateCount();
  if(!list.length){ view.innerHTML='<div class="empty">没有匹配的文物，换个关键词试试</div>'; return; }

  if(state.view==='list'){
    view.innerHTML='<div class="grid">'+list.map(cardHTML).join('')+'</div>';
    bindCommon(view); return;
  }

  const key=state.view==='museum'?'museum':'city';
  const groups={};
  list.forEach(it=>{ (groups[it[key]]=groups[it[key]]||[]).push(it); });
  const keys=Object.keys(groups).sort((a,b)=>groups[b].length-groups[a].length);

  view.innerHTML = keys.map(k=>{
    const g=groups[k];
    const museumInfo = museums[k] || {};
    const doneN=g.filter(i=>visited.has(i.id)).length;
    const pct=Math.round(doneN/g.length*100);
    const sub = state.view==='museum'
      ? esc(g[0].city)+(g[0].province && g[0].province!==g[0].city?' · '+esc(g[0].province):'')
      : [...new Set(g.map(i=>i.museum))].length+' 家单位';
    const url = g[0].museumUrl;
    const infoBits=[];
    if(museumInfo.type==='collection_institution') infoBits.push('<span>收藏机构</span>');
    if(museumInfo.address) infoBits.push('<span>地址：'+esc(museumInfo.address)+'</span>');
    if(museumInfo.openingHours) infoBits.push('<span>开放：'+esc(museumInfo.openingHours)+'</span>');
    else infoBits.push('<span class="pending">开放时间待官方核验</span>');
    if(museumInfo.reservation) infoBits.push('<span>预约：'+esc(museumInfo.reservation)+'</span>');
    return '<div class="mgroup"><div class="mhead">'+
      '<div style="flex:1"><div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">'+
      '<h3 class="mname">'+esc(k)+'</h3><span class="mcity">'+sub+'</span>'+
      (url?'<a href="'+esc(url)+'" target="_blank" rel="noopener" style="font-size:12.5px">官网 ↗</a>':'')+
      '</div><div class="mmeta">'+infoBits.join('')+'</div>'+ 
      '</div><div class="prog"><i style="width:'+pct+'%"></i></div></div>'+
      '<div class="mcount">'+doneN+' / '+g.length+'</div></div>'+
      '<div class="mbody"><div class="mlist">'+g.map(miniHTML).join('')+'</div></div></div>';
  }).join('');
  bindCommon(view);
  view.querySelectorAll('.mhead').forEach(h=>{h.onclick=()=>h.nextElementSibling.classList.toggle('hide');});
}

/* ---------- 影像三级回退：3D → 照片 → 类别示意图 ---------- */
const ART={}; items.forEach(i=>{ if(i.art) ART[i.catMain]=i.art; });
const degraded=new Set();   // 已降级的文物 id：重新筛选渲染后保持降级，避免反复失败

function degradeArt(box,id){
  if(!box||box.dataset.degraded) return;
  box.dataset.degraded='1';
  if(id) degraded.add(Number(id));
  box.removeAttribute('data-3d');
  box.innerHTML=(ART[box.dataset.cat]||'')+'<span class="arttag">示意图 · 非实物照片</span>';
}
function degradeAll(){
  document.querySelectorAll('.art').forEach(b=>{
    if(b.querySelector && b.querySelector('model-viewer'))
      degradeArt(b, b.parentNode && b.parentNode.dataset.id);
  });
}

(function enableMedia(){
  const MV_CDN='https://unpkg.com/@google/model-viewer/dist/model-viewer.min.js';
  // 照片加载失败 → 回退示意图（捕获阶段才收得到资源 error）
  document.addEventListener('error',e=>{
    const t=e.target;
    if(!t||t.tagName!=='IMG'||!t.parentNode||!t.parentNode.classList) return;
    const p=t.parentNode;
    if(p.classList.contains('art'))
      degradeArt(p, p.parentNode && p.parentNode.dataset.id);
    else if(p.classList.contains('mar'))
      p.style.visibility='hidden';       // 聚合视图里的小缩略图，失败就留白
  },true);
  if(!items.some(i=>i.model)) return;

  const s=document.createElement('script');
  s.type='module'; s.src=MV_CDN;
  s.onerror=degradeAll;
  document.head.appendChild(s);

  if(typeof customElements!=='undefined'&&customElements.whenDefined){
    customElements.whenDefined('model-viewer').then(()=>{
      document.querySelectorAll('model-viewer').forEach(mv=>{
        mv.addEventListener('error',()=>degradeArt(mv.parentNode,
          mv.parentNode.parentNode && mv.parentNode.parentNode.dataset.id));
        mv.addEventListener('load',()=>{ mv.dataset.ok='1'; });
      });
      setTimeout(()=>{
        document.querySelectorAll('model-viewer').forEach(mv=>{
          if(!mv.dataset.ok) degradeArt(mv.parentNode,
            mv.parentNode.parentNode && mv.parentNode.parentNode.dataset.id);
        });
      },20000);
    }).catch(degradeAll);
    setTimeout(()=>{ if(!customElements.get('model-viewer')) degradeAll(); },10000);
  }else{
    degradeAll();
  }
})();

/* ---------- 3D 放大查看 ---------- */
(function viewer3D(){
  const box=document.getElementById('viewer');
  const stage=document.getElementById('vstage');
  const nameEl=document.getElementById('vname');
  if(!box) return;
  function close(){
    box.classList.remove('open'); box.setAttribute('aria-hidden','true');
    stage.innerHTML='';                       // 释放 WebGL 上下文
  }
  function open(src,name){
    const mv=document.createElement('model-viewer');
    mv.setAttribute('src',src); mv.setAttribute('alt',name||'');
    mv.setAttribute('camera-controls',''); mv.setAttribute('auto-rotate','');
    mv.setAttribute('rotation-per-second','18deg'); mv.setAttribute('touch-action','pan-y');
    stage.innerHTML=''; stage.appendChild(mv);
    nameEl.textContent=name||'';
    box.classList.add('open'); box.setAttribute('aria-hidden','false');
  }
  document.addEventListener('click',e=>{
    const t=e.target;
    if(t===box) return close();
    const art=t&&t.closest?t.closest('.art'):null;
    if(!art||art.dataset.degraded) return;
    const mv=art.querySelector?art.querySelector('model-viewer'):null;
    if(!mv) return;
    open(mv.getAttribute('src'), mv.getAttribute('alt'));
  });
  const btn=document.getElementById('vclose');
  if(btn) btn.onclick=close;
  document.addEventListener('keydown',e=>{ if(e.key==='Escape') close(); });
})();

/* ---------- 拍照打卡：照片压缩后存本机 IndexedDB，不上传 ---------- */
const PDB='guobao_photos_v1', PSTORE='photos';
let _pdb=null, pcount={}, purls={};
const PHOTO_MAX=1400, PHOTO_Q=0.82;      // 长边 1400px / JPEG 0.82 → 手机原图约 300KB

function openPDB(){
  if(_pdb) return _pdb;
  _pdb=new Promise((res,rej)=>{
    if(typeof indexedDB==='undefined') return rej(new Error('当前浏览器不支持 IndexedDB'));
    const r=indexedDB.open(PDB,1);
    r.onupgradeneeded=()=>{
      const db=r.result;
      if(!db.objectStoreNames.contains(PSTORE)){
        db.createObjectStore(PSTORE,{keyPath:'id',autoIncrement:true})
          .createIndex('relic','relicId',{unique:false});
      }
    };
    r.onsuccess=()=>res(r.result);
    r.onerror=()=>rej(r.error);
  });
  return _pdb;
}
function ptx(mode,fn){
  return openPDB().then(db=>new Promise((res,rej)=>{
    const t=db.transaction(PSTORE,mode);
    const req=fn(t.objectStore(PSTORE));
    t.oncomplete=()=>res(req?req.result:undefined);
    t.onerror=()=>rej(t.error);
    t.onabort=()=>rej(t.error);
  }));
}
const photoAdd=(relicId,blob,w,h)=>ptx('readwrite',s=>s.add({relicId,blob,w,h,t:Date.now()}));
const photoOf=relicId=>ptx('readonly',s=>s.index('relic').getAll(IDBKeyRange.only(relicId)));
const photoAll=()=>ptx('readonly',s=>s.getAll());
const photoDel=id=>ptx('readwrite',s=>s.delete(id));

function compress(file){
  return new Promise((res,rej)=>{
    const url=URL.createObjectURL(file), img=new Image();
    img.onload=()=>{
      URL.revokeObjectURL(url);
      const k=Math.min(1,PHOTO_MAX/Math.max(img.width,img.height));
      const cw=Math.max(1,Math.round(img.width*k)), ch=Math.max(1,Math.round(img.height*k));
      const c=document.createElement('canvas'); c.width=cw; c.height=ch;
      const g=c.getContext('2d');
      g.fillStyle='#fff'; g.fillRect(0,0,cw,ch);
      g.drawImage(img,0,0,cw,ch);
      c.toBlob(b=>b?res({blob:b,w:cw,h:ch}):rej(new Error('压缩失败')),'image/jpeg',PHOTO_Q);
    };
    img.onerror=()=>{ URL.revokeObjectURL(url); rej(new Error('图片无法解码')); };
    img.src=url;
  });
}

function markVisited(id,on){
  if(on) visited.add(id); else visited.delete(id);
  const el=document.querySelector('.card[data-id="'+id+'"],.mitem[data-id="'+id+'"]');
  if(el) el.classList.toggle('done',visited.has(id));
  updateCount();
  try{ saveVisited(); }catch(e){ console.warn('打卡状态保存失败',e); }
}
function setSnapBusy(id,on){
  document.querySelectorAll('.snap[data-id="'+id+'"]').forEach(b=>{
    b.disabled=on;
    if(on) b.querySelector('.snaptx').textContent='处理中…';
  });
}

async function refreshStrip(relicId){
  const el=document.querySelector('.pstrip[data-id="'+relicId+'"]');
  let list=[];
  try{ list=(await photoOf(relicId))||[]; }catch(e){ list=[]; }
  pcount[relicId]=list.length;
  if(el){
    (purls[relicId]||[]).forEach(u=>URL.revokeObjectURL(u));
    purls[relicId]=list.map(p=>URL.createObjectURL(p.blob));
    el.innerHTML=list.map((p,i)=>
      '<div class="pthumb" data-id="'+relicId+'" data-i="'+i+'">'+
      '<img src="'+purls[relicId][i]+'" alt="我的照片 '+(i+1)+'" loading="lazy">'+
      '<button class="px" title="删除这张">×</button></div>').join('')+
      '<button class="padd" data-id="'+relicId+'" title="再加一张">＋</button>';
  }
  document.querySelectorAll('.snap[data-id="'+relicId+'"]').forEach(b=>{
    b.disabled=false;
    b.classList.toggle('has',list.length>0);
    const tx=b.querySelector('.snaptx');
    if(tx) tx.textContent=list.length?'我的照片 '+list.length:'拍照打卡';
  });
  if(list.length && !visited.has(relicId)) markVisited(relicId,true);
}

/* 每次 render() 之后把当前视图里的照片条补上 */
async function hydratePhotos(){
  try{ await openPDB(); }
  catch(err){
    document.querySelectorAll('.snap').forEach(b=>{
      b.disabled=true; b.title='本机不支持本地照片存储：'+err.message;
    });
    const meta=document.getElementById('pwmeta');
    if(meta) meta.textContent='本机不支持本地照片存储（'+err.message+'），打卡功能不受影响';
    return;
  }
  const ids=[...new Set([...document.querySelectorAll('.pstrip[data-id]')]
    .map(e=>+e.dataset.id))];
  for(const id of ids) await refreshStrip(id);
  await refreshPhotoWall();
}

/* 隐藏的 file input：手机上 capture 会直接唤起相机 */
let pickTarget=null;
const filePicker=(function(){
  const i=document.createElement('input');
  i.type='file'; i.accept='image/*'; i.multiple=true;
  i.setAttribute('capture','environment');
  i.style.cssText='position:fixed;left:-9999px;width:1px;height:1px;opacity:0';
  document.body.appendChild(i);
  i.onchange=async()=>{
    const files=Array.prototype.slice.call(i.files||[]);
    i.value='';
    const id=pickTarget;
    if(!files.length||id==null) return;
    setSnapBusy(id,true);
    let ok=0,bad=0;
    for(const f of files){
      try{ const r=await compress(f); await photoAdd(id,r.blob,r.w,r.h); ok++; }
      catch(e){ bad++; }
    }
    await refreshStrip(id);
    await refreshPhotoWall();
    if(bad) alert(bad+' 张处理失败（格式不支持或存储空间不足）');
    if(!ok) setSnapBusy(id,false);
  };
  return i;
})();
function pickPhoto(id){ pickTarget=id; filePicker.click(); }

async function removePhoto(relicId,i){
  const list=(await photoOf(relicId))||[];
  const p=list[i];
  if(!p) return;
  if(!confirm('删除这张照片？删了就找不回来了')) return;
  await photoDel(p.id);
  closeLB();
  await refreshStrip(relicId);
  await refreshPhotoWall();
}

/* ---------- 灯箱 ---------- */
let lb={relicId:null,i:0,list:[],url:null};
async function openLB(relicId,i){
  lb.relicId=relicId;
  lb.list=(await photoOf(relicId))||[];
  if(!lb.list.length) return;
  lb.i=Math.max(0,Math.min(i,lb.list.length-1));
  renderLB();
}
function renderLB(){
  const box=document.getElementById('lb'), p=lb.list[lb.i];
  if(!p) return closeLB();
  if(lb.url) URL.revokeObjectURL(lb.url);
  lb.url=URL.createObjectURL(p.blob);
  document.getElementById('lbimg').src=lb.url;
  const it=items.filter(x=>x.id===lb.relicId)[0];
  document.getElementById('lbcap').textContent=(it?it.name:'')+' · 第 '+(lb.i+1)+' / '+
    lb.list.length+' 张 · '+new Date(p.t).toLocaleString('zh-CN',
      {year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'});
  const multi=lb.list.length>1;
  ['lbprev','lbnext'].forEach(id=>{document.getElementById(id).style.display=multi?'':'none';});
  box.classList.add('open'); box.setAttribute('aria-hidden','false');
}
function closeLB(){
  const box=document.getElementById('lb');
  box.classList.remove('open'); box.setAttribute('aria-hidden','true');
  document.getElementById('lbimg').removeAttribute('src');
  if(lb.url){ URL.revokeObjectURL(lb.url); lb.url=null; }
}

/* ---------- 照片墙（足迹视图） ---------- */
async function refreshPhotoWall(){
  const wall=document.getElementById('pwall'), meta=document.getElementById('pwmeta');
  if(!wall) return;
  let all=[];
  try{ all=(await photoAll())||[]; }catch(e){ all=[]; }
  all.sort((a,b)=>b.t-a.t);
  const relics=new Set(all.map(p=>p.relicId));
  if(meta) meta.textContent=all.length
    ? all.length+' 张照片 · 覆盖 '+relics.size+' / '+items.length+' 件文物 · 全部只存在本机，不会上传'
    : '还没有照片。在清单里任意一件文物上点「📷 拍照打卡」，照片只存在你这台设备上';
  (wall._urls||[]).forEach(u=>URL.revokeObjectURL(u));
  wall._urls=all.map(p=>URL.createObjectURL(p.blob));
  wall.innerHTML=all.map((p,i)=>{
    const it=items.filter(x=>x.id===p.relicId)[0];
    return '<div class="pcell" data-id="'+p.relicId+'" data-i="'+i+'">'+
      '<img src="'+wall._urls[i]+'" alt="'+(it?esc(it.name):'')+'" loading="lazy">'+
      '<span class="pcn">'+(it?esc(it.name):'')+'</span></div>';
  }).join('');
}
async function openLBFromWall(relicId,wallIndex){
  let all=[];
  try{ all=(await photoAll())||[]; }catch(e){ return; }
  all.sort((a,b)=>b.t-a.t);
  const target=all[wallIndex];
  if(!target) return;
  const list=(await photoOf(relicId))||[];
  const idx=list.findIndex(p=>p.id===target.id);
  openLB(relicId, idx<0?0:idx);
}

/* 事件委托：拍照 / 删除 / 看大图 */
document.addEventListener('click',e=>{
  const t=e.target;
  if(!t||!t.closest) return;
  const snap=t.closest('.snap');
  if(snap){ pickPhoto(+snap.dataset.id); return; }
  const padd=t.closest('.padd');
  if(padd){ pickPhoto(+padd.dataset.id); return; }
  const px=t.closest('.px');
  if(px){ e.stopPropagation(); const box=px.closest('.pthumb');
          removePhoto(+box.dataset.id,+box.dataset.i); return; }
  const cell=t.closest('.pcell');
  if(cell){ openLBFromWall(+cell.dataset.id,+cell.dataset.i); return; }
  const th=t.closest('.pthumb');
  if(th) openLB(+th.dataset.id,+th.dataset.i);
});

function render(){ renderInner(); hydratePhotos(); }

(function bootLB(){
  const lbx=document.getElementById('lbx'); if(lbx) lbx.onclick=closeLB;
  const lbd=document.getElementById('lbd'); if(lbd) lbd.onclick=()=>removePhoto(lb.relicId,lb.i);
  const prev=document.getElementById('lbprev'), next=document.getElementById('lbnext');
  if(prev) prev.onclick=()=>{lb.i=(lb.i-1+lb.list.length)%lb.list.length;renderLB();};
  if(next) next.onclick=()=>{lb.i=(lb.i+1)%lb.list.length;renderLB();};
  document.addEventListener('keydown',e=>{
    if(!document.getElementById('lb').classList.contains('open')) return;
    if(e.key==='Escape') closeLB();
    if(e.key==='ArrowLeft'&&prev) prev.click();
    if(e.key==='ArrowRight'&&next) next.click();
  });
  document.getElementById('lb').addEventListener('click',e=>{
    if(e.target.id==='lb') closeLB();
  });
})();

render();
</script>
</body>
</html>
'''

html = HTML.replace('__PAYLOAD__', payload)
path = os.path.join(OUT, '195件永禁文物·博物馆探索.html')
with open(path, 'w', encoding='utf-8') as f:
    f.write(html)
print('HTML 写出:', path, os.path.getsize(path) // 1024, 'KB')
