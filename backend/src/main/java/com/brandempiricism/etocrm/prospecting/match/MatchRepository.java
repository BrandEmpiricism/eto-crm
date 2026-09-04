package com.brandempiricism.etocrm.prospecting.match;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface MatchRepository extends JpaRepository<MatchEntity,UUID>{List<MatchEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);List<MatchEntity> findByAccountIdAndSignalIdOrderByCreatedAtDesc(UUID accountId,UUID signalId);List<MatchEntity> findByOwnerAndStatusOrderByNextActionDateAsc(String owner,MatchStatus status);}
