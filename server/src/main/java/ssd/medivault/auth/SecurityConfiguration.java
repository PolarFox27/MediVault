package ssd.medivault.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Security filter chain for DOCTOR endpoints (X.509 certificate authentication).
     * This has higher priority (@Order(1)) and handles /doctor/** paths.
     * 
     * Doctors authenticate via mTLS - their certificate is verified during TLS handshake,
     * and their identity is extracted from the certificate Subject (CN, O fields).
     */
    @Bean
    @Order(1)
    SecurityFilterChain doctorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/doctor/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .x509(x509 -> x509
                        .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                        .userDetailsService(doctorUserDetailsService())
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendError(403, "Doctor certificate required");
                        })
                );
        
        return http.build();
    }

    /**
     * UserDetailsService for X.509 authenticated doctors.
     * The username is extracted from the certificate CN field.
     */
    @Bean
    UserDetailsService doctorUserDetailsService() {
        return username -> new User(
                username,
                "",  // No password - authentication is via certificate
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))
        );
    }

    /**
     * Security filter chain for PATIENT endpoints (WebAuthn authentication).
     * This has lower priority (@Order(2)) and handles all other paths.
     */
    @Bean
    @Order(2)
    SecurityFilterChain patientFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/welcome",
                                "/auth/**",
                                "/webauthn/register/**",
                                "/webauthn/login/**",
                                "/css/**",
                                "/javascript/**",
                                "/icons/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(getAuthenticationEntryPoint())
                        .accessDeniedHandler(getAccessDeniedHandler())
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                );

        return http.build();
    }

    @Bean
    AuthenticationEntryPoint getAuthenticationEntryPoint() {
        return (request, response, authException) ->
                response.sendRedirect("/");
    }

    @Bean
    AccessDeniedHandler getAccessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                response.sendRedirect("/");
    }
}
