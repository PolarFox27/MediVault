package ssd.medivault.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

public class AuthenticationToken
        extends AbstractAuthenticationToken {

    private final String username;

    /**
     * Constructor for the Authentication token.
     * It constructs a token for the given username and role.
     *
     * @param username The username.
     * @param role The authorization level given to the user.
     */
    public AuthenticationToken(String username, Role role) {
        super(List.of(new SimpleGrantedAuthority(role.toString())));
        this.username = username;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
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
    public static void authenticatePatient(String username, HttpServletRequest request) {

        // Create the authentication token
        AuthenticationToken auth = new AuthenticationToken(username, Role.PATIENT);

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


    public static enum Role{
        PATIENT,
        DOCTOR;
    }
}

