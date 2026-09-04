package com.brandempiricism.etocrm.accounts.account;

import com.brandempiricism.etocrm.accounts.AccountApplicationApi;
import com.brandempiricism.etocrm.accounts.contact.ContactService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class AccountService implements AccountApplicationApi {
    private final AccountRepository accounts; private final ContactService contacts;
    AccountService(AccountRepository accounts,ContactService contacts){this.accounts=accounts;this.contacts=contacts;}
    @Override @Transactional @PreAuthorize("hasAuthority('crm:write')") public AccountRef createAccount(CreateAccount command,String actor){
        var now=Instant.now();var entity=new AccountEntity(UUID.randomUUID(),required(command.name()),required(command.industry()),required(command.location()),website(command.website()),clean(command.owner()),clean(command.summary()),now,actor);
        accounts.save(entity);for(var contact:safe(command.contacts()))contacts.create(entity.id,contact,actor);return view(entity);
    }
    @Override public AccountRef getAccount(UUID id){return view(accounts.findById(id).orElseThrow(()->new IllegalArgumentException("Account does not exist.")));}
    List<AccountRef> list(){return accounts.findAll().stream().map(AccountService::view).toList();}
    private static AccountRef view(AccountEntity a){return new AccountRef(a.id,a.name,a.industry,a.location,a.website,a.owner,a.summary);}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Account name, industry, and location are required.");return value.trim();}
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String website(String value){var result=clean(value);if(result==null)return null;try{var uri=URI.create(result);if(("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))&&uri.getHost()!=null)return result;}catch(IllegalArgumentException ignored){}throw new IllegalArgumentException("Website must be a complete HTTP or HTTPS URL, for example https://example.com.");}
    private static List<CreateContact> safe(List<CreateContact> value){return value==null?List.of():value;}
}
