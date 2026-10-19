from sqlalchemy import func, select

from app import models as m
from app.knowledge_import import import_verified_knowledge
from app.retrieval import MetadataRetriever, RetrievalQuery


def test_verified_package_import_is_idempotent_and_retrievable(environment):
    sessions, _ = environment
    with sessions() as db:
        first = import_verified_knowledge(db)
        db.commit()
        assert first == {"created": 256, "updated": 0, "total": 256}

        second = import_verified_knowledge(db)
        db.commit()
        assert second == {"created": 0, "updated": 256, "total": 256}
        assert db.scalar(select(func.count()).select_from(m.KnowledgeItem)) == 256

        results = MetadataRetriever(db, "knowledge").retrieve(RetrievalQuery(
            subject="小学数学",
            grade="六年级",
            topic="分数乘法",
            section_type="教学实施路径",
            section_content="运算定律 猜想验证 迁移",
            top_k=3,
        ))
        assert results
        assert all(item.metadata["verification_status"] == "verified" for item in results)
        assert all(item.source_locator for item in results)
