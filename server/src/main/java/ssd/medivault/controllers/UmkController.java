package ssd.medivault.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.entities.PatientAuthenticator;

import java.util.Optional;

@RestController
public class UmkController {

    private final WebAuthnCredentialService credentialService;

    UmkController(WebAuthnCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * This endpoint is designed to be called right after the registration procedure of a new security key.
     * It accepts an encrypted version of the patient UMK, which is stored along the new credentials.
     *
     * @param encryptedUmk the object storing the encrypted UMK
     * @param auth the authentication token
     */
    @PostMapping("/umk/set")
    public void setPatientUmk(@RequestBody PatientAuthenticator.EncryptedUmk encryptedUmk, Authentication auth) {

        if(!credentialService.areCredentialsFromSamePatient(String.valueOf(auth.getCredentials()),
                                                                           encryptedUmk.credentialId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Optional<PatientAuthenticator> patientAuthenticator = credentialService.getAuthRepository()
                .findByCredentialId(encryptedUmk.credentialId());

        if(patientAuthenticator.isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (!credentialService.setUmk(encryptedUmk)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * This endpoint is used by the patient to retrieve its encrypted UMK.
     * It fetches the version corresponding to the current authentication key used by the patient
     * so that the patient can decrypt their UMK client-side.
     *
     * @param auth the authentication token
     * @return the encrypted Umk object.
     */
    @GetMapping("/umk/get")
    @ResponseBody
    public PatientAuthenticator.EncryptedUmk getPatientUmk(Authentication auth) {
        Optional<PatientAuthenticator> authenticator = this.credentialService.getAuthRepository()
                .findByCredentialId(String.valueOf(auth.getCredentials()));

        if(authenticator.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        return authenticator.get().getUmk();
    }
}

