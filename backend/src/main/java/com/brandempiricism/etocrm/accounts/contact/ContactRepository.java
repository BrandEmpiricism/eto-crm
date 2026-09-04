package com.brandempiricism.etocrm.accounts.contact;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface ContactRepository extends JpaRepository<ContactEntity,UUID>{List<ContactEntity> findByAccountIdOrderByName(UUID accountId);}
