package com.zone.lesoongen.platform;

import com.zone.lesoongen.auth.PlatformAccessService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformF4SessionListTest {
    @Test void unboundUserHasEmptyHistoryWithoutCallingOrProvisioningUpstream() throws Exception {
        var access = mock(PlatformAccessService.class);
        when(access.ownedNavigationSessionIds()).thenReturn(Set.of());
        var controller = new PlatformF4WorkbenchController("http://127.0.0.1:1", access, JsonMapper.builder().build());
        var response = controller.sessions();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("[]", new String(response.getBody(), StandardCharsets.UTF_8));
    }

    @Test void boundUserOnlySeesOwnedSessionsAndUpstreamFailuresAreNotReportedAsEmptySuccess() throws Exception {
        var access = mock(PlatformAccessService.class);
        when(access.ownedNavigationSessionIds()).thenReturn(Set.of("own"));
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sessions", exchange -> {
            byte[] body = "[{\"id\":\"own\"},{\"id\":\"other\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            var mapper = JsonMapper.builder().build();
            var controller = new PlatformF4WorkbenchController("http://127.0.0.1:" + server.getAddress().getPort(), access, mapper);
            var visible = mapper.readTree(controller.sessions().getBody());
            assertEquals(1, visible.size());
            assertEquals("own", visible.get(0).path("id").asText());
            status.set(500);
            var failed = controller.sessions();
            assertEquals(502, failed.getStatusCode().value());
            assertEquals("F4_UPSTREAM_ERROR", mapper.readTree(failed.getBody()).path("code").asText());
            status.set(503);
            assertEquals(503, controller.sessions().getStatusCode().value());
        } finally { server.stop(0); }
    }
}
