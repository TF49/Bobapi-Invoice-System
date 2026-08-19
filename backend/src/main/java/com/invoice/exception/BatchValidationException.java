package com.invoice.exception;

import com.invoice.dto.BatchInvoiceRowError;
import org.springframework.http.HttpStatus;

import java.util.List;

public class BatchValidationException extends BusinessException {

    private final List<BatchInvoiceRowError> errors;

    public BatchValidationException(List<BatchInvoiceRowError> errors) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, 42202, "批量申请包含无效数据");
        this.errors = List.copyOf(errors);
    }

    public List<BatchInvoiceRowError> getErrors() {
        return errors;
    }
}
