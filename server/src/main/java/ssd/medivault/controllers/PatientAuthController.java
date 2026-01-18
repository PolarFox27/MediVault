package ssd.medivault.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.auth.AuthenticationToken;
import ssd.medivault.auth.HCaptchaService;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.auth.WebAuthnRegistrationService;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;
import ssd.medivault.logging.AuditLogger;
import ssd.medivault.controllers.dto.FinishLoginRequest;
import ssd.medivault.controllers.dto.FinishRegistrationRequest;
import ssd.medivault.controllers.dto.LoginRequest;
import ssd.medivault.controllers.dto.RegisterRequest;
import ssd.medivault.utils.EncodingUtils;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Random;

@Controller
@RequiredArgsConstructor
public class PatientAuthController {

    private final WebAuthnCredentialService credentialService;
    private final WebAuthnRegistrationService registrationService;
    private final HCaptchaService hCaptchaService;
    private final AuditLogger logger;

    /**
     * Canonicalizes input by trimming whitespace and normalizing Unicode characters.
     * This prevents issues with different Unicode representations and extra spaces.
     */
    private String canonicalizeInput(String input) {
        if (input == null) return null;
        return Normalizer.normalize(input.trim(), Normalizer.Form.NFC);
    }

    /**
     * This function is the start endpoint of the WebAuthn registration protocol.
     * It creates a new Patient object with a random id, then returns the webauthn credentials options to the client.
     *
     * @param request the validated registration request
     * @param httpRequest the HTTP request object
     * @param session the HTTP session object, used to store some attributes for the registration.
     * @return the credentials options to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/register/start")
    @ResponseBody
    public ResponseEntity<?> startPatientRegistration(@Valid @ModelAttribute RegisterRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpSession session) {
        try {
            // Canonicalize inputs
            String canonicalCredname = canonicalizeInput(request.credname());

            if (!hCaptchaService.verify(request.captchaToken(), httpRequest.getRemoteAddr())) {
                logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_START_REGISTRATION", null, "Failed Captcha");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Captcha verification failed\", \"status\": 403}");
            }

            // Generate a random 32-bytes user handle for the patient
            byte[] bytes = new byte[32];
            new Random().nextBytes(bytes);
            ByteArray id = new ByteArray(bytes);

            // Create a user identity object and a Patient object
            UserIdentity userIdentity = UserIdentity.builder()
                    .name(id.getHex().substring(0, 16))
                    .displayName(id.getHex().substring(0, 16))
                    .id(id)
                    .build();
            Patient patient = new Patient(userIdentity);
            logger.logAction(AuditLogger.Level.INFO, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_START_REGISTRATION", null);
            String jsonResponse = registrationService.createJsonCredentialRegistrationOptions(patient, session);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);
        } catch (Exception e) {
            logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_START_REGISTRATION", null, "Registration start failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Registration start failed: " + e.getMessage() + "\", \"status\": 400}");
        }
    }

    /**
     * This function is the final endpoint of the WebAuthn registration protocol.
     * It retrieves the credentials sent by the client and stores permanently the patient and their credentials
     * in the database.
     * If the registration is successful, the patient is automatically logged in and authenticated for future requests.
     *
     * @param session the HTTP session object
     * @param request the HTTP request object
     * @return the public key of the registered credentials.
     */
    @PostMapping("/webauthn/register/finish")
    @ResponseBody
    public ResponseEntity<?> finishPatientRegistration(@Valid @ModelAttribute FinishRegistrationRequest request,
                                                                    HttpSession session,
                                                                    HttpServletRequest httpRequest) {
        try {
            // Canonicalize credname
            String canonicalCredname = canonicalizeInput(request.credname());

            // Complete the registration
            WebAuthnRegistrationService.RegistrationRecord registration = registrationService.completeRegistration(session,
                    request.credential());

            // Store the patient and their authenticator in the database
            Patient savedPatient = credentialService.getPatientRepository().save(registration.patient());
            PatientAuthenticator patientAuth = new PatientAuthenticator(registration.result(),
                    registration.pkc().getResponse(),
                    savedPatient,
                    canonicalCredname);
            credentialService.getAuthRepository().save(patientAuth);

            // Authenticate the patient
            AuthenticationToken.authenticatePatient(savedPatient.getUsername(),
                    EncodingUtils.toHex(patientAuth.getCredentialId()),
                    httpRequest);
            logger.logAction(AuditLogger.Level.INFO, httpRequest.getRemoteAddr(), "Patient:" + savedPatient.getUsername(), "PATIENT_FINISH_REGISTRATION", null);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(patientAuth.toKeyRecord());
        } catch (Exception e) {
            logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_FINISH_REGISTRATION", null, "Registration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Registration failed: " + e.getMessage() + "\", \"status\": 400}");
        }
    }

    /**
     * This function is the start endpoint of the WebAuthn login protocol.
     * It creates a challenge to assert the possession of the key by the user.
     *
     * @param session the HTTP session object, used to store the assertion request.
     * @return the assertion to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/login/start")
    @ResponseBody
    public ResponseEntity<?> startPatientLogin(@Valid @ModelAttribute LoginRequest request,
                                    HttpServletRequest httpRequest,
                                    HttpSession session) {

        if (!hCaptchaService.verify(request.captchaToken(), httpRequest.getRemoteAddr())) {
            logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_START_LOGIN", null, "Failed Captcha");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Captcha verification failed\", \"status\": 403}");
        }

        // Create the assertion to be sent to the client
        AssertionRequest assertionRequest = registrationService.getRelyingParty().startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build());
        try {
            // Try sending it to the client as JSON
            session.setAttribute("assertionRequest", assertionRequest);
            logger.logAction(AuditLogger.Level.INFO, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_START_LOGIN", null);
            String jsonResponse = assertionRequest.toCredentialsGetJson();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);
        } catch (JsonProcessingException e) {
            logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_START_LOGIN", null, "Bad Request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Failed to process JSON\", \"status\": 400}");
        }
    }

    /**
     * This function is the final endpoint of the WebAuthn login protocol.
     * It retrieves the assertion response sent by the client and checks its validity.
     * If it is valid, the user is authenticated for future requests.
     *
     * @param credential the patient credentials (in JSON)
     * @param session the HTTP session object
     * @param request the HTTP request object
     * @return a redirect link to the patient dashboard if the registration is successful
     */
    @PostMapping("/webauthn/login/finish")
    @ResponseBody
    public ResponseEntity<?> finishPatientLogin(@Valid @ModelAttribute FinishLoginRequest request,
                                     HttpSession session,
                                     HttpServletRequest httpRequest) {
        try {
            // Build the assertion result from the client response and the stored request in the HTTP session
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
            pkc = PublicKeyCredential.parseAssertionResponseJson(request.credential());
            AssertionRequest req = (AssertionRequest)session.getAttribute("assertionRequest");
            AssertionResult result = registrationService.getRelyingParty().finishAssertion(FinishAssertionOptions.builder()
                    .request(req)
                    .response(pkc)
                    .build());

            // If the assertion is successful, authenticate the user and redirect them to the patient dashboard
            if (result.isSuccess()) {
                PatientAuthenticator authenticator = credentialService.updateSignatureCount(result.getCredential().getCredentialId(),
                        result.getSignatureCount());

                if(authenticator == null) {
                    logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_FINISH_LOGIN", null, "Failed Authentication");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\": \"Authentication failed\", \"status\": 401}");
                }

                AuthenticationToken.authenticatePatient(result.getUsername(),
                                                        EncodingUtils.toHex(authenticator.getCredentialId()),
                                                        httpRequest);
                logger.logAction(AuditLogger.Level.INFO, httpRequest.getRemoteAddr(), "Patient:" + result.getUsername(), "PATIENT_FINISH_LOGIN", null);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(authenticator.toKeyRecord());
            } else {
                logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_FINISH_LOGIN", null, "Failed Authentication");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"Authentication failed\", \"status\": 401}");
            }

        } catch (IOException | AssertionFailedException e) {
            logger.logAction(AuditLogger.Level.WARN, httpRequest.getRemoteAddr(), "Non-authenticated User", "PATIENT_FINISH_LOGIN", null, "Failed Authentication");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Authentication failed: " + e.getMessage() + "\", \"status\": 401}");
        }
    }


    /**
     * This function is the start endpoint of the WebAuthn new key registration protocol.
     * It retrieves the patient information from the authentication token,
     * then returns the WebAuthn credentials registration options to the client.
     *
     * @param request the HTTP request object
     * @param session the HTTP session object, used to store some attributes for the registration.
     * @param auth the authentication token
     * @return the credentials options to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/newkey/start")
    @ResponseBody
    public ResponseEntity<?> startNewKeyRegistration(HttpServletRequest request, HttpSession session, Authentication auth) {

        Patient patient = credentialService.extractPatient(auth, "PATIENT_NEWKEY_START", request);
        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_NEWKEY_START", null);
        String jsonResponse = registrationService.createJsonCredentialRegistrationOptions(patient, session);
        return ResponseEntity.ok(jsonResponse);
    }

    /**
     * This function is the final endpoint of the WebAuthn new key registration protocol.
     * It retrieves the credentials sent by the client and stores permanently the new credential
     * in the database.
     *
     * @param credential the patient credentials (in JSON)
     * @param credname the credential name provided by the user
     * @param session the HTTP session object
     * @param request the HTTP request object
     * @return a redirect link to the key management page if the new key registration is successful
     */
    @PostMapping("/webauthn/newkey/finish")
    @ResponseBody
    public ResponseEntity<?> finishNewKeyRegistration(@RequestParam String credential,
                                                                   @RequestParam String credname,
                                                                   HttpSession session,
                                                                   HttpServletRequest request) {
        try {
            // Canonicalize credname
            String canonicalCredname = canonicalizeInput(credname);

            // Complete the registration
            WebAuthnRegistrationService.RegistrationRecord registration = registrationService.completeRegistration(session,
                    credential);

            // Store the patient and their authenticator in the database
            Patient savedPatient = credentialService.getPatientRepository().save(registration.patient());
            PatientAuthenticator patientAuth = new PatientAuthenticator(registration.result(),
                    registration.pkc().getResponse(),
                    savedPatient,
                    canonicalCredname);
            PatientAuthenticator savedAuth = credentialService.getAuthRepository().save(patientAuth);
            logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + savedPatient.getUsername(), "PATIENT_NEWKEY_FINISH", null);
            return ResponseEntity.ok(savedAuth.toKeyRecord());
        } catch (Exception e) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "PATIENT_NEWKEY_FINISH", null, "Registration failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Registration failed: " + e.getMessage() + "\", \"status\": 400}");
        }
    }

    /**
     * This function is the endpoint for deleting a security key of a patient.
     * It checks if the patient is the owner of the key and if it is not the key they are currently connected with.
     *
     * @param credentialId the key to delete
     * @param auth the authentication token
     * @param request the HTTP request object
     */
    @DeleteMapping("/webauthn/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAuthenticationKey(@RequestParam String credentialId, Authentication auth, HttpServletRequest request) {

        String currentCredentialId = String.valueOf(auth.getCredentials());
        if(credentialId.equals(currentCredentialId)){
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_DELETE_KEY", null, "Bad Request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove the current authentication key.");
        }

        if(!credentialService.areCredentialsFromSamePatient(credentialId, currentCredentialId)) {

            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_DELETE_KEY", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cannot remove this key.");
        }

        credentialService.deleteCredentials(credentialId);

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + auth.getPrincipal(), "PATIENT_DELETE_KEY", null);
    }
}
