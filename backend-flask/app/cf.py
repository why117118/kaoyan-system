"""
Hybrid Recommender: time-aware collaborative filtering + matrix factorization
"""
import math
import random
import numpy as np
import pandas as pd
from collections import defaultdict

# --------------- helpers ---------------

def _time_weight(ts_series, half_life_days=90):
    """Exponential time decay weights."""
    if ts_series.empty:
        return pd.Series(dtype=float)
    max_ts = ts_series.max()
    delta = (max_ts - ts_series).dt.total_seconds() / 86400.0
    return np.exp(-np.log(2) * delta / half_life_days)

# --------------- Matrix Factorization ---------------

class MatrixFactorization:
    """SGD-based matrix factorization with negative sampling."""

    def __init__(self, n_factors=32, lr=0.02, reg=0.01, epochs=15, neg_samples=3):
        self.n_factors = n_factors
        self.lr = lr
        self.reg = reg
        self.epochs = epochs
        self.neg_samples = neg_samples
        self.user_factors = None
        self.item_factors = None

    def fit(self, user_ids, item_ids, weights=None):
        users = sorted(set(user_ids))
        items = sorted(set(item_ids))
        self.u_map = {u: i for i, u in enumerate(users)}
        self.i_map = {it: i for i, it in enumerate(items)}
        nu, ni = len(users), len(items)
        scale = 1.0 / math.sqrt(self.n_factors)
        self.user_factors = np.random.normal(0, scale, (nu, self.n_factors)).astype(np.float32)
        self.item_factors = np.random.normal(0, scale, (ni, self.n_factors)).astype(np.float32)

        positives = defaultdict(set)
        triples = []
        for u, it in zip(user_ids, item_ids):
            ui, ii = self.u_map[u], self.i_map[it]
            positives[ui].add(ii)
            triples.append((ui, ii))

        all_items = list(range(ni))
        if weights is None:
            weights = [1.0] * len(triples)

        for epoch in range(self.epochs):
            lr = self.lr * (1.0 - epoch / self.epochs)   # linear decay
            random.shuffle(triples)
            for idx, (ui, ii) in enumerate(triples):
                w = weights[idx] if idx < len(weights) else 1.0
                self._update(ui, ii, 1.0, w, lr)
                for _ in range(self.neg_samples):
                    ni_neg = random.choice(all_items)
                    if ni_neg not in positives[ui]:
                        self._update(ui, ni_neg, 0.0, w * 0.3, lr)

    def _update(self, ui, ii, label, w, lr):
        pu = self.user_factors[ui]
        qi = self.item_factors[ii]
        pred = float(pu @ qi)
        err = (label - pred) * w
        self.user_factors[ui] += lr * (err * qi - self.reg * pu)
        self.item_factors[ii] += lr * (err * pu - self.reg * qi)

    def predict(self, u, item_list):
        if u not in self.u_map:
            return {it: 0.0 for it in item_list}
        ui = self.u_map[u]
        pu = self.user_factors[ui]
        scores = {}
        for it in item_list:
            if it in self.i_map:
                scores[it] = float(pu @ self.item_factors[self.i_map[it]])
            else:
                scores[it] = 0.0
        return scores

# --------------- Hybrid Recommender ---------------

MAX_INTERACTIONS = 100000   # sample to keep training fast

class HybridRecommender:
    """Time-aware CF (cosine sim + sliding window) + MF blend."""

    def __init__(self, df, half_life_days=90, window_months=6, min_common=2, mf_weight=0.4):
        self.half_life_days = half_life_days
        self.window_months = window_months
        self.min_common = min_common
        self.mf_weight = mf_weight

        df = df.copy()
        df['time'] = pd.to_datetime(df['time'], errors='coerce')
        df.dropna(subset=['time'], inplace=True)

        # Sample most recent interactions for speed
        if len(df) > MAX_INTERACTIONS:
            df = df.sort_values('time', ascending=False).head(MAX_INTERACTIONS).reset_index(drop=True)

        df['weight'] = _time_weight(df['time'], half_life_days)
        self.df = df

        # user-item weighted interaction
        self.user_items = defaultdict(dict)
        for _, row in df.iterrows():
            uid = row['stu_id']
            cid = row['course_index']
            w = row['weight']
            self.user_items[uid][cid] = self.user_items[uid].get(cid, 0) + w

        # course meta
        self.course_meta = {}
        for _, row in df.drop_duplicates('course_index').iterrows():
            self.course_meta[row['course_index']] = {
                'name': row.get('name', ''),
                'type': row.get('type', ''),
                'type_id': row.get('type_id', ''),
            }

        # MF
        self.mf = MatrixFactorization()
        self.mf.fit(df['stu_id'].tolist(), df['course_index'].tolist(), df['weight'].tolist())

    def _cosine_sim(self, u1, u2):
        items1 = self.user_items.get(u1, {})
        items2 = self.user_items.get(u2, {})
        common = set(items1) & set(items2)
        if len(common) < self.min_common:
            return 0.0
        dot = sum(items1[c] * items2[c] for c in common)
        n1 = math.sqrt(sum(v ** 2 for v in items1.values()))
        n2 = math.sqrt(sum(v ** 2 for v in items2.values()))
        if n1 == 0 or n2 == 0:
            return 0.0
        sim = dot / (n1 * n2)
        sim *= min(len(common), 10) / 10.0
        return sim

    def recommend(self, user_id, top_n=10, exclude_seen=True):
        user_courses = set(self.user_items.get(user_id, {}).keys())
        all_courses = set(self.course_meta.keys())
        if exclude_seen:
            candidates = all_courses - user_courses
            if not candidates:
                candidates = all_courses
        else:
            candidates = all_courses

        # Cold-start: user not in training data → return popular courses
        if user_id not in self.user_items:
            return self._popular_fallback(candidates, top_n)

        # CF scores
        sims = {}
        for other in self.user_items:
            if other == user_id:
                continue
            s = self._cosine_sim(user_id, other)
            if s > 0:
                sims[other] = s

        cf_scores = defaultdict(float)
        for other, sim in sorted(sims.items(), key=lambda x: -x[1])[:50]:
            for cid, w in self.user_items[other].items():
                if cid in candidates:
                    cf_scores[cid] += sim * w

        # normalize cf_scores to [0,1]
        max_cf = max(cf_scores.values()) if cf_scores else 1.0
        if max_cf > 0:
            for cid in cf_scores:
                cf_scores[cid] /= max_cf

        # MF scores
        mf_scores = self.mf.predict(user_id, list(candidates))
        max_mf = max(mf_scores.values()) if mf_scores else 1.0
        min_mf = min(mf_scores.values()) if mf_scores else 0.0
        rng = max_mf - min_mf if max_mf != min_mf else 1.0
        for cid in mf_scores:
            mf_scores[cid] = (mf_scores[cid] - min_mf) / rng

        # blend
        combined = {}
        for cid in candidates:
            cf = cf_scores.get(cid, 0.0)
            mf = mf_scores.get(cid, 0.0)
            combined[cid] = (1 - self.mf_weight) * cf + self.mf_weight * mf

        top = sorted(combined.items(), key=lambda x: -x[1])[:top_n]
        results = []
        for cid, score in top:
            meta = self.course_meta.get(cid, {})
            results.append({
                'course_index': cid,
                'name': meta.get('name', ''),
                'type_name': meta.get('type', ''),
                'type_id': meta.get('type_id', ''),
                'predicted_score': round(score, 4),
            })
        return results

    def _popular_fallback(self, candidates, top_n):
        """Cold-start fallback: recommend most popular courses."""
        popularity = defaultdict(float)
        for uid_items in self.user_items.values():
            for cid, w in uid_items.items():
                if cid in candidates:
                    popularity[cid] += w
        top = sorted(popularity.items(), key=lambda x: -x[1])[:top_n]
        results = []
        for cid, score in top:
            meta = self.course_meta.get(cid, {})
            results.append({
                'course_index': cid,
                'name': meta.get('name', ''),
                'type_name': meta.get('type', ''),
                'type_id': meta.get('type_id', ''),
                'predicted_score': round(score, 4),
            })
        return results

# --------------- Cached singleton with TTL ---------------

import time as _time
import threading

_recommender = None
_recommender_ts = 0
_lock = threading.Lock()
_TTL = 600  # rebuild every 10 minutes

def get_cached_recommender():
    """Return a cached recommender; rebuild only if TTL expired or first call."""
    global _recommender, _recommender_ts
    now = _time.time()
    if _recommender is not None and (now - _recommender_ts) < _TTL:
        return _recommender
    with _lock:
        # double-check inside lock
        if _recommender is not None and (_time.time() - _recommender_ts) < _TTL:
            return _recommender
        from .db import get_engine
        engine = get_engine()
        df = pd.read_sql(
            'SELECT i.stu_id, i.time, i.course_index, c.name, c.type, c.type_id '
            'FROM interactions i JOIN courses c ON i.course_index = c.course_index',
            engine
        )
        if df.empty:
            df = pd.read_sql('SELECT stu_id, time, course_index FROM interactions', engine)
        _recommender = HybridRecommender(df)
        _recommender_ts = _time.time()
        return _recommender

def invalidate_cache():
    """Force rebuild on next request."""
    global _recommender, _recommender_ts
    with _lock:
        _recommender = None
        _recommender_ts = 0
