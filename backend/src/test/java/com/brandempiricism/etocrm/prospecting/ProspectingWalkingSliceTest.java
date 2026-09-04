package com.brandempiricism.etocrm.prospecting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProspectingWalkingSliceTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void completeMatchBecomesActiveAndAppearsInOwnersQueue() throws Exception {
        mvc.perform(post("/api/prospecting/matches").header("X-Actor", "asha")
                .contentType(MediaType.APPLICATION_JSON).content(completeRequest("11111111-1111-1111-1111-111111111111")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.missingInformation").isEmpty());
        mvc.perform(get("/api/prospecting/work-queue").param("owner", "Asha Patel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].accountName").value("Northstar Assembly Systems"))
                .andExpect(jsonPath("$[0].nextAction").value("Validate the changeover baseline"));
    }

    @Test void unreliableEvidenceIsPreservedAsDraftWithActionableGuidance() throws Exception {
        mvc.perform(post("/api/prospecting/matches").header("X-Actor", "asha")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"accountName":"Draft Works","industry":"Machinery","location":"Chennai, India",
                     "capabilityId":"11111111-1111-1111-1111-111111111111","hypothesis":"Fixtures may help"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.missingInformation[0]").value("Add the signal source."));
    }

    @Test void inactiveCapabilityCannotActivateAMatch() throws Exception {
        mvc.perform(post("/api/prospecting/matches").header("X-Actor", "asha")
                .contentType(MediaType.APPLICATION_JSON).content(completeRequest("22222222-2222-2222-2222-222222222222")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Select an active capability before activating this match."));
    }

    @Test void stateChangeRequiresAnAuthenticatedActor() throws Exception {
        mvc.perform(post("/api/prospecting/matches").contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest("11111111-1111-1111-1111-111111111111")))
                .andExpect(status().isForbidden());
    }

    @Test void accountCanStoreMultipleContacts() throws Exception {
        mvc.perform(post("/api/prospecting/matches").header("X-Actor", "asha").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accountName":"Contact Works","industry":"Machinery","location":"Bengaluru, India",
                     "capabilityId":"11111111-1111-1111-1111-111111111111","contacts":[
                       {"name":"Mina Shah","email":"mina@example.com","role":"Plant manager","notes":"Owns line expansion"},
                       {"name":"Dev Rao","email":"dev@example.com","role":"Engineer","notes":"Technical evaluator"}]}
                    """))
                .andExpect(status().isOk());
        Integer count = jdbc.queryForObject("select count(*) from account_contact where email in ('mina@example.com','dev@example.com')", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2);
    }

    @Test void malformedWebsiteIsRejectedWithActionableProblemDetail() throws Exception {
        mvc.perform(post("/api/prospecting/matches").header("X-Actor", "asha").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accountName":"Bad URL Works","industry":"Machinery","location":"Delhi, India",
                     "website":"brandempiricism.com","capabilityId":"11111111-1111-1111-1111-111111111111"}
                    """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Website must be a complete HTTP or HTTPS URL, for example https://example.com."));
    }

    private static String completeRequest(String capabilityId) {
        return """
            {"accountName":"Northstar Assembly Systems","industry":"Industrial equipment","location":"Pune, India",
             "capabilityId":"%s","source":"Expansion permit","observedOn":"2026-09-01",
             "observedFact":"A permit records a new assembly line.","assumption":"Changeovers may constrain ramp-up.",
             "owner":"Asha Patel","hypothesis":"Fast-change fixtures can reduce line changeover time.",
             "nextAction":"Validate the changeover baseline","nextActionDate":"2026-09-10"}
            """.formatted(capabilityId);
    }
}
