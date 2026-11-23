package com.tinytrail.repository;

import com.tinytrail.model.ClickEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /** Rows of [day, count]; days with zero clicks are absent (the service fills them in). */
    @Query(value = "select date(clicked_at), count(*) from click_events "
            + "where short_code = :code and clicked_at >= :since group by date(clicked_at)", nativeQuery = true)
    List<Object[]> countPerDay(@Param("code") String code, @Param("since") Instant since);

    /** Rows of [referrer, count], most clicked first; pass PageRequest.of(0, 5) for the top 5. */
    @Query("select c.referrer, count(c) from ClickEvent c where c.shortCode = :code "
            + "group by c.referrer order by count(c) desc")
    List<Object[]> topReferrers(@Param("code") String code, Pageable page);
}
