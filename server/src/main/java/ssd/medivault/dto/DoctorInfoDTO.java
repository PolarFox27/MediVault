package ssd.medivault.dto;

/**
 * Data Transfer Object for exposing safe doctor information to patients.
 * Only contains non-sensitive, public fields extracted from doctor certificates.
 * 
 * Security: This DTO intentionally excludes internal fields like certificateSerial
 * to minimize data exposure following the principle of least privilege.
 */
public record DoctorInfoDTO(
    Long id,
    String name,
    String organization
) {}
