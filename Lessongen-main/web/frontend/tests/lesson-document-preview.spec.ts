import { expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import LessonDocumentPreview from "../src/components/LessonDocumentPreview.vue";
it("renders authoritative table cells and escapes untrusted HTML", () => {
  const wrapper = mount(LessonDocumentPreview, {
    props: {
      document: {
        schemaVersion: "1.1",
        blocks: [
          {
            id: "b",
            type: "teaching_process",
            tableRows: [
              ["教师", "学生", "额外"],
              ["<script>alert(1)</script>", "回答", "保留"],
            ],
          },
        ],
      },
    },
  });
  expect(wrapper.findAll("tr")).toHaveLength(2);
  expect(wrapper.text()).toContain("保留");
  expect(wrapper.find("script").exists()).toBe(false);
});

it("renders F2 presentation hints without changing the generic document contract", () => {
  const wrapper = mount(LessonDocumentPreview, {
    props: {
      document: {
        schemaVersion: "1.1",
        blocks: [
          {
            id: "metadata",
            type: "table",
            semanticRole: "lesson-metadata",
            layoutGroup: "header",
            tableRows: [
              ["项目", "内容"],
              ["学科", "数学"],
              ["年级", "初三"],
              ["课题", "配方法"],
              ["课时（分钟）", "45"],
            ],
          },
          {
            id: "objectives",
            type: "table",
            semanticRole: "learning-objectives",
            tableRows: [
              ["学习目标", "达成证据"],
              ["理解配方法", "完成解释卡"],
            ],
          },
          {
            id: "process",
            type: "teaching_process",
            semanticRole: "teaching-process",
            tableRows: [
              ["环节 / 时间", "教师活动与材料", "学生活动与证据"],
              ["探究\n10 分钟", "提出关键问题", "完成拼图\n观察证据：正确"],
            ],
          },
        ],
      },
    },
  });
  expect(wrapper.find(".lesson-paper").exists()).toBe(true);
  expect(wrapper.find(".paper-header").text()).toContain("配方法");
  expect(wrapper.find(".objective-list").text()).toContain("完成解释卡");
  expect(wrapper.find(".procedure-table").text()).toContain("观察证据：正确");
  expect(wrapper.text()).not.toContain("项目内容");
});
