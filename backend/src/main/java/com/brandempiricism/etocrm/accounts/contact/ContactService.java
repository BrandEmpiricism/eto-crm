package com.brandempiricism.etocrm.accounts.contact;

import com.brandempiricism.etocrm.accounts.AccountApplicationApi;
import java.time.Instant;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
@org.springframework.security.access.prepost.PreAuthorize("@tenantAuthorization.canRead(authentication)")
@Service
public class ContactService {
 private final ContactRepository contacts;private final AccountApplicationApi accounts;ContactService(ContactRepository contacts,@org.springframework.context.annotation.Lazy AccountApplicationApi accounts){this.contacts=contacts;this.accounts=accounts;}
 @PreAuthorize("@tenantAuthorization.canWrite(authentication, #actor)") public ContactView create(UUID accountId,AccountApplicationApi.CreateContact command,String actor){accounts.getAccount(accountId);var now=Instant.now();return view(contacts.save(new ContactEntity(UUID.randomUUID(),accountId,required(command.name(),"Contact name is required."),email(command.email()),clean(command.role()),clean(command.notes()),now,actor)));}
 List<ContactView> list(UUID accountId,String query){accounts.getAccount(accountId);var normalized=clean(query);return contacts.findByAccountIdOrderByName(accountId).stream().filter(c->normalized==null||contains(c.name,normalized)||contains(c.email,normalized)||contains(c.role,normalized)).map(ContactService::view).toList();}
 ContactView get(UUID accountId,UUID id){var contact=find(accountId,id);return view(contact);}
 @Transactional @PreAuthorize("@tenantAuthorization.canWrite(authentication, #actor)") ContactView update(UUID accountId,UUID id,AccountApplicationApi.CreateContact command,String actor){var contact=find(accountId,id);contact.name=required(command.name(),"Contact name is required.");contact.email=email(command.email());contact.role=clean(command.role());contact.notes=clean(command.notes());contact.updatedAt=Instant.now();contact.updatedBy=actor;return view(contact);}
 private ContactEntity find(UUID accountId,UUID id){var result=contacts.findById(id).orElseThrow(()->new com.brandempiricism.etocrm.commons.ResourceNotFoundException());if(!result.accountId.equals(accountId))throw new com.brandempiricism.etocrm.commons.ResourceNotFoundException();return result;}
 private static ContactView view(ContactEntity c){return new ContactView(c.id,c.accountId,c.name,c.email,c.role,c.notes,c.createdAt,c.updatedAt);}
 private static String required(String value,String message){if(value==null||value.isBlank())throw new IllegalArgumentException(message);return value.trim();}
 private static String email(String value){var result=required(value,"Contact email is required.");if(!result.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("Enter a valid contact email address.");return result;}
 private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
 private static boolean contains(String value,String query){return value!=null&&value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));}
 record ContactView(UUID id,UUID accountId,String name,String email,String role,String notes,Instant createdAt,Instant updatedAt){}
}
