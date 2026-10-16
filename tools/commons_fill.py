# -*- coding: utf-8 -*-
"""补充 Wikidata 未命中的 Commons 实物图。

只接受 Commons 搜索结果中包含文物名关键片段的候选；无法确认时保持缺图，
避免“有图但图错了”比“没有图”更糟糕。
"""
import html
import json
import os
import re
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA_FILE = os.path.join(ROOT, '195件永禁文物.json')
OUT_FILE = os.path.join(HERE, 'commons_manual.json')
CACHE_FILE = os.path.join(HERE, 'commons_search_cache.json')

ALIASES = {
    '长信宫灯': ['Changxin Palace Lantern'],
    '铜奔马（马踏飞燕）': ['Flying Horse of Gansu', 'Galloping Horse'],
    '「五星出东方利中国」护膊': ['Five stars rise in the east', 'Five stars out of the east'],
    '商太阳神鸟金箔片': ['Sun Bird Gold Foil'],
    '商金杖': ['Sanxingdui Golden Staff'],
    '西汉「皇后之玺」玉玺': ['Empress Jade Seal'],
    '曾侯乙青铜尊盘': ['Marquis Yi of Zeng bronze zun pan'],
    '司母戊鼎': ['Houmuwu Ding', 'Simuwu Ding'],
}

def clean(value):
    value = html.unescape(re.sub(r'<[^>]*>', ' ', value or ''))
    return ' '.join(value.split()).strip()

def norm(value):
    value = re.sub(r'[（(【\[].*?[)）】\]]', '', value or '')
    return re.sub(r'[《》「」〈〉\s·・,，。、;；:：\'"\-—_]', '', value).lower()

def fragments(name):
    base = norm(name)
    if len(base) > 8:
        yield base[-8:]
        yield base[-6:]
    if base:
        yield base

def api(params):
    url = 'https://commons.wikimedia.org/w/api.php?' + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={'User-Agent': 'MuseumExplorer/0.1'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)

data = json.load(open(DATA_FILE, encoding='utf-8'))
cache = json.load(open(CACHE_FILE, encoding='utf-8')) if os.path.exists(CACHE_FILE) else {}
manual = json.load(open(OUT_FILE, encoding='utf-8')) if os.path.exists(OUT_FILE) else {}

for item in data['items']:
    if item.get('image') or item['name'] in manual:
        continue
    queries = [item['name']]
    queries.extend(ALIASES.get(item['name'], []))
    compact = re.sub(r'^(新石器时代|西周|东周|战国|西汉|东汉|西晋|北魏|北齐|隋|唐|五代|北宋|南宋|元|明|清)', '', item['name'])
    if compact != item['name']:
        queries.append(compact)
    candidates = []
    for query in queries:
        if query in cache:
            result = cache[query]
        else:
            try:
                result = api({'action': 'query', 'format': 'json', 'list': 'search',
                              'srsearch': query, 'srnamespace': 6, 'srlimit': 8})
            except Exception:
                result = {}
            cache[query] = result
            time.sleep(0.35)
        for hit in (result.get('query') or {}).get('search', []):
            title = hit.get('title', '').removeprefix('File:')
            title_norm = norm(title)
            score = max((len(fragment) for fragment in fragments(item['name']) if fragment in title_norm), default=0)
            if score >= 6:
                candidates.append((score, title))
    if not candidates:
        continue
    candidates.sort(reverse=True)
    title = candidates[0][1]
    try:
        result = api({'action': 'query', 'format': 'json', 'titles': 'File:' + title,
                      'prop': 'imageinfo', 'iiprop': 'url|size|extmetadata', 'iiurlwidth': 960})
        pages = (result.get('query') or {}).get('pages') or {}
        info = next(iter(pages.values())).get('imageinfo', [{}])[0]
        thumb = (info.get('thumburl') or '').split('?')[0]
        width, height = info.get('width'), info.get('height')
        if not thumb or not width or not height or max(width / height, height / width) > 4:
            continue
        metadata = info.get('extmetadata') or {}
        def meta(key):
            return clean((metadata.get(key) or {}).get('value'))
        manual[item['name']] = {
            'image': thumb,
            'imageCredit': {
                'license': meta('LicenseShortName'),
                'author': meta('Artist') or meta('Credit') or info.get('user', ''),
                'authorSrc': 'artist',
                'page': info.get('descriptionurl') or '',
                'source': 'Wikimedia Commons',
                'qid': ''
            }
        }
        print('matched:', item['name'], '->', title)
    except Exception:
        continue

json.dump(cache, open(CACHE_FILE, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
json.dump(manual, open(OUT_FILE, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('manual images:', len(manual))
