package com.shopwave.repository;

import com.shopwave.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {
}