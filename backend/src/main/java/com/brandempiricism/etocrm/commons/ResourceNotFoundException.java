package com.brandempiricism.etocrm.commons;

/** Deliberately identical for absent resources and resources outside the requested account. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException() {
        super("The requested resource was not found.");
    }
}
