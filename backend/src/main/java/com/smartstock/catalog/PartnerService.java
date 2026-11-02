package com.smartstock.catalog;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.catalog.CatalogDtos.PartnerResponse;
import com.smartstock.catalog.CatalogDtos.SavePartnerRequest;
import com.smartstock.common.BusinessException;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ nhà cung cấp và khách hàng.
 *
 * <p>Hai danh mục này có cùng khuôn dạng dữ liệu nên dùng chung một service và chung
 * DTO {@code PartnerResponse}, giảm mã trùng lặp mà vẫn giữ hai bảng riêng (vì vai trò
 * nghiệp vụ khác nhau: NCC đứng trên phiếu nhập, khách hàng đứng trên phiếu xuất).
 *
 * <p>Xóa là xóa mềm: phiếu cũ vẫn phải hiển thị được tên đối tác.
 */
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    // ============================== Nhà cung cấp ==============================

    @Transactional(readOnly = true)
    public PageResponse<PartnerResponse> searchSuppliers(String keyword, Pageable pageable) {
        return PageResponse.from(supplierRepository.search(normalize(keyword), pageable), PartnerResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PartnerResponse> listSuppliers() {
        return supplierRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(PartnerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerResponse getSupplier(Long id) {
        return PartnerResponse.from(findSupplierOrThrow(id));
    }

    @Transactional
    public PartnerResponse createSupplier(SavePartnerRequest request) {
        if (supplierRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(request.code())) {
            throw new BusinessException("Mã nhà cung cấp đã tồn tại.", "SUPPLIER_CODE_EXISTS");
        }
        Supplier supplier = Supplier.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .phone(emptyToNull(request.phone()))
                .email(emptyToNull(request.email()))
                .address(emptyToNull(request.address()))
                .taxCode(emptyToNull(request.taxCode()))
                .note(emptyToNull(request.note()))
                .build();
        Supplier saved = supplierRepository.save(supplier);
        auditService.logChange(
                AuditAction.CREATE,
                "Supplier",
                saved.getId(),
                "Thêm nhà cung cấp " + saved.getCode(),
                null,
                PartnerResponse.from(saved));
        return PartnerResponse.from(saved);
    }

    @Transactional
    public PartnerResponse updateSupplier(Long id, SavePartnerRequest request) {
        Supplier supplier = findSupplierOrThrow(id);
        PartnerResponse before = PartnerResponse.from(supplier);

        if (supplierRepository.existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(request.code(), id)) {
            throw new BusinessException("Mã nhà cung cấp đã tồn tại.", "SUPPLIER_CODE_EXISTS");
        }
        supplier.setCode(request.code().trim().toUpperCase());
        supplier.setName(request.name().trim());
        supplier.setPhone(emptyToNull(request.phone()));
        supplier.setEmail(emptyToNull(request.email()));
        supplier.setAddress(emptyToNull(request.address()));
        supplier.setTaxCode(emptyToNull(request.taxCode()));
        supplier.setNote(emptyToNull(request.note()));

        Supplier saved = supplierRepository.save(supplier);
        auditService.logChange(
                AuditAction.UPDATE,
                "Supplier",
                id,
                "Cập nhật nhà cung cấp " + saved.getCode(),
                before,
                PartnerResponse.from(saved));
        return PartnerResponse.from(saved);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        Supplier supplier = findSupplierOrThrow(id);
        supplier.setDeletedAt(LocalDateTime.now());
        supplierRepository.save(supplier);
        auditService.log(AuditAction.DELETE, "Supplier", id, "Xóa nhà cung cấp " + supplier.getCode());
    }

    /** Lấy entity NCC cho service phiếu dùng. */
    @Transactional(readOnly = true)
    public Supplier requireSupplier(Long id) {
        return findSupplierOrThrow(id);
    }

    // =============================== Khách hàng ===============================

    @Transactional(readOnly = true)
    public PageResponse<PartnerResponse> searchCustomers(String keyword, Pageable pageable) {
        return PageResponse.from(customerRepository.search(normalize(keyword), pageable), PartnerResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PartnerResponse> listCustomers() {
        return customerRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(PartnerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerResponse getCustomer(Long id) {
        return PartnerResponse.from(findCustomerOrThrow(id));
    }

    @Transactional
    public PartnerResponse createCustomer(SavePartnerRequest request) {
        if (customerRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(request.code())) {
            throw new BusinessException("Mã khách hàng đã tồn tại.", "CUSTOMER_CODE_EXISTS");
        }
        Customer customer = Customer.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .phone(emptyToNull(request.phone()))
                .email(emptyToNull(request.email()))
                .address(emptyToNull(request.address()))
                .taxCode(emptyToNull(request.taxCode()))
                .note(emptyToNull(request.note()))
                .build();
        Customer saved = customerRepository.save(customer);
        auditService.logChange(
                AuditAction.CREATE,
                "Customer",
                saved.getId(),
                "Thêm khách hàng " + saved.getCode(),
                null,
                PartnerResponse.from(saved));
        return PartnerResponse.from(saved);
    }

    @Transactional
    public PartnerResponse updateCustomer(Long id, SavePartnerRequest request) {
        Customer customer = findCustomerOrThrow(id);
        PartnerResponse before = PartnerResponse.from(customer);

        if (customerRepository.existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(request.code(), id)) {
            throw new BusinessException("Mã khách hàng đã tồn tại.", "CUSTOMER_CODE_EXISTS");
        }
        customer.setCode(request.code().trim().toUpperCase());
        customer.setName(request.name().trim());
        customer.setPhone(emptyToNull(request.phone()));
        customer.setEmail(emptyToNull(request.email()));
        customer.setAddress(emptyToNull(request.address()));
        customer.setTaxCode(emptyToNull(request.taxCode()));
        customer.setNote(emptyToNull(request.note()));

        Customer saved = customerRepository.save(customer);
        auditService.logChange(
                AuditAction.UPDATE,
                "Customer",
                id,
                "Cập nhật khách hàng " + saved.getCode(),
                before,
                PartnerResponse.from(saved));
        return PartnerResponse.from(saved);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = findCustomerOrThrow(id);
        customer.setDeletedAt(LocalDateTime.now());
        customerRepository.save(customer);
        auditService.log(AuditAction.DELETE, "Customer", id, "Xóa khách hàng " + customer.getCode());
    }

    /** Lấy entity khách hàng cho service phiếu dùng. */
    @Transactional(readOnly = true)
    public Customer requireCustomer(Long id) {
        return findCustomerOrThrow(id);
    }

    private Supplier findSupplierOrThrow(Long id) {
        return supplierRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("nhà cung cấp", id));
    }

    private Customer findCustomerOrThrow(Long id) {
        return customerRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("khách hàng", id));
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
