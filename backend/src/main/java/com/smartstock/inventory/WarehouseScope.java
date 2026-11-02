package com.smartstock.inventory;

import java.util.List;

/**
 * Phạm vi kho được phép xem của một truy vấn, dùng chung cho mọi báo cáo và danh sách.
 *
 * <p>Vì sao cần lớp này: các truy vấn JPQL không nhận được danh sách rỗng trong mệnh đề
 * {@code IN}. Thay vì để mỗi service tự xử lý, ta gói lại thành một cặp giá trị:
 *
 * <ul>
 *   <li>{@code all = true}: xem mọi kho (ADMIN, không chọn kho cụ thể).</li>
 *   <li>{@code all = false}: chỉ xem các kho trong {@code ids}.</li>
 * </ul>
 *
 * @param ids luôn khác rỗng; khi {@code all = true} thì giá trị không được dùng tới
 */
public record WarehouseScope(boolean all, List<Long> ids) {

    /** Giá trị giữ chỗ để mệnh đề IN luôn có ít nhất một phần tử. */
    private static final List<Long> PLACEHOLDER = List.of(-1L);

    /** Xem mọi kho. */
    public static WarehouseScope allWarehouses() {
        return new WarehouseScope(true, PLACEHOLDER);
    }

    /** Chỉ xem các kho trong danh sách. Danh sách rỗng được hiểu là "xem tất cả". */
    public static WarehouseScope of(List<Long> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return allWarehouses();
        }
        return new WarehouseScope(false, List.copyOf(warehouseIds));
    }

    /** Chỉ xem một kho duy nhất. */
    public static WarehouseScope single(Long warehouseId) {
        return new WarehouseScope(false, List.of(warehouseId));
    }

    /** Kiểm tra một kho có nằm trong phạm vi được xem hay không. */
    public boolean includes(Long warehouseId) {
        return all || (warehouseId != null && ids.contains(warehouseId));
    }
}
