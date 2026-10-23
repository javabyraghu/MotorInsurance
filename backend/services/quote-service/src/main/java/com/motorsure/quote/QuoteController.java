package com.motorsure.quote;

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
@RequestMapping("/api/quotes")
@CrossOrigin(origins = "${frontend.origin}")
public class QuoteController {
    private final QuoteService quoteService;
    public QuoteController(QuoteService quoteService) { this.quoteService = quoteService; }

    @GetMapping
    public Page<QuoteResponse> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return quoteService.list(pageable);
    }

    @GetMapping("/{id}")
    public QuoteResponse get(@PathVariable UUID id) { return quoteService.get(id); }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody QuoteRequest request) {
        QuoteResponse response = quoteService.create(request);
        return ResponseEntity.created(URI.create("/api/quotes/" + response.id())).body(response);
    }
}
