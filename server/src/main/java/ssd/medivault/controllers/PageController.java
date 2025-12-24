package ssd.medivault.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.entities.PatientAuthenticator;

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
        model.addAttribute("username", auth.getPrincipal());
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
        model.addAttribute("username", username);
        model.addAttribute("currentCred", auth.getCredentials());

        List<PatientAuthenticator.KeyRecord> keys = registrationService.getAuthenticatorsForUsername(username)
                .stream()
                .map(PatientAuthenticator::toRecord)
                .toList();

        model.addAttribute("keys", keys);
        return "key-management";
    }
}
