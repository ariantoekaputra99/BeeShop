package com.bee.shop.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Token sesi disimpan sebagai hash SHA-256; token asli hanya dipegang client. */
@Entity
@Table(name = "admin_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AdminUser user;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;
}
