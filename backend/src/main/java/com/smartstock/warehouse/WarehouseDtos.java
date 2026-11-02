package com.smartstock.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** DTO của phân hệ kho / chi nhánh. */
public final class WarehouseDtos {

    private WarehouseDtos() {}

    /**
     * Dữ liệu tạo hoặc sửa kho.
     *
     * @param managerId người quản lý chính của kho, dùng để gửi email/thông báo khi có phiếu chờ duyệt
     */
    public record SaveWarehouseRequest(
            @NotBlank(message = "Vui lòng nhập mã kho")
                    @Size(max = 20, message = "Mã kho tối đa 20 ký tự")
                    @Pattern(
                            regexp = "^[A-Za-z0-9_-]+$",
                            message = "Mã kho chỉ gồm chữ không dấu, số và các ký tự _ -")
                    String code,
            @NotBlank(message = "Vui lòng nhập tên kho") @Size(max = 150, message = "Tên kho tối đa 150 ký tự")
                    String name,
            @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự") String address,
            @Pattern(regexp = "^$|^[0-9+\\s.()-]{8,20}$", message = "Số điện thoại không đúng định dạng") String phone,
            Long managerId,
            Boolean active) {}

    /** Thông tin kho trả về cho giao diện. */
    public record WarehouseResponse(
            Long id,
            String code,
            String name,
            String address,
            String phone,
            Long managerId,
            String managerName,
            boolean active,
            LocalDateTime createdAt) {

        public static WarehouseResponse from(Warehouse warehouse) {
            return new WarehouseResponse(
                    warehouse.getId(),
                    warehouse.getCode(),
                    warehouse.getName(),
                    warehouse.getAddress(),
                    warehouse.getPhone(),
                    warehouse.getManager() == null ? null : warehouse.getManager().getId(),
                    warehouse.getManager() == null
                            ? null
                            : warehouse.getManager().getFullName(),
                    warehouse.isActive(),
                    warehouse.getCreatedAt());
        }
    }
}
