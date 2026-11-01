import { beforeEach, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import axios from "axios";
import LessonSpaceView from "../src/views/LessonSpaceView.vue";
import TaskHistoryView from "../src/views/TaskHistoryView.vue";
import { listJobs } from "../src/api/client";

vi.mock("axios", () => ({
  default: {
    get: vi.fn(),
    isAxiosError: (e: { isAxiosError?: boolean }) => e?.isAxiosError === true,
  },
}));
vi.mock("../src/api/client", () => ({ listJobs: vi.fn() }));

const lesson = { id: "generated", title: "面积", currentVersionId: "v0" };
let hasSource = false;
let sourceModule = "F2_GENERATE";
let sourceMissing = false;
beforeEach(() => {
  vi.resetAllMocks();
  hasSource = false;
  sourceMissing = false;
  sourceModule = "F2_GENERATE";
  vi.mocked(listJobs).mockResolvedValue({
    items: [
      {
        jobId: "job",
        mode: "GENERATE",
        status: "COMPLETED",
        subject: "数学",
        grade: "五年级",
        topic: "面积",
        currentStage: "finalize",
        currentRound: 0,
        progressPercent: 100,
        createdAt: "2026-10-31T12:00:00Z",
        lessonId: "generated",
        versionId: "v0",
        versionNumber: 0,
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  });
  vi.mocked(axios.get).mockImplementation(async (url) => {
    if (url === "/api/platform/lessons") return { data: [lesson] };
    if (url === "/api/platform/lessons/generated")
      return {
        data: {
          lesson,
          hasSourceDocument: hasSource,
          versions: [
            {
              id: "v0",
              versionNumber: 0,
              content: "仅作降级显示的正文",
              sourceModule,
              createdAt: "2026-10-31T12:00:00Z",
            },
          ],
        },
      };
    if (String(url).endsWith("/document"))
      return {
        data: {
          lessonId: "generated",
          versionId: "v0",
          bindingStatus: "VERIFIED",
          warnings: [],
          document: {
            schemaVersion: "1.1",
            blocks: [
              { id: "h", type: "heading", level: 2, text: "教学目标" },
              { id: "p", type: "paragraph", text: "理解面积的意义" },
              {
                id: "t",
                type: "table",
                tableRows: [
                  ["学科", "数学"],
                  ["年级", "五年级"],
                ],
              },
            ],
          },
        },
      };
    if (String(url).endsWith("/source-document")) {
      if (sourceMissing)
        throw Object.assign(new Error("missing"), {
          isAxiosError: true,
          response: { status: 404 },
        });
      return {
        data: {
          originalFilename: "原稿.docx",
          sizeBytes: 1024,
          createdAt: "2026-10-31T12:00:00Z",
        },
      };
    }
    if (String(url).endsWith("/capabilities")) return { data: { pdf: false } };
    return { data: [] };
  });
});

async function open(
  component: typeof LessonSpaceView | typeof TaskHistoryView,
  path: string,
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/lessons/:lessonId", component: { template: "<div />" } },
      { path: "/tasks", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(component, {
    global: { plugins: [router], stubs: { LessonIssuePanel: true } },
  });
  await flushPromises();
  return wrapper;
}

it("shows generated V0 in task history without an optimization binding", async () => {
  const wrapper = await open(TaskHistoryView, "/tasks");
  expect(wrapper.find(".table-row").text()).toContain("V0");
  expect(wrapper.find(".table-row").text()).toContain("教案生成");
});

it("uses the formal document, translates the source, and never requests an absent generated original", async () => {
  const wrapper = await open(LessonSpaceView, "/lessons/generated");
  expect(wrapper.text()).toContain("设计修改生成教案");
  expect(wrapper.text()).toContain("教学目标");
  expect(wrapper.text()).toContain("理解面积的意义");
  expect(wrapper.find("table").exists()).toBe(true);
  expect(wrapper.text()).not.toContain("仅作降级显示的正文");
  for (const field of [
    "schema_version",
    "plan_id",
    "task_id",
    "template_id",
    "F2_GENERATE",
  ])
    expect(wrapper.text()).not.toContain(field);
  expect(
    vi
      .mocked(axios.get)
      .mock.calls.some(([url]) => String(url).endsWith("/source-document")),
  ).toBe(false);
  expect(wrapper.text()).not.toContain("下载原稿");
});

it("keeps DOCX original download links when source metadata exists", async () => {
  hasSource = true;
  sourceModule = "IMPORT_DOCX";
  const wrapper = await open(LessonSpaceView, "/lessons/generated");
  expect(wrapper.text()).toContain("原稿.docx");
  expect(wrapper.find('a[href$="/source-document/file"]').exists()).toBe(true);
});

it("treats a missing optional original as normal while preserving the lesson preview", async () => {
  hasSource = true;
  sourceMissing = true;
  const wrapper = await open(LessonSpaceView, "/lessons/generated");
  expect(wrapper.text()).toContain("理解面积的意义");
  expect(wrapper.find('[role="alert"]').exists()).toBe(false);
  expect(wrapper.text()).not.toContain("下载原稿");
});

it("reports an actual F4 service failure while keeping other task rows visible", async () => {
  const original = vi.mocked(axios.get).getMockImplementation()!;
  vi.mocked(axios.get).mockImplementation(async (url, config) => {
    if (url === "/api/platform/f4/sessions")
      throw new Error("service unavailable");
    return original(url, config);
  });
  const wrapper = await open(TaskHistoryView, "/tasks");
  expect(wrapper.find('[role="alert"]').text()).toContain(
    "协同共析任务暂时无法读取",
  );
  expect(wrapper.find(".table-row").text()).toContain("V0");
});
