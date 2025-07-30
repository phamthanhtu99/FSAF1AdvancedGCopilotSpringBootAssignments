package com.example.demo.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {
    private final Random random = new Random();

    @Override
    public Health health() {
        boolean up = random.nextBoolean();
        if (up) {
            return Health.up().withDetail("paymentGateway", "Available").build();
        } else {
            return Health.down().withDetail("paymentGateway", "Unavailable").build();
        }
    }
}
