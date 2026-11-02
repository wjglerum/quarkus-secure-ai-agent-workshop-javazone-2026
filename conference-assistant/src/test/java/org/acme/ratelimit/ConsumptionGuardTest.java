package org.acme.ratelimit;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.RateLimitException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ConsumptionGuardTest {

    @Inject
    ConsumptionGuard guard;

    @Test
    void exceedingTheRateLimitThrows() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(guard::enforce, "call " + (i + 1) + " should be allowed");
        }
        assertThrows(RateLimitException.class, guard::enforce);
    }
}
