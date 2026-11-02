package com.bee.shop.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {

    @Query("select s from AdminSession s join fetch s.user where s.tokenHash = :hash")
    Optional<AdminSession> findByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from AdminSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);

    @Modifying
    @Query("delete from AdminSession s where s.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from AdminSession s where s.user.id = :userId and s.id <> :keepId")
    int deleteOthersOfUser(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
