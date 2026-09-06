package com.brandempiricism.etocrm.platform.tenancy;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/tenants/{id}")
class TenantLifecycleController {
    private final TenantLifecycleService lifecycle;
    TenantLifecycleController(TenantLifecycleService lifecycle) { this.lifecycle = lifecycle; }
    @PostMapping("/suspend") TenantLifecycleService.Lifecycle suspend(@PathVariable UUID id) { return lifecycle.suspend(id); }
    @PostMapping("/close") TenantLifecycleService.Lifecycle close(@PathVariable UUID id, @RequestBody CloseTenant request) {
        return lifecycle.close(id, request.exportBackupReference());
    }
    @PostMapping("/approve-deletion") TenantLifecycleService.Lifecycle approveDeletion(@PathVariable UUID id) { return lifecycle.approveDeletion(id); }
    record CloseTenant(String exportBackupReference) {}
}
