# -*- coding: utf-8 -*-
"""补抓：缓存里 artist 为空或残留 HTML 的条目，重取一次署名

署名回退链：Artist → Attribution → Credit → 上传者（见 wdpipe.commons_info）。
CC BY-SA 要求署名，Artist 为空时不能就这么留白。
"""
import sys, os
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
from wdpipe import commons_info, save, cache

bad = [k for k, v in cache.items() if v and v.get('info') and
       (not v['info'].get('artist') or v['info']['artist'].startswith('<'))]
print('待补抓:', len(bad), flush=True)

fixed = 0
for k in bad:
    fname = cache[k]['info'].get('file')
    if not fname:
        continue
    info = commons_info(fname)
    if info and info.get('artist'):
        cache[k]['info']['artist'] = info['artist']
        cache[k]['info']['artistSrc'] = info.get('artistSrc', '')
        cache[k]['info']['license'] = info.get('license') or cache[k]['info'].get('license', '')
        fixed += 1
        src = info.get('artistSrc') or ''
        mark = '' if src == 'artist' else f' [{src}]'
        print('  ✓', k, '→', info['artist'][:40] + mark, flush=True)
    else:
        print('  ✗', k, '（Commons 上确实没有署名信息）', flush=True)

save()
print(f'补抓完成 {fixed}/{len(bad)}', flush=True)
