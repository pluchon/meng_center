from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from app.config.settings import Settings
from evals.retrieval.calibrate import calibrate, dataset
from evals.retrieval.collect import collect

DEFAULTS = {"search": 0.35, "related": 0.4, "recall": 0.3}


def fixture():
    rows, digest = dataset()
    return {
        "format": "raw-rerank-v1",
        "datasetSha256": digest,
        "model": "offline-fixture",
        "instructions": dict.fromkeys(DEFAULTS, "offline fixture instruction"),
        "records": [
            {
                "id": row["id"],
                "scores": [0.8 if i in row["relevant"] else 0.2 for i in range(len(row["documents"]))],
                "reranked": True,
            }
            for row in rows
        ],
    }


def test_original_dataset_has_independent_scenes_and_held_out_queries():
    rows, digest = dataset()
    assert len(rows) == 36 and len(digest) == 64
    assert len({(row["scene"], row["query"]) for row in rows}) == len(rows)
    report = calibrate(fixture(), DEFAULTS, "offline-fixture")
    assert not report["configurationChanged"]
    assert all(row["status"] == "VALIDATED" for row in report["scenes"].values())


def test_dataset_identity_is_stable_across_line_endings(tmp_path):
    from evals.retrieval.calibrate import DATASET

    copied = tmp_path / "cases.jsonl"
    copied.write_bytes(DATASET.read_bytes().replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
    assert dataset(copied) == dataset()


@pytest.mark.parametrize(
    "corruption", ["missing", "duplicate", "fallback", "nan", "model", "dataset", "instructions"]
)
def test_missing_or_incompatible_scores_never_produce_a_threshold(corruption):
    archive = fixture()
    if corruption == "missing":
        archive["records"].pop()
    elif corruption == "duplicate":
        archive["records"].append(archive["records"][0])
    elif corruption == "fallback":
        archive["records"][0]["reranked"] = False
    elif corruption == "nan":
        archive["records"][0]["scores"][0] = float("nan")
    elif corruption == "instructions":
        archive.pop("instructions")
    else:
        archive["model" if corruption == "model" else "datasetSha256"] = "mismatch"
    with pytest.raises(ValueError):
        calibrate(archive, DEFAULTS, "offline-fixture")


def test_validation_regression_is_reported_without_retuning_on_held_out_data():
    archive = fixture()
    rows, _ = dataset()
    for row, record in zip(rows, archive["records"], strict=True):
        record["scores"] = [
            (0.7 if row["split"] == "calibration" else 0.5)
            if index in row["relevant"]
            else (0.6 if row["split"] == "calibration" else 0.1)
            for index in range(len(row["documents"]))
        ]
    report = calibrate(archive, DEFAULTS, "offline-fixture")
    assert all(
        row["status"] == "VALIDATION_REGRESSION" and row["recommended"] is None
        for row in report["scenes"].values()
    )


async def test_failed_score_collection_checkpoints_once_and_stops(monkeypatch):
    rank = AsyncMock(return_value={"reranked": False, "indexes": [0], "scores": []})
    monkeypatch.setattr(
        "evals.retrieval.collect.RerankClient",
        lambda *args: SimpleNamespace(spec=SimpleNamespace(model="offline-fixture"), rank=rank),
    )
    models = SimpleNamespace(settings=Settings.model_construct(), http=None, gateway=None)
    archive, checkpoint = {}, AsyncMock()
    with pytest.raises(ValueError):
        await collect(models, archive, checkpoint)
    rank.assert_awaited_once()
    checkpoint.assert_awaited_once()
    assert not archive["records"]


async def test_collection_preserves_candidate_order_and_every_checkpoint(monkeypatch):
    rank = AsyncMock(return_value={"reranked": True, "indexes": [1, 0, 2], "scores": [0.8, 0.6, 0.1]})
    monkeypatch.setattr(
        "evals.retrieval.collect.RerankClient",
        lambda *args: SimpleNamespace(spec=SimpleNamespace(model="offline-fixture"), rank=rank),
    )
    models = SimpleNamespace(settings=Settings.model_construct(), http=None, gateway=None)
    archive, checkpoint = {}, AsyncMock()
    await collect(models, archive, checkpoint)
    assert len(archive["records"]) == 36 and checkpoint.await_count == 37
    assert archive["records"][0]["scores"] == [0.6, 0.8, 0.1]
