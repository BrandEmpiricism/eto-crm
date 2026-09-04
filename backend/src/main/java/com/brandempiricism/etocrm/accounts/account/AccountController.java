package com.brandempiricism.etocrm.accounts.account;

import com.brandempiricism.etocrm.accounts.AccountApplicationApi;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/accounts")
class AccountController {
    private final AccountService accounts;AccountController(AccountService accounts){this.accounts=accounts;}
    @GetMapping List<AccountApplicationApi.AccountRef> list(){return accounts.list();}
    @GetMapping("/{id}") AccountApplicationApi.AccountRef get(@PathVariable UUID id){return accounts.getAccount(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) AccountApplicationApi.AccountRef create(@RequestBody AccountApplicationApi.CreateAccount request,Principal principal){return accounts.createAccount(request,principal.getName());}
}
