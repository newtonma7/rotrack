package com.rotrack.repository;

import com.rotrack.model.NoteCreationReplay;
import com.rotrack.model.NoteCreationReplayId;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface NoteCreationReplayRepository extends JpaRepository<NoteCreationReplay, NoteCreationReplayId> {
    @Modifying
    @Query(value = """
            INSERT INTO public.note_creation_replays(owner_id, idempotency_key, fingerprint, note_id, created_at)
            VALUES (:ownerId, :key, :fingerprint, :noteId, now())
            ON CONFLICT (owner_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claim(
            @Param("ownerId") UUID ownerId,
            @Param("key") UUID key,
            @Param("fingerprint") byte[] fingerprint,
            @Param("noteId") UUID noteId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NoteCreationReplay> findByOwnerIdAndIdempotencyKey(UUID ownerId, UUID idempotencyKey);
    Optional<NoteCreationReplay> findByOwnerIdAndNoteId(UUID ownerId, UUID noteId);
}
