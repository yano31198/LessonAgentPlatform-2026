<script setup lang="ts">
import { computed } from "vue";
import type { LessonDocument } from "../types/lesson-document";

const props = defineProps<{ document: LessonDocument; warnings?: string[] }>();
type Block = LessonDocument["blocks"][number];

const canonical = computed(() =>
  props.document.blocks.some((block) => Boolean(block.semanticRole)),
);
const byRole = computed(() => {
  const result = new Map<string, Block>();
  for (const block of props.document.blocks)
    if (block.semanticRole) result.set(block.semanticRole, block);
  return result;
});
const role = (name: string) => byRole.value.get(name);
const text = (name: string) => role(name)?.text || "";
const items = (name: string) => role(name)?.items || [];
const rows = (name: string) => role(name)?.tableRows || [];
const lines = (value?: string) =>
  (value || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

const metadata = computed(() => {
  const result = new Map<string, string>();
  for (const row of rows("lesson-metadata").slice(1))
    if (row[0] && row[1]) result.set(row[0], row[1]);
  return result;
});
const meta = (name: string, fallback = "") =>
  metadata.value.get(name) || fallback;

const consumedRoles = new Set([
  "metadata-heading",
  "lesson-metadata",
  "design-heading",
  "design-thesis",
  "driving-question",
  "curriculum-standards-heading",
  "curriculum-standards",
  "content-analysis-heading",
  "content-analysis",
  "student-analysis-heading",
  "student-analysis",
  "objectives-heading",
  "learning-objectives",
  "key-points-heading",
  "key-points",
  "difficult-points-heading",
  "difficult-points",
  "learning-trajectory-heading",
  "learning-trajectory",
  "process-heading",
  "teaching-process",
  "assessment-plan-heading",
  "assessment-plan",
  "differentiation-heading",
  "differentiation",
  "homework-heading",
  "homework",
  "board-design-heading",
  "board-design",
]);
const remaining = computed(() =>
  props.document.blocks.filter(
    (block) => !block.semanticRole || !consumedRoles.has(block.semanticRole),
  ),
);
const has = (...roles: string[]) => roles.some((name) => role(name));
</script>

<template>
  <article v-if="canonical" class="lesson-paper canonical-lesson">
    <p v-for="warning in warnings || []" :key="warning" class="quality-note">
      {{ warning }}
    </p>
    <header v-if="role('lesson-metadata')" class="paper-header">
      <span>{{ meta("学科", "学科") }} · {{ meta("年级", "年级") }}</span>
      <h1>{{ meta("课题", "教案") }}</h1>
      <p>
        <template v-if="meta('课时（分钟）')"
          >{{ meta("课时（分钟）") }} 分钟</template
        ><template v-if="meta('教材版本')"> · {{ meta("教材版本") }}</template>
      </p>
    </header>

    <section
      v-if="has('design-thesis', 'driving-question')"
      class="design-lead"
    >
      <span class="section-number">01</span>
      <div>
        <h2>设计主张</h2>
        <p v-if="text('design-thesis')">{{ text("design-thesis") }}</p>
        <blockquote v-if="text('driving-question')">
          核心问题：{{ text("driving-question") }}
        </blockquote>
      </div>
    </section>

    <section v-if="text('curriculum-standards')">
      <span class="section-number">02</span>
      <div>
        <h2>课程标准</h2>
        <p>{{ text("curriculum-standards") }}</p>
      </div>
    </section>

    <section v-if="has('content-analysis', 'student-analysis')">
      <span class="section-number">03</span>
      <div class="paper-grid">
        <div v-if="text('content-analysis')">
          <h2>内容分析</h2>
          <p>{{ text("content-analysis") }}</p>
        </div>
        <div v-if="text('student-analysis')">
          <h2>学情分析</h2>
          <p>{{ text("student-analysis") }}</p>
        </div>
      </div>
    </section>

    <section v-if="rows('learning-objectives').length > 1">
      <span class="section-number">04</span>
      <div>
        <h2>学习目标与达成证据</h2>
        <ol class="objective-list">
          <li
            v-for="(row, index) in rows('learning-objectives').slice(1)"
            :key="index"
          >
            <strong>{{ row[0] }}</strong
            ><span v-if="row[1]">{{ row[1] }}</span>
          </li>
        </ol>
      </div>
    </section>

    <section v-if="has('key-points', 'difficult-points')">
      <span class="section-number">05</span>
      <div class="paper-grid">
        <div v-if="items('key-points').length">
          <h2>教学重点</h2>
          <ul>
            <li v-for="item in items('key-points')" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div v-if="items('difficult-points').length">
          <h2>教学难点</h2>
          <ul>
            <li v-for="item in items('difficult-points')" :key="item">
              {{ item }}
            </li>
          </ul>
        </div>
      </div>
    </section>

    <section v-if="text('learning-trajectory')">
      <span class="section-number">06</span>
      <div>
        <h2>学习进程</h2>
        <p>{{ text("learning-trajectory") }}</p>
      </div>
    </section>

    <section
      v-if="rows('teaching-process').length > 1"
      class="procedure-section"
    >
      <span class="section-number">07</span>
      <div>
        <h2>教学过程</h2>
        <div class="procedure-table" role="table" aria-label="教学过程">
          <div class="procedure-head" role="row">
            <strong
              v-for="cell in rows('teaching-process')[0]"
              :key="cell"
              role="columnheader"
              >{{ cell }}</strong
            >
          </div>
          <div
            v-for="(row, rowIndex) in rows('teaching-process').slice(1)"
            :key="rowIndex"
            class="procedure-row"
            role="row"
          >
            <div v-for="(cell, cellIndex) in row" :key="cellIndex" role="cell">
              <template v-if="cellIndex === 0">
                <strong>{{ lines(cell)[0] }}</strong
                ><span v-for="line in lines(cell).slice(1)" :key="line">{{
                  line
                }}</span>
              </template>
              <ul v-else>
                <li v-for="line in lines(cell)" :key="line">{{ line }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="has('assessment-plan', 'differentiation')">
      <span class="section-number">08</span>
      <div class="paper-grid">
        <div v-if="text('assessment-plan')">
          <h2>评价设计</h2>
          <p>{{ text("assessment-plan") }}</p>
        </div>
        <div v-if="text('differentiation')">
          <h2>差异化支持</h2>
          <p>{{ text("differentiation") }}</p>
        </div>
      </div>
    </section>

    <section v-if="has('homework', 'board-design')">
      <span class="section-number">09</span>
      <div class="paper-grid">
        <div v-if="text('homework')">
          <h2>作业设计</h2>
          <p>{{ text("homework") }}</p>
        </div>
        <div v-if="text('board-design')">
          <h2>板书设计</h2>
          <pre>{{ text("board-design") }}</pre>
        </div>
      </div>
    </section>

    <section v-if="remaining.length" class="canonical-extra">
      <span class="section-number">10</span>
      <div>
        <template v-for="block in remaining" :key="block.id">
          <div v-if="block.tableRows?.length" class="table-scroll">
            <table>
              <tbody>
                <tr
                  v-for="(row, r) in block.tableRows"
                  :key="block.rowIds?.[r] || r"
                >
                  <td v-for="(cell, c) in row" :key="c">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <component
            :is="block.ordered ? 'ol' : 'ul'"
            v-else-if="block.items?.length"
          >
            <li v-for="(item, i) in block.items" :key="i">{{ item }}</li>
          </component>
          <h2 v-else-if="block.type === 'heading'">{{ block.text }}</h2>
          <p v-else>{{ block.text }}</p>
        </template>
      </div>
    </section>
  </article>

  <article v-else class="lesson-document">
    <p v-for="warning in warnings || []" :key="warning" class="quality-note">
      {{ warning }}
    </p>
    <template v-for="block in document.blocks" :key="block.id">
      <div v-if="block.tableRows?.length" class="table-scroll">
        <table>
          <tbody>
            <tr
              v-for="(row, r) in block.tableRows"
              :key="block.rowIds?.[r] || r"
            >
              <td v-for="(cell, c) in row" :key="c">{{ cell }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <component
        :is="block.ordered ? 'ol' : 'ul'"
        v-else-if="block.items?.length"
      >
        <li v-for="(item, i) in block.items" :key="i">{{ item }}</li>
      </component>
      <h3 v-else-if="block.type === 'heading'">{{ block.text }}</h3>
      <p v-else>{{ block.text }}</p>
    </template>
  </article>
</template>

<style scoped>
.lesson-document {
  padding: clamp(20px, 4vw, 44px);
  line-height: 1.85;
  color: var(--color-text);
}
.lesson-document p,
.lesson-document td,
.canonical-lesson p,
.canonical-lesson td {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.table-scroll {
  overflow-x: auto;
  margin: 20px 0;
}
table {
  border-collapse: collapse;
  width: 100%;
  min-width: 480px;
}
td {
  border: 1px solid var(--color-border, #dce1e7);
  padding: 12px;
  vertical-align: top;
}
tr:first-child {
  background: var(--color-surface-soft, #f5f7fa);
  font-weight: 700;
}
.quality-note {
  padding: 12px;
  border-left: 3px solid var(--color-primary);
  background: var(--color-surface-soft);
}
.canonical-lesson .quality-note {
  margin: 0 0 24px;
}
.canonical-extra > div {
  min-width: 0;
  width: 100%;
}
</style>
