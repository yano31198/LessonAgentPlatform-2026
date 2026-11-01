package com.zone.lesoongen.platform;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Teacher-facing projection. The unmodified engine result remains the provenance record. */
public final class F2LessonDocumentMapper {
    private F2LessonDocumentMapper() {}
    public static LessonDocument map(JsonNode plan) {
        List<LessonDocument.Block> blocks = new ArrayList<>();
        metadata(blocks, plan.path("metadata"));
        lead(blocks, plan);
        paragraphSection(blocks, plan, "curriculum_standards", "课程标准", "curriculum-standards", null);
        pairedParagraphs(blocks, plan, "content_analysis", "内容分析", "content-analysis",
                "student_analysis", "学情分析", "student-analysis", "analysis");
        objectives(blocks, plan.path("learning_objectives"));
        pairedLists(blocks, plan, "key_points", "教学重点", "key-points",
                "difficult_points", "教学难点", "difficult-points", "focus");
        paragraphSection(blocks, plan, "learning_trajectory", "学习进程", "learning-trajectory", null);
        teachingProcess(blocks, plan.path("procedure_steps").isArray() && !plan.path("procedure_steps").isEmpty()
                ? plan.path("procedure_steps") : plan.path("teaching_process"));
        pairedParagraphs(blocks, plan, "assessment_plan", "评价设计", "assessment-plan",
                "differentiation", "差异化支持", "differentiation", "assessment");
        pairedParagraphs(blocks, plan, "homework", "作业设计", "homework",
                "board_design", "板书设计", "board-design", "closing");
        paragraphSection(blocks, plan, "teaching_strategy", "教学策略", "teaching-strategy", null);
        paragraphSection(blocks, plan, "resources", "教学资源", "resources", null);
        paragraphSection(blocks, plan, "teaching_artifacts", "学习成果", "teaching-artifacts", null);
        paragraphSection(blocks, plan, "reflection", "教学反思", "reflection", null);
        paragraphSection(blocks, plan, "references", "参考资料", "references", null);

        Set<String> handled = Set.of("metadata", "design_thesis", "driving_question",
                "curriculum_standards", "content_analysis", "student_analysis", "learning_objectives",
                "key_points", "difficult_points", "learning_trajectory", "procedure_steps", "teaching_process",
                "assessment_plan", "differentiation", "homework", "board_design", "teaching_strategy",
                "resources", "teaching_artifacts", "reflection", "references", "template_extensions");
        for (var entry : plan.properties()) {
            if (handled.contains(entry.getKey()) || internal(entry.getKey()) || blank(entry.getValue())) continue;
            section(blocks, label(entry.getKey()), "additional-section", null);
            paragraph(blocks, text(entry.getValue()), "additional-content", null);
        }
        templateExtensions(blocks, plan.path("template_extensions"));
        return stable(blocks);
    }

    private static LessonDocument stable(List<LessonDocument.Block> source) {
        List<LessonDocument.Block> blocks = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            var block = source.get(index);
            String blockId = "f2-b-" + (index + 1);
            List<String> rowIds = java.util.stream.IntStream.range(0, block.tableRows().size())
                    .mapToObj(row -> blockId + "-r-" + (row + 1)).toList();
            var process = block.teachingProcess() == null ? null
                    : LessonDocumentSupport.teachingProcess(block.tableRows(), rowIds);
            blocks.add(new LessonDocument.Block(blockId, block.type(), block.level(), block.text(), block.ordered(),
                    block.items(), block.tableRows(), process, rowIds, block.columnBindings(),
                    block.semanticRole(), block.layoutGroup()));
        }
        return new LessonDocument("1.1", blocks);
    }

    private static void metadata(List<LessonDocument.Block> blocks, JsonNode metadata) {
        if (!metadata.isObject()) return;
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("项目", "内容"));
        Set<String> ordered = Set.of("subject", "grade", "topic", "duration_minutes", "textbook_version", "class_size");
        for (String key : List.of("subject", "grade", "topic", "duration_minutes", "textbook_version", "class_size")) {
            if (!blank(metadata.path(key))) rows.add(List.of(label(key), text(metadata.path(key))));
        }
        for (var field : metadata.properties()) {
            if (!ordered.contains(field.getKey()) && !internal(field.getKey()) && !blank(field.getValue()))
                rows.add(List.of(label(field.getKey()), text(field.getValue())));
        }
        if (rows.size() == 1) return;
        section(blocks, "基本信息", "metadata-heading", "header");
        blocks.add(table(rows, "lesson-metadata", "header"));
    }

    private static void lead(List<LessonDocument.Block> blocks, JsonNode plan) {
        if (blank(plan.path("design_thesis")) && blank(plan.path("driving_question"))) return;
        section(blocks, "设计主张", "design-heading", "design");
        if (!blank(plan.path("design_thesis"))) paragraph(blocks, text(plan.path("design_thesis")), "design-thesis", "design");
        if (!blank(plan.path("driving_question"))) paragraph(blocks, text(plan.path("driving_question")), "driving-question", "design");
    }

    private static void objectives(List<LessonDocument.Block> blocks, JsonNode objectives) {
        if (!objectives.isArray() || objectives.isEmpty()) return;
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("学习目标", "达成证据"));
        for (var objective : objectives) {
            if (!objective.isObject()) {
                rows.add(List.of(text(objective), ""));
                continue;
            }
            String description = first(objective, "description", "objective", "content", "text");
            String evidence = first(objective, "evidence_of_achievement", "evidence", "success_criteria");
            List<String> extras = new ArrayList<>();
            Set<String> known = Set.of("description", "objective", "content", "text",
                    "evidence_of_achievement", "evidence", "success_criteria");
            for (var field : objective.properties()) {
                if (!known.contains(field.getKey()) && !internal(field.getKey()) && !blank(field.getValue()))
                    extras.add(label(field.getKey()) + "：" + text(field.getValue()));
            }
            rows.add(List.of(description, join(evidence, String.join("\n", extras))));
        }
        section(blocks, "学习目标与达成证据", "objectives-heading", "objectives");
        blocks.add(table(rows, "learning-objectives", "objectives"));
    }

    private static void teachingProcess(List<LessonDocument.Block> blocks, JsonNode steps) {
        if (!steps.isArray() || steps.isEmpty()) return;
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("环节 / 时间", "教师活动与材料", "学生活动与证据"));
        for (var step : steps) {
            if (!step.isObject()) continue;
            String minutes = step.path("duration_minutes").isNumber()
                    ? "\n" + step.path("duration_minutes").asInt() + " 分钟" : "";
            String teacher = text(step.path("teacher_actions"));
            List<String> questions = new ArrayList<>();
            JsonNode questionNodes = step.path("questions");
            if (questionNodes.isArray()) for (var question : questionNodes) {
                if (question.isTextual()) questions.add("关键问题：" + question.asText());
                else if (question.isObject()) {
                    String prompt = first(question, "question", "prompt", "text");
                    if (!prompt.isBlank()) questions.add("关键问题：" + prompt);
                    String expected = first(question, "expected_answer", "expected_responses", "expected_student_responses");
                    if (!expected.isBlank()) questions.add("预期回答：" + expected);
                    String followUp = first(question, "follow_up", "follow_up_questions");
                    if (!followUp.isBlank()) questions.add("追问：" + followUp);
                    Set<String> knownQuestion = Set.of("question", "prompt", "text", "expected_answer",
                            "expected_responses", "expected_student_responses", "follow_up", "follow_up_questions");
                    for (var field : question.properties()) {
                        if (!knownQuestion.contains(field.getKey()) && !internal(field.getKey()) && !blank(field.getValue()))
                            questions.add(label(field.getKey()) + "：" + text(field.getValue()));
                    }
                }
            }
            teacher = join(teacher, String.join("\n", questions));
            String student = text(step.path("student_actions"));
            if (!blank(step.path("assessment"))) student = join(student, "观察证据：" + text(step.path("assessment")));
            List<String> extras = new ArrayList<>();
            Set<String> known = Set.of("stage", "duration_minutes", "teacher_actions", "student_actions", "assessment", "questions");
            for (var field : step.properties()) {
                if (!known.contains(field.getKey()) && !internal(field.getKey()) && !blank(field.getValue()))
                    extras.add(label(field.getKey()) + "：" + text(field.getValue()));
            }
            student = join(student, String.join("\n", extras));
            rows.add(List.of(step.path("stage").asText("教学环节") + minutes, teacher, student));
        }
        if (rows.size() == 1) return;
        section(blocks, "教学过程", "process-heading", "process");
        blocks.add(table(rows, "teaching-process", "process"));
    }

    private static void pairedParagraphs(List<LessonDocument.Block> blocks, JsonNode plan,
            String leftKey, String leftTitle, String leftRole,
            String rightKey, String rightTitle, String rightRole, String group) {
        paragraphSection(blocks, plan, leftKey, leftTitle, leftRole, group);
        paragraphSection(blocks, plan, rightKey, rightTitle, rightRole, group);
    }

    private static void pairedLists(List<LessonDocument.Block> blocks, JsonNode plan,
            String leftKey, String leftTitle, String leftRole,
            String rightKey, String rightTitle, String rightRole, String group) {
        listSection(blocks, plan.path(leftKey), leftTitle, leftRole, group);
        listSection(blocks, plan.path(rightKey), rightTitle, rightRole, group);
    }

    private static void paragraphSection(List<LessonDocument.Block> blocks, JsonNode plan,
            String key, String title, String role, String group) {
        if (blank(plan.path(key))) return;
        section(blocks, title, role + "-heading", group);
        paragraph(blocks, text(plan.path(key)), role, group);
    }

    private static void listSection(List<LessonDocument.Block> blocks, JsonNode value,
            String title, String role, String group) {
        if (blank(value)) return;
        section(blocks, title, role + "-heading", group);
        List<String> items = new ArrayList<>();
        if (value.isArray()) value.forEach(item -> { if (!text(item).isBlank()) items.add(text(item)); });
        else items.add(text(value));
        blocks.add(new LessonDocument.Block(id(), "list", null, null, false, items, List.of(), null,
                List.of(), java.util.Map.of(), role, group));
    }

    private static void templateExtensions(List<LessonDocument.Block> blocks, JsonNode extensions) {
        if (!extensions.isObject()) return;
        for (var extension : extensions.properties()) {
            if (internal(extension.getKey()) || blank(extension.getValue())) continue;
            if ("source_table_columns".equals(extension.getKey()) && sourceColumns(blocks, extension.getValue())) continue;
            section(blocks, label(extension.getKey()), "template-extension-heading", "appendix");
            paragraph(blocks, text(extension.getValue()), "template-extension", "appendix");
        }
    }

    private static LessonDocument.Block table(List<List<String>> rows, String role, String group) {
        List<String> rowIds = rows.stream().map(row -> "r-" + UUID.randomUUID()).toList();
        var process = LessonDocumentSupport.teachingProcess(rows, rowIds);
        return new LessonDocument.Block(id(), process == null ? "table" : "teaching_process", null, null,
                null, List.of(), rows, process, rowIds, LessonDocumentSupport.columnBindings(rows), role, group);
    }

    private static void section(List<LessonDocument.Block> blocks, String title, String role, String group) {
        blocks.add(new LessonDocument.Block(id(), "heading", 2, title, null, List.of(), List.of(), null,
                List.of(), java.util.Map.of(), role, group));
    }

    private static void paragraph(List<LessonDocument.Block> blocks, String value, String role, String group) {
        if (value == null || value.isBlank()) return;
        blocks.add(new LessonDocument.Block(id(), "paragraph", null, value, null, List.of(), List.of(), null,
                List.of(), java.util.Map.of(), role, group));
    }

    private static String first(JsonNode object, String... keys) {
        for (String key : keys) if (!blank(object.path(key))) return text(object.path(key));
        return "";
    }

    private static boolean blank(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode()
                || value.isTextual() && value.asText().isBlank()
                || value.isArray() && value.isEmpty()
                || value.isObject() && value.isEmpty();
    }

    private static String join(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "\n" + second;
    }
    private static boolean sourceColumns(List<LessonDocument.Block> blocks, JsonNode columns) {
        if (!columns.isArray()) return false;
        for (var column : columns) {
            if (!column.path("heading").isTextual() || !column.path("rows").isArray()) return false;
            for (var row : column.path("rows"))
                if (!row.path("stage").isTextual() || !row.path("text").isTextual()) return false;
        }
        for (var column : columns) {
            String heading = column.path("heading").asText();
            blocks.add(new LessonDocument.Block(id(), "heading", 2, "原稿补充列：" + heading, null, List.of(), List.of(), null));
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("教学环节", heading));
            for (var row : column.path("rows")) rows.add(List.of(row.path("stage").asText(), row.path("text").asText()));
            // These are source notes, not additional classroom teaching steps.
            List<String> rowIds = rows.stream().map(row -> "r-" + UUID.randomUUID()).toList();
            blocks.add(new LessonDocument.Block(id(), "table", null, null, null, List.of(), rows, null,
                    rowIds, java.util.Map.of()));
        }
        return true;
    }
    private static String id() { return "b-" + UUID.randomUUID(); }
    private static void heading(List<LessonDocument.Block> blocks, String text) {
        blocks.add(new LessonDocument.Block(id(), "heading", 2, text, null, List.of(), List.of(), null));
    }
    private static boolean internal(String key) {
        return key.equals("id") || key.endsWith("_id") || key.equals("schema_version")
                || key.equals("schemaVersion") || key.equals("source_anchor")
                || key.equals("source_path") || key.equals("editable_path");
    }

    /** Upgrade old saved F2 presentation in memory after its stored hashes have been checked. */
    public static LessonDocument teacherDocument(LessonDocument saved) {
        boolean legacy = saved.blocks().stream().anyMatch(block ->
                "heading".equals(block.type()) && !java.util.Objects.equals(label(block.text()), block.text())
                || "paragraph".equals(block.type()) && block.text() != null && block.text().matches("(?s)^(subject|grade|topic|duration_minutes)[：:].*"));
        if (!legacy) return saved;
        List<LessonDocument.Block> blocks = new ArrayList<>();
        boolean skip = false, metadata = false;
        for (var block : saved.blocks()) {
            if ("heading".equals(block.type())) {
                skip = internal(block.text() == null ? "" : block.text());
                metadata = "基本信息".equals(block.text()) || "metadata".equals(block.text());
            }
            if (skip) continue;
            if (metadata && "paragraph".equals(block.type()) && block.text() != null) {
                List<List<String>> rows = new ArrayList<>();
                rows.add(List.of("项目", "内容"));
                for (String line : block.text().split("\\R")) {
                    String[] pair = line.split("[：:]", 2);
                    if (pair.length == 2 && !internal(pair[0].strip())) rows.add(List.of(label(pair[0].strip()), pair[1].strip()));
                }
                if (rows.size() > 1) {
                    List<String> rowIds = java.util.stream.IntStream.range(0, rows.size())
                            .mapToObj(i -> block.id() + "-r" + i).toList();
                    blocks.add(new LessonDocument.Block(block.id(), "table", null, null, null, List.of(), rows, null, rowIds, java.util.Map.of()));
                    continue;
                }
            }
            var rows = block.tableRows().stream().map(row -> row.stream().map(F2LessonDocumentMapper::teacherText).toList()).toList();
            blocks.add(new LessonDocument.Block(block.id(), block.type(), block.level(),
                    "heading".equals(block.type()) ? label(block.text()) : teacherText(block.text()), block.ordered(),
                    block.items().stream().map(F2LessonDocumentMapper::teacherText).toList(),
                    rows, block.teachingProcess() == null ? null : "1.0".equals(saved.schemaVersion())
                            ? LegacyLessonDocumentSupport.teachingProcess(rows) : LessonDocumentSupport.teachingProcess(rows, block.rowIds()),
                    block.rowIds(), block.columnBindings(), block.semanticRole(), block.layoutGroup()));
        }
        return new LessonDocument(saved.schemaVersion(), blocks);
    }

    private static String teacherText(String text) {
        if (text == null) return null;
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            var matcher = java.util.regex.Pattern.compile("^([a-z][a-z_0-9]*)[：:]\\s*(.*)$").matcher(line);
            if (matcher.matches()) {
                if (!internal(matcher.group(1))) lines.add(label(matcher.group(1)) + "：" + matcher.group(2));
            } else lines.add(line);
        }
        return String.join("\n", lines);
    }
    private static String text(JsonNode value) {
        if (value.isNull() || value.isMissingNode()) return "";
        if (value.isTextual()) return value.asText();
        List<String> result = new ArrayList<>();
        if (value.isArray()) value.forEach(v -> result.add(text(v)));
        else if (value.isObject()) value.properties().stream().filter(e -> !internal(e.getKey()))
                .forEach(e -> result.add(label(e.getKey()) + "：" + text(e.getValue())));
        else return value.asText();
        return String.join("\n", result);
    }
    private static String label(String key) {
        if (key == null) return null;
        return switch (key) {
            case "metadata" -> "基本信息"; case "learning_objectives" -> "学习目标";
            case "key_points" -> "教学重点"; case "difficult_points" -> "教学难点";
            case "homework" -> "作业设计"; case "board_design" -> "板书设计";
            case "assessment_plan" -> "评价设计"; case "student_analysis" -> "学情分析";
            case "content_analysis" -> "内容分析"; case "design_thesis" -> "设计主张";
            case "driving_question" -> "核心问题"; case "differentiation" -> "差异化支持";
            case "learning_trajectory" -> "学习进程"; case "curriculum_standards" -> "课程标准";
            case "teaching_strategy" -> "教学策略"; case "resources" -> "教学资源";
            case "teaching_artifacts" -> "学习成果"; case "reflection" -> "教学反思";
            case "references" -> "参考资料"; case "procedure_steps", "teaching_process" -> "教学过程";
            case "subject" -> "学科"; case "grade" -> "年级"; case "topic", "title" -> "课题";
            case "duration_minutes" -> "课时（分钟）"; case "textbook_version" -> "教材版本";
            case "class_size" -> "班级人数"; case "course_information" -> "课程信息";
            case "stage" -> "教学环节"; case "teacher_actions" -> "教师活动";
            case "student_actions" -> "学生活动"; case "assessment" -> "评价";
            case "questions" -> "问题设计"; case "question", "prompt" -> "问题";
            case "expected_answer", "expected_responses", "expected_student_responses" -> "预期回答";
            case "follow_up", "follow_up_questions" -> "追问";
            case "objective", "description", "text", "content" -> "内容";
            case "evidence", "evidence_of_achievement", "success_criteria" -> "达成证据";
            case "purpose", "rationale" -> "设计意图";
            case "materials" -> "教学材料"; case "type" -> "类别";
            case "support" -> "学习支持"; case "challenge" -> "拓展任务";
            default -> key.matches("[a-zA-Z][a-zA-Z0-9_]*") ? "补充说明" : key;
        };
    }
}
