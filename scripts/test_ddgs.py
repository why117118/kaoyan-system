"""Test ddgs (new package name) for searching course URLs"""
from ddgs import DDGS

keyword = "心理学概论"

print(f"Test 1: {keyword} site:xuetangx.com")
try:
    results = DDGS().text(f"{keyword} site:xuetangx.com", max_results=5)
    print(f"Got {len(results)} results:")
    for r in results:
        print(f"  {r['href']}  |  {r['title']}")
except Exception as e:
    print(f"Error: {e}")

print(f"\nTest 2: {keyword} 学堂在线 MOOC")
try:
    results = DDGS().text(f"{keyword} 学堂在线 MOOC", max_results=5)
    print(f"Got {len(results)} results:")
    for r in results:
        print(f"  {r['href']}  |  {r['title']}")
except Exception as e:
    print(f"Error: {e}")

print(f"\nTest 3: {keyword} 在线课程")
try:
    results = DDGS().text(f"{keyword} 在线课程", max_results=5)
    print(f"Got {len(results)} results:")
    for r in results:
        print(f"  {r['href']}  |  {r['title']}")
except Exception as e:
    print(f"Error: {e}")
