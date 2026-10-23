package com.motorsure.coreapi.customer.api;

import com.motorsure.coreapi.common.validation.NotFutureDate;
import com.motorsure.coreapi.customer.domain.CustomerStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class CustomerRequests {
    private CustomerRequests() { }
    public record Create(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 254) @Pattern(regexp = "\\s*[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\s*", message = "must be a valid email address") String email,
            @Size(max = 32) @Pattern(regexp = "(?:|(?=(?:\\D*\\d){7,15}\\D*$)[+0-9(). -]+)", message = "must contain 7 to 15 digits and supported punctuation") String phoneNumber,
            @NotNull @NotFutureDate LocalDate dateOfBirth) { }
    public record Update(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 254) @Pattern(regexp = "\\s*[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\s*", message = "must be a valid email address") String email,
            @Size(max = 32) @Pattern(regexp = "(?:|(?=(?:\\D*\\d){7,15}\\D*$)[+0-9(). -]+)", message = "must contain 7 to 15 digits and supported punctuation") String phoneNumber,
            @NotNull @NotFutureDate LocalDate dateOfBirth) { }
    public record Status(@NotNull CustomerStatus status) { }
}
