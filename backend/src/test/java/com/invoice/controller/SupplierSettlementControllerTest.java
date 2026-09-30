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
        request.setRemark("结算备注");

        SupplierSettlementResponse mockResponse = new SupplierSettlementResponse(
                1L, new BigDecimal("500.00"), "结算备注", "admin", OffsetDateTime.now()
        );
        when(service.createSettlement(new BigDecimal("500.00"), "结算备注", 1L, null)).thenReturn(mockResponse);

        ApiResponse<SupplierSettlementResponse> response = controller.createSettlement(request, null, principal);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().settlementAmount()).isEqualByComparingTo("500.00");
        assertThat(response.getData().operatorName()).isEqualTo("admin");
        verify(service, times(1)).createSettlement(new BigDecimal("500.00"), "结算备注", 1L, null);
    }

    @Test
    void getSettlementHistory_callsServiceAndReturnsList() {
        SupplierSettlementService service = mock(SupplierSettlementService.class);
        SupplierSettlementController controller = new SupplierSettlementController(service);

        List<SupplierSettlementResponse> mockList = List.of(
                new SupplierSettlementResponse(1L, new BigDecimal("500.00"), "备注", "admin", OffsetDateTime.now())
        );
        when(service.getSettlementHistory()).thenReturn(mockList);

        ApiResponse<List<SupplierSettlementResponse>> response = controller.getSettlementHistory();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        verify(service, times(1)).getSettlementHistory();
    }
}
