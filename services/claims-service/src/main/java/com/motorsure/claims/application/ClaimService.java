package com.motorsure.claims.application;

import com.motorsure.claims.api.ClaimRequest;
import com.motorsure.claims.api.ClaimResponse;
import com.motorsure.claims.api.ClaimStatusChangeRequest;
import com.motorsure.claims.domain.Claim;
import com.motorsure.claims.domain.ClaimNotFoundException;
import com.motorsure.claims.infrastructure.persistence.ClaimRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ClaimService {
    private final ClaimRepository claims;
    public ClaimService(ClaimRepository claims) { this.claims = claims; }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> list(Pageable pageable) { return claims.findAll(pageable).map(ClaimResponse::from); }

    @Transactional(readOnly = true)
    public ClaimResponse get(UUID id) {
        return claims.findById(id).map(ClaimResponse::from).orElseThrow(() -> new ClaimNotFoundException(id));
    }

    @Transactional
    public ClaimResponse create(ClaimRequest request) {
        Claim claim = claims.save(new Claim(request.policyNumber().trim(), request.vehicleRegistration().trim().toUpperCase(),
                request.incidentType().trim(), request.incidentDate(), request.description().trim()));
        return ClaimResponse.from(claim);
    }

    @Transactional
    public ClaimResponse changeStatus(UUID id, ClaimStatusChangeRequest request) {
        Claim claim = claims.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
        claim.transitionTo(request.status());
        return ClaimResponse.from(claims.save(claim));
    }
}
