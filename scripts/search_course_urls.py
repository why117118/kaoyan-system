"""
课程 URL 搜索脚本
功能：
  1. 从 courses 表读取 url 为空的课程
  2. 用 ddgs (DuckDuckGo) 搜索 "课程名 site:xuetangx.com" 或 "课程名 site:icourse163.org"
  3. 取第一个匹配结果写入 courses.url
  4. 每 50 条批量写入一次数据库（断点续传）
  5. 随机 sleep 防反爬
"""

import time
import random
import re
import pymysql
from ddgs import DDGS

# ────────── 配置 ──────────
DB_HOST = "localhost"
DB_USER = "root"
DB_PASS = "123456"
DB_NAME = "grad_project"
BATCH_SIZE = 50          # 每多少条写入一次
MIN_SLEEP  = 4           # 最短等待秒数
MAX_SLEEP  = 10          # 最长等待秒数
# ──────────────────────────

# 优先域名列表（按优先级排序）
PREFERRED_DOMAINS = [
    "xuetangx.com",
    "icourse163.org",
]


def get_connection():
    return pymysql.connect(
        host=DB_HOST, user=DB_USER, password=DB_PASS,
        database=DB_NAME, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def load_pending_courses(conn):
    """读取 url 为空 / NULL 的课程列表（断点续传的关键）"""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT course_index, name FROM courses "
            "WHERE url IS NULL OR url = '' "
            "ORDER BY course_index"
        )
        return cur.fetchall()


def pick_best_url(results: list[dict]) -> str:
    """
    从搜索结果中选出最佳 URL。
    优先选 xuetangx.com 的课程页面，其次 icourse163.org，
    排除非课程页面（如首页、频道页）。
    """
    # 排除的 URL 模式（首页、频道页等非课程页面）
    exclude_patterns = [
        re.compile(r"^https?://(www\.)?xuetangx\.com/?$"),
        re.compile(r"^https?://(www\.)?icourse163\.org/?$"),
        re.compile(r"icourse163\.org/channel/"),
    ]

    for domain in PREFERRED_DOMAINS:
        for r in results:
            href = r.get("href", "")
            if domain not in href:
                continue
            # 跳过非课程页面
            if any(p.search(href) for p in exclude_patterns):
                continue
            return href

    # 兜底：返回第一个非排除链接
    for r in results:
        href = r.get("href", "")
        if any(p.search(href) for p in exclude_patterns):
            continue
        if href:
            return href
    return ""


def search_course_url(course_name: str) -> str:
    """
    用 DuckDuckGo 搜索课程在学堂在线或中国大学 MOOC 上的链接。
    策略：
      1. 先搜 "课程名 site:xuetangx.com"
      2. 如果没找到，再搜 "课程名 site:icourse163.org"
      3. 如果还是没有，搜 "课程名 在线课程 MOOC"
    """
    strategies = [
        f"{course_name} site:xuetangx.com",
        f"{course_name} site:icourse163.org",
        f"{course_name} 学堂在线 OR 中国大学MOOC",
    ]

    for query in strategies:
        try:
            results = DDGS().text(query, max_results=5)
            if results:
                url = pick_best_url(results)
                if url:
                    return url
        except Exception as e:
            print(f"  [WARN] 搜索 '{query}' 失败: {e}")
            # 如果被限流，等久一点
            time.sleep(random.uniform(10, 20))

    return ""


def flush_batch(conn, batch: list[tuple]):
    """将 (url, course_index) 批量写入数据库"""
    if not batch:
        return
    with conn.cursor() as cur:
        cur.executemany(
            "UPDATE courses SET url = %s WHERE course_index = %s",
            batch,
        )
    conn.commit()
    found = sum(1 for url, _ in batch if url)
    print(f"  ✔ 已写入 {len(batch)} 条到数据库（找到URL: {found}, 未找到: {len(batch) - found}）")


def main():
    conn = get_connection()
    courses = load_pending_courses(conn)
    total = len(courses)
    print(f"共 {total} 门课程待搜索\n")

    if total == 0:
        print("所有课程已有 URL，无需搜索。")
        conn.close()
        return

    batch: list[tuple] = []   # [(url, course_index), ...]
    done = 0
    found_count = 0

    for idx, course in enumerate(courses, 1):
        cid = course["course_index"]
        name = course["name"]
        print(f"[{idx}/{total}] course_index={cid}  {name}")

        url = search_course_url(name)
        if url:
            print(f"  → {url}")
            found_count += 1
        else:
            print(f"  → (未找到)")

        batch.append((url, cid))
        done += 1

        # 每 BATCH_SIZE 条写入一次
        if done % BATCH_SIZE == 0:
            flush_batch(conn, batch)
            batch.clear()
            print(f"  --- 进度: {done}/{total}, 已找到URL: {found_count} ---\n")

        # 随机等待
        delay = random.uniform(MIN_SLEEP, MAX_SLEEP)
        time.sleep(delay)

    # 收尾：把剩余不满一批的写入
    flush_batch(conn, batch)
    batch.clear()

    conn.close()
    print(f"\n全部完成！共处理 {done} 门课程，找到URL: {found_count}，未找到: {done - found_count}。")


if __name__ == "__main__":
    main()
