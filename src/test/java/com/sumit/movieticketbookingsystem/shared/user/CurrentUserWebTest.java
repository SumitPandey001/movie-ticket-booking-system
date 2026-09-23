package com.sumit.movieticketbookingsystem.shared.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Only the echo controller is loaded; it needs the explicit import because Boot never scans classes nested in tests.
@WebMvcTest(CurrentUserWebTest.EchoController.class)
@Import(CurrentUserWebTest.EchoController.class)
class CurrentUserWebTest {

    private static final String USER_ID = "7c0e9a3e-2d51-4f0b-9a57-2f7a1d7f4b10";

    @Autowired
    private MockMvc mvc;

    @Test
    void missingUserIdIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Missing or invalid X-User-Id header"));
    }

    @Test
    void malformedUserIdIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-User-Id", "not-a-uuid").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownRoleIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-User-Id", USER_ID).header("X-User-Role", "SUPERUSER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing or invalid X-User-Role header"));
    }

    @Test
    void controllerReceivesTheUserFromHeaders() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "customer")
                        .header("X-User-Email", "asha@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void customerCannotUseAdminPaths() throws Exception {
        mvc.perform(get("/api/v1/admin/ping").header("X-User-Id", USER_ID).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanUseAdminPaths() throws Exception {
        mvc.perform(get("/api/v1/admin/ping").header("X-User-Id", USER_ID).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @RestController
    static class EchoController {

        @GetMapping("/api/v1/me")
        CurrentUser me(CurrentUser user) {
            return user;
        }

        @GetMapping("/api/v1/admin/ping")
        String ping() {
            return "pong";
        }
    }
}
