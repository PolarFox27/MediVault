package ssd.medivault.auth;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "authn")
public class WebAuthnConfiguration {
    private String hostName;
    private String display;
    private Set<String> origin;
}
