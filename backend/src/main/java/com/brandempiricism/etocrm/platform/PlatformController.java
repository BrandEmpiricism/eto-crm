package com.brandempiricism.etocrm.platform;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
class PlatformController {
    @GetMapping
    PlatformSummary summary() {
        return new PlatformSummary("ETO CRM", "development", List.of("accounts", "capabilities", "prospecting", "activities"));
    }

    record PlatformSummary(String name, String stage, List<String> modules) {}
}

