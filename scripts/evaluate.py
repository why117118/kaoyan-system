"""
evaluate.py  —  Evaluate the recommendation model (Precision@K, Recall@K, NDCG@K).
"""
import sys
import os
import math
import random
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'backend-flask'))

from app.db import get_conn
from app.cf import get_cached_recommender

def evaluate(top_k=10, max_users=500):
    rec = get_cached_recommender()
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute('SELECT DISTINCT stu_id FROM interactions')
        all_users = [r['stu_id'] for r in cur.fetchall()]
    finally:
        conn.close()

    sample = random.sample(all_users, min(max_users, len(all_users)))
    precisions, recalls, ndcgs = [], [], []

    for uid in sample:
        actual = set(rec.user_items.get(uid, {}).keys())
        if not actual:
            continue
        preds = rec.recommend(uid, top_k)
        pred_set = set(r['course_index'] for r in preds)
        hits = actual & pred_set
        p = len(hits) / top_k
        r = len(hits) / len(actual) if actual else 0
        dcg = sum(1.0 / math.log2(i + 2) for i, pr in enumerate(preds) if pr['course_index'] in actual)
        idcg = sum(1.0 / math.log2(i + 2) for i in range(min(len(actual), top_k)))
        ndcg = dcg / idcg if idcg > 0 else 0.0
        precisions.append(p)
        recalls.append(r)
        ndcgs.append(ndcg)

    print(f"Evaluated {len(precisions)} users (top_k={top_k})")
    print(f"  Precision@{top_k}: {sum(precisions)/len(precisions):.4f}" if precisions else "  No data")
    print(f"  Recall@{top_k}:    {sum(recalls)/len(recalls):.4f}" if recalls else "  No data")
    print(f"  NDCG@{top_k}:      {sum(ndcgs)/len(ndcgs):.4f}" if ndcgs else "  No data")

if __name__ == '__main__':
    k = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    evaluate(k)
