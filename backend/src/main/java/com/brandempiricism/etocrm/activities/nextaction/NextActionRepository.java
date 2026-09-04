package com.brandempiricism.etocrm.activities.nextaction;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface NextActionRepository extends JpaRepository<NextActionEntity,UUID>{List<NextActionEntity> findByAccountIdOrderByDueAtAsc(UUID accountId);}
