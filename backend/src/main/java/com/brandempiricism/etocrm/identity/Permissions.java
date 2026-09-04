package com.brandempiricism.etocrm.identity;

/** Centrally governed permission vocabulary used at application boundaries. */
public final class Permissions {
    public static final String CRM_READ = "crm:read";
    public static final String CRM_WRITE = "crm:write";
    public static final String TENANT_ADMINISTER = "tenant:administer";
    public static final String PLATFORM_OPERATE = "platform:operate";
    public static final String SUPPORT_DIAGNOSE = "support:diagnose";

    private Permissions() {}
}
