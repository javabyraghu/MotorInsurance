package com.motorsure.claims.api;

import com.motorsure.claims.application.ClaimService;
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
@RequestMapping("/api/claims")
@CrossOrigin(origins = "${frontend.origin}")
public class ClaimController {
    private final ClaimService claimService;
    public ClaimController(ClaimService claimService) { this.claimService = claimService; }

    @GetMapping
    public Page<ClaimResponse> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return claimService.list(pageable);
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@PathVariable UUID id) { return claimService.get(id); }

    @PostMapping
    public ResponseEntity<ClaimResponse> create(@Valid @RequestBody ClaimRequest request) {
        ClaimResponse response = claimService.create(request);
        return ResponseEntity.created(URI.create("/api/claims/" + response.id())).body(response);
    }

    @PatchMapping("/{id}/status")
    public ClaimResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ClaimStatusChangeRequest request) {
        return claimService.changeStatus(id, request);
    }
}
