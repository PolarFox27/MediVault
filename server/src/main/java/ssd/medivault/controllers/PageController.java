package ssd.medivault.controllers;

import jakarta.servlet.http.HttpServletRequest;
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
     * Mapping returning the base page layout for all pages requested.
     *
     * @return the base HTML layout.
     */
    @GetMapping("/")
    public String app() {
        return "layout";
    }

    /**
     * GET Endpoint for the welcome page.
     *
     * @param request the HTTP Request object
     * @return the welcome page model.
     */
    @GetMapping("/welcome")
    public String welcome(HttpServletRequest request) {
        if ("SPA".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return "pages/welcome :: frag-welcome";
        }
        return "layout";
    }

    /**
     * GET Endpoint for the patient dashboard page.
     * This endpoint is protected and only accessible to authenticated patients.
     *
     * @param request the HTTP Request object
     * @param model the UI model
     * @param auth the Authentication object to retrieve the patient username
     * @return the patient dashboard model
     */
    @GetMapping("/patient/dashboard")
    public String patientDashboardPage(HttpServletRequest request, Model model, Authentication auth) {
        setBasicModelAttributes(model, registrationService, auth);

        if ("SPA".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return "pages/patient/dashboard :: frag-patient-dashboard";
        }
        return "layout";
    }

    /**
     * GET Endpoint for the patient header fragment.
     * This endpoint is protected and only accessible to authenticated patients.
     *
     * @param request the HTTP Request object
     * @return the patient header fragment.
     */
    @GetMapping("/patient/header")
    public String patientHeader(HttpServletRequest request) {
        if ("SPA".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return "fragments/topbar-patient :: frag-topbar-patient";
        }
        return "layout";
    }

    /**
     * GET Endpoint for the patient authentication page.
     *
     * @param request the HTTP Request object
     * @return the patient authentication page model.
     */
    @GetMapping("/auth/patient")
    public String patientAuthPage(HttpServletRequest request) {
        if ("SPA".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return "pages/auth/patient-authentication :: frag-patient-authentication";
        }
        return "layout";
    }

    /**
     * GET Endpoint for the patient account management page.
     * This endpoint is protected and only accessible to authenticated patients.
     *
     * @param request the HTTP Request object
     * @param model the UI model.
     * @param auth the Authentication object to retrieve the patient keys.
     * @return the patient account management model.
     */
    @GetMapping("/patient/account")
    public String keyManagementPage(HttpServletRequest request, Model model, Authentication auth) {
        String username = String.valueOf(auth.getPrincipal());
        setBasicModelAttributes(model, registrationService, auth);

        List<PatientAuthenticator.KeyRecord> keys = registrationService.getAuthenticatorsForUsername(username)
                .stream()
                .map(PatientAuthenticator::toKeyRecord)
                .toList();
        model.addAttribute("keys", keys);

        if ("SPA".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return "pages/patient/account :: frag-patient-account";
        }
        return "layout";
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
