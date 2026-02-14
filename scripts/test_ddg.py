"""Test DuckDuckGo search for course URLs"""
from duckduckgo_search import DDGS

keyword = "心理学概论"
query = f"{keyword} xuetangx.com OR icourse163.org"

print(f"Searching: {query}")
try:
    with DDGS() as ddgs:
        results = list(ddgs.text(query, max_results=5))
        print(f"Got {len(results)} results:")
        for r in results:
            print(f"  {r['href']}")
            print(f"    {r['title']}")
except Exception as e:
    print(f"Error: {e}")

# Also try plain search
print(f"\nPlain search: {keyword} 学堂在线")
try:
    with DDGS() as ddgs:
        results = list(ddgs.text(f"{keyword} 学堂在线", max_results=5))
        print(f"Got {len(results)} results:")
        for r in results:
            print(f"  {r['href']}")
            print(f"    {r['title']}")
except Exception as e:
    print(f"Error: {e}")
