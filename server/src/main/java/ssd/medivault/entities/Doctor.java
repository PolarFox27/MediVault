package ssd.medivault.entities;

import com.yubico.webauthn.data.exception.HexException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ssd.medivault.utils.EncodingUtils;

import java.time.LocalDateTime;

/**
 * Entity representing a Doctor in the MediVault system.
 * Doctors authenticate via X.509 client certificates (mTLS).
 * Their identity is extracted from the certificate:
 * - CN (Common Name) = Doctor's full name
 * - O (Organization) = Medical organization
 * Security notes:
 * - No password is stored (authentication via PKI only)
 * - Certificate serial number is stored for audit/tracking
 * - Non-repudiation is provided by the certificate signature
 */
@Entity
@Getter
@Setter
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String organization;

    @Column(nullable = false, unique = true)
    private String certificateSerialNumber;

    @Column(nullable = false)
    private String certificateIssuer;

    @Column(nullable = false)
    private LocalDateTime firstLogin;

    @Column(nullable = false)
    private LocalDateTime lastLogin;

    @Lob
    @Column(nullable = false)
    private byte[] publicKey;

    @Column(nullable = false)
    private byte[] encryptedPrivateKey;

    @Column(nullable = false)
    private byte[] encryptedPrivateKeyIv;

    @Column(nullable = false)
    private byte[] encryptedPrivateKeySalt;

    public Doctor() {}

    public Doctor(String fullName, String organization, String certificateSerialNumber, String certificateIssuer) {
        this.fullName = fullName;
        this.organization = organization;
        this.certificateSerialNumber = certificateSerialNumber;
        this.certificateIssuer = certificateIssuer;
        this.firstLogin = LocalDateTime.now();
        this.lastLogin = LocalDateTime.now();

        this.publicKey = new byte[]{};
        this.encryptedPrivateKey = new byte[]{};
        this.encryptedPrivateKeyIv = new byte[]{};
        this.encryptedPrivateKeySalt = new byte[]{};
    }

    public void updateLastLogin() { this.lastLogin = LocalDateTime.now(); }

    @Override
    public String toString() {
        return "Doctor{id=" + id + ", fullName='" + fullName + "', organization='" + organization + "'}";
    }

    public record DoctorKeyData(String publicKey, String privateKey, String privateKeySalt, String privateKeyIv) {}

    public DoctorKeyData getKeyData(){
        return new DoctorKeyData(EncodingUtils.toHex(this.publicKey),
                EncodingUtils.toHex(this.encryptedPrivateKey),
                EncodingUtils.toHex(this.encryptedPrivateKeySalt),
                EncodingUtils.toHex(this.encryptedPrivateKeyIv));
    }

    public void fromKeyData(DoctorKeyData data) throws HexException {
        this.publicKey = EncodingUtils.fromHex(data.publicKey);
        this.encryptedPrivateKey = EncodingUtils.fromHex(data.privateKey);
        this.encryptedPrivateKeySalt = EncodingUtils.fromHex(data.privateKeySalt);
        this.encryptedPrivateKeyIv = EncodingUtils.fromHex(data.privateKeyIv);
    }
}
