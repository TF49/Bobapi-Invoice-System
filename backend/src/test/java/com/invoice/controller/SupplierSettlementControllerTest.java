package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.SupplierSettlementRequest;
import com.invoice.dto.SupplierSettlementResponse;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.service.SupplierSettlementService;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SupplierSettlementControllerTest {

    @Test
    void rejectsMalformedSettlementIdempotencyKey() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<SupplierSettlementController>> violations = validator.forExecutables()
                .validateParameters(
                        new SupplierSettlementController(mock(SupplierSettlementService.class)),
                        findCreateMethod(),
                        new Object[]{new SupplierSettlementRequest(), "bad key", new JwtUserPrincipal(1L, "admin", "ADMIN", 0L)});

        assertThat(violations).isNotEmpty();
    }

    private java.lang.reflect.Method findCreateMethod() {
        return java.util.Arrays.stream(SupplierSettlementController.class.getMethods())
                .filter(method -> method.getName().equals("createSettlement"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void createSettlement_callsServiceAndReturnsSuccess() {
        SupplierSettlementService service = mock(SupplierSettlementService.class);
        SupplierSettlementController controller = new SupplierSettlementController(service);

        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "admin", "ADMIN", 0L);
        SupplierSettlementRequest request = new SupplierSettlementRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setSellerCompany("TECH");
        request.setInvoiceCategory("VAT_SPECIAL");
        request.setRemark("结算备注");

        SupplierSettlementResponse mockResponse = new SupplierSettlementResponse(
                1L, new BigDecimal("500.00"), "TECH", "VAT_SPECIAL", "结算备注", "admin", OffsetDateTime.now(), false, null, null, null
        );
        when(service.createSettlement(new BigDecimal("500.00"), "TECH", "VAT_SPECIAL", "结算备注", 1L, null)).thenReturn(mockResponse);

        ApiResponse<SupplierSettlementResponse> response = controller.createSettlement(request, null, principal);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().settlementAmount()).isEqualByComparingTo("500.00");
        assertThat(response.getData().operatorName()).isEqualTo("admin");
        verify(service, times(1)).createSettlement(new BigDecimal("500.00"), "TECH", "VAT_SPECIAL", "结算备注", 1L, null);
    }

    @Test
    void getSettlementHistory_callsServiceAndReturnsList() {
        SupplierSettlementService service = mock(SupplierSettlementService.class);
        SupplierSettlementController controller = new SupplierSettlementController(service);

        List<SupplierSettlementResponse> mockList = List.of(
                new SupplierSettlementResponse(1L, new BigDecimal("500.00"), "DEFAULT", "NORMAL", "备注", "admin", OffsetDateTime.now(), false, null, null, null)
        );
        when(service.getSettlementHistory()).thenReturn(mockList);

        ApiResponse<List<SupplierSettlementResponse>> response = controller.getSettlementHistory();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        verify(service, times(1)).getSettlementHistory();
    }

    @Test
    void requestValidation_requiresValidChannel() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SupplierSettlementRequest request = new SupplierSettlementRequest();
        request.setAmount(new BigDecimal("100.00"));

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("sellerCompany", "invoiceCategory");

        request.setSellerCompany("OTHER");
        request.setInvoiceCategory("NORMAL");
        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("sellerCompany");

        request.setSellerCompany("DEFAULT");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void voidSettlement_delegatesToServiceWithOperator() {
        SupplierSettlementService service = mock(SupplierSettlementService.class);
        SupplierSettlementController controller = new SupplierSettlementController(service);
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "admin", "ADMIN", 0L);
        com.invoice.dto.SupplierSettlementVoidRequest request = new com.invoice.dto.SupplierSettlementVoidRequest();
        request.setReason("选错通道");
        SupplierSettlementResponse voided = new SupplierSettlementResponse(
                5L, new BigDecimal("100.00"), "DEFAULT", "NORMAL", null, "admin", OffsetDateTime.now(),
                true, OffsetDateTime.now(), "admin", "选错通道");
        when(service.voidSettlement(5L, "选错通道", 1L)).thenReturn(voided);

        ApiResponse<SupplierSettlementResponse> response = controller.voidSettlement(5L, request, principal);

        assertThat(response.getData().voided()).isTrue();
        verify(service).voidSettlement(5L, "选错通道", 1L);
    }

    @Test
    void voidRequestValidation_requiresReason() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        com.invoice.dto.SupplierSettlementVoidRequest request = new com.invoice.dto.SupplierSettlementVoidRequest();
        request.setReason("  ");
        assertThat(validator.validate(request)).isNotEmpty();
        request.setReason("x".repeat(201));
        assertThat(validator.validate(request)).isNotEmpty();
        request.setReason("填错金额");
        assertThat(validator.validate(request)).isEmpty();
    }
}
