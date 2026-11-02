package com.bee.shop.auth;

import com.bee.shop.auth.AuthDtos.*;
import com.bee.shop.config.AppProperties;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Username atau password salah";

    private final AdminUserRepository users;
    private final AdminSessionRepository sessions;
    private final AppProperties.Admin config;
    private final LoginRateLimiter rateLimiter;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    /** Hash dummy agar waktu respons sama untuk username yang ada maupun tidak (cegah user enumeration). */
    private final String dummyHash;

    public AuthService(AdminUserRepository users, AdminSessionRepository sessions, AppProperties props,
                       LoginRateLimiter rateLimiter) {
        this.users = users;
        this.sessions = sessions;
        this.config = props.admin();
        this.rateLimiter = rateLimiter;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    // ---------------- Login / sesi ----------------

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest req, String ip, String userAgent) {
        if (!rateLimiter.tryAcquire(ip)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Terlalu banyak percobaan login. Coba lagi nanti.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Optional<AdminUser> found = users.findByUsernameIgnoreCase(req.username().trim());
        if (found.isEmpty()) {
            encoder.matches(req.password(), dummyHash);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        AdminUser user = found.get();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            encoder.matches(req.password(), dummyHash);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Akun terkunci sementara karena terlalu banyak percobaan gagal");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash()) || !user.isActive()) {
            if (user.isActive()) registerFailure(user, now);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);

        String token = newToken();
        OffsetDateTime expiresAt = now.plusHours(config.sessionHours());
        sessions.save(AdminSession.builder()
                .tokenHash(sha256(token)).user(user).createdAt(now).expiresAt(expiresAt)
                .ipAddress(truncate(ip, 64)).userAgent(truncate(userAgent, 255))
                .build());
        log.info("Admin login: {} dari {}", user.getUsername(), ip);
        return new LoginResponse(token, expiresAt, UserResponse.from(user));
    }

    private void registerFailure(AdminUser user, OffsetDateTime now) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= config.maxFailedAttempts()) {
            user.setLockedUntil(now.plusMinutes(config.lockMinutes()));
            user.setFailedAttempts(0);
            log.warn("Akun admin {} dikunci {} menit setelah {} percobaan gagal", user.getUsername(), config.lockMinutes(), attempts);
        }
    }

    /** Dipanggil filter untuk setiap request admin. Empty bila token tidak valid/kedaluwarsa/user nonaktif. */
    @Transactional(readOnly = true)
    public Optional<AdminPrincipal> authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 200) return Optional.empty();
        return sessions.findByTokenHash(sha256(token))
                .filter(s -> s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .filter(s -> s.getUser().isActive())
                .map(s -> new AdminPrincipal(s.getUser().getId(), s.getId(), s.getUser().getUsername(),
                        s.getUser().getDisplayName(), s.getUser().getRole()));
    }

    @Transactional
    public void logout(AdminPrincipal principal) {
        sessions.deleteById(principal.sessionId());
    }

    @Transactional(readOnly = true)
    public UserResponse me(AdminPrincipal principal) {
        return UserResponse.from(findUser(principal.userId()));
    }

    @Transactional
    public void changePassword(AdminPrincipal principal, ChangePasswordRequest req) {
        AdminUser user = findUser(principal.userId());
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Password saat ini salah");
        }
        setPassword(user, req.newPassword());
        // Sesi lain (perangkat lain) ikut keluar, sesi sekarang tetap
        sessions.deleteOthersOfUser(user.getId(), principal.sessionId());
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void purgeExpiredSessions() {
        int n = sessions.deleteExpired(OffsetDateTime.now());
        if (n > 0) log.info("{} sesi admin kedaluwarsa dihapus", n);
    }

    // ---------------- Manajemen user (OWNER) ----------------

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Username sudah dipakai");
        }
        AdminUser user = AdminUser.builder()
                .username(req.username().trim().toLowerCase())
                .displayName(req.displayName().trim())
                .role(req.role())
                .passwordHash("")
                .build();
        setPassword(user, req.password());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse updateUser(AdminPrincipal actor, Long id, UpdateUserRequest req) {
        AdminUser user = findUser(id);
        boolean self = user.getId().equals(actor.userId());

        if (req.displayName() != null && !req.displayName().isBlank()) user.setDisplayName(req.displayName().trim());

        boolean demote = req.role() != null && req.role() != AdminRole.OWNER && user.getRole() == AdminRole.OWNER;
        boolean deactivate = Boolean.FALSE.equals(req.active()) && user.isActive();
        if (self && (demote || deactivate)) {
            throw new BusinessException("Tidak bisa menonaktifkan atau menurunkan role akun sendiri");
        }
        if ((demote || deactivate) && user.getRole() == AdminRole.OWNER && users.countByRoleAndActiveTrue(AdminRole.OWNER) <= 1) {
            throw new BusinessException("Harus ada minimal satu OWNER aktif");
        }
        if (req.role() != null) user.setRole(req.role());
        if (req.active() != null) {
            user.setActive(req.active());
            if (req.active()) { user.setLockedUntil(null); user.setFailedAttempts(0); }
        }
        if (req.newPassword() != null) {
            setPassword(user, req.newPassword());
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
        }
        // Perubahan hak akses berlaku segera: paksa login ulang
        if (!self && (deactivate || demote || req.newPassword() != null)) sessions.deleteAllOfUser(user.getId());
        return UserResponse.from(user);
    }

    /** Membuat OWNER pertama dari env bila belum ada admin sama sekali. */
    @Transactional
    public boolean bootstrapOwnerIfEmpty() {
        if (users.count() > 0) return false;
        String username = config.bootstrapUsername();
        String password = config.bootstrapPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Belum ada akun admin. Set ADMIN_BOOTSTRAP_USERNAME dan ADMIN_BOOTSTRAP_PASSWORD lalu restart.");
            return false;
        }
        createUser(new CreateUserRequest(username, "Owner", password, AdminRole.OWNER));
        log.info("Akun OWNER pertama '{}' dibuat. Segera ganti password dan hapus ADMIN_BOOTSTRAP_PASSWORD dari env.", username);
        return true;
    }

    // ---------------- helper ----------------

    private void setPassword(AdminUser user, String raw) {
        if (raw.length() < AuthDtos.MIN_PASSWORD) {
            throw new BusinessException("Password minimal " + AuthDtos.MIN_PASSWORD + " karakter");
        }
        user.setPasswordHash(encoder.encode(raw));
        user.setPasswordChangedAt(OffsetDateTime.now());
    }

    private AdminUser findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Admin tidak ditemukan"));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
