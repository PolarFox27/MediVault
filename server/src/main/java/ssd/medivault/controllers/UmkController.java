package ssd.medivault.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.auth.AuthenticationToken;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;
import ssd.medivault.entities.PatientPrivateDetails;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class UmkController {

    private final WebAuthnCredentialService credentialService;

    /**
     * This endpoint is designed to be called right after the registration procedure of a new security key.
     * It accepts an encrypted version of the patient UMK, which is stored along the new credentials.
     *
     * @param encryptedUmk the object storing the encrypted UMK
     * @param auth the authentication token
     */
    @PostMapping("/patient/umk")
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
     * It fetches the encrypted version corresponding to the current authentication key used by the patient
     * so that the patient can decrypt their UMK client-side.
     *
     * @param auth the authentication token
     * @return the encrypted UMK object
     */
    @GetMapping("/patient/umk")
    @ResponseBody
    public PatientAuthenticator.EncryptedUmk getPatientUmk(Authentication auth) {
        Optional<PatientAuthenticator> authenticator = this.credentialService.getAuthRepository()
                .findByCredentialId(String.valueOf(auth.getCredentials()));

        if(authenticator.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        return authenticator.get().getUmk();
    }

    /**
     * This endpoint is called to update / set the private details of a patient.
     * This includes the date of birth and full name
     *
     * @param encryptedDetails the object storing the encrypted patient private details
     * @param auth the authentication token
     */
    @PostMapping("/patient/details")
    public void setPatientDetails(@RequestBody PatientPrivateDetails.PatientPrivateDetailsRecord encryptedDetails,
                                  Authentication auth) {

        Patient patient = AuthenticationToken.extractPatient(auth, credentialService.getPatientRepository());

        if (!credentialService.setPrivateDetails(encryptedDetails, patient)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * This endpoint is used by the patient to retrieve their encrypted private details.
     * The patient can then decrypt them using their UMK client-side
     *
     * @param auth the authentication token
     * @return the encrypted patient details
     */
    @GetMapping("/patient/details")
    @ResponseBody
    public PatientPrivateDetails.PatientPrivateDetailsRecord getPatientDetails(Authentication auth) {
        Patient patient = AuthenticationToken.extractPatient(auth, credentialService.getPatientRepository());

        return patient.getDetails().toRecord();
    }
}

