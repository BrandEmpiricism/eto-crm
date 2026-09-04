package com.brandempiricism.etocrm.accounts.contact;
import com.brandempiricism.etocrm.accounts.*;import java.security.Principal;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/accounts/{accountId}/contacts")
class ContactController{
 private final ContactService contacts;private final AccountApplicationApi accounts;ContactController(ContactService contacts,AccountApplicationApi accounts){this.contacts=contacts;this.accounts=accounts;}
 @GetMapping List<ContactService.ContactView> list(@PathVariable UUID accountId,@RequestParam(required=false)String q){accounts.getAccount(accountId);return contacts.list(accountId,q);}
 @GetMapping("/{id}") ContactService.ContactView get(@PathVariable UUID accountId,@PathVariable UUID id){accounts.getAccount(accountId);return contacts.get(accountId,id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) ContactService.ContactView create(@PathVariable UUID accountId,@RequestBody AccountApplicationApi.CreateContact request,Principal principal){accounts.getAccount(accountId);return contacts.create(accountId,request,principal.getName());}
 @PutMapping("/{id}") ContactService.ContactView update(@PathVariable UUID accountId,@PathVariable UUID id,@RequestBody AccountApplicationApi.CreateContact request,Principal principal){accounts.getAccount(accountId);return contacts.update(accountId,id,request,principal.getName());}
}
