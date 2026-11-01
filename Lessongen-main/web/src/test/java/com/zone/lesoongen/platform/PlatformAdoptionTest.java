package com.zone.lesoongen.platform;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.zone.lesoongen.application.LessonResultService;
import com.zone.lesoongen.application.AppException;
import com.zone.lesoongen.api.dto.LessonResponses;
import com.zone.lesoongen.domain.job.JobStatus;
import com.zone.lesoongen.domain.shared.Ulids;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
class PlatformAdoptionTest {
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformGeneratedLessonService generated;
    @Autowired PlatformOptimizeService optimize;
    @Autowired PlatformLessonService lessons;
    @Autowired PlatformLessonDocumentService documents;
    @MockitoBean LessonResultService results;
    @Autowired com.zone.lesoongen.application.LessonJobService jobs;
    @Autowired LessonVersionWriteService writer;

    private String job(String mode) {
        String id = Ulids.next(java.time.Clock.systemUTC());
        db.update("INSERT INTO lesson_job (id, mode, status, subject, grade, topic, duration_minutes, request_snapshot, request_sha256, owner_id, best_version_id, created_at, updated_at) VALUES (?, ?, 'COMPLETED', '数学', '五', '面积', 40, '{}', ?, ?, 'revision-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, mode, "a".repeat(64), com.zone.lesoongen.auth.CurrentUserService.LEGACY_USER_ID);
        return id;
    }
    @Test void serverAdoptionIsIdempotentAndKeepsProcedureAndAdditionalContent() throws Exception {
        String job = job("GENERATE");
        var plan = mapper.readTree("""
                {"schema_version":"paper4-lesson-plan-v0.1","plan_id":"private-plan","task_id":"private-task","template_id":"general",
                 "metadata":{"subject":"数学","grade":"五","topic":"面积","duration_minutes":40},
                 "curriculum_standards":["根据课程标准设计"],"learning_trajectory":["观察","解释"],
                 "learning_objectives":[{"objective_id":"private-objective","description":"说明面积关系","evidence_of_achievement":"画图并解释"}],
                 "procedure_steps":[{"step_id":"private-step","stage":"讨论","duration_minutes":8,"teacher_actions":["追问依据"],"student_actions":["说明依据"],"assessment":"同伴评价","questions":[{"question":"为什么？","expected_answer":"根据面积关系","follow_up":"还有别的方法吗？"}],"custom":"附加内容"}],
                 "homework":"测量教室","design_thesis":"保留设计主张"}
                """);
        when(results.result(job)).thenReturn(new LessonResponses.LessonResult(job, JobStatus.COMPLETED, null,
                "revision-1", "revision-1", plan, Map.of(), null, null, null, null, List.of(), List.of(), List.of()));
        var first = generated.adopt(job);
        var replay = generated.adopt(job);
        assertEquals(first.versionId(), replay.versionId()); assertTrue(replay.alreadySaved());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM platform_generated_lesson WHERE job_id = ?", Integer.class, job));
        var snapshot = documents.get(first.lessonId(), first.versionId());
        assertTrue(snapshot.usableForSimulation());
        assertEquals(8, LessonDocumentMaterialAdapter.materials(snapshot).get(0).get("durationMinutes"));
        String content = lessons.detail(first.lessonId()).versions().get(0).content();
        assertTrue(content.contains("附加内容") && content.contains("测量教室") && content.contains("保留设计主张"));
        for (String key : List.of("schema_version", "plan_id", "task_id", "template_id", "step_id", "private-plan", "private-step"))
            assertFalse(content.contains(key), key);
        assertTrue(content.contains("课程标准") && content.contains("学习进程"));
        assertEquals("VERIFIED", snapshot.bindingStatus());
        assertTrue(snapshot.document().blocks().stream().anyMatch(b -> b.tableRows().contains(List.of("学科", "数学"))));
        var objectives = snapshot.document().blocks().stream().filter(b -> "learning-objectives".equals(b.semanticRole())).findFirst().orElseThrow();
        assertTrue(objectives.tableRows().contains(List.of("说明面积关系", "画图并解释")));
        var process = snapshot.document().blocks().stream().filter(b -> "teaching-process".equals(b.semanticRole())).findFirst().orElseThrow();
        assertTrue(process.tableRows().toString().contains("为什么？"));
        assertTrue(process.tableRows().toString().contains("根据面积关系"));
        assertTrue(process.tableRows().toString().contains("还有别的方法吗？"));
        assertFalse(lessons.detail(first.lessonId()).hasSourceDocument());
        assertEquals("private-plan", plan.path("plan_id").asText(), "Original engine result must remain unchanged");
        var summary = jobs.list(0, 50, null, null).items().stream().filter(j -> j.jobId().equals(job)).findFirst().orElseThrow();
        assertEquals(first.lessonId(), summary.lessonId());
        assertEquals(first.versionId(), summary.versionId());
        assertEquals(0, summary.versionNumber());
        db.update("UPDATE lesson_job SET owner_id = NULL WHERE id = ?", job);
        assertThrows(AppException.class, () -> generated.adopt(job));
    }

    @Test void historicalGeneratedSchemaIsAdaptedWithoutRewritingStoredVersion() throws Exception {
        var old = new LessonDocument("1.1", List.of(
                new LessonDocument.Block("schema", "heading", 2, "schema_version", null, List.of(), List.of(), null),
                new LessonDocument.Block("schema-value", "paragraph", null, "paper4-v0.1", null, List.of(), List.of(), null),
                new LessonDocument.Block("plan", "heading", 2, "plan_id", null, List.of(), List.of(), null),
                new LessonDocument.Block("plan-value", "paragraph", null, "private-legacy", null, List.of(), List.of(), null),
                new LessonDocument.Block("info", "heading", 2, "基本信息", null, List.of(), List.of(), null),
                new LessonDocument.Block("info-value", "paragraph", null, "subject：数学\ngrade：五年级", null, List.of(), List.of(), null),
                new LessonDocument.Block("trajectory", "heading", 2, "learning_trajectory", null, List.of(), List.of(), null),
                new LessonDocument.Block("trajectory-value", "paragraph", null, "观察面积", null, List.of(), List.of(), null),
                LessonDocumentSupport.tableBlock("process", List.of(List.of("教学环节", "教师活动", "学生活动"),
                        List.of("讨论（8分钟）", "提问", "回答")))));
        String stored = LessonDocumentProjection.project(old).content();
        var initial = writer.createInitial(new PlatformLessonController.CreateLesson("历史生成教案", "数学", "五", "面积", 40, stored),
                "F2_GENERATE", "legacy-reference", old, "PARSED", "F2_RESULT", List.of());
        String originalStructure = db.queryForObject("SELECT structured_content FROM platform_lesson_version_structure WHERE version_id = ?", String.class, initial.versionId());
        String generatedJob = job("GENERATE");
        var originalPlan = mapper.readTree("""
                {"schema_version":"paper4-lesson-plan-v0.1","plan_id":"legacy-private",
                 "metadata":{"subject":"数学","grade":"五","topic":"面积","duration_minutes":40},
                 "learning_trajectory":["观察面积"],
                 "procedure_steps":[{"stage":"讨论","duration_minutes":8,"teacher_actions":["提问"],"student_actions":["回答"]}]}
                """);
        when(results.result(generatedJob)).thenReturn(new LessonResponses.LessonResult(generatedJob, JobStatus.COMPLETED, null,
                "revision-1", "revision-1", originalPlan, Map.of(), null, null, null, null, List.of(), List.of(), List.of()));
        db.update("INSERT INTO platform_generated_lesson (job_id, adopted_revision_id, owner_id, lesson_id, version_id, created_at) VALUES (?, 'revision-1', ?, ?, ?, CURRENT_TIMESTAMP)",
                generatedJob, com.zone.lesoongen.auth.CurrentUserService.LEGACY_USER_ID, initial.lessonId(), initial.versionId());
        var snapshot = documents.get(initial.lessonId(), initial.versionId());
        String visible = lessons.detail(initial.lessonId()).versions().get(0).content();
        assertFalse(visible.contains("schema_version") || visible.contains("plan_id") || visible.contains("private-legacy"));
        assertTrue(visible.contains("学科") && visible.contains("学习进程"));
        assertTrue(snapshot.usableForSimulation());
        assertTrue(snapshot.document().blocks().stream().anyMatch(b -> "lesson-metadata".equals(b.semanticRole())));
        assertTrue(snapshot.document().blocks().stream().anyMatch(b -> "teaching-process".equals(b.semanticRole())));
        assertEquals(visible, LessonDocumentProjection.project(snapshot.document()).content());
        var material = LessonDocumentMaterialAdapter.materials(snapshot).get(0);
        assertFalse(material.toString().contains("schema_version") || material.toString().contains("private-legacy"));
        assertTrue(material.toString().contains("学习进程"));
        try (var word = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(
                LessonDocumentDocxExporter.exportForDownload(snapshot.document())))) {
            assertEquals(2, word.getTables().size());
            assertFalse(word.getParagraphs().stream().anyMatch(p -> p.getText().contains("schema_version")));
        }
        assertEquals(stored, db.queryForObject("SELECT content FROM platform_lesson_version WHERE id = ?", String.class, initial.versionId()));
        assertEquals(originalStructure, db.queryForObject("SELECT structured_content FROM platform_lesson_version_structure WHERE version_id = ?", String.class, initial.versionId()));
        assertEquals(documents.get(initial.lessonId(), initial.versionId()).structureSha256(), snapshot.structureSha256());
    }
    @Test void differentTeacherDraftCannotReplayAnExistingSave() {
        String job = job("OPTIMIZE");
        var source = lessons.create(new PlatformLessonController.CreateLesson("面积", "数学", "五", "面积", 40, "原稿"));
        String lesson = source.lesson().id(), version = source.lesson().currentVersionId();
        db.update("INSERT INTO platform_optimize_job_link (job_id, lesson_id, source_version_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", job, lesson, version);
        when(results.artifacts(job)).thenReturn(List.of());
        var first = optimize.submitReview(lesson, job, new PlatformOptimizeReviewController.Submit(version, "教师稿 A", true));
        assertEquals(first.versionId(), optimize.submitReview(lesson, job, new PlatformOptimizeReviewController.Submit(version, "教师稿 A", true)).versionId());
        assertThrows(AppException.class, () -> optimize.submitReview(lesson, job, new PlatformOptimizeReviewController.Submit(version, "教师稿 B", true)));
        assertEquals(2, lessons.detail(lesson).versions().size());
    }
}
