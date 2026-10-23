package com.motorsure.policy.application;

import com.motorsure.policy.api.PolicyRequest;
import com.motorsure.policy.api.PolicyResponse;
import com.motorsure.policy.domain.Policy;
import com.motorsure.policy.domain.PolicyNotFoundException;
import com.motorsure.policy.infrastructure.persistence.PolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PolicyService {
    private final PolicyRepository policies;
    public PolicyService(PolicyRepository policies) { this.policies = policies; }

    @Transactional(readOnly = true)
    public Page<PolicyResponse> list(Pageable pageable) { return policies.findAll(pageable).map(PolicyResponse::from); }

    @Transactional(readOnly = true)
    public PolicyResponse get(UUID id) {
        return policies.findById(id).map(PolicyResponse::from).orElseThrow(() -> new PolicyNotFoundException(id));
    }

    @Transactional
    public PolicyResponse create(PolicyRequest request) {
        String cover = request.coverType().equalsIgnoreCase("third party") ? "Third party" : "Comprehensive";
        Policy policy = policies.save(new Policy(request.customerName().trim(), request.vehicleRegistration().trim().toUpperCase(),
                request.vehicleMake().trim(), cover));
        return PolicyResponse.from(policy);
    }
}
