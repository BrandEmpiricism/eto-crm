package com.brandempiricism.etocrm.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformController.class)
class PlatformControllerTest {
    @Autowired private MockMvc mockMvc;
    @Test void returnsPlatformSummary() throws Exception {
        mockMvc.perform(get("/api/platform")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("ETO CRM"));
    }
}
