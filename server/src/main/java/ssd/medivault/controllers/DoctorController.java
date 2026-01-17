package ssd.medivault.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ssd.medivault.auth.DoctorX509AuthService;
import ssd.medivault.data.EncryptedFileRepository;
import ssd.medivault.data.FileChangeRequestRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.*;
import ssd.medivault.logging.AuditLogger;
import ssd.medivault.utils.EncodingUtils;
import com.yubico.webauthn.data.exception.HexException;

import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final AuditLogger logger;
    private final DoctorX509AuthService doctorAuthService;
    private final PatientRepository patientRepository;
    private final EncryptedFileRepository fileRepository;
    private final FileChangeRequestRepository changeRequestRepository;


    /**
     * GET endpoint for the doctor personal information.
     * The doctor identity is extracted from the certificate included in the request.
     * If there is no certificate, an error code is returned.
     *
     * @param request the HTTP request object
     * @return a Map containing the doctor personal information
     */
    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDoctorInfo(HttpServletRequest request) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_DOCTOR_DETAILS", null, "No certificate found in request");
            return ResponseEntity.status(403).body(Map.of("error", "No certificate"));
        }

        try {
            Doctor doctor = doctorAuthService.authenticateDoctor(cert);
            Map<String, Object> response = new HashMap<>();
            response.put("id", doctor.getId());
            response.put("fullName", doctor.getFullName());
            response.put("organization", doctor.getOrganization());
            response.put("certificateSerial", doctor.getCertificateSerialNumber());
            response.put("firstLogin", doctor.getFirstLogin().toString());
            response.put("lastLogin", doctor.getLastLogin().toString());
            logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_DOCTOR_DETAILS", null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_DOCTOR_DETAILS", null, "Unauthorized");
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET endpoint for the doctor key pair. (plaintext public key and encrypted private key)
     * The doctor identity is extracted from the certificate included in the request.
     * If there is no certificate, an error code is returned.
     *
     * @param request the HTTP request object
     * @return the doctor key pair
     */
    @GetMapping("/api/key")
    @ResponseBody
    public Doctor.DoctorKeyData getDoctorKey(HttpServletRequest request) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_DOCTOR_KEY", null, "No certificate found in request");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Doctor doctor = doctorAuthService.authenticateDoctor(cert);

        if(doctor.getPublicKey().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_DOCTOR_KEY", null);
        return doctor.getKeyData();
    }

    /**
     * POST endpoint for the doctor key pair. (plaintext public key and encrypted private key)
     * The doctor identity is extracted from the certificate included in the request.
     * If there is no certificate, an error code is returned.
     * The doctor uploads their key pair here. It will be used by patients to share medical records securely.
     *
     * @param request the HTTP request object
     */
    @PostMapping("/api/key")
    @ResponseBody
    public void setDoctorKey(HttpServletRequest request, @RequestBody Doctor.DoctorKeyData keyData) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "SET_DOCTOR_KEY", null, "No certificate found in request");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Doctor doctor = doctorAuthService.authenticateDoctor(cert);

        try {
            doctor.fromKeyData(keyData);
            doctorAuthService.getDoctorRepository().save(doctor);
        }
        catch (HexException e) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "SET_DOCTOR_KEY", null, "Bad Request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }


        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "SET_DOCTOR_KEY", null);
    }

    /**
     * GET endpoint for the doctor appointed patients.
     * The doctor identity is extracted from the certificate included in the request.
     * If there is no certificate, an error code is returned.
     *
     * @param request the HTTP request object
     * @return the list of appointed patients
     */
    @GetMapping("/api/patients")
    @ResponseBody
    public List<PatientPrivateDetails.PatientPrivateDetailsRecord> getPatients(HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_APPOINTED_PATIENTS", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Set<Patient> patients = doctor.getPatients();

        List<PatientPrivateDetails.PatientPrivateDetailsRecord> patientList = patients.stream()
            .map(p -> {
                String fek = p.getDoctorKeys().stream()
                        .filter(k -> k.getDoctorId().equals(doctor.getId()))
                        .findAny()
                        .map(DoctorFekVersion::getEncryptedFek)
                        .orElseThrow(() -> {
                            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_APPOINTED_PATIENTS", null, "Unauthorized");
                            return new ResponseStatusException(HttpStatus.FORBIDDEN);
                        });
                return new PatientPrivateDetails.PatientPrivateDetailsRecord(EncodingUtils.toHex(p.details.getDob()),
                        EncodingUtils.toHex(p.details.getName()),
                        EncodingUtils.toHex(p.details.getDobIv()),
                        EncodingUtils.toHex(p.details.getNameIv()),
                        fek, "", p.getId());
            })
            .collect(Collectors.toList());

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_APPOINTED_PATIENTS", null);
        return patientList;
    }


    /**
     * GET endpoint used by the doctor to retrieve the medical record of a patient.
     * The doctor identity is extracted from the certificate included in the request.
     * If there is no certificate, an error code is returned.
     * If the doctor is not appointed to that patient, an error is returned.
     *
     * @param request the HTTP request object
     * @param patientId the patient ID
     * @return the doctor key pair
     */
    @GetMapping("/api/patients/{patientId}/files")
    @ResponseBody
    public List<EncryptedFile.FileData> getPatientFiles(@PathVariable Long patientId, HttpServletRequest request) {

        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_PATIENT_FILES_AS_DOCTOR", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> {
                logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_PATIENT_FILES_AS_DOCTOR", null, "Not Found");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
            });

        if (patient.getAppointedDoctors().stream().noneMatch(d -> d.getId().equals(doctor.getId()))) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_PATIENT_FILES_AS_DOCTOR", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        System.out.println("GETTING PATIENT FILES");
        for(EncryptedFile f : fileRepository.findAllByPatient(patient)) {
            System.out.println(f.getUpdatedAt().toEpochMilli() + " => " + f.getDoctorKeys().size());
        }

        return fileRepository.findAllByPatient(patient)
                .stream()
                .filter(f -> f.getDoctorKeys().stream().anyMatch(k -> k.getDoctorId().equals(doctor.getId())))
                .map(f -> {
                    String fek = f.getDoctorKeys()
                            .stream()
                            .filter(k -> k.getDoctorId().equals(doctor.getId()))
                            .findAny()
                            .map(DoctorFekVersion::getEncryptedFek)
                            .orElse("");
                    return new EncryptedFile.FileData(fek, "",
                            EncodingUtils.toHex(f.getFilename()),
                            EncodingUtils.toHex(f.getFilenameIv()),
                            f.getUpdatedAt().toString(), f.getId());
                })
                .toList();
    }

    /**
     * GET endpoint used by the doctor to download a file from a patient's medical record.
     * The doctor identity is extracted from the certificate included in the request.
     * If there is no certificate, an error code is returned.
     * If the doctor is not appointed to that patient, an error is returned.
     *
     * @param request the HTTP request object
     * @return the doctor key pair
     */
    @GetMapping(value = "/api/patients/{patientId}/files/{fileId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadPatientFile(
            @PathVariable Long patientId, @PathVariable Long fileId, HttpServletRequest request) {
        
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> {
                logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Not Found");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
            });

        if (patient.getAppointedDoctors().stream().noneMatch(d -> d.getId().equals(doctor.getId()))) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient not in your organization");
        }

        EncryptedFile file = fileRepository.findById(fileId)
            .orElseThrow(() -> {
                logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Not Found");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            });

        if (!file.getPatient().getId().equals(patient.getId())) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", "File:" + fileId, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File does not belong to this patient");
        }

        StreamingResponseBody stream = outputStream -> {
            outputStream.write(file.getData());
            outputStream.flush();
        };

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", "File:" + fileId);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"encrypted.bin\"")
            .header("X-File-IV", EncodingUtils.toHex(file.getDataIv()))
            .contentLength(file.getData().length)
            .body(stream);
    }

    /**
     * Helper function to extract the doctor identity from the certificate present in the HTTP request.
     *
     * @param request the HTTP request object
     * @return the authenticated doctor, or null
     */
    private Doctor authenticateRequest(HttpServletRequest request) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) return null;
        try {
            return doctorAuthService.authenticateDoctor(cert);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper function to extract the certificate from the HTTP request.
     *
     * @param request the HTTP request object
     * @return the certificate
     */
    private X509Certificate extractCertificate(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs != null && certs.length > 0) {
            return certs[0];
        }
        return null;
    }
}
