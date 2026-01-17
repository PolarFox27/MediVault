package ssd.medivault.controllers.dto;

import jakarta.validation.constraints.NotBlank;

public record FinishLoginRequest(
    @NotBlank(message = "Les credentials sont requis")
    String credential
) {}