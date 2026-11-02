package com.bee.shop.auth;

import com.bee.shop.auth.AuthDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService auth;

    /** Satu-satunya endpoint /api/admin yang tidak butuh token. */
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        auth.logout(AdminPrincipal.from(http));
    }

    @GetMapping("/auth/me")
    public UserResponse me(HttpServletRequest http) {
        return auth.me(AdminPrincipal.from(http));
    }

    @PostMapping("/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http) {
        auth.changePassword(AdminPrincipal.from(http), req);
    }

    // ---- Khusus OWNER (dicek di filter) ----

    @GetMapping("/users")
    public List<UserResponse> users() {
        return auth.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return auth.createUser(req);
    }

    @PatchMapping("/users/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req, HttpServletRequest http) {
        return auth.updateUser(AdminPrincipal.from(http), id, req);
    }
}
