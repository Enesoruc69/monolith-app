package com.shopwave.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Random;

@Slf4j
@Component
public class ChaosHelper {

    @Value("${shopwave.chaos.delay.enabled:false}")
    private boolean enabled;

    @Value("${shopwave.chaos.delay.fixed-ms:0}")
    private int fixedMs;

    @Value("${shopwave.chaos.delay.jitter-ms:0}")
    private int jitterMs;

    private final Random random = new Random();

    public void injectLatency() {
        if (!enabled) {
            return;
        }

        int delay = fixedMs;
        if (jitterMs > 0) {
            delay += random.nextInt(jitterMs);
        }

        if (delay > 0) {
            try {
                log.info("Injecting chaos latency: {} ms", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Chaos latency injection interrupted", e);
            }
        }
    }
}
