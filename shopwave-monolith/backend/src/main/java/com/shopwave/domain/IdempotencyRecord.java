package com.shopwave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {
    
    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(nullable = false)
    private String operation;
    
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}