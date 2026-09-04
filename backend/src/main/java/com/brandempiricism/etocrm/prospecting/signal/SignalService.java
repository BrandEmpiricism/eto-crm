package com.brandempiricism.etocrm.prospecting.signal;
import java.time.*;import java.util.*;import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
@Service public class SignalService{
 private final SignalRepository signals;SignalService(SignalRepository signals){this.signals=signals;}
 @PreAuthorize("hasAuthority('crm:write')") public SignalView create(UUID accountId,CreateSignal command,String actor,boolean allowDraft){if(!allowDraft){required(command.source(),"Signal source is required.");if(command.observedOn()==null)throw new IllegalArgumentException("Observation date is required.");required(command.observedFact(),"Observed fact is required.");}var entity=new SignalEntity(UUID.randomUUID(),accountId,clean(command.source()),command.observedOn(),clean(command.observedFact()),clean(command.assumption()),Instant.now(),actor);return view(signals.save(entity));}
 public SignalView get(UUID accountId,UUID id){var signal=signals.findById(id).orElseThrow(()->new IllegalArgumentException("Signal does not exist."));if(!signal.accountId.equals(accountId))throw new IllegalArgumentException("Signal does not belong to this account.");return view(signal);}
 List<SignalView> list(UUID accountId){return signals.findByAccountIdOrderByObservedOnDesc(accountId).stream().map(SignalService::view).toList();}
 private static SignalView view(SignalEntity s){return new SignalView(s.id,s.accountId,s.source,s.observedOn,s.observedFact,s.assumption,s.createdAt);}
 private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}private static String required(String v,String m){if(v==null||v.isBlank())throw new IllegalArgumentException(m);return v.trim();}
 public record CreateSignal(String source,LocalDate observedOn,String observedFact,String assumption){}
 public record SignalView(UUID id,UUID accountId,String source,LocalDate observedOn,String observedFact,String assumption,Instant createdAt){}
}
