package com.brandempiricism.etocrm.commons;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail resourceNotFound(ResourceNotFoundException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Resource not found");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler({com.brandempiricism.etocrm.identity.TenantAccessDeniedException.class,
        org.springframework.security.access.AccessDeniedException.class})
    ProblemDetail accessDenied(RuntimeException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "The requested operation is not permitted.");
        detail.setTitle("Access denied");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler({org.springframework.transaction.CannotCreateTransactionException.class,
        org.springframework.dao.DataAccessResourceFailureException.class})
    ProblemDetail databaseUnavailable(RuntimeException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The requested service is temporarily unavailable.");
        detail.setTitle("Service unavailable");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

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
