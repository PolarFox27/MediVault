package ssd.medivault.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FinishRegistrationRequest(
    @NotBlank(message = "Les credentials sont requis")
    String credential,

    @NotBlank(message = "Le nom de la clé est obligatoire")
    @Size(min = 3, max = 16, message = "Le nom de clé doit contenir 3-16 caractères")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "Lettres, chiffres et espaces uniquement")
    String credname
) {}