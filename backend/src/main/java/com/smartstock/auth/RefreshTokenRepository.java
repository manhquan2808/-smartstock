package com.smartstock.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Thu hồi toàn bộ token của một người: dùng khi đăng xuất mọi thiết bị hoặc khi khóa tài khoản. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllByUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Dọn các token đã hết hạn (gọi theo lịch hằng ngày). */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :threshold")
    int deleteExpired(@Param("threshold") LocalDateTime threshold);
}
