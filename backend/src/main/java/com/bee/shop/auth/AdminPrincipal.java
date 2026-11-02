package com.bee.shop.auth;

import jakarta.servlet.http.HttpServletRequest;

/** Admin yang sedang login, dipasang di request oleh {@link com.bee.shop.config.AuthFilter}. */
public record AdminPrincipal(Long userId, Long sessionId, String username, String displayName, AdminRole role) {

    public static final String ATTRIBUTE = AdminPrincipal.class.getName();

    public static AdminPrincipal from(HttpServletRequest request) {
        Object p = request.getAttribute(ATTRIBUTE);
        if (p instanceof AdminPrincipal principal) return principal;
        throw new IllegalStateException("Request admin tanpa principal");
    }

    public String actor() {
        return "admin:" + username;
    }
}
