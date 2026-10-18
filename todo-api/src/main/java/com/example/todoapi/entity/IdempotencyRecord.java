package com.example.todoapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * CR-002 (API Idempotency): one row per client-supplied Idempotency-Key.
 * A unique constraint on idempotencyKey is the real concurrency safeguard —
 * see IdempotencyRecordRepository.tryClaim().
 */
@Entity
@Table(name = "IDEMPOTENCY_RECORD")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "IDEMPOTENCY_KEY", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "REQUEST_FINGERPRINT", nullable = false, length = 64)
    private String requestFingerprint;

    /** JSON of the original TaskResponse. Placeholder "{}" / status 0 until the task is actually created. */
    @Lob
    @Column(name = "RESPONSE_BODY", nullable = false)
    private String responseBody;

    @Column(name = "RESPONSE_STATUS", nullable = false)
    private int responseStatus;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;
}
