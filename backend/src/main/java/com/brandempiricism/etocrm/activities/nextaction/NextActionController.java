package com.brandempiricism.etocrm.activities.nextaction;
import java.security.Principal;import java.util.UUID;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/accounts/{accountId}/next-actions")class NextActionController{private final NextActionService actions;NextActionController(NextActionService actions){this.actions=actions;}
 @GetMapping NextActionService.NextActionGroups list(@PathVariable UUID accountId){return actions.grouped(accountId);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)NextActionService.NextActionView create(@PathVariable UUID accountId,@RequestBody NextActionService.CreateAction request,Principal principal){return actions.create(accountId,request,principal.getName());}
 @PatchMapping("/{id}/complete")NextActionService.NextActionView complete(@PathVariable UUID accountId,@PathVariable UUID id,Principal principal){return actions.complete(accountId,id,principal.getName());}
 @PatchMapping("/{id}/reschedule")NextActionService.NextActionView reschedule(@PathVariable UUID accountId,@PathVariable UUID id,@RequestBody NextActionService.RescheduleAction request,Principal principal){return actions.reschedule(accountId,id,request,principal.getName());}
}
