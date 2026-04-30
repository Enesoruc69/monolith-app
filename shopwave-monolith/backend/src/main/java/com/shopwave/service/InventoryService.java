package com.shopwave.service;

import com.shopwave.domain.Inventory;
import com.shopwave.exception.InsufficientStockException;
import com.shopwave.exception.NotFoundException;
import com.shopwave.repository.InventoryRepository;
import com.shopwave.util.ChaosHelper; // <-- 1. Import eklendi
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * InventoryService — stok rezervasyon ve güncelleme işlemleri.
 *
 * Monolith'te bu servis, OrderService ile AYNI transaction içinde çalışır.
 * Sipariş kaydedilirken stok aynı anda rezerve edilir; rollback durumunda
 * her ikisi de geri alınır. Bu "free" atomiklik dağıtık sistemde kaybolur.
 *
 * LAB NOTU (Servis Ayrımı — Lab 3):
 *   Bu sınıf ayrı bir "inventory-service" Spring Boot uygulamasına taşınacak.
 *   OrderService HTTP ile iletişim kuracak.
 *   Ağ kesilmesi, timeout, partial failure senaryoları gözlemlenecek.
 *   Çözüm yolları: Saga (choreography / orchestration), 2PC, Outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final AuditService        auditService;
    private final ChaosHelper         chaosHelper;

    // ─── Queries ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Inventory getByProductId(Long productId) {
        chaosHelper.injectLatency(); // <-- Gecikme eklendi (Ağ yavaşlığını simüle eder)
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found for product: " + productId));
    }

    @Transactional(readOnly = true)
    public List<Inventory> getLowStock(int threshold) {
        return inventoryRepository.findLowStock(threshold);
    }

    // ─── Commands ─────────────────────────────────────────────

    /**
     * Sipariş için stok ayır.
     * Pessimistic lock ile eş zamanlı isteklerde race condition önlenir.
     *
     * LAB NOTU:
     *   Tek instance'ta bu yeterli. Birden fazla backend instance çalıştığında
     *   DB lock hâlâ işe yarar (DB-level lock). Ama inventory ayrı servise
     *   taşınırsa bu metot HTTP'ye dönüşür ve DB lock artık kullanılamaz.
     */
    @Transactional
    public void reserve(Long productId, int quantity) {
        // LAB-2: Yapay gecikme (chaos delay) eklendi.
        // @Transactional içinde olduğu için bu süre boyunca DB connection'ı meşgul kalacak!
        chaosHelper.injectLatency(); // <-- 3. Asıl gecikme simülasyonu burada

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
    @Transactional
    public void release(Long productId, int quantity) {
        chaosHelper.injectLatency(); // İptal işlemlerinde de gecikme olabilir

        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.release(quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_RELEASED", "Inventory", productId, "qty=" + quantity);
        log.info("Stock released productId={} qty={}", productId, quantity);
    }

    /** Sipariş tesliminde fiziksel stoktan düş. */
    @Transactional
    public void deduct(Long productId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.deduct(quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_DEDUCTED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
    }

    /** Manuel stok ekle (yeni sevkiyat geldiğinde). */
    @Transactional
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