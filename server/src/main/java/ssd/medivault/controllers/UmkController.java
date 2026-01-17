package ssd.medivault.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;
import ssd.medivault.entities.PatientPrivateDetails;
import ssd.medivault.logging.AuditLogger;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class UmkController {

    private final WebAuthnCredentialService credentialService;
    private final AuditLogger logger;

    /**
     * This endpoint is designed to be called right after the registration procedure of a new security key.
     * It accepts an encrypted version of the patient UMK, which is stored along the new credentials.
     *
     * @param encryptedUmk the object storing the encrypted UMK
     * @param auth the authentication token
     */
    @PostMapping("/patient/umk")
    public void setPatientUmk(@RequestBody PatientAuthenticator.EncryptedUmk encryptedUmk, Authentication auth, HttpServletRequest request) {

        if(!credentialService.areCredentialsFromSamePatient(String.valueOf(auth.getCredentials()),
                                                                           encryptedUmk.credentialId())) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_SET_UMK", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Optional<PatientAuthenticator> patientAuthenticator = credentialService.getAuthRepository()
                .findByCredentialId(encryptedUmk.credentialId());

        if(patientAuthenticator.isEmpty()){
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_SET_UMK", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (!credentialService.setUmk(encryptedUmk)) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_SET_UMK", null, "Bad Request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_SET_UMK", null);
    }

    /**
     * This endpoint is used by the patient to retrieve its encrypted UMK.
     * It fetches the encrypted version corresponding to the current authentication key used by the patient
     * so that the patient can decrypt their UMK client-side.
     *
     * @param auth the authentication token
     * @param request the HTTP request object
     * @return the encrypted UMK object
     */
    @GetMapping("/patient/umk")
    @ResponseBody
    public PatientAuthenticator.EncryptedUmk getPatientUmk(Authentication auth, HttpServletRequest request) {
        Optional<PatientAuthenticator> authenticator = this.credentialService.getAuthRepository()
                .findByCredentialId(String.valueOf(auth.getCredentials()));

        if(authenticator.isEmpty()) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_GET_UMK", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_GET_UMK", null);
        return authenticator.get().getUmk();
    }

    /**
     * This endpoint is called to update / set the private details of a patient.
     * This includes the date of birth and full name
     *
     * @param encryptedDetails the object storing the encrypted patient private details
     * @param auth the authentication token
     * @param request the HTTP request object
     */
    @PostMapping("/patient/details")
    public void setPatientDetails(@RequestBody PatientPrivateDetails.PatientPrivateDetailsRecord encryptedDetails,
                                  Authentication auth, HttpServletRequest request) {

        Patient patient = credentialService.extractPatient(auth, "PATIENT_SET_DETAILS", request);

        if (!credentialService.setPrivateDetails(encryptedDetails, patient)) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_SET_DETAILS", null, "Bad Request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_SET_DETAILS", null);
    }

    /**
     * This endpoint is used by the patient to retrieve their encrypted private details.
     * The patient can then decrypt them using their UMK client-side
     *
     * @param auth the authentication token
     * @return the encrypted patient details
     * @param request the HTTP request object
     */
    @GetMapping("/patient/details")
    @ResponseBody
    public PatientPrivateDetails.PatientPrivateDetailsRecord getPatientDetails(Authentication auth, HttpServletRequest request) {
        Patient patient = credentialService.extractPatient(auth, "PATIENT_GET_DETAILS", request);

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_GET_DETAILS", null);
        return patient.getDetails().toRecord(patient.getId());
    }
}

