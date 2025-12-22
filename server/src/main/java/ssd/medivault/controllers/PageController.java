package ssd.medivault.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

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
}
