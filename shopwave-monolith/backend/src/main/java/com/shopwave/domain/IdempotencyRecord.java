package com.shopwave.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {
    @Id
    private String idempotencyKey;
    private String operation;
    private LocalDateTime createdAt;
}