package com.smartstock.setting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn tham số cấu hình. Khóa chính là chuỗi tên tham số. */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    List<AppSetting> findAllByOrderBySettingKeyAsc();
}
