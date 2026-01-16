package ssd.medivault.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
@Table(name = "doctors")
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

    public Doctor() {}

    public Doctor(String fullName, String organization, String certificateSerialNumber, String certificateIssuer) {
        this.fullName = fullName;
        this.organization = organization;
        this.certificateSerialNumber = certificateSerialNumber;
        this.certificateIssuer = certificateIssuer;
        this.firstLogin = LocalDateTime.now();
        this.lastLogin = LocalDateTime.now();
    }

    public void updateLastLogin() { this.lastLogin = LocalDateTime.now(); }

    @Override
    public String toString() {
        return "Doctor{id=" + id + ", fullName='" + fullName + "', organization='" + organization + "'}";
    }
}
