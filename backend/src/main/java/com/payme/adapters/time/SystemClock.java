package com.payme.adapters.time;

import com.payme.ports.Clock;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
