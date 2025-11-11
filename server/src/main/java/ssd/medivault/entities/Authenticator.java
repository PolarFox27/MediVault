package ssd.medivault.entities;

import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.AttestedCredentialData;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.util.Optional;

@Entity
@Getter
@NoArgsConstructor
public class Authenticator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column
    private String name;

    @Lob
    @Column(nullable = false)
    private ByteArray credentialId;

    @Lob
    @Column(nullable = false)
    private ByteArray publicKey;

    @ManyToOne
    private AppUser user;

    /* The authenticator potentially provides a range of additional information. This
     * application stores some of it to enable functionality that could be useful for
     * a production-quality web authentication project.
     */

    @Column(nullable = false)
    private Long count;

    @Lob
    @Column(nullable = true)
    private ByteArray aaguid;

    public Authenticator(RegistrationResult result,
                         AuthenticatorAttestationResponse response,
                         AppUser user,
                         String name) {
        Optional<AttestedCredentialData> attestationData = response.getAttestation()
                .getAuthenticatorData()
                .getAttestedCredentialData();
        this.credentialId = result.getKeyId().getId();
        this.publicKey = result.getPublicKeyCose();
        this.aaguid = attestationData.get().getAaguid();
        this.count = result.getSignatureCount();
        this.name = name;
        this.user = user;
    }
}
