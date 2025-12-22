package ssd.medivault.controllers;

import com.yubico.webauthn.data.ByteArray;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ssd.medivault.data.PatientAuthenticatorRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class PageController {

    public final PatientRepository patientRepository;
    public final PatientAuthenticatorRepository authenticatorRepository;


    public PageController(PatientRepository patientRepository, PatientAuthenticatorRepository authenticatorRepository) {
        this.patientRepository = patientRepository;
        this.authenticatorRepository = authenticatorRepository;
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
        String username = Optional.ofNullable(auth.getPrincipal()).orElse("???").toString();
        model.addAttribute("username", username);

        Patient patient = patientRepository.findByUsername(auth.getPrincipal().toString());
        List<PatientAuthenticator.KeyRecord> keys = authenticatorRepository.findAllByPatient(patient)
                .stream()
                .map(PatientAuthenticator::toRecord)
                .toList();

        for(PatientAuthenticator.KeyRecord k : keys){
            System.out.println(k.name());
            System.out.println(k.credentialId());
        }

        model.addAttribute("keys", keys);
        return "key-management";
    }
}
