package com.motorsure.policy.api;

import com.motorsure.policy.application.PolicyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
@CrossOrigin(origins = "${frontend.origin}")
public class PolicyController {
    private final PolicyService policyService;
    public PolicyController(PolicyService policyService) { this.policyService = policyService; }

    @GetMapping
    public Page<PolicyResponse> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return policyService.list(pageable);
    }

    @GetMapping("/{id}")
    public PolicyResponse get(@PathVariable UUID id) { return policyService.get(id); }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody PolicyRequest request) {
        PolicyResponse response = policyService.create(request);
        return ResponseEntity.created(URI.create("/api/policies/" + response.id())).body(response);
    }
}
