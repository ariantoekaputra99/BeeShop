package com.bee.shop.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Melindungi admin API dan endpoint integrasi BeePos dengan API key di header.
 * Fail-closed: bila key belum dikonfigurasi, endpoint selalu menolak akses.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String ADMIN_HEADER = "X-Admin-Key";
    public static final String INTEGRATION_HEADER = "X-Integration-Key";

    private final AppProperties props;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean protectedPath = path.startsWith("/api/admin/") || path.startsWith("/api/integrations/");
        return !protectedPath || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean admin = request.getRequestURI().startsWith("/api/admin/");
        String expected = admin ? props.adminApiKey() : props.integrationApiKey();
        String provided = request.getHeader(admin ? ADMIN_HEADER : INTEGRATION_HEADER);

        if (!matches(expected, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"API key tidak valid\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
