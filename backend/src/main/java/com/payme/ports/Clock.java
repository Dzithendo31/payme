package com.payme.ports;

import java.time.Instant;

public interface Clock {
    
    Instant now();
}
