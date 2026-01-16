package ssd.medivault.auth;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ssd.medivault.data.DoctorRepository;
import ssd.medivault.entities.Doctor;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for authenticating doctors via X.509 client certificates.
 * Extracts identity from certificate Subject:
 * - CN (Common Name) = Doctor's full name
 * - O (Organization) = Medical organization
 * Security: No passwords involved - authentication is based on
 * cryptographic proof of private key possession during TLS handshake.
 */
@Service
@Getter
public class DoctorX509AuthService {

    private static final Logger logger = LoggerFactory.getLogger(DoctorX509AuthService.class);
    
    private final DoctorRepository doctorRepository;

    public DoctorX509AuthService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    /**
     * Authenticate a doctor based on their X.509 certificate.
     * Creates or updates the doctor record in the database.
     * 
     * @param certificate The X.509 certificate from the TLS handshake
     * @return The authenticated Doctor entity
     * @throws IllegalArgumentException if certificate is invalid or missing required fields
     */
    public Doctor authenticateDoctor(X509Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("No certificate provided");
        }

        // Extract identity from certificate subject
        Map<String, String> subjectFields = parseX500Principal(certificate.getSubjectX500Principal());
        
        String fullName = subjectFields.get("CN");
        String organization = subjectFields.get("O");
        String serialNumber = certificate.getSerialNumber().toString(16).toUpperCase();
        String issuer = certificate.getIssuerX500Principal().getName();

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Certificate missing CN (Common Name)");
        }
        if (organization == null || organization.isBlank()) {
            throw new IllegalArgumentException("Certificate missing O (Organization)");
        }

        logger.info("Doctor certificate authentication: CN={}, O={}, Serial={}", 
                    fullName, organization, serialNumber);

        // Check if doctor already exists (by certificate serial number)
        Optional<Doctor> existingDoctor = doctorRepository.findByCertificateSerialNumber(serialNumber);
        
        if (existingDoctor.isPresent()) {
            // Update last login time
            Doctor doctor = existingDoctor.get();
            doctor.updateLastLogin();
            return doctorRepository.save(doctor);
        }

        // Check if this is a certificate renewal (same name/org, different serial)
        Optional<Doctor> existingByNameOrg = doctorRepository.findByFullNameAndOrganization(fullName, organization);
        
        if (existingByNameOrg.isPresent()) {
            // Certificate renewal - update serial number and last login
            Doctor doctor = existingByNameOrg.get();
            logger.info("Certificate renewal detected for doctor: {}", fullName);
            doctor.setCertificateSerialNumber(serialNumber);
            doctor.setCertificateIssuer(issuer);
            doctor.updateLastLogin();
            return doctorRepository.save(doctor);
        }

        // New doctor - create record
        logger.info("New doctor registered via certificate: {} from {}", fullName, organization);
        Doctor newDoctor = new Doctor(fullName, organization, serialNumber, issuer);
        return doctorRepository.save(newDoctor);
    }

    /**
     * Parse X.500 Distinguished Name into a map of field names to values.
     * Example: "CN=Dr. Smith, O=Hospital, C=BE" -> {CN: "Dr. Smith", O: "Hospital", C: "BE"}
     */
    private Map<String, String> parseX500Principal(X500Principal principal) {
        Map<String, String> result = new HashMap<>();
        String name = principal.getName();
        
        // Split by comma, but handle escaped commas
        String[] parts = name.split("(?<!\\\\),");
        
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        
        return result;
    }
}
