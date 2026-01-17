package ssd.medivault.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Le nom complet est obligatoire")
    @Size(min = 3, max = 32, message = "Le nom doit contenir 3-32 caractères")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s]+$", message = "Le nom ne peut contenir que des lettres")
    String name,

    @NotBlank(message = "La date de naissance est obligatoire")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Format: YYYY-MM-DD")
    String dob,

    @NotBlank(message = "Le nom de la clé est obligatoire")
    @Size(min = 3, max = 16, message = "Le nom de clé doit contenir 3-16 caractères")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "Lettres, chiffres et espaces uniquement")
    String credname,

    @NotBlank(message = "Le token captcha est requis")
    String captchaToken
) {}