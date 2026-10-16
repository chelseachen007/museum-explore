# -*- coding: utf-8 -*-
"""从 195 件文物数据生成馆级资料骨架。

不猜测开放时间：没有经过官方来源核验的字段保持为空，页面会明确显示待核验。
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ITEMS_FILE = os.path.join(ROOT, '195件永禁文物.json')
OUTPUT_FILE = os.path.join(ROOT, 'museums.json')
OVERRIDES_FILE = os.path.join(HERE, 'museum_overrides.json')

with open(ITEMS_FILE, encoding='utf-8') as f:
    items = json.load(f)['items']

existing = {}
if os.path.exists(OUTPUT_FILE):
    with open(OUTPUT_FILE, encoding='utf-8') as f:
        existing = {m['name']: m for m in json.load(f).get('museums', [])}
overrides = {}
if os.path.exists(OVERRIDES_FILE):
    with open(OVERRIDES_FILE, encoding='utf-8') as f:
        overrides = json.load(f)

groups = {}
for item in items:
    museum = item['museum']
    group = groups.setdefault(museum, {
        'name': museum,
        'type': 'museum',
        'city': item.get('city'),
        'province': item.get('province'),
        'officialUrl': item.get('museumUrl'),
        'wechat': item.get('museumWechat') or museum,
        'address': None,
        'intro': None,
        'openingHours': None,
        'closedDays': None,
        'reservation': None,
        'sources': [item.get('museumUrl')] if item.get('museumUrl') else [],
        'verifiedAt': None,
        'status': 'pending_verification',
    })
    if museum in existing:
        group.update(existing[museum])
        group['name'] = museum
    if museum in overrides:
        group.update(overrides[museum])
        group['name'] = museum
    if not group['officialUrl'] and item.get('museumUrl'):
        group['officialUrl'] = item['museumUrl']
    if item.get('museumUrl') and item['museumUrl'] not in group.get('sources', []):
        group.setdefault('sources', []).append(item['museumUrl'])

non_museum_keywords = ('大学', '研究院', '研究所', '图书馆', '科学院')
for group in groups.values():
    if any(keyword in group['name'] for keyword in non_museum_keywords):
        group['type'] = 'collection_institution'

data = {
    'meta': {
        'title': '195 件永禁出境文物收藏机构资料',
        'total': len(groups),
        'description': '馆级资料与文物清单分离维护；空字段表示尚未完成官方来源核验，不代表该机构没有相关信息。',
        'listSource': 'https://zh.wikipedia.org/wiki/禁止出境展览文物',
        'verificationPolicy': '开放时间、地址和预约方式只在官方来源明确后填写；每次更新必须记录 verifiedAt。',
        'fieldPolicy': {
            'openingHours': '优先采用机构官网或官方预约平台，需记录 verifiedAt。',
            'reservation': '只记录官方预约渠道，不保存账号、Token 或个人信息。',
        },
    },
    'museums': sorted(groups.values(), key=lambda x: (x.get('province') or '', x['name'])),
}

with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write('\n')

print(f'generated {len(groups)} institutions -> {OUTPUT_FILE}')
