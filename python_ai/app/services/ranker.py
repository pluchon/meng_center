from __future__ import annotations

from typing import Any

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from app.schemas.models import TrainingRequest


def train(request: TrainingRequest) -> dict[str, Any]:
    samples = sorted(request.samples, key=lambda item: item.occurred_at)
    if len(samples) < 5000 or sum(item.clicked for item in samples) < 200:
        return {"eligible": False, "sampleCount": len(samples), "clickCount": sum(s.clicked for s in samples)}
    dimensions = len(samples[0].features)
    if any(len(s.features) != dimensions or not all(np.isfinite(s.features)) for s in samples):
        raise ValueError("Invalid feature shape")
    features = np.array([s.features for s in samples])
    labels = np.array([int(s.clicked) for s in samples])
    split = int(len(samples) * 0.8)
    if len(set(labels[:split])) != 2 or len(set(labels[split:])) != 2:
        return {"eligible": False, "sampleCount": len(samples), "clickCount": int(labels.sum())}
    pipeline = make_pipeline(StandardScaler(), LogisticRegression(max_iter=500, random_state=42))
    pipeline.fit(features[:split], labels[:split])
    auc = roc_auc_score(labels[split:], pipeline.predict_proba(features[split:])[:, 1])
    scaler, model = pipeline.steps[0][1], pipeline.steps[1][1]
    weights = model.coef_[0] / scaler.scale_
    intercept = model.intercept_[0] - np.dot(weights, scaler.mean_)
    return {
        "eligible": True,
        "weights": weights.tolist(),
        "intercept": float(intercept),
        "auc": float(auc),
        "sampleCount": len(samples),
        "clickCount": int(labels.sum()),
    }
