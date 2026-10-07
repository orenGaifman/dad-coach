package com.dadcoach.domain.father;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FatherRepository extends JpaRepository<Father, Long> {

    /** The father who owns this WhatsApp number (E.164; unique in the database). */
    Optional<Father> findByPhone(String phone);
}
