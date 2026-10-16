# -*- coding: utf-8 -*-
"""共用：文物分类示意图（内联 SVG）+ 博物馆官方入口元数据"""

# ---------- 分类示意图 ----------
# 统一 120x120 viewBox、描边风格；返回 (body_svg, accent_color)
CAT_ART = {
    "青铜器": ("#5c6b5a",
        '<rect x="30" y="42" width="60" height="30" rx="4"/>'
        '<path d="M40 42V32a7 7 0 0 1 14 0v10M66 42V32a7 7 0 0 1 14 0v10"/>'
        '<path d="M42 72v20M78 72v20M60 72v20"/>'
        '<path d="M30 52h60"/>'),
    "陶瓷": ("#8a7a63",
        '<path d="M52 26h16v14c10 5 16 14 16 25v19c0 9-9 15-24 15s-24-6-24-15V65c0-11 6-20 16-25z"/>'
        '<path d="M46 26h28"/>'),
    "陶器": ("#94795c",
        '<path d="M48 32h24v9c13 5 19 15 19 27v9c0 11-11 17-31 17s-31-6-31-17v-9c0-12 6-22 19-27z"/>'
        '<path d="M44 32h32"/>'),
    "玉器": ("#6e8a7d",
        '<rect x="34" y="28" width="52" height="64" rx="3"/>'
        '<circle cx="60" cy="60" r="13"/>'
        '<path d="M34 42h52M34 78h52"/>'),
    "书画": ("#8a6a52",
        '<rect x="30" y="34" width="60" height="52" rx="2"/>'
        '<circle cx="25" cy="60" r="6"/><circle cx="95" cy="60" r="6"/>'
        '<path d="M42 50h36M42 62h36M42 74h24"/>'),
    "漆木器": ("#7a5340",
        '<ellipse cx="60" cy="56" rx="34" ry="10"/>'
        '<path d="M26 56c0 15 15 26 34 26s34-11 34-26"/>'
        '<ellipse cx="60" cy="56" rx="20" ry="5.5"/>'),
    "丝织刺绣": ("#9a6b7a",
        '<rect x="30" y="32" width="60" height="56" rx="4"/>'
        '<path d="M30 50h60M30 70h60M47 32v56M73 32v56"/>'),
    "壁画石刻": ("#6f6f76",
        '<path d="M40 46a20 15 0 0 1 40 0v36H40z"/>'
        '<rect x="32" y="82" width="56" height="10" rx="3"/>'
        '<path d="M50 52h20M50 62h20M50 72h13"/>'),
    "金银器": ("#a9803a",
        '<path d="M44 40h24v12c9 5 13 12 13 21v17c0 7-7 12-19 12H50c-12 0-19-5-19-12V73c0-9 4-16 13-21z"/>'
        '<path d="M44 34h24"/>'
        '<path d="M81 50c12 2 15 10 15 20s-3 16-13 18"/>'),
    "文献杂项": ("#7a6a4a",
        '<rect x="32" y="30" width="9" height="60" rx="2"/>'
        '<rect x="46" y="30" width="9" height="60" rx="2"/>'
        '<rect x="60" y="30" width="9" height="60" rx="2"/>'
        '<rect x="74" y="30" width="9" height="60" rx="2"/>'
        '<path d="M30 48h56M30 72h56"/>'),
    "画像砖": ("#75706a",
        '<rect x="26" y="40" width="68" height="46" rx="3"/>'
        '<circle cx="47" cy="57" r="7"/>'
        '<path d="M38 80v-8a9 9 0 0 1 18 0v8"/>'
        '<path d="M68 54h18M68 64h18M68 74h12"/>'),
}
CAT_FALLBACK = ("#7a6a4a",
    '<rect x="30" y="34" width="60" height="52" rx="6"/>'
    '<path d="M42 54h36M42 66h24"/>')


def art_svg(cat, size=96):
    color, body = CAT_ART.get(cat, CAT_FALLBACK)
    return ('<svg viewBox="0 0 120 120" width="' + str(size) + '" height="' + str(size) + '" '
            'fill="none" stroke="' + color + '" stroke-width="2.4" stroke-linejoin="round" '
            'stroke-linecap="round" aria-hidden="true">' + body + '</svg>')


def art_color(cat):
    return CAT_ART.get(cat, CAT_FALLBACK)[0]


# ---------- 博物馆官方入口 ----------
# url: 已实测可访问（200）；带 block 标记的为服务器存在但拦截自动请求（403/412）
MUSEUM_SITES = {
    "中国国家博物馆": "https://www.chnmuseum.cn",
    "故宫博物院": "https://www.dpm.org.cn",
    "上海博物馆": "https://www.shanghaimuseum.net",
    "南京博物院": "https://www.njmuseum.com",
    "浙江省博物馆": "https://www.zhejiangmuseum.com",
    "湖南博物院": "https://www.hnmuseum.com",
    "湖北省博物馆": "http://www.hbww.org",
    "陕西历史博物馆": "https://www.sxhm.com",
    "辽宁省博物馆": "https://www.lnmuseum.com.cn",
    "山西博物院": "https://www.shanximuseum.com",
    "河北博物院": "https://www.hebeimuseum.org.cn",
    "河南博物院": "https://www.chnmus.net",
    "三星堆博物馆": "https://www.sxd.cn",
    "成都金沙遗址博物馆": "https://www.jinshasitemuseum.com",
    "甘肃省博物馆": "https://www.gansumuseum.com",
    "安徽博物院": "https://www.ahm.cn",
    "首都博物馆": "https://www.capitalmuseum.org.cn",
    "天津博物馆": "https://www.tjbwg.cn",
    "苏州博物馆": "https://www.szmuseum.com",
    "杭州博物馆": "https://hz-museum.cn",
    "秦始皇帝陵博物院": "https://www.bmy.com.cn",
    "西安碑林博物馆": "https://www.beilin-museum.com",
    "云南省博物馆": "http://www.ynbwg.cn",
    "贵州省博物馆": "https://www.gzmuseum.com",
    "江西省博物馆": "https://www.jxmuseum.cn",
    "宁夏博物馆": "https://www.nxbwg.com",
    "青海省博物馆": "https://www.qhmuseum.com",
    "洛阳博物馆": "http://www.lymuseum.com",
    "吉林省博物院": "https://jlmuseum.net",
    "山东博物馆": "https://www.sdmuseum.com",
    "宝鸡青铜器博物院": "https://www.bjqtm.com",
    "镇江博物馆": "https://www.zjmuseum.com.cn",
    "敦煌研究院": "https://www.dha.ac.cn",
    "中国国家图书馆": "https://www.nlc.cn",
    "清华大学艺术博物馆": "https://www.artmuseum.tsinghua.edu.cn",
    "周原博物馆": "http://www.zhouyuanmuseum.com",
    "法门寺博物馆": "http://www.famensi.com",
    "淄博市博物馆": "http://www.zbsbwg.com",
}
# 以下馆未找到可靠官网，统一引导「微信搜一搜」：
# 中国社会科学院考古研究所 / 南越王博物院 / 新疆维吾尔自治区博物馆 / 新疆维吾尔自治区文物考古研究所
# 西藏博物馆 / 定州市博物馆 / 大同市博物馆 / 马鞍山市博物馆 / 绵阳市博物馆 / 淳化县博物馆
# 茂陵博物馆 / 荆门市博物馆 / 安徽文物考古研究所 / 辽宁省文物考古研究院 / 扬州博物馆
# 西安博物院 / 南京市博物总馆 / 杭州市临安区博物馆 / 南京大学 / 清华大学 / 湖南大学


def wechat_name(museum):
    """微信搜一搜的关键词：去掉「市」等冗余，贴近公众号命名习惯"""
    return museum


def site_of(museum):
    return MUSEUM_SITES.get(museum)
