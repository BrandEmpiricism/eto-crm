package com.brandempiricism.etocrm.commons.persistence;

import javax.sql.DataSource;
import com.brandempiricism.etocrm.commons.TenantDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.flywaydb.core.Flyway;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.brandempiricism.etocrm.accounts",
        "com.brandempiricism.etocrm.activities",
        "com.brandempiricism.etocrm.capabilities",
        "com.brandempiricism.etocrm.prospecting"
    },
    entityManagerFactoryRef = "tenantEntityManagerFactory",
    transactionManagerRef = "tenantTransactionManager"
)
public class TenantPersistenceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties tenantDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource tenantBootstrapDataSource() {
        return tenantDataSourceProperties().initializeDataSourceBuilder()
            .type(HikariDataSource.class).build();
    }

    @Bean(name = "tenantDataSource")
    @Primary
    @ConditionalOnProperty(name = "eto.tenancy.routing.enabled", havingValue = "false", matchIfMissing = true)
    DataSource tenantDataSource(@Qualifier("tenantBootstrapDataSource") DataSource bootstrap) {
        return bootstrap;
    }

    @Bean(name = "tenantDataSource")
    @Primary
    @ConditionalOnProperty(name = "eto.tenancy.routing.enabled", havingValue = "true")
    DataSource routedTenantDataSource(TenantDataSourceFactory factory,
            @Value("${eto.tenancy.routing.maximum-pools:20}") int maximumPools) {
        return new TenantRoutingDataSource(factory, maximumPools);
    }

    @Bean(initMethod = "migrate")
    @Primary
    @ConditionalOnProperty(name = "eto.tenancy.routing.enabled", havingValue = "false", matchIfMissing = true)
    Flyway tenantFlyway(@Qualifier("tenantBootstrapDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/tenant")
            .load();
    }

    @Bean
    @Primary
    JdbcTemplate tenantJdbcTemplate(@Qualifier("tenantDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("tenantDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource)
            .packages(
                "com.brandempiricism.etocrm.accounts",
                "com.brandempiricism.etocrm.activities",
                "com.brandempiricism.etocrm.capabilities",
                "com.brandempiricism.etocrm.prospecting"
            )
            .persistenceUnit("tenant")
            .build();
    }

    @Bean
    @Primary
    PlatformTransactionManager tenantTransactionManager(
            LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory) {
        return new JpaTransactionManager(tenantEntityManagerFactory.getObject());
    }
}
