package ssd.medivault.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.RegistrationFailedException;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.data.PatientAuthenticatorRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.Patient;

import java.io.IOException;

@Service
@Getter
public class WebAuthnRegistrationService {


    private final RelyingParty relyingParty;
    private final PatientRepository patientRepository;
    private final PatientAuthenticatorRepository authRepository;

    /**
     * Constructor for the WebAuthnRegistrationService class
     *
     * @param patientRepository the JPA repository storing patient information
     * @param authRepository the JPA repository storing patient authentication keys
     * @param relyingParty the Yubico "relying" party for credential registration
     */
    public WebAuthnRegistrationService(RelyingParty relyingParty,
                                       PatientRepository patientRepository,
                                       PatientAuthenticatorRepository authRepository) {
        this.relyingParty = relyingParty;
        this.patientRepository = patientRepository;
        this.authRepository = authRepository;
    }


    /**
     * This function builds credentials registration options and returns them in JSON format.
     * It is the initial step of the WebAuthn registration procedure.
     * It stores the registration request as well as the patient object in the HTTP session for future reference.
     *
     * @param patient the patient for which the registration options are built
     * @param session the HTTP session, storing information for the final step of the registration procedure
     * @return the JSON representation of the credentials registration options
     */
    public String createJsonCredentialRegistrationOptions(Patient patient, HttpSession session){
        UserIdentity userIdentity = patient.toUserIdentity();

        // Create WebAuthn credentials options for the created patient object
        // It requires the key to be resident, which allows username-less login
        StartRegistrationOptions registrationOptions = StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .authenticatorAttachment(AuthenticatorAttachment.CROSS_PLATFORM)
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

    public RegistrationRecord completeRegistration(HttpSession session, String credential, String credentialName){
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

            // Construct the WebAuthn credentials from the user response.
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                    PublicKeyCredential.parseRegistrationResponseJson(credential);
            FinishRegistrationOptions options = FinishRegistrationOptions.builder()
                    .request(requestOptions)
                    .response(pkc)
                    .build();
            return new RegistrationRecord(patient, pkc, relyingParty.finishRegistration(options));

        } catch (RegistrationFailedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Registration failed.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to save credentials, please try again!", e);
        }
    }

    public record RegistrationRecord(Patient patient,
                                     PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc,
                                     RegistrationResult result) {}
}
