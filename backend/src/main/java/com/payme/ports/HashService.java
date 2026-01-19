package com.payme.ports;

public interface HashService {
    /**
     * Computes SHA-256 hash of the input string.
     *
     * @param input The input string to hash
     * @return Hexadecimal representation of the hash
     */
    String sha256(String input);
}
