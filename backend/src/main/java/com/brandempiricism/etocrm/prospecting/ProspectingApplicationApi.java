package com.brandempiricism.etocrm.prospecting;
import java.util.UUID;
public interface ProspectingApplicationApi {
 MatchRef getMatch(UUID id);
 record MatchRef(UUID id,UUID accountId,String accountName,String capabilityName,String status){}
}
