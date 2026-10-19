"""Verified classic-lesson and methodology-paper retrieval for F1."""
from __future__ import annotations

import json
import os
import re
from functools import lru_cache
from typing import Dict, List


DATA_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "knowledge_base",
    "external_verified",
)


def _normalize_subject(value: str) -> str:
    value = (value or "").strip().lower()
    return {
        "小学数学": "数学", "math": "数学", "mathematics": "数学",
        "小学英语": "英语", "english": "英语",
    }.get(value, value)


def _tokens(value: str) -> set[str]:
    value = (value or "").lower()
    words = set(re.findall(r"[a-z]+|[\u4e00-\u9fff]{2,}", value))
    chinese = "".join(re.findall(r"[\u4e00-\u9fff]", value))
    words.update(chinese[i:i + 2] for i in range(max(0, len(chinese) - 1)))
    return words


@lru_cache(maxsize=1)
def load_verified_external_knowledge() -> List[Dict]:
    path = os.path.join(DATA_DIR, "knowledge_chunks.json")
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as handle:
        rows = json.load(handle)
    return [row for row in rows if row.get("verificationStatus") == "VERIFIED"]


def search_verified_external_knowledge(
    query: str,
    subject: str = "",
    grade=None,
    document_type: str | None = None,
    top_k: int = 2,
) -> List[Dict]:
    query_tokens = _tokens(query)
    normalized_subject = _normalize_subject(subject)
    grade_text = str(grade or "").strip()
    ranked = []
    for row in load_verified_external_knowledge():
        if normalized_subject and _normalize_subject(row.get("subject", "")) != normalized_subject:
            continue
        if document_type and row.get("documentType") != document_type:
            continue
        searchable = " ".join([
            row.get("title", ""), row.get("sectionTitle", ""), row.get("content", ""),
            " ".join(row.get("topicTags", [])), " ".join(row.get("retrievalTags", [])),
        ])
        score = len(query_tokens & _tokens(searchable))
        if grade_text and row.get("grade") and grade_text in str(row.get("grade")):
            score += 3
        if row.get("sectionTitle") and row["sectionTitle"] in query:
            score += 4
        if score > 0:
            ranked.append((score, row))
    ranked.sort(key=lambda pair: (-pair[0], pair[1]["chunkId"]))
    return [{**row, "retrievalScore": score} for score, row in ranked[:top_k]]


def format_external_reference(row: Dict) -> str:
    locator = row.get("sourceLocator", "")
    return f"《{row.get('title', '')}》：{row.get('content', '')}（出处：{locator}）"
