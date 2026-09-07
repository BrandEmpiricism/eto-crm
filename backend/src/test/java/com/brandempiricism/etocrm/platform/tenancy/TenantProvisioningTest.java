package com.brandempiricism.etocrm.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.brandempiricism.etocrm.identity.IdentityApplicationApi;import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.ObjectMapper;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.beans.factory.annotation.Qualifier;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.http.MediaType;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.test.context.bean.override.mockito.MockitoBean;import org.springframework.test.web.servlet.*;

@SpringBootTest @AutoConfigureMockMvc
class TenantProvisioningTest{
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate jdbc;@Autowired @Qualifier("tenantJdbcTemplate") JdbcTemplate tenantJdbc;
 @MockitoBean TenantProvisioningInfrastructure infrastructure;
 @MockitoBean IdentityApplicationApi identities;
 @Test void initialClientIsRegisteredForDedicatedDatabaseProvisioning()throws Exception{mvc.perform(get("/api/platform/tenants").header("X-Actor","platform-operator")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.slug == 'r-hyper-tooling')].displayName").value(hasItem("R Hyper Tooling"))).andExpect(jsonPath("$[?(@.slug == 'r-hyper-tooling')].website").value(hasItem("https://rhypertooling.ca/"))).andExpect(jsonPath("$[?(@.slug == 'r-hyper-tooling')].databaseName").value(hasItem("eto_crm_r_hyper_tooling"))).andExpect(jsonPath("$[?(@.slug == 'r-hyper-tooling')].provisioningStep").value(hasItem("REGISTERED")));}
 @Test void repeatedProvisioningKeyReturnsSameTenantWithoutDuplication()throws Exception{String request="""
 {"slug":"example-tools","displayName":"Example Tools","website":"https://example.com/","idempotencyKey":"provision-example-tools"}
 """;JsonNode first=body(mvc.perform(post("/api/platform/tenants").header("X-Actor","platform-operator").header("X-Request-Id","provision-request-1").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isAccepted()).andExpect(header().string("X-Request-Id","provision-request-1")).andReturn());JsonNode second=body(mvc.perform(post("/api/platform/tenants").header("X-Actor","platform-operator").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isAccepted()).andReturn());assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());assertThat(jdbc.queryForObject("select count(*) from tenant_registry where idempotency_key='provision-example-tools'",Integer.class)).isEqualTo(1);}
 @Test void invalidCorrelationInputIsNotReflectedAndProblemIncludesRequestId()throws Exception{MvcResult result=mvc.perform(post("/api/platform/tenants").header("X-Actor","platform-operator").header("X-Request-Id","bad value").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();assertThat(result.getResponse().getHeader("X-Request-Id")).doesNotContain(" ").isEqualTo(json.readTree(result.getResponse().getContentAsString()).get("requestId").asText());}
 @Test void platformAndTenantSchemasArePhysicallySeparated(){assertThat(tableCount(jdbc,"TENANT_REGISTRY")).isEqualTo(1);assertThat(tableCount(jdbc,"ACCOUNT")).isZero();assertThat(tableCount(tenantJdbc,"ACCOUNT")).isEqualTo(1);assertThat(tableCount(tenantJdbc,"TENANT_REGISTRY")).isZero();}
 @Test void activatesOnlyAfterEveryProvisioningStepCompletes()throws Exception{String request="""
 {"slug":"workflow-tools","displayName":"Workflow Tools","website":"https://example.com/","idempotencyKey":"provision-workflow-tools"}
 """;JsonNode registered=body(mvc.perform(post("/api/platform/tenants").header("X-Actor","platform-operator").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isAccepted()).andReturn());var id=java.util.UUID.fromString(registered.get("id").asText());var spec=new TenantProvisioningInfrastructure.TenantDatabaseSpec(id,"workflow-tools","eto_crm_workflow_tools");when(infrastructure.allocateDatabase(spec)).thenReturn(new TenantProvisioningInfrastructure.Allocation("secret://tenants/workflow-tools/database"));mvc.perform(post("/api/platform/tenants/{id}/provision",id).header("X-Actor","platform-operator")).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.provisioningStep").value("COMPLETE"));var order=inOrder(infrastructure,identities);order.verify(infrastructure).allocateDatabase(spec);order.verify(infrastructure).migrate(spec,"secret://tenants/workflow-tools/database");order.verify(infrastructure).seedDefaults(spec,"secret://tenants/workflow-tools/database");order.verify(infrastructure).verify(spec,"secret://tenants/workflow-tools/database");order.verify(identities).assignInitialAdministrator(id,"platform-operator");}
 @Test void tenantRegistryIsNotDisclosedToOrdinaryUsers()throws Exception{mvc.perform(get("/api/platform/tenants").header("X-Actor","bd-user")).andExpect(status().isForbidden());}
 @Test void migrationFailureAndRetryPreserveCorrelationWithoutInfrastructureDetails() throws Exception {
  var registered=body(mvc.perform(post("/api/platform/tenants").header("X-Actor","platform-operator")
   .contentType(MediaType.APPLICATION_JSON).content("""
    {"slug":"correlated-migration","displayName":"customer-canary","idempotencyKey":"correlated-migration"}
    """)).andExpect(status().isAccepted()).andReturn());
  var id=java.util.UUID.fromString(registered.path("id").asText());
  when(infrastructure.allocateDatabase(any())).thenReturn(new TenantProvisioningInfrastructure.Allocation("secret-canary"));
  doAnswer(call -> {
   assertThat(org.slf4j.MDC.get("tenantId")).isEqualTo(id.toString());
   assertThat(org.slf4j.MDC.get("actorId")).isEqualTo("platform-operator");
   assertThat(org.slf4j.MDC.get("businessTransactionId")).isEqualTo("migration-workflow");
   throw new IllegalStateException("jdbc:postgresql://host-canary/db password-canary");
  }).doNothing().when(infrastructure).migrate(any(),eq("secret-canary"));
  try(var logs=new com.brandempiricism.etocrm.commons.observability.LogCapture()) {
   mvc.perform(post("/api/platform/tenants/{id}/provision",id).header("X-Actor","platform-operator")
     .header("X-Request-Id","migration-failed").header("X-Business-Transaction-Id","migration-workflow"))
    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").value("migration-failed"))
    .andExpect(jsonPath("$.detail").value("The request could not be completed."));
   assertThat(jdbc.queryForObject("select provisioning_step from tenant_registry where id=?",String.class,id)).isEqualTo("DATABASE_ALLOCATED");
   mvc.perform(post("/api/platform/tenants/{id}/provision",id).header("X-Actor","platform-operator")
     .header("X-Request-Id","migration-retried").header("X-Business-Transaction-Id","migration-workflow"))
    .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("ACTIVE"));
   var events=logs.events("tenant.migration.completed");
   assertThat(events).hasSize(2).allSatisfy(event -> {
    assertThat(event.path("tenantId").asText()).isEqualTo(id.toString());
    assertThat(event.path("businessTransactionId").asText()).isEqualTo("migration-workflow");
    assertThat(event.path("parentSpanId").asText()).hasSize(16);
   });
   assertThat(events.get(0).path("event.outcome").asText()).isEqualTo("failure");
   assertThat(events.get(0).path("requestId").asText()).isEqualTo("migration-failed");
   assertThat(events.get(1).path("event.outcome").asText()).isEqualTo("success");
   assertThat(events.get(1).path("requestId").asText()).isEqualTo("migration-retried");
   assertThat(logs.output()).doesNotContain("canary","jdbc:");
  }
  assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
 }
 private JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
 private static int tableCount(JdbcTemplate database,String table){return database.queryForObject("select count(*) from information_schema.tables where table_schema='PUBLIC' and table_name=?",Integer.class,table);}
}
