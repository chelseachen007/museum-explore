# -*- coding: utf-8 -*-
"""从维基百科主表导入文物图片与收藏单位链接。

维基表格是本项目 195 件清单的直接可读来源。图片只记录 Commons 文件页，
实际缩略图由 Special:FilePath 提供，避免依赖页面上的低分辨率缩略图。
"""
import html
import json
import os
import re
import urllib.parse
import urllib.request
from difflib import SequenceMatcher

from bs4 import BeautifulSoup

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA_FILE = os.path.join(ROOT, '195件永禁文物.json')
OUTPUT_FILE = os.path.join(HERE, 'wiki_import.json')
API = 'https://zh.wikipedia.org/w/api.php'
WIKI_BASE = 'https://zh.wikipedia.org'
COMMONS_BASE = 'https://commons.wikimedia.org/wiki/File:'
NAME_ALIASES = {
    '晋侯稣钟 （一套14件）': '晋侯苏钟（一套 16 件，14 件在上博、2 件在山西博物院）',
    '西周 㝬 簋': '西周害夫簋（胡簋）',
}

def fetch_page():
    params = urllib.parse.urlencode({
        'action': 'parse', 'page': '禁止出境展览文物', 'prop': 'text', 'format': 'json'
    })
    request = urllib.request.Request(API + '?' + params, headers={'User-Agent': 'MuseumExplorer/0.1'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)['parse']['text']['*']

def norm(value):
    value = html.unescape(value or '')
    value = value.replace('長', '长').replace('紗', '纱').replace('褝', '禅').replace('稣', '苏')
    value = re.sub(r'[“”‘’「」《》〈〉（）()【】\[\]、，。,.;；:：·・\s_\-—]', '', value)
    return value.lower()

def text(node):
    return ' '.join(node.get_text(' ', strip=True).split()) if node else ''

def match_name(wiki_name, names):
    if wiki_name in NAME_ALIASES:
        return NAME_ALIASES[wiki_name]
    key = norm(wiki_name)
    exact = [name for name in names if norm(name) == key]
    if exact:
        return exact[0]
    candidates = []
    for name in names:
        other = norm(name)
        if len(key) >= 2 and (key in other or other in key):
            candidates.append((min(len(key), len(other)), name))
    if candidates:
        return max(candidates)[1]
    scored = sorted(((SequenceMatcher(None, key, norm(name)).ratio(), name) for name in names), reverse=True)
    if scored and scored[0][0] >= 0.72:
        return scored[0][1]
    return None

data = json.load(open(DATA_FILE, encoding='utf-8'))
items = data['items']
names = [item['name'] for item in items]
museum_names = {norm(item['museum']): item['museum'] for item in items}
soup = BeautifulSoup(fetch_page(), 'html.parser')
imports = {}
unmatched = []

for table in soup.find_all('table'):
    table_rows = table.find_all('tr')
    header_row = next((row for row in table_rows[:4]
                       if '名称' in [text(cell) for cell in row.find_all(['th', 'td'], recursive=False)]
                       and '图片' in [text(cell) for cell in row.find_all(['th', 'td'], recursive=False)]), None)
    if not header_row:
        continue
    headers = [text(cell) for cell in header_row.find_all(['th', 'td'], recursive=False)]
    current_museum = None
    for row in table_rows[table_rows.index(header_row) + 1:]:
        cells = row.find_all(['th', 'td'], recursive=False)
        if not cells:
            continue
        title_link = cells[0].find('a', href=re.compile(r'^/wiki/'))
        wiki_name = text(cells[0])
        item_name = match_name(wiki_name, names)
        if not item_name:
            unmatched.append(wiki_name)
            continue

        links = row.find_all('a', href=re.compile(r'^/wiki/'))
        linked_museum = None
        linked_museum_url = None
        for link in links:
            candidate = text(link)
            if norm(candidate) in museum_names:
                linked_museum = museum_names[norm(candidate)]
                linked_museum_url = WIKI_BASE + link.get('href', '')
                break
        if linked_museum:
            current_museum = linked_museum

        file_link = row.find('a', class_='mw-file-description')
        file_name = None
        if file_link:
            href = file_link.get('href', '')
            if '/wiki/File:' in href:
                file_name = urllib.parse.unquote(href.split('/wiki/File:', 1)[1])

        record = {'wikiName': wiki_name,
                  'wikiUrl': WIKI_BASE + title_link.get('href', '') if title_link else ''}
        if current_museum:
            record['museum'] = current_museum
        if linked_museum_url:
            record['museumWikiUrl'] = linked_museum_url
        if file_name:
            record['file'] = file_name
            encoded = urllib.parse.quote(file_name.replace(' ', '_'), safe='')
            record['image'] = f'https://commons.wikimedia.org/wiki/Special:FilePath/{encoded}?width=960'
            record['imageCredit'] = {
                'license': '见 Commons 文件页',
                'author': '',
                'authorSrc': '',
                'page': COMMONS_BASE + urllib.parse.quote(file_name.replace(' ', '_'), safe=''),
                'source': 'Wikimedia Commons',
                'qid': ''
            }
        imports[item_name] = record

json.dump({'source': 'https://zh.wikipedia.org/wiki/禁止出境展览文物',
           'items': imports, 'unmatchedWikiNames': unmatched},
          open(OUTPUT_FILE, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('wiki rows:', len(imports), 'with images:', sum('image' in x for x in imports.values()))
print('unmatched wiki names:', len(unmatched))
