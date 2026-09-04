package com.brandempiricism.etocrm.commons;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        detail.setTitle("Request validation failed");
        detail.setType(URI.create("https://eto-crm.example/problems/validation"));
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail provisioningUnavailable(ServiceUnavailableException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        detail.setTitle("Tenant provisioning unavailable");
        detail.setType(URI.create("https://eto-crm.example/problems/tenant-provisioning-unavailable"));
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }
}
