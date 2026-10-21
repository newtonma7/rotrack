package com.rotrack.repository;

import com.rotrack.model.ReflectionCreationReplay;
import com.rotrack.model.ReflectionCreationReplayId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReflectionCreationReplayRepository extends JpaRepository<ReflectionCreationReplay, ReflectionCreationReplayId> {
    Optional<ReflectionCreationReplay> findByOwnerIdAndIdempotencyKey(UUID ownerId, UUID idempotencyKey);

    @Modifying
    @Query(value = "insert into public.reflection_creation_replays "
            + "(owner_id, idempotency_key, fingerprint, reflection_id) "
            + "values (:ownerId, :idempotencyKey, :fingerprint, :reflectionId) "
            + "on conflict (owner_id, idempotency_key) do nothing", nativeQuery = true)
    int claim(
            @Param("ownerId") UUID ownerId,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("fingerprint") byte[] fingerprint,
            @Param("reflectionId") UUID reflectionId
    );
}
