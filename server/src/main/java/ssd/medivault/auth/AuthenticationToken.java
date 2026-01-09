package ssd.medivault.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.Patient;

import java.util.List;
import java.util.Optional;

public class AuthenticationToken
        extends AbstractAuthenticationToken {

    private final String username;
    private final String credentialId;

    /**
     * Constructor for the Authentication token.
     * It constructs a token for the given username, credential and role.
     *
     * @param username the username.
     * @param role The authorization level given to the user.
     */
    public AuthenticationToken(String username, String credentialId, Role role) {
        super(List.of(new SimpleGrantedAuthority(role.toString())));
        this.username = username;
        this.credentialId = credentialId;
        setAuthenticated(true);
    }

    /**
     * Getter for the credential ID.
     *
     * @return the credential id associated with this token.
     */
    @Override
    public Object getCredentials() {
        return credentialId;
    }

    /**
     * Getter for the username.
     * @return the username associated with this token.
     */
    @Override
    public Object getPrincipal() {
        return username;
    }

    /**
     * This function creates an authentication token for the given patient username and
     * authenticates the provided HTTP request with the created token.
     *
     * @param username the patient username
     * @param request the HTTP request
     */
    public static void authenticatePatient(String username, String credentialId, HttpServletRequest request) {

        // Create the authentication token
        AuthenticationToken auth = new AuthenticationToken(username, credentialId, Role.PATIENT);

        // Create a security context with the token
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // Authenticate the HTTP session with the security context
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
    }

    /**
     * Helper function that extracts the patient identity from an authentication token.
     * If the token is invalid or the patient doesn't exist. HTTP 401 Unauthorized is thrown
     *
     * @param token the authentication token
     * @param repository the patient repository
     * @return the patient object
     */
    public static Patient extractPatient(Authentication token, PatientRepository repository){
        Optional<Patient> patient = repository.findByUsername(String.valueOf(token.getPrincipal()));

        if(patient.isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        return patient.get();
    }


    public enum Role{
        PATIENT,
        DOCTOR
    }
}

