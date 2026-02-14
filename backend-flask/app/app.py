from flask import Flask, request, jsonify
from flask_cors import CORS
import math
import traceback

app = Flask(__name__)
CORS(app)

def _recommender():
    from .cf import get_cached_recommender
    return get_cached_recommender()

@app.route('/health')
def health():
    return jsonify({'status': 'ok'})

@app.route('/api/recommend')
def recommend():
    try:
        user_id = request.args.get('userId', type=str)
        top_n = request.args.get('topN', 10, type=int)
        if user_id is None:
            return jsonify({'error': 'userId required'}), 400
        rec = _recommender()
        results = rec.recommend(user_id, top_n)
        return jsonify({'recommendations': results})
    except Exception as e:
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500

@app.route('/api/reload', methods=['POST'])
def reload_recommender():
    """Force rebuild the recommender model."""
    try:
        from .cf import invalidate_cache
        invalidate_cache()
        _recommender()  # trigger rebuild now
        return jsonify({'status': 'ok'})
    except Exception as e:
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500

@app.route('/api/evaluate')
def evaluate():
    try:
        top_k = request.args.get('topK', 10, type=int)
        max_users = request.args.get('maxUsers', 500, type=int)
        rec = _recommender()
        import random

        # Pick users with at least 3 interactions for meaningful evaluation
        eligible = [uid for uid, items in rec.user_items.items() if len(items) >= 3]
        sample = random.sample(eligible, min(max_users, len(eligible))) if eligible else []
        precisions, recalls, ndcgs = [], [], []

        for uid in sample:
            actual = set(rec.user_items.get(uid, {}).keys())
            if not actual:
                continue
            # Get recommendations including seen courses to test ranking quality
            preds = rec.recommend(uid, top_k, exclude_seen=False)
            pred_set = set(r['course_index'] for r in preds)
            hits = actual & pred_set
            p = len(hits) / top_k if top_k else 0
            r_val = len(hits) / min(len(actual), top_k) if actual else 0
            # NDCG
            dcg = 0.0
            for i, pr in enumerate(preds):
                if pr['course_index'] in actual:
                    dcg += 1.0 / math.log2(i + 2)
            idcg = sum(1.0 / math.log2(i + 2) for i in range(min(len(actual), top_k)))
            ndcg = dcg / idcg if idcg > 0 else 0.0
            precisions.append(p)
            recalls.append(r_val)
            ndcgs.append(ndcg)

        result = {
            f'Precision@{top_k}': round(sum(precisions) / len(precisions), 4) if precisions else 0,
            f'Recall@{top_k}': round(sum(recalls) / len(recalls), 4) if recalls else 0,
            f'NDCG@{top_k}': round(sum(ndcgs) / len(ndcgs), 4) if ndcgs else 0,
            'evaluated_users': len(precisions),
        }
        return jsonify(result)
    except Exception as e:
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500
