package ssd.medivault.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;
import ssd.medivault.utils.EncodingUtils;

import java.util.List;
import java.util.Optional;

@Controller
public class PageController {

    private final WebAuthnCredentialService registrationService;


    public PageController(WebAuthnCredentialService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * GET Endpoint for the home page.
     *
     * @return the home page model.
     */
    @GetMapping("/")
    public String welcome() {
        return "index";
    }

    /**
     * GET Endpoint for the patient dashboard page.
     * This endpoint is protected and only accessible to authenticated patients.
     *
     * @param model the UI model.
     * @param auth the Authentication object to retrieve the patient username.
     * @return the patient dashboard model.
     */
    @GetMapping("/patient-dashboard")
    public String patientDashboardPage(Model model, Authentication auth) {
        setBasicModelAttributes(model, registrationService, auth);
        return "patient-dashboard";
    }

    /**
     * GET Endpoint for the patient authentication page.
     *
     * @return the patient authentication page model.
     */
    @GetMapping("/patient-authentication")
    public String patientAuthPage() {
        return "patient-authentication";
    }

    /**
     * GET Endpoint for the patient key management page.
     * This endpoint is protected and only accessible to authenticated patients.
     *
     * @param model the UI model.
     * @param auth the Authentication object to retrieve the patient keys.
     * @return the patient dashboard model.
     */
    @GetMapping("/key-management")
    public String keyManagementPage(Model model, Authentication auth) {
        String username = String.valueOf(auth.getPrincipal());
        setBasicModelAttributes(model, registrationService, auth);

        List<PatientAuthenticator.KeyRecord> keys = registrationService.getAuthenticatorsForUsername(username)
                .stream()
                .map(PatientAuthenticator::toKeyRecord)
                .toList();

        model.addAttribute("keys", keys);
        return "key-management";
    }

    /**
     * Helper function that defines the basic patient attributes in the model to be returned to the client.
     * These attributes include the username, the active credentials, the name and DOB.
     * These are retrieved from the authentication token and the database.
     *
     * @param model the Model to configure
     * @param service the credential management service
     * @param authentication the authentication token
     */
    public static void setBasicModelAttributes(Model model, WebAuthnCredentialService service, Authentication authentication) {
        Optional<Patient> patient = service.getPatientRepository().findByUsername(String.valueOf(authentication.getPrincipal()));

        if(patient.isPresent()) {
            model.addAttribute("username", patient.get().getUsername());
            model.addAttribute("currentCred", authentication.getCredentials());
            model.addAttribute("encryptedName", EncodingUtils.toHex(patient.get().getEncryptedName()));
            model.addAttribute("encryptedDOB", EncodingUtils.toHex(patient.get().getEncryptedDOB()));
        }
    }
}
