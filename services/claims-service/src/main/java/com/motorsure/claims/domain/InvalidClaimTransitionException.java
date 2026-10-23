package com.motorsure.claims.domain;

public class InvalidClaimTransitionException extends RuntimeException {
    public InvalidClaimTransitionException(ClaimStatus current, ClaimStatus requested) {
        super("Claim status cannot transition from " + current + " to " + requested);
    }
}
