package com.brandempiricism.etocrm.platform.tenancy;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface TenantRegistryRepository extends JpaRepository<TenantRegistryEntity,UUID>{Optional<TenantRegistryEntity> findByIdempotencyKey(String key);List<TenantRegistryEntity> findAllByOrderByDisplayNameAsc();}
