package com.example.todoapi.repository;

import com.example.todoapi.entity.IdempotencyRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Tries to claim a brand-new idempotency key in its OWN short transaction,
     * independent of the caller's (REQUIRES_NEW) — so a failed claim never
     * poisons the caller's ongoing transaction, and a successful claim is
     * visible to a concurrent request immediately, before the task itself is
     * even created.
     *
     * Returns the saved record on success, or empty() if the key was already
     * taken (unique-constraint race) — the caller should then read the
     * existing record and either replay it or reject a payload mismatch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default Optional<IdempotencyRecord> tryClaim(IdempotencyRecord record) {
        try {
            return Optional.of(saveAndFlush(record));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    /**
     * Releases a claimed key (its own REQUIRES_NEW transaction) when the
     * actual task creation failed afterwards — otherwise the key would stay
     * stuck forever on a placeholder response, blocking every future retry
     * even with a corrected request body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default void releaseClaim(Long id) {
        deleteById(id);
    }
}
