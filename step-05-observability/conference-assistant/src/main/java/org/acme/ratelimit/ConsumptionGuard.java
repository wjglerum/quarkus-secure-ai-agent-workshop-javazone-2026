package org.acme.ratelimit;

import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.temporal.ChronoUnit;

/**
 * Caps how many chat turns are processed in a time window, as a defense against
 * OWASP LLM10 Unbounded Consumption (denial of wallet). The WebSocket endpoint
 * calls {@link #enforce()} before invoking the model; when the limit is exceeded
 * SmallRye Fault Tolerance throws {@code RateLimitException} and the model is
 * never called.
 *
 * <p>Keeping the gate separate from the model call makes the limit
 * deterministically testable without a running model. The limit is global per
 * method here for workshop simplicity; a production system would key it per user.
 */
@ApplicationScoped
public class ConsumptionGuard {

    @RateLimit(value = 5, window = 10, windowUnit = ChronoUnit.SECONDS)
    public void enforce() {
        // Intentionally empty: the @RateLimit interceptor enforces the cap.
    }
}
