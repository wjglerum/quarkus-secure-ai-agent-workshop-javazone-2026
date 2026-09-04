package org.acme.ratelimit;

import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class ConsumptionGuard {

    @RateLimit(value = 5, window = 10, windowUnit = ChronoUnit.SECONDS)
    public void enforce() {
    }
}
