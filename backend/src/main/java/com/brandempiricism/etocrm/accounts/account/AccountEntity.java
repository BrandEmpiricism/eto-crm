package com.brandempiricism.etocrm.accounts.account;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="account")
class AccountEntity {
    @Id UUID id;
    String name; String industry; String location; String website; String owner; String summary;
    Instant createdAt; String createdBy; Instant updatedAt; String updatedBy;
    protected AccountEntity() {}
    AccountEntity(UUID id,String name,String industry,String location,String website,String owner,String summary,Instant now,String actor){
        this.id=id;this.name=name;this.industry=industry;this.location=location;this.website=website;this.owner=owner;this.summary=summary;
        this.createdAt=now;this.createdBy=actor;this.updatedAt=now;this.updatedBy=actor;
    }
}
