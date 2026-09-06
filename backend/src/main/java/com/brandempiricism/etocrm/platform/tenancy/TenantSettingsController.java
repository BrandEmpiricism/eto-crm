package com.brandempiricism.etocrm.platform.tenancy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/settings")
class TenantSettingsController {
    private final TenantSettingsService settings;
    TenantSettingsController(TenantSettingsService settings) { this.settings = settings; }
    @GetMapping TenantSettingsService.Settings get() { return settings.get(); }
    @PutMapping TenantSettingsService.Settings update(@RequestBody TenantSettingsService.UpdateSettings input) { return settings.update(input); }
}
