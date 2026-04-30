package com.shopwave.service;

import com.shopwave.domain.IdempotencyRecord;
import com.shopwave.domain.Inventory;
import com.shopwave.exception.InsufficientStockException;
import com.shopwave.exception.NotFoundException;
import com.shopwave.repository.IdempotencyRepository;
import com.shopwave.repository.InventoryRepository;
import com.shopwave.util.ChaosHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC; // <-- Request ID (Traceability) için eklendi
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InventoryService — stok rezervasyon ve güncelleme işlemleri.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final IdempotencyRepository idempotencyRepository; // <-- Eklendi
    private final AuditService        auditService;
    private final ChaosHelper         chaosHelper;

    // ─── Queries ──────────────────────────────────────────────

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
     * Idempotency eklendi: Aynı istek (retry) gelirse ikinci kez stok düşmez.
     */
    @Transactional(timeout = 3)
    public void reserve(Long productId, int quantity, String idempotencyKey) {
        // Loglarda bu işlemin hangi HTTP isteğine ait olduğunu yakalamak için MDC'den Request ID okunur
        String requestId = MDC.get("requestId");

        // 1. Idempotency Kontrolü: Bu istek daha önce başarıyla işlendi mi?
        if (idempotencyKey != null && idempotencyRepository.existsById(idempotencyKey)) {
            log.warn("[ReqID: {}] Idempotency hit! Rezervasyon isteği daha önce işlenmiş, atlanıyor. Key: {}", requestId, idempotencyKey);
            return; // İşlem daha önce yapılmış, hata fırlatma, başarılı dön
        }

        chaosHelper.injectLatency();

        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        if (!inv.canReserve(quantity)) {
            throw new InsufficientStockException(productId, inv.availableQuantity(), quantity);
        }

        // 2. Stok işlemini yap
        inv.reserve(quantity);
        inventoryRepository.save(inv);

        // 3. İşlemin başarıyla yapıldığını Idempotency tablosuna kaydet
        if (idempotencyKey != null) {
            idempotencyRepository.save(new IdempotencyRecord(idempotencyKey, "RESERVE", LocalDateTime.now()));
        }

        auditService.log("STOCK_RESERVED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
        log.info("[ReqID: {}] Stock reserved productId={} qty={} available={}", requestId, productId, quantity, inv.availableQuantity());
    }

    @Transactional(timeout = 3)
    public void release(Long productId, int quantity, String idempotencyKey) {
        String requestId = MDC.get("requestId");

        if (idempotencyKey != null && idempotencyRepository.existsById(idempotencyKey)) {
            log.warn("[ReqID: {}] Idempotency hit! İptal isteği daha önce işlenmiş. Key: {}", requestId, idempotencyKey);
            return;
        }

        chaosHelper.injectLatency();

        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.release(quantity);
        inventoryRepository.save(inv);

        if (idempotencyKey != null) {
            idempotencyRepository.save(new IdempotencyRecord(idempotencyKey, "RELEASE", LocalDateTime.now()));
        }

        auditService.log("STOCK_RELEASED", "Inventory", productId, "qty=" + quantity);
        log.info("[ReqID: {}] Stock released productId={} qty={}", requestId, productId, quantity);
    }

    @Transactional(timeout = 3)
    public void deduct(Long productId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));

        inv.deduct(quantity);
        inventoryRepository.save(inv);

        auditService.log("STOCK_DEDUCTED", "Inventory", productId,
                "qty=" + quantity + " remaining=" + inv.availableQuantity());
    }

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