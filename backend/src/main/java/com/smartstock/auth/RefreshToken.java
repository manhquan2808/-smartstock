package com.smartstock.auth;

import com.smartstock.common.BaseEntity;
import com.smartstock.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token đang có hiệu lực của một phiên đăng nhập.
 *
 * <p>Chỉ lưu BẢN BĂM SHA-256 của token. Mỗi lần người dùng gọi làm mới token, dòng cũ
 * bị thu hồi ({@code revokedAt}) và một dòng mới được tạo - cơ chế "token rotation"
 * giúp phát hiện và vô hiệu hóa token bị đánh cắp.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Token còn dùng được: chưa bị thu hồi và chưa hết hạn. */
    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}
