# -*- coding: utf-8 -*-
import json, sys, os
# 全部路径相对脚本自身定位，整个工作区搬到哪里都能跑
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
from data_part1 import PART1
from data_part2 import PART2
from common import site_of
from text_clean import clean_tree

OUT = os.path.dirname(_HERE)          # 工作区根目录（tools 的上一级）

rows = []
for t in PART1:
    rows.append(dict(name=t[0], era=t[1], cat=t[2], museum=t[3], city=t[4],
                     province=t[5], tags=t[6], reason=t[7], story=t[8]))
for t in PART2:
    rows.append(dict(name=t[0], era=t[1], cat=t[2], museum=t[3], city=t[4],
                     province=t[5], tags=t[6], reason=t[7], story=t[8]))

assert len(rows) == 195, f"条数不对: {len(rows)}"

for i, r in enumerate(rows, 1):
    r['id'] = i
    # image / model: 由 Wikidata→Commons 流水线回填（见 enrich_images.py），拿不到则为 None，
    # 前端会回退到按材质类别绘制的内联 SVG 示意图
    r['image'] = None
    r['imageCredit'] = None
    r['model'] = None
    r['museumUrl'] = site_of(r['museum'])
    r['museumWechat'] = r['museum']
    if i <= 64:
        r['batch'] = 1; r['year'] = 2002; r['batchName'] = '第一批'
    elif i <= 101:
        r['batch'] = 2; r['year'] = 2012; r['batchName'] = '第二批'
    else:
        r['batch'] = 3; r['year'] = 2013; r['batchName'] = '第三批'

data = {
    "meta": {
        "title": "禁止出国（境）展览文物",
        "total": 195,        "batches": [
            {"batch": 1, "year": 2002, "count": 64, "name": "第一批", "note": "综合类，含青铜、玉器、漆器、壁画、丝织等"},
            {"batch": 2, "year": 2012, "count": 37, "name": "第二批", "note": "书画类，书法 17 件 + 绘画 20 件"},
            {"batch": 3, "year": 2013, "count": 94, "name": "第三批", "note": "分青铜器、陶瓷、玉器、杂项四类"}
        ],
        "law": "《中华人民共和国文物保护法实施条例》第四十九条：一级文物中的孤品和易损品，禁止出境展览。禁止出境展览文物的目录，由国务院文物行政主管部门定期公布。",
        "media": {
            "note": "每条文物的影像为可选字段，前端按 model → image → 类别示意图 三级回退渲染。",
            "model": "3D 模型 URL（glTF/GLB），留空则不渲染 3D。前端用 model-viewer 组件渲染，可拖拽旋转、点击放大。",
            "image": "实物照片的本地相对路径（relic_images/<id>.<ext>）。来源：Wikimedia Commons（经 Wikidata P18 属性匹配），均为公有领域或 CC 授权，需保留署名。留空则用类别示意图。",
            "imageCredit": "照片署名信息：license / author / page（Commons 文件页）/ qid（Wikidata 实体）。",
            "catArt": "类别示意图由 catMain 字段映射，共 11 类：青铜器/陶瓷/玉器/书画/漆木器/丝织刺绣/壁画石刻/金银器/文献杂项/画像砖/陶器。"
        },
        "sources": [
            "国家文物局 文物办发〔2002〕5 号《首批禁止出国（境）展览文物目录》",
            "国家文物局 文物博函〔2012〕1345 号《第二批禁止出国（境）展览文物目录（书画类）》",
            "国家文物局 文物博函〔2013〕1320 号《第三批禁止出境展览文物目录》"
        ]
    },
    "items": rows
}

# ---- 回填实物照片：读取 Wikidata→Commons 流水线的缓存 ----
import re, html as _html

def clean(s, n=90):
    """Commons 的 Artist 字段常带 HTML 链接，去掉标签并解码实体"""
    s = s or ''
    s = re.sub(r'<[^>]*>', ' ', s)      # 成对/闭合的标签
    s = re.sub(r'<.*$', ' ', s)         # 被截断留下的半截标签（无闭合 >）
    s = _html.unescape(s)
    # Credit 字段常混入时间戳和 "Own work" 之类的说明
    s = re.sub(r'\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?', ' ', s)
    s = re.sub(r'\bOwn work\b', ' ', s, flags=re.I)
    s = re.sub(r'[,，]\s*(?=[,，])', ' ', s)
    s = ' '.join(s.split())
    return s.strip(' ,，、')[:n]

CACHE = os.path.join(_HERE, 'wd_cache.json')
MANUAL_IMAGES = os.path.join(_HERE, 'commons_manual.json')
WIKI_IMPORT = os.path.join(_HERE, 'wiki_import.json')
LOCAL_IMAGES = os.path.join(_HERE, 'local_images.json')
EXISTING_DATA = os.path.join(OUT, '195件永禁文物.json')

# 本地图片索引优先级最高：它由 tools/localize_assets.py 生成，
# 记录已经下载到 relic_images/ 的离线图片，重建数据时不能被外链覆盖。
local_images = {}
if os.path.exists(LOCAL_IMAGES):
    try:
        local_images = json.load(open(LOCAL_IMAGES, encoding='utf-8')).get('images') or {}
    except Exception:
        local_images = {}

existing_local = {}
if os.path.exists(EXISTING_DATA):
    try:
        for old in json.load(open(EXISTING_DATA, encoding='utf-8')).get('items', []):
            old_image = old.get('image') or ''
            if old_image.startswith('relic_images/'):
                existing_local[old['name']] = old_image
    except Exception:
        existing_local = {}

# 已知错配：缓存键 → 被匹配到的 Wikidata 实体。宁可不出图，也不能张冠李戴。
BAD_MATCH = {
    '摇钱树': 'Pteroceltis tatarinowii',   # 榆科树种，不是汉代青铜摇钱树
}

def norm(s):
    """归一化：去括号内容、书名号、标点、空白，便于跨命名习惯匹配"""
    s = re.sub(r'[（(【\[].*?[)）】\]]', '', s or '')
    s = re.sub(r'[《》「」〈〉\s·・,，。、;；:：\'"\-—_]', '', s)
    return s

# 卡片图片区固定 112px 高 + object-fit:cover。极端长宽比的图（长卷书画等）会被放大裁切，
# 又糊又是随机一段，还不如用类别示意图。超过这个比例就不采用。
MAX_ASPECT = 4.0

# 手动指定：自动匹配不合适时（长卷、错配等），在这里钉一个 Commons 文件名。
# 值可以是 {'file': 'Xxx.jpg'} —— 会自动查 imageinfo 取缩略图与署名。
MANUAL_IMAGE = {
    # 清明上河图全卷是 38414x1800（比 21:1），塞进 112px 高的卡片只会是一条糊缝。
    # 待挑一张合适的局部特写后填在这里，例如：
    # '张择端《清明上河图》卷': {'file': 'Along the River During the Qingming Festival (detail).jpg'},
}

def too_flat(info):
    w, h = info.get('w'), info.get('h')
    if not w or not h:
        return False
    r = w / h
    return r > MAX_ASPECT or r < 1 / MAX_ASPECT

if os.path.exists(CACHE):
    wd = json.load(open(CACHE, encoding='utf-8'))

    # 建归一化索引：同一个键可能被多个文物名指向，取第一个命中的
    idx = {}
    for k, v in wd.items():
        if not v or not v.get('info'):
            continue
        for cand in {norm(k), norm(v.get('matched') or '')}:
            if cand:
                idx.setdefault(cand, k)

    def lookup(name):
        """精确 → 归一化精确 → 子串（取最长命中）"""
        hit = wd.get(name)
        if hit and hit.get('info'):
            return hit, name
        n = norm(name)
        k = idx.get(n)
        if k:
            return wd[k], k
        # 子串匹配：如「张择端《清明上河图》卷」→ 缓存键「清明上河图」
        best, best_len = None, 0
        for cand, key in idx.items():
            if len(cand) >= 3 and (cand in n or n in cand) and len(cand) > best_len:
                best, best_len = key, len(cand)
        return (wd[best], best) if best else (None, None)

    filled, bad, flat, unmatched = 0, [], [], []
    for r in rows:
        hit, key = lookup(r['name'])
        if not hit or not hit.get('info'):
            unmatched.append(r['name'])
            continue
        # 错配拦截
        if key in BAD_MATCH and (hit.get('matched') or '') == BAD_MATCH[key]:
            bad.append(r['name'])
            continue
        info = hit['info']
        thumb = (info.get('thumb') or info.get('url') or '').split('?')[0]   # 去掉 utm 参数
        if not thumb:
            unmatched.append(r['name'])
            continue
        # 长宽比拦截
        if too_flat(info):
            flat.append((r['name'], info.get('w'), info.get('h')))
            continue
        r['image'] = thumb
        r['imageCredit'] = {
            'license': clean(info.get('license'), 40),
            'author': clean(info.get('artist')),
            'authorSrc': info.get('artistSrc') or '',
            'page': info.get('page') or '',
            'source': 'Wikimedia Commons',
            'qid': hit.get('qid') or ''
        }
        filled += 1

    # ---- 手动指定的图片优先覆盖自动匹配 ----
    manual_n = 0
    if MANUAL_IMAGE:
        import urllib.request, urllib.parse
        byname = {r['name']: r for r in rows}
        for name, spec in MANUAL_IMAGE.items():
            r = byname.get(name)
            if not r:
                print(f"  [手动指定] 名称不在清单里，已跳过: {name}")
                continue
            fname = spec.get('file') if isinstance(spec, dict) else spec
            try:
                api = 'https://commons.wikimedia.org/w/api.php?' + urllib.parse.urlencode({
                    'action': 'query', 'format': 'json', 'prop': 'imageinfo',
                    'iiprop': 'url|size|extmetadata', 'iiurlwidth': '960',
                    'titles': 'File:' + fname})
                req = urllib.request.Request(api, headers={'User-Agent': 'relic-research/1.0'})
                j = json.load(urllib.request.urlopen(req, timeout=30))
                pg = list((j.get('query') or {}).get('pages', {}).values())[0]
                ii = (pg.get('imageinfo') or [{}])[0]
                t = (ii.get('thumburl') or '').split('?')[0]
                if not t:
                    print(f"  [手动指定] 查不到文件: {fname}")
                    continue
                em = ii.get('extmetadata') or {}
                r['image'] = t
                r['imageCredit'] = {
                    'license': clean((em.get('LicenseShortName') or {}).get('value'), 40),
                    'author': clean((em.get('Artist') or {}).get('value')),
                    'authorSrc': 'artist',
                    'page': ii.get('descriptionurl') or '',
                    'source': 'Wikimedia Commons',
                    'qid': ''
                }
                manual_n += 1
            except Exception as e:
                print(f"  [手动指定] 失败 {name}: {e}")
    if manual_n:
        print(f"  手动指定生效 {manual_n} 条")

    if os.path.exists(MANUAL_IMAGES):
        manual_images = json.load(open(MANUAL_IMAGES, encoding='utf-8'))
        byname = {r['name']: r for r in rows}
        manual_fill_n = 0
        for name, spec in manual_images.items():
            r = byname.get(name)
            if not r or not spec.get('image'):
                continue
            r['image'] = spec['image']
            r['imageCredit'] = spec.get('imageCredit')
            manual_fill_n += 1
        if manual_fill_n:
            print(f"  Commons 搜索补充生效 {manual_fill_n} 条")

    if os.path.exists(WIKI_IMPORT):
        wiki_import = json.load(open(WIKI_IMPORT, encoding='utf-8')).get('items', {})
        byname = {r['name']: r for r in rows}
        wiki_fill_n = 0
        wiki_image_n = 0
        for name, spec in wiki_import.items():
            r = byname.get(name)
            if not r:
                continue
            if spec.get('image'):
                r['image'] = spec['image']
                r['imageCredit'] = spec.get('imageCredit')
                wiki_image_n += 1
            if spec.get('wikiUrl'):
                r['wikiUrl'] = spec['wikiUrl']
            if spec.get('museumWikiUrl'):
                r['museumWikiUrl'] = spec['museumWikiUrl']
            wiki_fill_n += 1
        if wiki_fill_n:
            print(f"  维基百科表格导入 {wiki_fill_n} 条，图片 {wiki_image_n} 条")

    # 本地图片兜底：优先使用 localize_assets.py 生成的索引，
    # 其次保留上一次构建已经落到 relic_images/ 的图片。
    local_fill_n = 0
    for r in rows:
        local = local_images.get(r['name']) or existing_local.get(r['name'])
        if local and os.path.exists(os.path.join(OUT, local)):
            if r.get('image') != local:
                r['image'] = local
                local_fill_n += 1
    if local_fill_n:
        print(f"  本地图片兜底 {local_fill_n} 条")

    print(f"实物照片回填: {filled + manual_n}/{len(rows)} 件（其余用类别示意图）")
    if bad:
        print(f"  已拦截错配 {len(bad)} 条: {', '.join(bad)}")
    if flat:
        print(f"  已拦截长宽比过极端 {len(flat)} 条:")
        for n, w, h in flat:
            print(f"    {n}  ({w}x{h}, 比 {w/h:.1f})")
    print(f"  未匹配 {len(unmatched)} 条")

data = clean_tree(data)
with open(os.path.join(OUT, '195件永禁文物.json'), 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
    f.write('\n')

# 统计
from collections import Counter
mc = Counter(r['museum'] for r in rows)
cc = Counter(r['city'] for r in rows)
print("文物总数:", len(rows))
print("博物馆数:", len(mc), " 城市数:", len(cc))
print("批次分布:", Counter(r['batchName'] for r in rows))
print("类别分布:", Counter(r['cat'] for r in rows))
print("\n藏品最多的 12 家博物馆:")
for m, c in mc.most_common(12):
    print(f"  {m}: {c}")
print("\n藏品最多的 10 座城市:")
for m, c in cc.most_common(10):
    print(f"  {m}: {c}")
