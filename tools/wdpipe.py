# -*- coding: utf-8 -*-
"""Wikidata P18 → Commons 图片流水线（限速 + 缓存 + 重试）"""
import json, os, time, urllib.parse, subprocess

_HERE = os.path.dirname(os.path.abspath(__file__))
PROXY='http://127.0.0.1:7890'
UA='MuseumExplorer/0.1 (personal study; contact: local)'
CACHE=os.path.join(_HERE, 'wd_cache.json')
LAST=[0.0]
cache = json.load(open(CACHE, encoding='utf-8')) if os.path.exists(CACHE) else {}

def get(url, tries=4):
    for i in range(tries):
        dt = time.time() - LAST[0]
        if dt < 1.3: time.sleep(1.3 - dt)          # 限速：约 0.77 req/s
        LAST[0] = time.time()
        r = subprocess.run(['curl','-s','-m','30','-x',PROXY,'-A',UA,url],
                           capture_output=True, text=True)
        txt = r.stdout.strip()
        if txt.startswith('{'):
            try: return json.loads(txt)
            except Exception: pass
        time.sleep(2.5 * (i + 1))                   # 命中限流：退避重试
    return {}

def save():
    json.dump(cache, open(CACHE,'w',encoding='utf-8'), ensure_ascii=False, indent=1)

def wd_search(name, lang='zh'):
    d = get('https://www.wikidata.org/w/api.php?' + urllib.parse.urlencode({
        'action':'wbsearchentities','search':name,'language':lang,'uselang':lang,
        'format':'json','limit':'5','type':'item'}))
    return [(x['id'], x.get('label'), x.get('description','')) for x in d.get('search',[])]

def p18(qid):
    d = get('https://www.wikidata.org/w/api.php?' + urllib.parse.urlencode({
        'action':'wbgetentities','ids':qid,'props':'claims|labels|descriptions',
        'languages':'zh|en','format':'json'}))
    ent=(d.get('entities') or {}).get(qid) or {}
    files=[]
    for c in (ent.get('claims') or {}).get('P18',[]):
        try: files.append(c['mainsnak']['datavalue']['value'])
        except Exception: pass
    lab=ent.get('labels') or {}
    return files, (lab.get('en') or {}).get('value'), (lab.get('zh') or {}).get('value')

def commons_info(fname):
    d = get('https://commons.wikimedia.org/w/api.php?' + urllib.parse.urlencode({
        'action':'query','format':'json','titles':'File:'+fname,
        'prop':'imageinfo','iiprop':'url|extmetadata|size|user','iiurlwidth':'900'}))
    pages=(d.get('query') or {}).get('pages') or {}
    for pg in pages.values():
        ii=(pg.get('imageinfo') or [{}])[0]
        if not ii: return None
        em=ii.get('extmetadata') or {}
        g=lambda k:(em.get(k) or {}).get('value')
        # Artist 字段常带 HTML，先清标签再截断，否则截在标签中间就再也洗不干净
        import re as _re, html as _h
        def cl(v, n=90):
            v = _h.unescape(_re.sub(r'<[^>]*>', ' ', v or ''))
            # Credit 字段常混入时间戳和 "Own work" 之类的说明，清掉
            v = _re.sub(r'\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?', ' ', v)
            v = _re.sub(r'\bOwn work\b', ' ', v, flags=_re.I)
            v = _re.sub(r'[,，]\s*(?=[,，])', ' ', v)      # 去掉连续逗号留下的空段
            v = ' '.join(v.split())
            v = v.strip(' ,，、')
            return v[:n]
        # 署名回退链：Artist → Attribution → Credit → 上传者。
        # CC BY-SA 要求署名，Artist 为空时不能就这么留白。
        art, src = '', ''
        for key, tag in (('Artist','artist'), ('Attribution','attribution'),
                         ('Credit','credit'), ('user','uploader')):
            raw = ii.get('user') if key == 'user' else g(key)
            v = cl(raw)
            if v and not v.isdigit():
                art, src = v, tag
                break
        return {'thumb':ii.get('thumburl'),'file':fname,
                'w':ii.get('width'),'h':ii.get('height'),
                'license':g('LicenseShortName') or '',
                'artist':art,'artistSrc':src,
                'page':'https://commons.wikimedia.org/wiki/File:'+urllib.parse.quote(fname.replace(' ','_'))}
    return None

def resolve(name, alt_names=()):
    """返回 {file,info,qid,matched} 或 None"""
    if name in cache:
        return cache[name]
    for q in (name,) + tuple(alt_names):
        for lang in ('zh','en'):
            cands = wd_search(q, lang)[:2]          # 只试前 2 个候选，控制请求量
            for qid, lab, desc in cands:
                if not qid: continue
                files, en, zh = p18(qid)
                if files:
                    info = commons_info(files[0])
                    if info:
                        cache[name] = {'qid':qid,'matched':lab,'en':en,
                                       'file':files[0],'info':info}
                        save(); return cache[name]
    cache[name] = None
    save(); return None
