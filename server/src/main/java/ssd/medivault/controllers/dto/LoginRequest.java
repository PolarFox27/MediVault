package ssd.medivault.controllers.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Le token captcha est requis")
    String captchaToken
) {}