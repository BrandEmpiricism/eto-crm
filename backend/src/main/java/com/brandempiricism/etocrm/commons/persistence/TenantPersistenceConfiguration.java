package com.brandempiricism.etocrm.commons.persistence;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.DependsOn;
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
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource tenantDataSource() {
        return tenantDataSourceProperties().initializeDataSourceBuilder()
            .type(HikariDataSource.class).build();
    }

    @Bean(initMethod = "migrate")
    @Primary
    Flyway tenantFlyway(@Qualifier("tenantDataSource") DataSource dataSource) {
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
    @DependsOn("tenantFlyway")
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
