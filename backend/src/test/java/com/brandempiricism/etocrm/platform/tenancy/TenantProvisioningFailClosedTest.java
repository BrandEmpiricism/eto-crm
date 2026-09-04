package com.brandempiricism.etocrm.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantProvisioningFailClosedTest {
    private static final String INITIAL_TENANT = "33333333-3333-3333-3333-333333333333";

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate jdbc;

    @Test
    void missingInfrastructureCannotActivateTenant() throws Exception {
        mvc.perform(post("/api/platform/tenants/{id}/provision", INITIAL_TENANT)
                .header("X-Actor", "platform-operator")
                .header("X-Request-Id", "rht-provisioning-request"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("X-Request-Id", "rht-provisioning-request"))
            .andExpect(jsonPath("$.title").value("Tenant provisioning unavailable"))
            .andExpect(jsonPath("$.requestId").value("rht-provisioning-request"));

        assertThat(jdbc.queryForObject(
            "select status from tenant_registry where id = cast(? as uuid)",
            String.class,
            INITIAL_TENANT
        )).isEqualTo("PROVISIONING");
        assertThat(jdbc.queryForObject(
            "select failure_code from tenant_registry where id = cast(? as uuid)",
            String.class,
            INITIAL_TENANT
        )).isEqualTo("INFRASTRUCTURE_UNAVAILABLE");
    }
}
