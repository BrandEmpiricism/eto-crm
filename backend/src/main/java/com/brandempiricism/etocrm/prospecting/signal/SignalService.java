package com.brandempiricism.etocrm.prospecting.signal;
import com.brandempiricism.etocrm.events.BusinessEvent;
import com.brandempiricism.etocrm.events.BusinessEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
@org.springframework.security.access.prepost.PreAuthorize("@tenantAuthorization.canRead(authentication)")
@Service public class SignalService{
 private final BusinessEventPublisher events;private final SignalRepository signals;private final com.brandempiricism.etocrm.accounts.AccountApplicationApi accounts;SignalService(SignalRepository signals,com.brandempiricism.etocrm.accounts.AccountApplicationApi accounts,BusinessEventPublisher events){this.signals=signals;this.accounts=accounts;this.events=events;}
 @Transactional("tenantTransactionManager") @PreAuthorize("@tenantAuthorization.canWrite(authentication, #actor)") public SignalView create(UUID accountId,CreateSignal command,String actor,boolean allowDraft){accounts.getAccount(accountId);if(!allowDraft){required(command.source(),"Signal source is required.");if(command.observedOn()==null)throw new IllegalArgumentException("Observation date is required.");required(command.observedFact(),"Observed fact is required.");}var entity=new SignalEntity(UUID.randomUUID(),accountId,clean(command.source()),command.observedOn(),clean(command.observedFact()),clean(command.assumption()),Instant.now(),actor);signals.save(entity);events.record(new BusinessEvent.SignalRecorded(entity.id,accountId),actor);com.brandempiricism.etocrm.commons.DiagnosticEvents.afterCommit(com.brandempiricism.etocrm.commons.DiagnosticEvents.Event.SIGNAL_RECORDED,entity.id);return view(entity);}
 public SignalView get(UUID accountId,UUID id){var signal=signals.findById(id).orElseThrow(()->new com.brandempiricism.etocrm.commons.ResourceNotFoundException());if(!signal.accountId.equals(accountId))throw new com.brandempiricism.etocrm.commons.ResourceNotFoundException();return view(signal);}
 List<SignalView> list(UUID accountId){accounts.getAccount(accountId);return signals.findByAccountIdOrderByObservedOnDesc(accountId).stream().map(SignalService::view).toList();}
 private static SignalView view(SignalEntity s){return new SignalView(s.id,s.accountId,s.source,s.observedOn,s.observedFact,s.assumption,s.createdAt);}
 private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}private static String required(String v,String m){if(v==null||v.isBlank())throw new IllegalArgumentException(m);return v.trim();}
 public record CreateSignal(String source,LocalDate observedOn,String observedFact,String assumption){}
 public record SignalView(UUID id,UUID accountId,String source,LocalDate observedOn,String observedFact,String assumption,Instant createdAt){}
}
