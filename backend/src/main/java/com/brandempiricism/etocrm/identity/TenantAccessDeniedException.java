package com.brandempiricism.etocrm.identity;

public class TenantAccessDeniedException extends org.springframework.security.access.AccessDeniedException {
    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
