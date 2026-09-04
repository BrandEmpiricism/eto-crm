package com.brandempiricism.etocrm.prospecting.signal;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface SignalRepository extends JpaRepository<SignalEntity,UUID>{List<SignalEntity> findByAccountIdOrderByObservedOnDesc(UUID accountId);}
