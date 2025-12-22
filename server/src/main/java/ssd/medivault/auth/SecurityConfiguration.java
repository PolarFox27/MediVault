package ssd.medivault.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * This function configures Spring Security for the application.
     * It specifies the endpoints requiring authenticated access.
     * It defines the logout URL and behavior.
     *
     * @param http the HTTP security builder
     * @return the constructed HTTP security configuration
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {

        http
                .csrf(AbstractHttpConfigurer::disable)  // WebAuthn handles challenge security, no need for CSRF
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/patient-authentication",
                                "/webauthn/**",
                                "/css/**",
                                "/javascript/**",
                                "/icons/**"
                        ).permitAll()                   // Authorize unauthenticated access to some endpoints and resources
                        .anyRequest().authenticated()   // Require authenticated access for any other request
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/logout")         // Specifies the logout URL
                        .logoutSuccessUrl("/")        // Specifies where to be redirected after logging out.
                );

        return http.build();
    }
}

