package com.motorsure.quote;

import java.util.UUID;

public class QuoteNotFoundException extends RuntimeException {
    public QuoteNotFoundException(UUID id) { super("Quote not found: " + id); }
}
