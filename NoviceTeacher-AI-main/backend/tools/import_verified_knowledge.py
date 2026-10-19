#!/usr/bin/env python3
"""Load the bundled, human-verified knowledge package into F4."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.db import SessionLocal
from app.knowledge_import import import_verified_knowledge


def main() -> None:
    with SessionLocal() as db:
        result = import_verified_knowledge(db)
        db.commit()
    print(
        "F4_KNOWLEDGE_IMPORT_OK "
        f"created={result['created']} updated={result['updated']} total={result['total']}"
    )


if __name__ == "__main__":
    main()
