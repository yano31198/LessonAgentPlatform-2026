package com.zone.lesoongen.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;
import com.zone.lesoongen.domain.shared.Ulids;

@SpringBootTest(properties = "app.security.enabled=true")
@ActiveProfiles("test")
class PlatformAuthApiTest {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired PlatformUserRepository users;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registerLoginLogoutAndLessonIsolation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String alice = "alice_" + suffix;
        String bob = "bob_" + suffix;
        register(alice, "Alice");
        register(bob, "Bob");

        MockHttpSession aliceSession = login(alice);
        String lessonBody = mapper.writeValueAsString(Map.of(
                "title", "Alice 教案", "subject", "数学", "grade", "七年级",
                "topic", "一次函数", "durationMinutes", 45, "content", "教学内容"));
        MvcResult created = mvc.perform(post("/api/platform/lessons")
                        .session(aliceSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(lessonBody))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> createdBody = mapper.readValue(
                created.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        @SuppressWarnings("unchecked")
        String lessonId = (String) ((Map<String, Object>) createdBody.get("lesson")).get("id");

        mvc.perform(get("/api/platform/lessons").session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(lessonId));

        MockHttpSession bobSession = login(bob);
        mvc.perform(get("/api/platform/lessons").session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + lessonId + "')]").isEmpty());
        mvc.perform(get("/api/platform/lessons/" + lessonId).session(bobSession))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/logout").session(aliceSession))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(aliceSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPlatformRequestReturns401() throws Exception {
        mvc.perform(get("/api/platform/lessons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void taskAndModuleRecordsAreInvisibleAcrossUsers() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String alice = "owner_" + suffix;
        String bob = "viewer_" + suffix;
        register(alice, "Owner");
        register(bob, "Viewer");
        MockHttpSession aliceSession = login(alice);

        String lessonBody = mapper.writeValueAsString(Map.of(
                "title", "私有教案", "subject", "数学", "grade", "五年级",
                "topic", "三角形面积", "durationMinutes", 45, "content", "教学内容"));
        MvcResult created = mvc.perform(post("/api/platform/lessons")
                        .session(aliceSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(lessonBody)).andExpect(status().isCreated()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> createdBody = mapper.readValue(
                created.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> lesson = (Map<String, Object>) createdBody.get("lesson");
        String lessonId = String.valueOf(lesson.get("id"));
        String versionId = String.valueOf(lesson.get("currentVersionId"));
        String ownerId = users.findByUsername(alice).orElseThrow().id();
        String jobId = Ulids.next(Clock.systemUTC());
        Instant now = Instant.now();
        db.update("""
                INSERT INTO lesson_job
                (id, mode, status, status_version, subject, grade, topic, duration_minutes,
                 request_snapshot, request_sha256, current_stage, current_round,
                 progress_percent, owner_id, created_at, updated_at)
                VALUES (?, 'OPTIMIZE', 'COMPLETED', 0, '数学', '五年级', '三角形面积', 45,
                        '{}', ?, 'finalize', 0, 100, ?, ?, ?)
                """, jobId, "0".repeat(64), ownerId, Timestamp.from(now), Timestamp.from(now));
        db.update("""
                INSERT INTO platform_optimize_job_link
                (job_id, lesson_id, source_version_id, saved_version_id, created_at)
                VALUES (?, ?, ?, NULL, ?)
                """, jobId, lessonId, versionId, Timestamp.from(now));
        UUID launchId = UUID.randomUUID();
        db.update("""
                INSERT INTO platform_simulation_launch
                (launch_id, request_key, lesson_id, version_id, state, timeout_seconds,
                 message, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', 3, '完成', ?, ?)
                """, launchId.toString(), UUID.randomUUID().toString(), lessonId, versionId,
                Timestamp.from(now), Timestamp.from(now));
        UUID f4Session = UUID.randomUUID();
        db.update("""
                INSERT INTO platform_navigation_link
                (session_id, lesson_id, source_version_id, created_at)
                VALUES (?, ?, ?, ?)
                """, f4Session.toString(), lessonId, versionId, Timestamp.from(now));

        MockHttpSession bobSession = login(bob);
        mvc.perform(get("/api/v1/lesson-jobs/" + jobId).session(bobSession))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/lesson-jobs").session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.jobId == '" + jobId + "')]").isEmpty());
        mvc.perform(get("/api/platform/optimizations").session(bobSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/platform/simulation-launches/" + launchId).session(bobSession))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/platform/simulation-launches").session(bobSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/platform/navigation/sessions/" + f4Session + "/binding")
                        .session(bobSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCanUpdateProfileAndChangePassword() throws Exception {
        String username = "profile_" + UUID.randomUUID().toString().substring(0, 8);
        register(username, "原显示名称");
        MockHttpSession session = login(username);

        mvc.perform(patch("/api/auth/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("displayName", "新显示名称"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("新显示名称"));

        mvc.perform(post("/api/auth/password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "currentPassword", "SecurePass123!",
                                "newPassword", "ChangedPass456!"))))
                .andExpect(status().isNoContent());

        loginExpecting(username, "SecurePass123!", 401);
        loginExpecting(username, "ChangedPass456!", 200);
    }

    @Test
    void adminCanListAndDisableUsersButCannotDisableSelf() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminName = "admin_" + suffix;
        String memberName = "member_" + suffix;
        register(adminName, "管理员");
        register(memberName, "普通用户");
        db.update("UPDATE platform_user SET role = 'ADMIN' WHERE username = ?", adminName);

        MockHttpSession adminSession = login(adminName);
        MockHttpSession memberSession = login(memberName);
        PlatformUser admin = users.findByUsername(adminName).orElseThrow();
        PlatformUser member = users.findByUsername(memberName).orElseThrow();

        mvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == '" + memberName + "')]").exists());
        mvc.perform(get("/api/admin/users").session(memberSession))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/users/" + member.id() + "/status")
                        .session(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(get("/api/auth/me").session(memberSession))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/admin/users/" + admin.id() + "/status")
                        .session(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_SELF_DISABLE"));
    }

    @Test
    void repeatedLoginFailuresTemporarilyLockAccountAndAreAudited() throws Exception {
        String username = "locked_" + UUID.randomUUID().toString().substring(0, 8);
        register(username, "锁定测试");

        loginExpecting(username, "WrongPassword1!", 401);
        loginExpecting(username, "WrongPassword2!", 401);
        loginExpecting(username, "WrongPassword3!", 401);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "username", username, "password", "SecurePass123!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_LOCKED"));

        PlatformUser user = users.findByUsername(username).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.lockedUntil()).isNotNull();
        Integer attempts = db.queryForObject("""
                SELECT COUNT(*) FROM platform_login_audit WHERE username_attempted = ?
                """, Integer.class, username);
        org.assertj.core.api.Assertions.assertThat(attempts).isGreaterThanOrEqualTo(4);
    }

    @Test
    // csrf() in other tests replaces the filter's repository. Use the real cookie repository here.
    @org.springframework.test.annotation.DirtiesContext(
            methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    void generateRequiresLoginAndRealCsrfTokenAndKeepsUserAndAdminJobsPrivate() throws Exception {
        String endpoint = "/api/v1/lesson-jobs/generate";
        String body = "{\"subject\":\"Math\",\"grade\":\"7\",\"topic\":\"Functions\"}";
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String member = "generate_" + suffix;
        String admin = "generate_admin_" + suffix;
        register(member, "Member");
        register(admin, "Admin");
        db.update("UPDATE platform_user SET role = 'ADMIN' WHERE username = ?", admin);
        MockHttpSession memberSession = login(member);
        MockHttpSession adminSession = login(admin);
        for (MockHttpSession session : new MockHttpSession[] { memberSession, adminSession }) {
            mvc.perform(post(endpoint).session(session).contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", UUID.randomUUID().toString()).content(body))
                    .andExpect(status().isForbidden());
            MvcResult csrfResult = mvc.perform(get("/api/auth/csrf").session(session))
                    .andExpect(status().isOk()).andReturn();
            var token = mapper.readTree(csrfResult.getResponse().getContentAsString());
            var cookie = org.springframework.mock.web.MockCookie.parse(
                    csrfResult.getResponse().getHeader("Set-Cookie"));
            org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
            mvc.perform(post(endpoint).session(session).cookie(cookie)
                            .header(token.get("headerName").asString(), "invalid-token")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            MvcResult created = mvc.perform(post(endpoint).session(session).cookie(cookie)
                            .header(token.get("headerName").asString(), token.get("token").asString())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isAccepted()).andReturn();
            String jobId = mapper.readTree(created.getResponse().getContentAsString()).get("jobId").asString();
            mvc.perform(get("/api/v1/lesson-jobs/" + jobId).session(session))
                    .andExpect(status().isOk());
            MockHttpSession other = session == memberSession ? adminSession : memberSession;
            for (String path : new String[] { "", "/result", "/artifacts", "/events" }) {
                mvc.perform(get("/api/v1/lesson-jobs/" + jobId + path).session(other))
                        .andExpect(status().isForbidden());
            }
        }
    }

    private void register(String username, String displayName) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "username", username, "password", "SecurePass123!",
                                "displayName", displayName))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "username", username, "password", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void loginExpecting(String username, String password, int statusCode) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().is(statusCode));
    }
}
