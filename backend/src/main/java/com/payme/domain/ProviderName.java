package com.payme.domain;

public enum ProviderName {
    FAKE,
    PAYFAST,
    // @dec(INT-001) PayShap rail — see decisions/INT-001.md
    // Mock-first; the real adapter (Stitch or other aggregator) will be added later
    // and registered against this same enum value via the registry (ARCH-001).
    PAYSHAP
}
