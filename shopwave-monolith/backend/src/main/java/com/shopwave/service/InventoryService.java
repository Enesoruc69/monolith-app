package com.shopwave.service;

import com.shopwave.domain.Inventory;
import com.shopwave.exception.InsufficientStockException;
import com.shopwave.exception.NotFoundException;
import com.shopwave.repository.InventoryRepository;
import com.shopwave.util.ChaosHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * InventoryService — stok rezervasyon ve güncelleme işlemleri.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final AuditService        auditService;
    private final ChaosHelper         chaosHelper;

    // ─── Queries ──────────────────────────────────────────────

    // Okuma işlemlerine 5 saniye timeout eklendi
    @Transactional(readOnly = true, timeout = 5)
    public Inventory getByProductId(Long productId) {
        chaosHelper.injectLatency();
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found for product: " + productId));
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<Inventory> getLowStock(int threshold) {
        return inventoryRepository.findLowStock(threshold);
    }

    // ─── Commands ─────────────────────────────────────────────

    /**
     * Sipariş için stok ayır.
     * Pessimistic lock ile eş zamanlı isteklerde race condition önlenir.
     */
    // Yazma/Locking işlemlerine 3 saniye (katı) timeout eklendi
    @Transactional(timeout = 3)
    public void reserve(Long productId, int quantity) {
        chaosHelper.injectLatency(); // Eğer bu süre 3 saniyeyi aşarsa işlem iptal olur.

        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        if (!inv.canReserve(quantity)) {
            throw new InsufficientStockException(productId, inv.availableQuantity(), quantity);
        }

        inv.reserve(quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_RESERVED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
        log.info("Stock reserved productId={} qty={} available={}", productId, quantity, inv.availableQuantity());
    }

    /** Sipariş iptalinde rezervasyonu geri bırak. */
    @Transactional(timeout = 3)
    public void release(Long productId, int quantity) {
        chaosHelper.injectLatency();

        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.release(quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_RELEASED", "Inventory", productId, "qty=" + quantity);
        log.info("Stock released productId={} qty={}", productId, quantity);
    }

    /** Sipariş tesliminde fiziksel stoktan düş. */
    @Transactional(timeout = 3)
    public void deduct(Long productId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.deduct(quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_DEDUCTED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
    }

    /** Manuel stok ekle (yeni sevkiyat geldiğinde). */
    @Transactional(timeout = 5)
    public void addStock(Long productId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.setQuantity(inv.getQuantity() + quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_ADDED", "Inventory", productId,
                "added=" + quantity + " total=" + inv.getQuantity());
        log.info("Stock added productId={} qty={} total={}", productId, quantity, inv.getQuantity());
    }
}