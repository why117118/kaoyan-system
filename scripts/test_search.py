"""快速测试: 百度搜索 + 学堂在线直接搜索"""
import requests, re, json

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

def test_baidu(keyword):
    """用百度搜索课程链接"""
    print(f"\n=== 百度搜索: {keyword} ===")
    r = requests.get("https://www.baidu.com/s",
        params={"wd": f"{keyword} site:xuetangx.com OR site:icourse163.org"},
        headers={"User-Agent": UA}, timeout=10)
    print(f"Status: {r.status_code}")
    # 直接链接
    links1 = re.findall(r'href="(https?://www\.xuetangx\.com/[^"]+)"', r.text)
    links2 = re.findall(r'href="(https?://www\.icourse163\.org/[^"]+)"', r.text)
    print(f"xuetangx links: {links1[:3]}")
    print(f"icourse163 links: {links2[:3]}")
    # baidu redirect links
    blinks = re.findall(r'href="(https?://www\.baidu\.com/link\?[^"]+)"', r.text)
    print(f"Baidu redirect links: {len(blinks)}")
    return links1 + links2

def test_bing(keyword):
    """用Bing搜索课程链接"""
    print(f"\n=== Bing搜索: {keyword} ===")
    r = requests.get("https://www.bing.com/search",
        params={"q": f"{keyword} site:xuetangx.com OR site:icourse163.org"},
        headers={"User-Agent": UA}, timeout=10)
    print(f"Status: {r.status_code}")
    links1 = re.findall(r'href="(https?://www\.xuetangx\.com/[^"]+)"', r.text)
    links2 = re.findall(r'href="(https?://www\.icourse163\.org/[^"]+)"', r.text)
    print(f"xuetangx links: {links1[:3]}")
    print(f"icourse163 links: {links2[:3]}")
    return links1 + links2

def test_xuetangx_direct(keyword):
    """直接用学堂在线搜索页面"""
    print(f"\n=== 学堂在线搜索: {keyword} ===")
    import urllib.parse
    url = f"https://www.xuetangx.com/search?query={urllib.parse.quote(keyword)}"
    r = requests.get(url, headers={"User-Agent": UA}, timeout=10)
    print(f"Status: {r.status_code}, Length: {len(r.text)}")
    # 看看页面里是否有课程链接
    links = re.findall(r'"/course/([^"]+)"', r.text)
    print(f"Course slugs: {links[:5]}")
    return links

keyword = "心理学概论"
test_baidu(keyword)
test_bing(keyword)
test_xuetangx_direct(keyword)
