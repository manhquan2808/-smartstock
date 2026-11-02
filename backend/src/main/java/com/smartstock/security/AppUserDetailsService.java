package com.smartstock.security;

import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp người dùng từ CSDL cho Spring Security khi đăng nhập bằng mật khẩu.
 *
 * <p>Chỉ dùng ở bước đăng nhập. Các request sau đó dựng lại thông tin từ access token
 * (xem {@link JwtService}) nên không phải truy vấn CSDL mỗi lần.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + username));
        return new AppUserDetails(user);
    }
}
