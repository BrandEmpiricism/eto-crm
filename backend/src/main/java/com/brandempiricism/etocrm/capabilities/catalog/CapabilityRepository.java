package com.brandempiricism.etocrm.capabilities.catalog;
import java.util.UUID;import org.springframework.data.jpa.repository.JpaRepository;
interface CapabilityRepository extends JpaRepository<CapabilityEntity,UUID>{}
