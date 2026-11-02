package com.smartstock.issue;

/** Loại phiếu xuất kho. Mỗi loại có yêu cầu bắt buộc riêng về đối tác / lý do. */
public enum IssueType {
    /** Xuất bán cho khách - bắt buộc chọn khách hàng. */
    SALE("Xuất bán", false, true, false),

    /** Trả hàng lại nhà cung cấp - bắt buộc chọn nhà cung cấp. */
    SUPPLIER_RETURN("Trả nhà cung cấp", true, false, false),

    /** Xuất dùng nội bộ - bắt buộc ghi lý do. */
    INTERNAL("Xuất dùng nội bộ", false, false, true),

    /** Xuất hủy (hàng hỏng, hết hạn) - bắt buộc ghi lý do. */
    DISPOSAL("Xuất hủy", false, false, true);

    private final String displayName;
    private final boolean supplierRequired;
    private final boolean customerRequired;
    private final boolean reasonRequired;

    IssueType(String displayName, boolean supplierRequired, boolean customerRequired, boolean reasonRequired) {
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
