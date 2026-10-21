package com.rotrack.repository;

import com.rotrack.model.Reflection;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReflectionRepository extends JpaRepository<Reflection, UUID> {
    Optional<Reflection> findByUserIdAndLocalDate(UUID userId, LocalDate localDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reflection from Reflection reflection "
            + "where reflection.userId = :userId and reflection.localDate = :localDate")
    Optional<Reflection> lockByOwnerAndDate(
            @Param("userId") UUID userId,
            @Param("localDate") LocalDate localDate
    );

    @Query("select reflection.localDate as localDate, "
            + "(case when trim(reflection.contentText) <> '' then true else false end) as hasContent "
            + "from Reflection reflection "
            + "where reflection.userId = :userId and reflection.localDate between :start and :end")
    List<ReflectionSummaryProjection> findSummaries(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    interface ReflectionSummaryProjection {
        LocalDate getLocalDate();
        boolean getHasContent();
    }
}
