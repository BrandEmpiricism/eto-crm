./VERIFY    # Backend

Spring Boot modular-monolith backend for ETO CRM. For local PostgreSQL, run with `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

The primary datasource is the initial R Hyper Tooling tenant database. The separately configured platform datasource contains only tenant provisioning control-plane data. Tenant and platform Flyway migrations are maintained in `db/tenant` and `db/platform` respectively.

