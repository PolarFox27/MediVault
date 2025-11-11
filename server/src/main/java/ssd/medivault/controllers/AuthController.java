package ssd.medivault.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import ssd.medivault.auth.RegistrationService;
import ssd.medivault.entities.AppUser;
import ssd.medivault.entities.Authenticator;

import java.io.IOException;
import java.util.Random;

@Controller
public class AuthController {

    private final RelyingParty relyingParty;
    private final RegistrationService service;

    AuthController(RegistrationService service, RelyingParty relyingParty) {
        this.relyingParty = relyingParty;
        this.service = service;
    }

    @GetMapping("/")
    public String welcome() {
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerUser(Model model) {
        return "register";
    }

    @PostMapping("/register")
    @ResponseBody
    public String newUserRegistration(@RequestParam String username,
                                      @RequestParam String display,
                                      HttpSession session) {
        AppUser existingUser = service.getUserRepo().findByUsername(username);
        if (existingUser == null) {

            byte[] bytes = new byte[32];
            new Random().nextBytes(bytes);
            ByteArray id = new ByteArray(bytes);

            UserIdentity userIdentity = UserIdentity.builder()
                    .name(username)
                    .displayName(display)
                    .id(id)
                    .build();
            AppUser saveUser = new AppUser(userIdentity);
            service.getUserRepo().save(saveUser);
            String response = newAuthRegistration(saveUser, session);
            return response;
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username " + username
                    + " already exists. Choose a new name.");
        }
    }

    @PostMapping("/registerauth")
    @ResponseBody
    public String newAuthRegistration(@RequestParam AppUser user, HttpSession session) {
        AppUser existingUser = service.getUserRepo().findByHandle(user.getHandle());
        if (existingUser != null) {
            UserIdentity userIdentity = user.toUserIdentity();
            StartRegistrationOptions registrationOptions = StartRegistrationOptions.builder()
                    .user(userIdentity)
                    .build();
            PublicKeyCredentialCreationOptions registration = relyingParty.startRegistration(registrationOptions);
            session.setAttribute(userIdentity.getName(), registration);
            System.out.println("credentials stored in session");
            System.out.println("Session ID: " + session.getId());
            System.out.println("Username: " + userIdentity.getName());
            try {
                return registration.toCredentialsCreateJson();
            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error processing JSON.", e);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User " + user.getUsername()
                    + " does not exist. Please register.");
        }
    }

    @PostMapping("/login")
    @ResponseBody
    public String startLogin(@RequestParam String username, HttpSession session) {
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(username)
                .build());
        try {
            session.setAttribute(username, request);
            return request.toCredentialsGetJson();
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/finishauth")
    @ResponseBody
    public ModelAndView finishRegisration(@RequestParam String credential,
                                          @RequestParam String username,
                                          @RequestParam String credname,
                                          HttpSession session) {
        try {
            AppUser user = service.getUserRepo().findByUsername(username);
            PublicKeyCredentialCreationOptions requestOptions =
                    (PublicKeyCredentialCreationOptions) session.getAttribute(user.getUsername());
            if (requestOptions != null) {
                PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                        PublicKeyCredential.parseRegistrationResponseJson(credential);
                FinishRegistrationOptions options = FinishRegistrationOptions.builder()
                        .request(requestOptions)
                        .response(pkc)
                        .build();
                RegistrationResult result = relyingParty.finishRegistration(options);
                Authenticator savedAuth = new Authenticator(result, pkc.getResponse(), user, credname);
                service.getAuthRepository().save(savedAuth);
                return new ModelAndView("redirect:/login");
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Cached request expired. Try to register again!");
            }
        } catch (RegistrationFailedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Registration failed.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to save credenital, please try again!", e);
        }
    }

    @PostMapping("/welcome")
    public String finishLogin(@RequestParam String credential,
                              @RequestParam String username,
                              Model model,
                              HttpSession session) {
        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
            pkc = PublicKeyCredential.parseAssertionResponseJson(credential);
            AssertionRequest request = (AssertionRequest)session.getAttribute(username);
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(pkc)
                    .build());
            if (result.isSuccess()) {
                model.addAttribute("username", username);
                return "welcome";
            } else {
                return "index";
            }
        } catch (IOException | AssertionFailedException e) {
            throw new RuntimeException("Authentication failed", e);
        }
    }
}
