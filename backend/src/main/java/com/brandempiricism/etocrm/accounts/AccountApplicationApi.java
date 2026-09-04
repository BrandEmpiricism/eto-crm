package com.brandempiricism.etocrm.accounts;

import java.util.UUID;
import java.util.List;

public interface AccountApplicationApi {
    AccountRef createAccount(CreateAccount command, String actor);
    AccountRef getAccount(UUID id);

    record CreateAccount(String name, String industry, String location, String website, String owner, String summary,
                         List<CreateContact> contacts) {}
    record CreateContact(String name, String email, String role, String notes) {}
    record AccountRef(UUID id, String name, String industry, String location, String website, String owner, String summary) {}
}
