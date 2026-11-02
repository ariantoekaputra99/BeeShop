package com.bee.shop.config;

import com.bee.shop.auth.AdminPrincipal;
import com.bee.shop.auth.AdminRole;
import com.bee.shop.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * - {@code /api/admin/**}: wajib {@code Authorization: Bearer <token sesi>} dari login admin.
 *   {@code /api/admin/users/**} hanya untuk role OWNER.
 * - {@code /api/integrations/**}: wajib header {@code X-Integration-Key} (server-to-server BeePos).
 * Fail-closed: integration key kosong = endpoint integrasi selalu ditolak.
 */
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    public static final String INTEGRATION_HEADER = "X-Integration-Key";
    private static final String LOGIN_PATH = "/api/admin/auth/login";

    private final AppProperties props;
    private final AuthService auth;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedPath = path.startsWith("/api/admin/") || path.startsWith("/api/integrations/");
        return !protectedPath || path.equals(LOGIN_PATH) || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/integrations/")) {
            if (!constantTimeEquals(props.integrationApiKey(), request.getHeader(INTEGRATION_HEADER))) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Integration key tidak valid");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        Optional<AdminPrincipal> principal = auth.authenticate(bearer(request));
        if (principal.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Sesi tidak valid atau sudah berakhir, silakan login ulang");
            return;
        }
        if (path.startsWith("/api/admin/users") && principal.get().role() != AdminRole.OWNER) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "Hanya OWNER yang dapat mengelola akun admin");
            return;
        }
        request.setAttribute(AdminPrincipal.ATTRIBUTE, principal.get());
        chain.doFilter(request, response);
    }

    private static String bearer(HttpServletRequest request) {
        String h = request.getHeader(HttpHeaders.AUTHORIZATION);
        return h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String error = status == 401 ? "Unauthorized" : "Forbidden";
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }

    static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
