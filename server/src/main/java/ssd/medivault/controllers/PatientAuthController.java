package ssd.medivault.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import ssd.medivault.utils.EncodingUtils;

import java.io.IOException;
import java.util.Random;

@Controller
@RequiredArgsConstructor
public class PatientAuthController {

    private final WebAuthnCredentialService credentialService;
    private final WebAuthnRegistrationService registrationService;
    private final HCaptchaService hCaptchaService;

    /**
     * This function is the start endpoint of the WebAuthn registration protocol.
     * It creates a new Patient object with a random id, then returns the webauthn credentials options to the client.
     *
     * @param session the HTTP session object, used to store some attributes for the registration.
     * @return the credentials options to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/register/start")
    @ResponseBody
    public String startPatientRegistration(@RequestParam String captchaToken,
                                           HttpServletRequest request,
                                           HttpSession session) {

        if (!hCaptchaService.verify(captchaToken, request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Captcha verification failed");
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
        return registrationService.createJsonCredentialRegistrationOptions(patient, session);
    }

    /**
     * This function is the final endpoint of the WebAuthn registration protocol.
     * It retrieves the credentials sent by the client and stores permanently the patient and their credentials
     * in the database.
     * If the registration is successful, the patient is automatically logged in and authenticated for future requests.
     *
     * @param credential the patient credentials (in JSON)
     * @param credname the credential name provided by the user
     * @param session the HTTP session object
     * @param request the HTTP request object
     * @return the public key of the registered credentials.
     */
    @PostMapping("/webauthn/register/finish")
    @ResponseBody
    public PatientAuthenticator.KeyRecord finishPatientRegistration(@RequestParam String credential,
                                                                    @RequestParam String credname,
                                                                    HttpSession session,
                                                                    HttpServletRequest request) {
        // Complete the registration
        WebAuthnRegistrationService.RegistrationRecord registration = registrationService.completeRegistration(session,
                credential);

        // Store the patient and their authenticator in the database
        Patient savedPatient = credentialService.getPatientRepository().save(registration.patient());
        PatientAuthenticator patientAuth = new PatientAuthenticator(registration.result(),
                registration.pkc().getResponse(),
                savedPatient,
                credname);
        credentialService.getAuthRepository().save(patientAuth);

        // Authenticate the patient
        AuthenticationToken.authenticatePatient(savedPatient.getUsername(),
                EncodingUtils.toHex(patientAuth.getCredentialId()),
                request);

        return patientAuth.toKeyRecord();
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
    public String startPatientLogin(@RequestParam String captchaToken,
                                    HttpServletRequest request,
                                    HttpSession session) {

        if (!hCaptchaService.verify(captchaToken, request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Captcha verification failed");
        }

        // Create the assertion to be sent to the client
        AssertionRequest assertionRequest = registrationService.getRelyingParty().startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build());
        try {
            // Try sending it to the client as JSON
            session.setAttribute("assertionRequest", assertionRequest);
            return assertionRequest.toCredentialsGetJson();
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
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
    public PatientAuthenticator.KeyRecord finishPatientLogin(@RequestParam String credential,
                                     HttpSession session,
                                     HttpServletRequest request) {
        try {
            // Build the assertion result from the client response and the stored request in the HTTP session
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
            pkc = PublicKeyCredential.parseAssertionResponseJson(credential);
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
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed");
                }

                AuthenticationToken.authenticatePatient(result.getUsername(),
                                                        EncodingUtils.toHex(authenticator.getCredentialId()),
                                                        request);
                return authenticator.toKeyRecord();
            } else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed");
            }

        } catch (IOException | AssertionFailedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed", e);
        }
    }


    /**
     * This function is the start endpoint of the WebAuthn new key registration protocol.
     * It retrieves the patient information from the authentication token,
     * then returns the WebAuthn credentials registration options to the client.
     *
     * @param session the HTTP session object, used to store some attributes for the registration.
     * @return the credentials options to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/newkey/start")
    @ResponseBody
    public String startNewKeyRegistration(HttpSession session, Authentication auth) {

        Patient patient = AuthenticationToken.extractPatient(auth, registrationService.getPatientRepository());

        return registrationService.createJsonCredentialRegistrationOptions(patient, session);
    }

    /**
     * This function is the final endpoint of the WebAuthn new key registration protocol.
     * It retrieves the credentials sent by the client and stores permanently the new credential
     * in the database.
     *
     * @param credential the patient credentials (in JSON)
     * @param credname the credential name provided by the user
     * @param session the HTTP session object
     * @return a redirect link to the key management page if the new key registration is successful
     */
    @PostMapping("/webauthn/newkey/finish")
    @ResponseBody
    public PatientAuthenticator.KeyRecord finishNewKeyRegistration(@RequestParam String credential,
                                                 @RequestParam String credname,
                                                 HttpSession session) {
        // Complete the registration
        WebAuthnRegistrationService.RegistrationRecord registration = registrationService.completeRegistration(session,
                credential);

        // Store the patient and their authenticator in the database
        Patient savedPatient = credentialService.getPatientRepository().save(registration.patient());
        PatientAuthenticator patientAuth = new PatientAuthenticator(registration.result(),
                registration.pkc().getResponse(),
                savedPatient,
                credname);
        PatientAuthenticator savedAuth = credentialService.getAuthRepository().save(patientAuth);
        return savedAuth.toKeyRecord();
    }

    /**
     * This function is the endpoint for deleting a security key of a patient.
     * It checks if the patient is the owner of the key and if it is not the key they are currently connected with.
     *
     * @param credentialId the key to delete
     * @param auth the authentication token
     */
    @DeleteMapping("/webauthn/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAuthenticationKey(@RequestParam String credentialId, Authentication auth) {

        String currentCredentialId = String.valueOf(auth.getCredentials());
        if(credentialId.equals(currentCredentialId)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove the current authentication key.");
        }

        if(!credentialService.areCredentialsFromSamePatient(credentialId, currentCredentialId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cannot remove this key.");
        }

        credentialService.deleteCredentials(credentialId);
    }
}
