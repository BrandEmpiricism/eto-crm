package com.brandempiricism.etocrm.accounts.account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface AccountRepository extends JpaRepository<AccountEntity,UUID> {}
