# -*- coding: utf-8 -*-
import sys, json, os
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
from wdpipe import resolve, save, cache

OUT = os.path.dirname(_HERE)          # 工作区根目录（tools 的上一级）
data=json.load(open(os.path.join(OUT,'195件永禁文物.json'),encoding='utf-8'))
names=[it['name'] for it in data['items']]
print('待处理:', len(names), '已有缓存:', len(cache), flush=True)

# 常见别名补充：官方目录名与 Wikidata 条目标签不完全一致时用得上
ALIAS={
 '良渚玉琮王':('Jade cong','Liangzhu Jade Cong','玉琮'),
 '长信宫灯':('Changxin Palace Lantern','Gilt Bronze Human-Shaped Lamp'),
 '河姆渡出土朱漆碗':('Hemudu lacquer bowl','河姆渡遗址'),
 '河姆渡出土陶灶':('Hemudu pottery stove','河姆渡遗址'),
 '新石器时代河姆渡文化双鸟朝阳纹象牙雕刻器':('Hemudu ivory carving','双鸟朝阳'),
 '新石器时代良渚文化神人兽面纹玉钺':('Liangzhu jade yue','良渚玉钺'),
 '战国水晶杯':('Warring States crystal cup','杭州博物馆'),
 '越窑青釉褐彩云纹五足炉':('Yue ware incense burner','越窑'),
}
done=0
for n in names:
    r=resolve(n, ALIAS.get(n, ()))
    done+=1
    if done % 10 == 0: print(f'  {done}/{len(names)}  命中 {sum(1 for v in cache.values() if v)}', flush=True)
save()
hit=sum(1 for v in cache.values() if v)
print(f'完成。缓存 {len(cache)} 条，命中 {hit} 条', flush=True)
