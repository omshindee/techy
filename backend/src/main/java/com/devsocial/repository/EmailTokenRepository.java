package com.devsocial.repository;

import com.devsocial.model.EmailToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, EmailToken.Purpose purpose);

    /** Retires earlier unused links of the same kind when a new one is sent. */
    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    void retireUnused(@Param("userId") Long userId, @Param("purpose") EmailToken.Purpose purpose, @Param("now") Instant now);
}
