"""Idempotent importer for reviewed external knowledge packages."""
from __future__ import annotations

import json
from pathlib import Path
from uuid import UUID, uuid5

from sqlalchemy import select

from . import models as m


KNOWLEDGE_NAMESPACE = UUID("4c16ac56-13c2-4b17-a912-e1c1f538bb9d")
DEFAULT_PACKAGE = (
    Path(__file__).resolve().parents[1]
    / "knowledge_base"
    / "external_verified"
    / "knowledge_chunks.json"
)


def stable_item_id(chunk_id: str) -> str:
    return str(uuid5(KNOWLEDGE_NAMESPACE, chunk_id))


def load_verified_chunks(path: Path = DEFAULT_PACKAGE) -> list[dict]:
    rows = json.loads(path.read_text(encoding="utf-8"))
    return [row for row in rows if row.get("verificationStatus") == "VERIFIED"]


def import_verified_knowledge(db, path: Path = DEFAULT_PACKAGE) -> dict[str, int]:
    created = updated = 0
    for row in load_verified_chunks(path):
        item_id = stable_item_id(row["chunkId"])
        item = db.get(m.KnowledgeItem, item_id)
        values = {
            "subject": row.get("subject", ""),
            "grade": row.get("grade"),
            "topic": row.get("title", ""),
            "section_type": row.get("sectionType") or row.get("sectionTitle"),
            "verification_status": "verified",
            "asset_metadata": {
                "chunk_id": row["chunkId"],
                "document_id": row["documentId"],
                "document_type": row.get("documentType"),
                "content_hash": row.get("contentHash"),
                "retrieval_tags": row.get("retrievalTags", []),
                "page_start": row.get("pageStart"),
                "page_end": row.get("pageEnd"),
            },
            "embedding": None,
            "content": row["content"],
            "source": row.get("title", ""),
            "source_type": row.get("documentType", "EXTERNAL_KNOWLEDGE"),
            "source_locator": row.get("sourceLocator", ""),
            "pedagogical_dimension": row.get("sectionTitle") or row.get("sectionType"),
        }
        if item is None:
            db.add(m.KnowledgeItem(id=item_id, **values))
            created += 1
        else:
            for key, value in values.items():
                setattr(item, key, value)
            updated += 1
    db.flush()
    return {"created": created, "updated": updated, "total": created + updated}
