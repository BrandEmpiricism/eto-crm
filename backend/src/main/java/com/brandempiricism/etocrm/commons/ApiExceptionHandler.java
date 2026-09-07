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
        DiagnosticEvents.emit(DiagnosticEvents.Event.REQUEST_REJECTED, DiagnosticEvents.Outcome.rejected);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Resource not found");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ProblemDetail accessDenied(RuntimeException exception) {
        DiagnosticEvents.emit(DiagnosticEvents.Event.AUTHORIZATION_REJECTED, DiagnosticEvents.Outcome.rejected);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "The requested operation is not permitted.");
        detail.setTitle("Access denied");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler({org.springframework.transaction.CannotCreateTransactionException.class,
        org.springframework.dao.DataAccessResourceFailureException.class})
    ProblemDetail databaseUnavailable(RuntimeException exception) {
        DiagnosticEvents.emit(DiagnosticEvents.Event.ROUTE_FAILED, DiagnosticEvents.Outcome.failure, exception);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The requested service is temporarily unavailable.");
        detail.setTitle("Service unavailable");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        DiagnosticEvents.emit(DiagnosticEvents.Event.REQUEST_REJECTED, DiagnosticEvents.Outcome.rejected);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        detail.setTitle("Request validation failed");
        detail.setType(URI.create("https://eto-crm.example/problems/validation"));
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail provisioningUnavailable(ServiceUnavailableException exception) {
        DiagnosticEvents.emit(DiagnosticEvents.Event.ROUTE_FAILED, DiagnosticEvents.Outcome.failure, exception);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The requested service is temporarily unavailable.");
        detail.setTitle("Tenant provisioning unavailable");
        detail.setType(URI.create("https://eto-crm.example/problems/tenant-provisioning-unavailable"));
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class})
    ProblemDetail invalidRequest(Exception exception) {
        DiagnosticEvents.emit(DiagnosticEvents.Event.REQUEST_REJECTED, DiagnosticEvents.Outcome.rejected);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request format is invalid.");
        detail.setTitle("Invalid request");
        detail.setProperty("requestId", MDC.get("requestId"));
        return detail;
    }

    @ExceptionHandler(Exception.class)
    org.springframework.http.ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            DiagnosticEvents.emit(DiagnosticEvents.Event.REQUEST_REJECTED, DiagnosticEvents.Outcome.rejected);
            var detail = ProblemDetail.forStatusAndDetail(error.getStatusCode(), "The request could not be accepted.");
            detail.setProperty("requestId", MDC.get("requestId"));
            return new org.springframework.http.ResponseEntity<>(detail, error.getHeaders(), error.getStatusCode());
        }
        DiagnosticEvents.emit(DiagnosticEvents.Event.REQUEST_FAILED, DiagnosticEvents.Outcome.failure, exception);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed.");
        detail.setTitle("Request failed");
        detail.setProperty("requestId", MDC.get("requestId"));
        return org.springframework.http.ResponseEntity.internalServerError().body(detail);
    }
}
