package com.brandempiricism.etocrm.identity;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Shared application-boundary checks for CRM permissions and the verified tenant decision. */
@Component("tenantAuthorization")
public class TenantAuthorization {
    private final boolean developmentIdentity;

    public TenantAuthorization(Environment environment) {
        developmentIdentity = environment.acceptsProfiles(Profiles.of("local", "test"))
            && !environment.acceptsProfiles(Profiles.of("prod"))
            && "actor-header".equals(environment.getProperty("eto.security.mode"))
            && !environment.getProperty("eto.tenancy.routing.enabled", Boolean.class, false);
    }

    public boolean canRead(Authentication authentication) {
        return permitted(authentication, Permissions.CRM_READ);
    }

    public boolean canWrite(Authentication authentication, String actor) {
        return permitted(authentication, Permissions.CRM_WRITE) && authentication.getName().equals(actor);
    }

    public boolean canWriteAs(String actor) {
        return canWrite(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(), actor);
    }

    private boolean permitted(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getAuthorities().stream().noneMatch(value -> permission.equals(value.getAuthority()))) {
            return false;
        }
        var context = TenantContextHolder.current();
        if (context.isEmpty()) return developmentIdentity;
        return context.get().actorId().equals(authentication.getName())
            && SecurityRole.valueOf(context.get().role().name()).permissions().contains(permission);
    }
}
