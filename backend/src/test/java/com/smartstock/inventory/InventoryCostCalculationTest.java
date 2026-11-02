package com.smartstock.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit test cho công thức GIÁ VỐN BÌNH QUÂN GIA QUYỀN.
 *
 * <p>Đây là unit test thuần: không cần cơ sở dữ liệu, không cần Spring, nên chạy rất nhanh.
 * Công thức được tách thành phương thức riêng trong {@code InventoryService} chính là để
 * kiểm thử trực tiếp như thế này.
 *
 * <p>Công thức:
 *
 * <pre>
 *   giá vốn mới = (tồn cũ x giá vốn cũ + SL nhập x đơn giá nhập) / (tồn cũ + SL nhập)
 * </pre>
 */
@DisplayName("Giá vốn bình quân gia quyền")
class InventoryCostCalculationTest {

    /** Không cần tiêm phụ thuộc vì phương thức tính toán là hàm thuần. */
    private final InventoryService inventoryService = new InventoryService(null, null);

    @Test
    @DisplayName("Tồn cũ bằng 0 thì giá vốn mới chính là đơn giá nhập")
    void tonCuBangKhong_layDonGiaNhap() {
        BigDecimal result = inventoryService.calculateWeightedAverageCost(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("150000"));

        assertThat(result).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("Ví dụ trong đặc tả: 10 thùng giá 100.000 nhập thêm 10 thùng giá 120.000 thì giá vốn là 110.000")
    void viDuTrongDacTa() {
        BigDecimal result = inventoryService.calculateWeightedAverageCost(
                new BigDecimal("10"), new BigDecimal("100000"), new BigDecimal("10"), new BigDecimal("120000"));

        assertThat(result).isEqualByComparingTo("110000");
    }

    @Test
    @DisplayName("Nhập thêm đúng giá vốn hiện tại thì giá vốn không đổi")
    void nhapDungGiaVonThiKhongDoi() {
        BigDecimal result = inventoryService.calculateWeightedAverageCost(
                new BigDecimal("25"), new BigDecimal("80000"), new BigDecimal("75"), new BigDecimal("80000"));

        assertThat(result).isEqualByComparingTo("80000");
    }

    @ParameterizedTest(name = "tồn {0} x {1} + nhập {2} x {3} = giá vốn {4}")
    @CsvSource({
        // tồn cũ, giá vốn cũ, SL nhập, đơn giá nhập, giá vốn mong đợi
        "100, 50000,  100, 60000,  55000", // trộn hai lô bằng nhau -> trung bình đúng giữa
        "90,  10000,  10,  20000,  11000", // lô nhập nhỏ kéo giá lên ít
        "10,  20000,  90,  10000,  11000", // lô nhập lớn kéo giá xuống nhiều
        "1,   99000,  1,   1000,   50000", // số lượng nhỏ, chênh lệch giá lớn
        "5,   30000,  5,   0,      15000" // nhập hàng tặng (đơn giá 0) làm giảm giá vốn
    })
    @DisplayName("Các trường hợp bình quân gia quyền tiêu biểu")
    void cacTruongHopBinhQuan(
            BigDecimal oldQuantity,
            BigDecimal oldAvgCost,
            BigDecimal inQuantity,
            BigDecimal inUnitPrice,
            BigDecimal expected) {

        BigDecimal result =
                inventoryService.calculateWeightedAverageCost(oldQuantity, oldAvgCost, inQuantity, inUnitPrice);

        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Kết quả luôn được làm tròn về 2 chữ số thập phân")
    void luonLamTronHaiChuSo() {
        // (3 x 10000 + 4 x 10001) / 7 = 10000.5714... -> 10000.57
        BigDecimal result = inventoryService.calculateWeightedAverageCost(
                new BigDecimal("3"), new BigDecimal("10000"), new BigDecimal("4"), new BigDecimal("10001"));

        assertThat(result).isEqualByComparingTo("10000.57");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Giá trị null được coi là 0, không gây lỗi NullPointerException")
    void giaTriNullKhongGayLoi() {
        BigDecimal result =
                inventoryService.calculateWeightedAverageCost(new BigDecimal("10"), null, new BigDecimal("10"), null);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Hỗ trợ số lượng thập phân (đơn vị kg, lít)")
    void hoTroSoLuongThapPhan() {
        // (2.5 x 40000 + 2.5 x 60000) / 5 = 50000
        BigDecimal result = inventoryService.calculateWeightedAverageCost(
                new BigDecimal("2.500"), new BigDecimal("40000"), new BigDecimal("2.500"), new BigDecimal("60000"));

        assertThat(result).isEqualByComparingTo("50000");
    }
}
