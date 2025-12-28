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
import ssd.medivault.utils.EncodingUtils;

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
    @Column(nullable = false)
    private byte[] aaguid;

    @Lob
    @Column(nullable = false)
    private byte[] encryptedUmk;

    @Lob
    @Column(nullable = false)
    private byte[] iv;


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
        this.encryptedUmk = new byte[]{};
        this.iv = new byte[]{};
    }

    public record KeyRecord(String name, String credentialId, String publicKey, long count) {}

    /**
     * Converts this authentication into a record storing the key details.
     * This record can be sent over to the client via HTTP as the byte fields are encoded into Hexadecimal.
     *
     * @return the converted record.
     */
    public KeyRecord toKeyRecord(){
        return new KeyRecord(this.name, EncodingUtils.toHex(this.credentialId),
                EncodingUtils.toHex(this.publicKey), this.count);
    }

    public record EncryptedUmk(String encryptedUmk, String iv, String credentialId) {}

    /**
     * Creates a record storing the encrypted UMK, stored in hexadecimal format.
     * It is sent to the client who can then decrypt it to retrieve the UMK.
     *
     * @return the object representing the encrypted UMK.
     */
    public EncryptedUmk getUmk(){
        return new EncryptedUmk(EncodingUtils.toHex(this.encryptedUmk),
                EncodingUtils.toHex(this.iv),
                EncodingUtils.toHex(this.credentialId));
    }
}
