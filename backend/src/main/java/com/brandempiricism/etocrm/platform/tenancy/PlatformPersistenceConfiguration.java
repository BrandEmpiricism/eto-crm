package com.brandempiricism.etocrm.platform.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableJpaRepositories(
    basePackageClasses = TenantRegistryRepository.class,
    entityManagerFactoryRef = "platformEntityManagerFactory",
    transactionManagerRef = "platformTransactionManager"
)
class PlatformPersistenceConfiguration {

    @Bean
    @ConfigurationProperties("eto.platform.datasource")
    DataSourceProperties platformDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("eto.platform.datasource.hikari")
    DataSource platformDataSource() {
        return platformDataSourceProperties().initializeDataSourceBuilder()
            .type(HikariDataSource.class).build();
    }

    @Bean(initMethod = "migrate")
    Flyway platformFlyway(@Qualifier("platformDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/platform")
            .load();
    }

    @Bean
    JdbcTemplate platformJdbcTemplate(@Qualifier("platformDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @DependsOn("platformFlyway")
    LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("platformDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource)
            .packages(TenantRegistryEntity.class)
            .persistenceUnit("platform")
            .build();
    }

    @Bean
    PlatformTransactionManager platformTransactionManager(
            @Qualifier("platformEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
