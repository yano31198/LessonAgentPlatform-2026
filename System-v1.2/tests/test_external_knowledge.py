from external_knowledge import (
    load_verified_external_knowledge,
    search_verified_external_knowledge,
)


def test_verified_external_knowledge_is_loaded_and_filtered():
    rows = load_verified_external_knowledge()
    assert len(rows) == 256
    assert all(row["verificationStatus"] == "VERIFIED" for row in rows)


def test_math_exemplar_and_methodology_can_be_retrieved():
    exemplar = search_verified_external_knowledge(
        "分数乘法 运算定律 猜想验证",
        subject="数学",
        grade="六年级",
        document_type="CLASSIC_LESSON",
        top_k=2,
    )
    papers = search_verified_external_knowledge(
        "数学作业 分层设计 评价",
        subject="小学数学",
        grade="六年级",
        document_type="METHODOLOGY_PAPER",
        top_k=2,
    )
    assert exemplar and exemplar[0]["documentType"] == "CLASSIC_LESSON"
    assert papers and papers[0]["documentType"] == "METHODOLOGY_PAPER"
    assert exemplar[0]["sourceLocator"]
    assert papers[0]["sourceLocator"]
