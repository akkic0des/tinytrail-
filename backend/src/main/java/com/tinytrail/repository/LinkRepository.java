package com.tinytrail.repository;

import com.tinytrail.model.Link;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    // Single atomic UPDATE (not read-modify-write) so concurrent clicks never lose counts.
    @Modifying
    @Query("update Link l set l.clickCount = l.clickCount + 1 where l.shortCode = :code")
    void incrementClickCount(@Param("code") String code);
}
