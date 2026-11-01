import { describe, expect, it } from "vitest";
import axios from "axios";
import { clearCsrfToken, setCsrfToken } from "../src/api/csrf";

describe("generation CSRF transport", () => {
  it("sends the latest token and credentials on the generation client", async () => {
    const previous = axios.defaults.adapter;
    const captured: Array<{ token: unknown; credentials: unknown; xsrf: unknown }> = [];
    axios.defaults.adapter = async (config) => {
      captured.push({ token: config.headers.get("X-XSRF-TOKEN"), credentials: config.withCredentials, xsrf: config.withXSRFToken });
      return { data: { jobId: "job-1" }, status: 202, statusText: "Accepted", headers: {}, config };
    };
    try {
      const { createGenerate } = await import("../src/api/client");
      const input = { subject: "Math", grade: "7", topic: "Functions", durationMinutes: 45 };
      setCsrfToken("X-XSRF-TOKEN", "masked-first");
      await createGenerate(input, "first-key");
      setCsrfToken("X-XSRF-TOKEN", "masked-after-login");
      await createGenerate(input, "second-key");
      expect(captured).toEqual([
        { token: "masked-first", credentials: true, xsrf: false },
        { token: "masked-after-login", credentials: true, xsrf: false },
      ]);
    } finally {
      axios.defaults.adapter = previous;
      clearCsrfToken();
    }
  });
});
