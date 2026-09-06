package com.brandempiricism.etocrm.platform.tenancy;

import com.brandempiricism.etocrm.identity.IdentityApplicationApi;
import com.brandempiricism.etocrm.identity.TenantContextHolder;
import com.brandempiricism.etocrm.identity.TenantAccessDeniedException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSettingsService {
    private final JdbcTemplate database;
    private final IdentityApplicationApi identities;

    TenantSettingsService(@Qualifier("platformJdbcTemplate") JdbcTemplate database, IdentityApplicationApi identities) {
        this.database = database;
        this.identities = identities;
    }

    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    @PreAuthorize("hasAuthority('crm:read')")
    public Settings get() {
        var context = verifiedContext();
        return view(context.tenantId());
    }

    @Transactional("platformTransactionManager")
    @PreAuthorize("hasAuthority('tenant:administer')")
    public Settings update(UpdateSettings input) {
        var context = verifiedContext();
        identities.requireTenantAdministrator(context.actorId(), context.tenantId());
        var name = text(input.displayName(), 200, "Company display name");
        if (name.contains("<") || name.contains(">")) throw new IllegalArgumentException("Company display name must be plain text.");
        var locale = text(input.locale(), 50, "Locale");
        try {
            var parsed = new Locale.Builder().setLanguageTag(locale).build();
            if (parsed.getLanguage().isBlank()) throw new IllegalArgumentException("Locale must contain a language.");
            locale = parsed.toLanguageTag();
        } catch (java.util.IllformedLocaleException failure) {
            throw new IllegalArgumentException("Locale must be a valid language tag.");
        }
        var timezone = text(input.timezone(), 100, "Timezone");
        try { ZoneId.of(timezone); }
        catch (java.time.DateTimeException failure) { throw new IllegalArgumentException("Timezone must be a valid zone identifier."); }
        var color = input.brandColor();
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Brand color must be a six-digit hexadecimal color.");
        }
        var now = Timestamp.from(Instant.now());
        database.update("update tenant_registry set display_name=?,updated_at=?,updated_by=? where id=?", name, now, context.actorId(), context.tenantId());
        int changed = database.update("update tenant_settings set locale=?,timezone=?,brand_color=?,updated_at=?,updated_by=? where tenant_id=?",
            locale, timezone, color, now, context.actorId(), context.tenantId());
        if (changed == 0) database.update("insert into tenant_settings(tenant_id,locale,timezone,brand_color,updated_at,updated_by) values (?,?,?,?,?,?)",
            context.tenantId(), locale, timezone, color, now, context.actorId());
        audit(context.tenantId(), context.actorId(), "tenant.settings.updated", "Company display and regional settings updated");
        return view(context.tenantId());
    }

    private IdentityApplicationApi.TenantContext verifiedContext() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var context = TenantContextHolder.current().orElseThrow(() -> new TenantAccessDeniedException("Verified tenant context is required."));
        if (!context.actorId().equals(authentication.getName())) throw new TenantAccessDeniedException("Verified tenant context is required.");
        return context;
    }

    private Settings view(UUID tenant) {
        var rows = database.query("select t.id,t.display_name,t.status,coalesce(s.locale,'en') as locale,coalesce(s.timezone,'UTC') as timezone,s.brand_color "
            + "from tenant_registry t left join tenant_settings s on s.tenant_id=t.id where t.id=? and t.status='ACTIVE'",
            (row, index) -> new Settings(row.getObject("id", UUID.class), row.getString("display_name"), row.getString("status"),
                row.getString("locale"), row.getString("timezone"), row.getString("brand_color")), tenant);
        if (rows.size() != 1) throw new TenantAccessDeniedException("An active company membership is required.");
        return rows.getFirst();
    }

    private void audit(UUID tenant, String actor, String action, String summary) {
        database.update("insert into platform_tenant_audit(id,tenant_id,actor_id,action,occurred_at,request_id,summary) values (?,?,?,?,?,?,?)",
            UUID.randomUUID(), tenant, actor, action, Timestamp.from(Instant.now()), MDC.get("requestId"), summary);
    }

    private static String text(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.length() > limit) throw new IllegalArgumentException(field + " is required and must fit its length limit.");
        return value.trim();
    }

    public record UpdateSettings(String displayName, String locale, String timezone, String brandColor) {}
    public record Settings(UUID tenantId, String displayName, String status, String locale, String timezone, String brandColor) {}
}
