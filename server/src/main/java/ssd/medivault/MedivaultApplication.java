package ssd.medivault;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ssd.medivault.auth.WebauthnRegistrationService;
import ssd.medivault.auth.WebAuthnConfiguration;

@SpringBootApplication
public class MedivaultApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedivaultApplication.class, args);
	}


    @Bean
    public RelyingParty relyingParty(WebauthnRegistrationService registrationRepository,
                                     WebAuthnConfiguration properties) {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(properties.getHostName())
                .name(properties.getDisplay())
                .build();

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(registrationRepository)
                .origins(properties.getOrigin())
                .build();
    }

}
