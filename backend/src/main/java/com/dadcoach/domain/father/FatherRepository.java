package com.dadcoach.domain.father;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FatherRepository extends JpaRepository<Father, Long> {

    /** The father who owns this WhatsApp number (E.164; unique in the database). */
    Optional<Father> findByPhone(String phone);

    /**
     * The father, with his row locked until the transaction ends - serializes his bookings, so two
     * schedule_quality_time calls for the same slot (one per child) end in one session, never two.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Father f WHERE f.id = :id")
    Optional<Father> findByIdForUpdate(@Param("id") Long id);
}
