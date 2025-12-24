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
import lombok.Setter;

import java.util.Optional;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PatientAuthenticator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column
    private String name;

    @Lob
    @Column(nullable = false)
    private byte[] credentialId;

    @Lob
    @Column(nullable = false)
    private byte[] publicKey;

    @ManyToOne
    private Patient patient;

    @Column(nullable = false)
    private Long count;

    @Lob
    @Column(nullable = true)
    private byte[] aaguid;

    public PatientAuthenticator(RegistrationResult result,
                                AuthenticatorAttestationResponse response,
                                Patient patient,
                                String name) {
        Optional<AttestedCredentialData> attestationData = response.getAttestation()
                .getAuthenticatorData()
                .getAttestedCredentialData();
        this.credentialId = result.getKeyId().getId().getBytes();
        this.publicKey = result.getPublicKeyCose().getBytes();
        this.aaguid = attestationData.map(AttestedCredentialData::getAaguid)
                .map(ByteArray::getBytes)
                .orElse(new byte[]{});
        this.count = result.getSignatureCount();
        this.name = name;
        this.patient = patient;
    }

    /**
     * Getter for the credential id in hexadecimal format.
     *
     * @return the credentialID as a hex string.
     */
    public String getCredentialIdAsString(){
        return new ByteArray(this.credentialId).getHex();
    }

    public record KeyRecord(String name, String credentialId, long count) {}

    public KeyRecord toRecord(){
        return new KeyRecord(this.name, this.getCredentialIdAsString(), this.count);
    }
}
