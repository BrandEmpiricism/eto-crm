package com.brandempiricism.etocrm.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class AccountWorkspaceBackendTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    private static final String ACTOR="workspace-user";

    @Test void accountWorkspaceSupportsContactsSignalsMatchesAndActions() throws Exception {
        JsonNode account=body(mvc.perform(post("/api/accounts").header("X-Actor",ACTOR).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Workspace Manufacturing","industry":"Machinery","location":"Coimbatore, India",
                 "website":"https://example.com","owner":"Asha Patel","summary":"Adding a flexible assembly cell"}
                """)).andExpect(status().isCreated()).andReturn());
        String accountId=account.get("id").asText();
        mvc.perform(get("/api/accounts/{id}",accountId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("Asha Patel")).andExpect(jsonPath("$.summary").value("Adding a flexible assembly cell"));

        JsonNode contact=body(mvc.perform(post("/api/accounts/{id}/contacts",accountId).header("X-Actor",ACTOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mina Shah\",\"email\":\"mina@example.com\",\"role\":\"Plant manager\",\"notes\":\"Expansion owner\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(put("/api/accounts/{accountId}/contacts/{id}",accountId,contact.get("id").asText()).header("X-Actor",ACTOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mina Shah\",\"email\":\"mina@example.com\",\"role\":\"Operations director\",\"notes\":\"Expansion owner\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Operations director"));
        mvc.perform(get("/api/accounts/{id}/contacts",accountId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].email").value("mina@example.com"));
        mvc.perform(get("/api/accounts/{id}/contacts",accountId).param("q","operations"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Mina Shah"));

        JsonNode signal=body(mvc.perform(post("/api/accounts/{id}/signals",accountId).header("X-Actor",ACTOR).contentType(MediaType.APPLICATION_JSON).content("""
                {"source":"Expansion permit","observedOn":"2026-09-01","observedFact":"A new assembly cell is permitted.","assumption":"Changeover speed may affect ramp-up."}
                """)).andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/accounts/{id}/signals",accountId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].observedFact").value("A new assembly cell is permitted."));

        JsonNode match=body(mvc.perform(post("/api/accounts/{id}/capability-matches",accountId).header("X-Actor",ACTOR).contentType(MediaType.APPLICATION_JSON).content("""
                {"signalId":"%s","capabilityId":"11111111-1111-1111-1111-111111111111","owner":"Asha Patel",
                 "hypothesis":"Fast-change fixtures can reduce cell changeovers.","nextAction":"Validate the baseline","nextActionDate":"2026-09-15"}
                """.formatted(signal.get("id").asText()))).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn());
        mvc.perform(get("/api/accounts/{id}/capability-matches",accountId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].capabilityName").value("Reduce fixture changeover time"));
        mvc.perform(get("/api/accounts/{accountId}/capability-matches/{id}",accountId,match.get("id").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signalId").value(signal.get("id").asText()));
        mvc.perform(get("/api/accounts/{accountId}/signals/{id}",accountId,signal.get("id").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.relatedMatches[0].id").value(match.get("id").asText()));

        JsonNode action=body(mvc.perform(post("/api/accounts/{id}/next-actions",accountId).header("X-Actor",ACTOR).contentType(MediaType.APPLICATION_JSON).content("""
                {"capabilityMatchId":"%s","description":"Interview the plant manager","dueAt":"2099-09-15T09:00:00Z"}
                """.formatted(match.get("id").asText()))).andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/accounts/{id}/next-actions",accountId)).andExpect(status().isOk()).andExpect(jsonPath("$.upcoming[0].description").value("Interview the plant manager"));
        mvc.perform(patch("/api/accounts/{accountId}/next-actions/{id}/complete",accountId,action.get("id").asText()).header("X-Actor",ACTOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/accounts/{id}/next-actions",accountId)).andExpect(status().isOk()).andExpect(jsonPath("$.completed[0].completedAt").exists());
    }

    @Test void updatesRequireAnAuthenticatedActor() throws Exception {
        mvc.perform(put("/api/accounts/00000000-0000-0000-0000-000000000000/contacts/00000000-0000-0000-0000-000000000000")
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(patch("/api/accounts/00000000-0000-0000-0000-000000000000/next-actions/00000000-0000-0000-0000-000000000000/complete"))
                .andExpect(status().isForbidden());
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception{return json.readTree(result.getResponse().getContentAsString());}
}
