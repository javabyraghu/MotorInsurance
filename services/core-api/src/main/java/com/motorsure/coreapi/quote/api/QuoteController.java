package com.motorsure.coreapi.quote.api;

import com.motorsure.coreapi.quote.application.QuoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private static final Set<String> SORT_FIELDS = Set.of("quoteNumber", "status", "createdAt", "updatedAt");
    private final QuoteService service;
    public QuoteController(QuoteService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<QuoteResponses.View> create(@Valid @RequestBody QuoteRequests.Create request) {
        QuoteResponses.View created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
    @GetMapping("/{id}") public QuoteResponses.View get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping public QuoteResponses.PageView list(@RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        if (!SORT_FIELDS.contains(sortBy)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported sort field");
        var result = service.list(customerId, page, size, sortBy, direction);
        return new QuoteResponses.PageView(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @PostMapping("/{id}/submit") public QuoteResponses.View submit(@PathVariable UUID id) { return service.submit(id); }
    @PostMapping("/{id}/cancel") public QuoteResponses.View cancel(@PathVariable UUID id) { return service.cancel(id); }
}
