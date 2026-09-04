package com.brandempiricism.etocrm.prospecting.signal;
import com.brandempiricism.etocrm.accounts.AccountApplicationApi;import com.brandempiricism.etocrm.prospecting.match.MatchService;import java.security.Principal;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/accounts/{accountId}/signals") class SignalController{
 private final SignalService signals;private final MatchService matches;private final AccountApplicationApi accounts;SignalController(SignalService signals,MatchService matches,AccountApplicationApi accounts){this.signals=signals;this.matches=matches;this.accounts=accounts;}
 @GetMapping List<SignalService.SignalView> list(@PathVariable UUID accountId){accounts.getAccount(accountId);return signals.list(accountId);}
 @GetMapping("/{id}") SignalDetails get(@PathVariable UUID accountId,@PathVariable UUID id){accounts.getAccount(accountId);return new SignalDetails(signals.get(accountId,id),matches.listForSignal(accountId,id));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) SignalService.SignalView create(@PathVariable UUID accountId,@RequestBody SignalService.CreateSignal request,Principal principal){accounts.getAccount(accountId);return signals.create(accountId,request,principal.getName(),false);}
 record SignalDetails(SignalService.SignalView signal,List<MatchService.RelatedMatch> relatedMatches){}
}
