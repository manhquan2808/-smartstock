package com.smartstock.receipt;

/**
 * Loại phiếu nhập kho. Mỗi loại có yêu cầu bắt buộc riêng về đối tác / lý do.
 */
public enum ReceiptType {
    /** Nhập mua từ nhà cung cấp - bắt buộc chọn nhà cung cấp. */
    PURCHASE("Nhập mua", true, false, false),

    /** Khách trả lại hàng - bắt buộc chọn khách hàng. */
    CUSTOMER_RETURN("Khách trả lại", false, true, false),

    /** Nhập khác (điều chỉnh, quà tặng, hàng mẫu...) - bắt buộc ghi lý do. */
    OTHER("Nhập khác", false, false, true);

    private final String displayName;
    private final boolean supplierRequired;
    private final boolean customerRequired;
    private final boolean reasonRequired;

    ReceiptType(String displayName, boolean supplierRequired, boolean customerRequired, boolean reasonRequired) {
        this.displayName = displayName;
        this.supplierRequired = supplierRequired;
        this.customerRequired = customerRequired;
        this.reasonRequired = reasonRequired;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSupplierRequired() {
        return supplierRequired;
    }

    public boolean isCustomerRequired() {
        return customerRequired;
    }

    public boolean isReasonRequired() {
        return reasonRequired;
    }
}
