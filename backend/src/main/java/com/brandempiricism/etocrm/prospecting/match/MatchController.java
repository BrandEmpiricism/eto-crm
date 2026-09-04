package com.brandempiricism.etocrm.prospecting.match;
import java.security.Principal;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController class MatchController{private final MatchService matches;MatchController(MatchService matches){this.matches=matches;}
 @GetMapping("/api/accounts/{accountId}/capability-matches")List<MatchService.MatchView> list(@PathVariable UUID accountId){return matches.list(accountId);}
 @GetMapping("/api/accounts/{accountId}/capability-matches/{id}")MatchService.MatchView get(@PathVariable UUID accountId,@PathVariable UUID id){return matches.get(accountId,id);}
 @PostMapping("/api/accounts/{accountId}/capability-matches")@ResponseStatus(HttpStatus.CREATED)MatchService.MatchView create(@PathVariable UUID accountId,@RequestBody MatchService.CreateAccountMatch request,Principal principal){return matches.createForAccount(accountId,request,principal.getName());}
 @PostMapping("/api/prospecting/matches")MatchService.MatchView walkingSlice(@RequestBody MatchService.CreateWalkingSlice request,Principal principal){return matches.createWalkingSlice(request,principal.getName());}
 @GetMapping("/api/prospecting/work-queue")List<MatchService.MatchView> queue(@RequestParam String owner){return matches.queue(owner);}
}
