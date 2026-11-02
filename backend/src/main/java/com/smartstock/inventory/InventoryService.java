package com.smartstock.inventory;

import com.smartstock.catalog.Product;
import com.smartstock.common.BusinessException;
import com.smartstock.common.InsufficientStockException;
import com.smartstock.common.InsufficientStockException.ShortageDetail;
import com.smartstock.common.MoneyUtils;
import com.smartstock.security.SecurityUtils;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SERVICE DUY NHẤT được phép thay đổi tồn kho. Đây là trái tim của hệ thống.
 *
 * <p><b>Vì sao phải tập trung một chỗ?</b> Nếu nhiều nơi cùng sửa bảng {@code inventories}
 * thì rất dễ xảy ra: cộng tồn mà quên ghi sổ giao dịch, trừ tồn mà không kiểm tra đủ hàng,
 * hoặc hai người cùng xuất một lô hàng. Gom về một service giúp mọi thay đổi tồn đều đi
 * qua cùng một bộ kiểm tra và luôn ghi kèm một dòng {@code stock_transactions}.
 *
 * <p><b>Bất biến hệ thống:</b>
 *
 * <pre>
 *   inventories.quantity == SUM(stock_transactions.quantity_change)
 * </pre>
 *
 * cho mỗi cặp (sản phẩm, kho). Có test tự động kiểm tra điều này.
 *
 * <p><b>Chống tranh chấp khi nhiều người thao tác cùng lúc:</b>
 *
 * <ol>
 *   <li>Mọi phương thức ghi đều bắt buộc nằm trong một giao dịch đang mở
 *       ({@code Propagation.MANDATORY}) - tức là phải được gọi từ service phiếu, để việc
 *       trừ tồn và đổi trạng thái phiếu cùng thành công hoặc cùng thất bại.</li>
 *   <li>Trước khi sửa, dòng tồn được khóa bằng {@code SELECT ... FOR UPDATE}.</li>
 *   <li>Khi một phiếu có nhiều dòng, các dòng được sắp xếp theo {@code product_id} tăng
 *       dần rồi mới khóa lần lượt. Nhờ thứ tự khóa thống nhất, hai giao dịch không bao
 *       giờ khóa chéo nhau nên không xảy ra deadlock.</li>
 * </ol>
 *
 * <p>Giải thích cho người quen PHP: {@code @Transactional} tương đương
 * {@code DB::transaction(function () { ... })} của Laravel. Khác biệt là ở Java, nếu một
 * ngoại lệ không kiểm tra (RuntimeException) được ném ra, Spring tự động rollback toàn bộ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;

    /**
     * Cộng tồn khi hoàn tất phiếu nhập và cập nhật giá vốn bình quân gia quyền.
     *
     * <p>Công thức giá vốn mới:
     *
     * <pre>
     *   giá vốn mới = (tồn cũ x giá vốn cũ + SL nhập x đơn giá nhập) / (tồn cũ + SL nhập)
     * </pre>
     *
     * <p>Ví dụ: đang có 10 thùng giá vốn 100.000đ, nhập thêm 10 thùng giá 120.000đ thì
     * giá vốn mới = (10x100.000 + 10x120.000) / 20 = 110.000đ.
     *
     * <p>Trường hợp đặc biệt: nếu tồn cũ bằng 0 (hoặc âm do dữ liệu cũ) thì lấy luôn
     * đơn giá nhập làm giá vốn, vì không có gì để bình quân.
     *
     * @param movements danh sách dòng cần cộng tồn
     * @return các giao dịch đã ghi vào sổ kho
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockTransaction> applyReceipt(List<StockMovement> movements) {
        List<StockTransaction> result = new ArrayList<>();

        for (StockMovement movement : sortByProductId(movements)) {
            Inventory inventory = lockOrCreateInventory(movement.product(), movement.warehouse());

            BigDecimal oldQuantity = inventory.getQuantity();
            BigDecimal oldAvgCost = inventory.getAvgCost();
            BigDecimal inQuantity = MoneyUtils.quantity(movement.quantity());
            BigDecimal inUnitPrice = MoneyUtils.money(movement.unitPrice());

            if (!MoneyUtils.isPositive(inQuantity)) {
                throw new BusinessException("Số lượng nhập phải lớn hơn 0.", "INVALID_QUANTITY");
            }

            BigDecimal newQuantity = oldQuantity.add(inQuantity);
            BigDecimal newAvgCost = calculateWeightedAverageCost(oldQuantity, oldAvgCost, inQuantity, inUnitPrice);

            inventory.setQuantity(MoneyUtils.quantity(newQuantity));
            inventory.setAvgCost(MoneyUtils.money(newAvgCost));
            inventoryRepository.save(inventory);

            result.add(writeTransaction(
                    movement, TransactionType.RECEIPT, inQuantity, inUnitPrice, inventory.getQuantity()));
        }
        return result;
    }

    /**
     * Trừ tồn khi hoàn tất phiếu xuất.
     *
     * <p>Kiểm tra đủ tồn được thực hiện SAU khi đã khóa dòng tồn, nên con số dùng để so
     * sánh chắc chắn là con số mới nhất. Đây là lý do hai phiếu xuất đồng thời mà tổng
     * lượng vượt tồn thì chỉ một phiếu thành công.
     *
     * <p>Giá vốn KHÔNG đổi khi xuất (phương pháp bình quân gia quyền), nhưng giá vốn tại
     * thời điểm xuất được ghi vào dòng phiếu và sổ kho để tái hiện giá trị xuất về sau.
     *
     * @throws InsufficientStockException nếu bất kỳ dòng nào không đủ tồn
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockTransaction> applyIssue(List<StockMovement> movements) {
        List<StockTransaction> result = new ArrayList<>();
        List<ShortageDetail> shortages = new ArrayList<>();
        List<Inventory> lockedInventories = new ArrayList<>();
        List<StockMovement> sorted = sortByProductId(movements);

        // Vòng 1: khóa mọi dòng tồn liên quan và thu thập toàn bộ chỗ thiếu.
        // Thu thập hết rồi mới báo lỗi để người dùng thấy đầy đủ các dòng cần sửa,
        // thay vì sửa xong dòng này lại báo lỗi dòng khác.
        for (StockMovement movement : sorted) {
            BigDecimal requested = MoneyUtils.quantity(movement.quantity());
            if (!MoneyUtils.isPositive(requested)) {
                throw new BusinessException("Số lượng xuất phải lớn hơn 0.", "INVALID_QUANTITY");
            }

            Inventory inventory = inventoryRepository
                    .findForUpdate(movement.product().getId(), movement.warehouse().getId())
                    .orElse(null);
            BigDecimal available = inventory == null ? BigDecimal.ZERO : inventory.getQuantity();

            if (available.compareTo(requested) < 0) {
                shortages.add(new ShortageDetail(
                        movement.product().getId(),
                        movement.product().getSku(),
                        movement.product().getName(),
                        requested,
                        available));
            }
            lockedInventories.add(inventory);
        }

        if (!shortages.isEmpty()) {
            // Ném ngoại lệ -> Spring rollback giao dịch -> không có dòng nào bị trừ
            throw new InsufficientStockException(shortages);
        }

        // Vòng 2: trừ tồn và ghi sổ giao dịch
        for (int i = 0; i < sorted.size(); i++) {
            StockMovement movement = sorted.get(i);
            Inventory inventory = lockedInventories.get(i);
            BigDecimal outQuantity = MoneyUtils.quantity(movement.quantity());

            // Giá vốn tại thời điểm xuất (dùng cho báo cáo giá trị xuất kho)
            BigDecimal costAtIssue = inventory.getAvgCost();

            inventory.setQuantity(MoneyUtils.quantity(inventory.getQuantity().subtract(outQuantity)));
            inventoryRepository.save(inventory);

            result.add(writeTransaction(
                    movement,
                    TransactionType.ISSUE,
                    outQuantity.negate(),
                    costAtIssue,
                    inventory.getQuantity()));
        }
        return result;
    }

    /**
     * Điều chỉnh tồn về số đếm thực tế sau khi duyệt phiếu kiểm kê.
     *
     * <p>Chênh lệch dương (đếm nhiều hơn hệ thống) thì cộng thêm; chênh lệch âm thì trừ bớt.
     * Giá vốn không thay đổi: hàng vẫn là hàng cũ, chỉ số lượng được sửa cho đúng thực tế.
     *
     * @param movements {@code quantity} là SỐ ĐẾM THỰC TẾ, không phải chênh lệch
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockTransaction> applyStocktakeAdjustment(List<StockMovement> movements) {
        List<StockTransaction> result = new ArrayList<>();

        for (StockMovement movement : sortByProductId(movements)) {
            Inventory inventory = lockOrCreateInventory(movement.product(), movement.warehouse());

            BigDecimal actualQuantity = MoneyUtils.quantity(movement.quantity());
            if (actualQuantity.signum() < 0) {
                throw new BusinessException("Số lượng thực tế không được âm.", "INVALID_QUANTITY");
            }

            BigDecimal difference = actualQuantity.subtract(inventory.getQuantity());
            if (MoneyUtils.isZero(difference)) {
                continue; // không lệch thì không cần ghi giao dịch
            }

            inventory.setQuantity(actualQuantity);
            inventoryRepository.save(inventory);

            result.add(writeTransaction(
                    movement, TransactionType.ADJUSTMENT, difference, inventory.getAvgCost(), actualQuantity));
        }
        return result;
    }

    /**
     * Đảo ngược các giao dịch của một phiếu đã hoàn tất (khi hủy phiếu).
     *
     * <p>Cách làm: với mỗi giao dịch gốc, ghi thêm một giao dịch {@code REVERSAL} có
     * lượng ngược dấu. Không sửa và không xóa giao dịch gốc, nhờ vậy nhật ký kho vẫn
     * thể hiện đúng rằng "đã từng nhập, sau đó bị hủy".
     *
     * <p>Hủy phiếu NHẬP sẽ làm giảm tồn, nên phải kiểm tra tồn còn đủ để trừ lại - nếu
     * hàng đã xuất đi rồi thì chặn, buộc người dùng xử lý bằng phiếu khác.
     *
     * @param originalTransactions giao dịch gốc của phiếu bị hủy
     * @param reason lý do hủy, được ghi vào ghi chú của giao dịch đảo ngược
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockTransaction> reverseTransactions(List<StockTransaction> originalTransactions, String reason) {
        List<StockTransaction> result = new ArrayList<>();
        List<ShortageDetail> shortages = new ArrayList<>();

        List<StockTransaction> sorted = originalTransactions.stream()
                .sorted(Comparator.comparing(t -> t.getProduct().getId()))
                .toList();

        // Vòng 1: khóa và kiểm tra khả năng đảo ngược
        List<Inventory> lockedInventories = new ArrayList<>();
        for (StockTransaction original : sorted) {
            Inventory inventory = lockOrCreateInventory(original.getProduct(), original.getWarehouse());
            BigDecimal reversalChange = original.getQuantityChange().negate();

            // reversalChange âm nghĩa là phải trừ bớt tồn -> cần kiểm tra đủ hàng
            if (reversalChange.signum() < 0
                    && inventory.getQuantity().compareTo(reversalChange.abs()) < 0) {
                shortages.add(new ShortageDetail(
                        original.getProduct().getId(),
                        original.getProduct().getSku(),
                        original.getProduct().getName(),
                        reversalChange.abs(),
                        inventory.getQuantity()));
            }
            lockedInventories.add(inventory);
        }

        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }

        // Vòng 2: ghi giao dịch đảo ngược
        for (int i = 0; i < sorted.size(); i++) {
            StockTransaction original = sorted.get(i);
            Inventory inventory = lockedInventories.get(i);
            BigDecimal reversalChange = original.getQuantityChange().negate();

            inventory.setQuantity(MoneyUtils.quantity(inventory.getQuantity().add(reversalChange)));
            inventoryRepository.save(inventory);

            StockTransaction reversal = StockTransaction.builder()
                    .product(original.getProduct())
                    .warehouse(original.getWarehouse())
                    .type(TransactionType.REVERSAL)
                    .refType(original.getRefType())
                    .refId(original.getRefId())
                    .refCode(original.getRefCode())
                    .quantityChange(MoneyUtils.quantity(reversalChange))
                    .unitCost(original.getUnitCost())
                    .balanceAfter(inventory.getQuantity())
                    .note("Đảo ngược do hủy phiếu" + (reason == null || reason.isBlank() ? "" : ": " + reason))
                    .transactionDate(LocalDateTime.now())
                    .createdBy(SecurityUtils.getCurrentUserIdOrNull())
                    .createdAt(LocalDateTime.now())
                    .build();

            result.add(stockTransactionRepository.save(reversal));
        }
        return result;
    }

    /**
     * Kiểm tra đủ tồn mà KHÔNG thay đổi gì (không khóa dòng).
     *
     * <p>Dùng cho hai việc: cảnh báo ngay trên màn hình lập phiếu, và kiểm tra sơ bộ khi
     * gửi duyệt. Lần kiểm tra có tính quyết định vẫn là lúc hoàn tất phiếu, bên trong
     * {@link #applyIssue(List)} - vì chỉ ở đó dòng tồn mới được khóa.
     *
     * @return danh sách các dòng thiếu tồn, rỗng nghĩa là đủ hàng
     */
    @Transactional(readOnly = true)
    public List<ShortageDetail> checkAvailability(List<StockMovement> movements) {
        List<ShortageDetail> shortages = new ArrayList<>();
        for (StockMovement movement : movements) {
            BigDecimal available = getQuantityOnHand(
                    movement.product().getId(), movement.warehouse().getId());
            BigDecimal requested = MoneyUtils.quantity(movement.quantity());
            if (available.compareTo(requested) < 0) {
                shortages.add(new ShortageDetail(
                        movement.product().getId(),
                        movement.product().getSku(),
                        movement.product().getName(),
                        requested,
                        available));
            }
        }
        return shortages;
    }

    /** Số tồn hiện tại của một sản phẩm tại một kho (0 nếu chưa từng có giao dịch). */
    @Transactional(readOnly = true)
    public BigDecimal getQuantityOnHand(Long productId, Long warehouseId) {
        return inventoryRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .map(Inventory::getQuantity)
                .orElse(BigDecimal.ZERO.setScale(MoneyUtils.QUANTITY_SCALE));
    }

    /** Giá vốn bình quân hiện tại của một sản phẩm tại một kho. */
    @Transactional(readOnly = true)
    public BigDecimal getAverageCost(Long productId, Long warehouseId) {
        return inventoryRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .map(Inventory::getAvgCost)
                .orElse(BigDecimal.ZERO.setScale(MoneyUtils.MONEY_SCALE));
    }

    /**
     * Tính giá vốn bình quân gia quyền.
     *
     * <p>Tách thành phương thức riêng để viết unit test trực tiếp cho công thức.
     */
    public BigDecimal calculateWeightedAverageCost(
            BigDecimal oldQuantity, BigDecimal oldAvgCost, BigDecimal inQuantity, BigDecimal inUnitPrice) {
        BigDecimal totalQuantity = MoneyUtils.zeroIfNull(oldQuantity).add(MoneyUtils.zeroIfNull(inQuantity));

        // Tồn cũ <= 0 thì không có gì để bình quân: lấy luôn đơn giá nhập
        if (MoneyUtils.zeroIfNull(oldQuantity).signum() <= 0) {
            return MoneyUtils.money(inUnitPrice);
        }
        if (totalQuantity.signum() <= 0) {
            return MoneyUtils.money(oldAvgCost);
        }

        BigDecimal oldValue = oldQuantity.multiply(MoneyUtils.zeroIfNull(oldAvgCost));
        BigDecimal inValue = MoneyUtils.zeroIfNull(inQuantity).multiply(MoneyUtils.zeroIfNull(inUnitPrice));
        return oldValue.add(inValue).divide(totalQuantity, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Lấy dòng tồn đã khóa, tạo mới nếu sản phẩm chưa từng có tồn ở kho này.
     *
     * <p>Vì đang trong giao dịch có khóa, hai lần gọi đồng thời cho cùng cặp
     * (sản phẩm, kho) sẽ nối tiếp nhau; ràng buộc UNIQUE ở CSDL là lớp bảo vệ cuối cùng.
     */
    private Inventory lockOrCreateInventory(Product product, Warehouse warehouse) {
        Optional<Inventory> existing = inventoryRepository.findForUpdate(product.getId(), warehouse.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Inventory created = Inventory.builder()
                .product(product)
                .warehouse(warehouse)
                .quantity(BigDecimal.ZERO.setScale(MoneyUtils.QUANTITY_SCALE))
                .avgCost(BigDecimal.ZERO.setScale(MoneyUtils.MONEY_SCALE))
                .build();
        return inventoryRepository.saveAndFlush(created);
    }

    /** Ghi một dòng vào sổ giao dịch kho. */
    private StockTransaction writeTransaction(
            StockMovement movement,
            TransactionType type,
            BigDecimal quantityChange,
            BigDecimal unitCost,
            BigDecimal balanceAfter) {

        StockTransaction transaction = StockTransaction.builder()
                .product(movement.product())
                .warehouse(movement.warehouse())
                .type(type)
                .refType(movement.refType())
                .refId(movement.refId())
                .refCode(movement.refCode())
                .quantityChange(MoneyUtils.quantity(quantityChange))
                .unitCost(MoneyUtils.money(unitCost))
                .balanceAfter(MoneyUtils.quantity(balanceAfter))
                .note(movement.note())
                .transactionDate(movement.transactionDate() == null ? LocalDateTime.now() : movement.transactionDate())
                .createdBy(SecurityUtils.getCurrentUserIdOrNull())
                .createdAt(LocalDateTime.now())
                .build();
        return stockTransactionRepository.save(transaction);
    }

    /**
     * Sắp xếp theo product_id tăng dần trước khi khóa dòng.
     *
     * <p>Đây là biện pháp CHỐNG DEADLOCK: nếu phiếu A khóa sản phẩm 1 rồi 2, còn phiếu B
     * khóa sản phẩm 2 rồi 1, hai giao dịch sẽ chờ nhau vĩnh viễn. Khi mọi nơi đều khóa
     * theo cùng một thứ tự thì tình huống đó không thể xảy ra.
     */
    private List<StockMovement> sortByProductId(List<StockMovement> movements) {
        if (movements == null || movements.isEmpty()) {
            throw new BusinessException("Phiếu phải có ít nhất một dòng hàng.", "EMPTY_DOCUMENT");
        }
        return movements.stream()
                .sorted(Comparator.comparing(m -> m.product().getId()))
                .toList();
    }

    /**
     * Một thay đổi tồn cần thực hiện.
     *
     * @param quantity số lượng dương (ý nghĩa tăng/giảm do phương thức gọi quyết định)
     * @param unitPrice đơn giá nhập; khi xuất thì bỏ qua vì lấy giá vốn hiện hành
     * @param transactionDate ngày hiệu lực; null thì lấy thời điểm hiện tại
     */
    public record StockMovement(
            Product product,
            Warehouse warehouse,
            BigDecimal quantity,
            BigDecimal unitPrice,
            RefType refType,
            Long refId,
            String refCode,
            String note,
            LocalDateTime transactionDate) {}
}
