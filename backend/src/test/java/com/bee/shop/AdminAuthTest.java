package com.bee.shop;

import com.bee.shop.auth.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthTest extends TestSupport {

    @Autowired AdminUserRepository adminUsers;

    @Test
    void bootstrapOwnerCanLoginAndSeeProfile() throws Exception {
        String token = ownerToken();
        mvc.perform(get("/api/admin/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        // Password tidak disimpan plain
        assertThat(adminUsers.findByUsernameIgnoreCase("owner").orElseThrow().getPasswordHash()).startsWith("$2");
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameError() throws Exception {
        postJson("/api/admin/auth/login", Map.of("username", "owner", "password", "salah-total-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Username atau password salah"));
        postJson("/api/admin/auth/login", Map.of("username", "tidak-ada", "password", "salah-total-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Username atau password salah"));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        String user = createUser("STAFF");
        for (int i = 0; i < 3; i++) {
            postJson("/api/admin/auth/login", Map.of("username", user, "password", "bukan-password")).andExpect(status().isUnauthorized());
        }
        // Password benar pun ditolak selama terkunci
        postJson("/api/admin/auth/login", Map.of("username", user, "password", "staff-password-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("terkunci")));

        // OWNER membuka kunci dengan reset password
        Long id = adminUsers.findByUsernameIgnoreCase(user).orElseThrow().getId();
        patchUser(ownerToken(), id, Map.of("newPassword", "password-baru-12345")).andExpect(status().isOk());
        login(user, "password-baru-12345");
    }

    @Test
    void staffCannotManageUsersButCanManageOrders() throws Exception {
        String user = createUser("STAFF");
        String staff = bearer(login(user, "staff-password-123"));
        mvc.perform(get("/api/admin/users").header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders").header("Authorization", staff)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/products").header("Authorization", staff)).andExpect(status().isOk());
    }

    @Test
    void deactivatingUserRevokesSessionsImmediately() throws Exception {
        String user = createUser("STAFF");
        String staff = bearer(login(user, "staff-password-123"));
        Long id = adminUsers.findByUsernameIgnoreCase(user).orElseThrow().getId();

        patchUser(ownerToken(), id, Map.of("active", false)).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/admin/orders").header("Authorization", staff)).andExpect(status().isUnauthorized());
        postJson("/api/admin/auth/login", Map.of("username", user, "password", "staff-password-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCannotLockThemselvesOut() throws Exception {
        String token = ownerToken();
        Long ownerId = adminUsers.findByUsernameIgnoreCase("owner").orElseThrow().getId();
        patchUser(token, ownerId, Map.of("active", false)).andExpect(status().isUnprocessableEntity());
        patchUser(token, ownerId, Map.of("role", "STAFF")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void changePasswordKeepsCurrentSessionAndRevokesOthers() throws Exception {
        String user = createUser("OWNER");
        String phone = bearer(login(user, "staff-password-123"));
        String laptop = bearer(login(user, "staff-password-123"));

        mvc.perform(post("/api/admin/auth/password").header("Authorization", laptop).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "salah-salah-salah", "newPassword", "baru-baru-baru-1"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/auth/password").header("Authorization", laptop).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "staff-password-123", "newPassword", "baru-baru-baru-1"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/auth/me").header("Authorization", laptop)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", phone)).andExpect(status().isUnauthorized());
        login(user, "baru-baru-baru-1");
    }

    @Test
    void logoutInvalidatesToken() throws Exception {
        String token = bearer(ownerToken());
        mvc.perform(post("/api/admin/auth/logout").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void createUserValidatesInput() throws Exception {
        String token = bearer(ownerToken());
        mvc.perform(post("/api/admin/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "x y", "displayName", "X", "password", "pendek", "role", "STAFF"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.password").exists());
        mvc.perform(post("/api/admin/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "OWNER", "displayName", "X", "password", "panjang-sekali-123", "role", "STAFF"))))
                .andExpect(status().isConflict());
    }

    private String createUser(String role) throws Exception {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(ownerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "displayName", "Staf Gudang",
                                "password", "staff-password-123", "role", role))))
                .andExpect(status().isCreated());
        return username;
    }

    private org.springframework.test.web.servlet.ResultActions patchUser(String token, Long id, Map<String, ?> body) throws Exception {
        return mvc.perform(patch("/api/admin/users/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
