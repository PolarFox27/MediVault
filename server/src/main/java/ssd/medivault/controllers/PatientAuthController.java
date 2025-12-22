package ssd.medivault.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import ssd.medivault.auth.AuthenticationToken;
import ssd.medivault.auth.WebauthnRegistrationService;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;

import java.io.IOException;
import java.util.Random;

@Controller
public class PatientAuthController {

    private final RelyingParty relyingParty;
    private final WebauthnRegistrationService service;

    PatientAuthController(WebauthnRegistrationService service, RelyingParty relyingParty) {
        this.relyingParty = relyingParty;
        this.service = service;
    }

    /**
     * This function is the start endpoint of the Webauthn registration protocol.
     * It creates a new Patient object with a random id, then returns the webauthn credentials options to the client.
     *
     * @param session the HTTP session object, used to store some attributes for the registration.
     * @return the credentials options to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/register/user")
    @ResponseBody
    public String startPatientRegistration(HttpSession session) {

        // Generate a random 32-bytes user handle for the patient
        byte[] bytes = new byte[32];
        new Random().nextBytes(bytes);
        ByteArray id = new ByteArray(bytes);

        // Create a user identity object and a Patient object
        UserIdentity userIdentity = UserIdentity.builder()
                .name(id.getHex().substring(0, 16))
                .displayName("Patient <" + id.getHex().substring(0, 16) + ">")
                .id(id)
                .build();
        Patient patient = new Patient(userIdentity);

        // Create Webauthn credentials options for the created patient object
        // It requires the key to be resident, which allows username-less login
        StartRegistrationOptions registrationOptions = StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .residentKey(ResidentKeyRequirement.REQUIRED)
                                .userVerification(UserVerificationRequirement.PREFERRED)
                                .build()
                )
                .build();
        PublicKeyCredentialCreationOptions registration = relyingParty.startRegistration(registrationOptions);

        // Stores the registration information in the HTTP session (to be used when registration finishes)
        session.setAttribute("registrationUser", patient);
        session.setAttribute("registrationRequest", registration);

        try {
            // Sends the credentials options to the client as JSON
            return registration.toCredentialsCreateJson();
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing JSON.", e);
        }
    }

    /**
     * This function is the final endpoint of the Webauthn registration protocol.
     * It retrieves the credentials sent by the client and stores permanently the patient and their credentials
     * in the database.
     * If the registration is successful, the patient is automatically logged in and authenticated for future requests.
     *
     * @param credential the patient credentials (in JSON)
     * @param credname the credential name provided by the user
     * @param session the HTTP session object
     * @param request the HTTP request object
     * @return a redirect link to the patient dashboard if the registration is successful
     */
    @PostMapping("/webauthn/register/finish")
    @ResponseBody
    public ModelAndView finishPatientRegistration(@RequestParam String credential,
                                                  @RequestParam String credname,
                                                  HttpSession session,
                                                  HttpServletRequest request) {
        try {
            // Retrieve the patient object and registration info from the HTTP session
            Patient patient = (Patient) session.getAttribute("registrationUser");
            PublicKeyCredentialCreationOptions requestOptions =
                    (PublicKeyCredentialCreationOptions) session.getAttribute("registrationRequest");

            // If the HTTP session is empty, throw an error
            if(requestOptions == null || patient == null){
                throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT,
                        "Cached request expired. Try to register again!");
            }

            // Construct the Webauthn credentials from the user response.
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                    PublicKeyCredential.parseRegistrationResponseJson(credential);
            FinishRegistrationOptions options = FinishRegistrationOptions.builder()
                    .request(requestOptions)
                    .response(pkc)
                    .build();
            RegistrationResult result = relyingParty.finishRegistration(options);

            // Store the patient and their authenticator in the database
            Patient savedPatient = service.getPatientRepository().save(patient);
            PatientAuthenticator patientAuth = new PatientAuthenticator(result, pkc.getResponse(),
                                                                             savedPatient, credname);
            service.getAuthRepository().save(patientAuth);

            // Authenticate the patient and redirect to the dashboard
            AuthenticationToken.authenticatePatient(savedPatient.getUsername(), request);
            return new ModelAndView("redirect:/patient-dashboard");

        } catch (RegistrationFailedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Registration failed.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to save credentials, please try again!", e);
        }
    }

    /**
     * This function is the start endpoint of the Webauthn login protocol.
     * It creates a challenge to assert the possession of the key by the user.
     *
     * @param session the HTTP session object, used to store the assertion request.
     * @return the assertion to be sent to the client, in JSON format.
     */
    @PostMapping("/webauthn/login/start")
    @ResponseBody
    public String startPatientLogin(HttpSession session) {

        // Create the assertion to be sent to the client
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build());
        try {
            // Try sending it to the client as JSON
            session.setAttribute("assertionRequest", request);
            return request.toCredentialsGetJson();
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * This function is the final endpoint of the Webauthn login protocol.
     * It retrieves the assertion response sent by the client and checks its validity.
     * If it is valid, the user is authenticated for future requests.
     *
     * @param credential the patient credentials (in JSON)
     * @param session the HTTP session object
     * @param request the HTTP request object
     * @return a redirect link to the patient dashboard if the registration is successful
     */
    @PostMapping("/webauthn/login/finish")
    public String finishPatientLogin(@RequestParam String credential,
                                     HttpSession session,
                                     HttpServletRequest request) {
        try {
            // Build the assertion result from the client response and the stored request in the HTTP session
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
            pkc = PublicKeyCredential.parseAssertionResponseJson(credential);
            AssertionRequest req = (AssertionRequest)session.getAttribute("assertionRequest");
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(req)
                    .response(pkc)
                    .build());

            // If the assertion is successful, authenticate the user redirect them to the patient dashboard
            if (result.isSuccess()) {
                AuthenticationToken.authenticatePatient(result.getUsername(), request);
                return "redirect:/patient-dashboard";
            } else {
                // If the assertion fails, redirect the client to the home page
                return "redirect:/";
            }

        } catch (IOException | AssertionFailedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed", e);
        }
    }
}
