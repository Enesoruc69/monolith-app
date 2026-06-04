package com.shopwave.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LAB-3: p95 / p99 Gecikme Ölçümü
 * Micrometer histogram ile her HTTP endpoint'inin gecikme dağılımını ölçer.
 */
@Configuration
@ConditionalOnProperty(name = "shopwave.metrics.percentiles.enabled", havingValue = "true")
public class MetricsConfig {

    @Bean
    public MeterFilter customMeterFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("http.server.requests")) {
                    return DistributionStatisticConfig.builder()
                            .percentiles(0.95, 0.99)
                            .percentileHistogram(true)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
