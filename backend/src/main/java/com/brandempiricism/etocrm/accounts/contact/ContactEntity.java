package com.brandempiricism.etocrm.accounts.contact;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="account_contact")
class ContactEntity {
 @Id UUID id;UUID accountId;String name;String email;String role;String notes;Instant createdAt;String createdBy;Instant updatedAt;String updatedBy;
 protected ContactEntity(){} ContactEntity(UUID id,UUID accountId,String name,String email,String role,String notes,Instant now,String actor){this.id=id;this.accountId=accountId;this.name=name;this.email=email;this.role=role;this.notes=notes;this.createdAt=now;this.createdBy=actor;this.updatedAt=now;this.updatedBy=actor;}
}
