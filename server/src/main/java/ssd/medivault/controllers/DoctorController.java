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
import ssd.medivault.entities.Doctor;
import ssd.medivault.entities.EncryptedFile;
import ssd.medivault.entities.FileChangeRequest;
import ssd.medivault.entities.Patient;
import ssd.medivault.logging.AuditLogger;
import ssd.medivault.utils.EncodingUtils;
import com.yubico.webauthn.data.exception.HexException;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @GetMapping("/api/patients")
    @ResponseBody
    public ResponseEntity<?> getPatients(HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_APPOINTED_PATIENTS", null, "Unauthorized");
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }

        Set<Patient> patients = doctor.getPatients();

        System.out.println("#Patients: " + patients.size());
        List<Map<String, Object>> patientList = patients.stream()
            .map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("username", p.getUsername());
                return m;
            })
            .collect(Collectors.toList());

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_APPOINTED_PATIENTS", null);
        return ResponseEntity.ok(patientList);
    }

    @GetMapping("/api/patients/{patientId}/files")
    @ResponseBody
    public ResponseEntity<?> getPatientFiles(@PathVariable Long patientId, HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "GET_PATIENT_FILES_AS_DOCTOR", null, "Unauthorized");
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> {
                logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_PATIENT_FILES_AS_DOCTOR", null, "Not Found");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
            });

        if (!doctor.getOrganization().equals(patient.getOrganization())) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "GET_PATIENT_FILES_AS_DOCTOR", null, "Unauthorized");
            return ResponseEntity.status(403).body(Map.of("error", "Patient not in your organization"));
        }

        List<EncryptedFile> files = fileRepository.findAllByPatient(patient);
        List<EncryptedFile.FileData> fileList = files.stream()
            .map(EncryptedFile::toRecord)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(fileList);
    }

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

        if (!doctor.getOrganization().equals(patient.getOrganization())) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient not in your organization");
        }

        EncryptedFile file = fileRepository.findById(fileId)
            .orElseThrow(() -> {
                logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Not Found");
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            });

        if (!file.getPatient().getId().equals(patient.getId())) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File does not belong to this patient");
        }

        StreamingResponseBody stream = outputStream -> {
            outputStream.write(file.getData());
            outputStream.flush();
        };

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Doctor:" + doctor.getFullName(), "DOWNLOAD_PATIENT_FILE_AS_DOCTOR", null);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"encrypted.bin\"")
            .header("X-File-IV", EncodingUtils.toHex(file.getDataIv()))
            .header("X-FEK", EncodingUtils.toHex(file.getFek()))
            .header("X-FEK-IV", EncodingUtils.toHex(file.getFekIv()))
            .header("X-Filename", EncodingUtils.toHex(file.getFilename()))
            .header("X-Filename-IV", EncodingUtils.toHex(file.getFilenameIv()))
            .contentLength(file.getData().length)
            .body(stream);
    }

    @DeleteMapping("/api/patients/{patientId}/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePatientFile(@PathVariable Long patientId, @PathVariable Long fileId, HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (!doctor.getOrganization().equals(patient.getOrganization())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient not in your organization");
        }

        EncryptedFile file = fileRepository.findById(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (!file.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File does not belong to this patient");
        }

        fileRepository.delete(file);
    }

    @PostMapping("/api/patients/{patientId}/files/{fileId}/change-request")
    @ResponseBody
    public ResponseEntity<?> createChangeRequest(
            @PathVariable Long patientId, @PathVariable Long fileId,
            @RequestBody Map<String, String> payload, HttpServletRequest request) {
        
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (!doctor.getOrganization().equals(patient.getOrganization())) {
            return ResponseEntity.status(403).body(Map.of("error", "Patient not in your organization"));
        }

        EncryptedFile file = fileRepository.findById(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (!file.getPatient().getId().equals(patient.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "File does not belong to this patient"));
        }

        FileChangeRequest changeRequest = new FileChangeRequest();
        changeRequest.setDoctor(doctor);
        changeRequest.setPatient(patient);
        changeRequest.setTargetFile(file);
        
        try {
            if (payload.containsKey("encryptedComment")) {
                changeRequest.setEncryptedComment(EncodingUtils.fromHex(payload.get("encryptedComment")));
            }
            if (payload.containsKey("encryptedCommentIv")) {
                changeRequest.setEncryptedCommentIv(EncodingUtils.fromHex(payload.get("encryptedCommentIv")));
            }
        } catch (HexException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid hex encoding"));
        }

        FileChangeRequest saved = changeRequestRepository.save(changeRequest);

        return ResponseEntity.ok(Map.of(
            "id", saved.getId(),
            "status", saved.getStatus().toString(),
            "createdAt", saved.getCreatedAt().toString()
        ));
    }

    @GetMapping("/api/change-requests")
    @ResponseBody
    public ResponseEntity<?> getMyChangeRequests(HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }

        List<FileChangeRequest> requests = changeRequestRepository.findAllByDoctor(doctor);
        List<Map<String, Object>> result = requests.stream()
            .map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("patientId", r.getPatient().getId());
                m.put("patientUsername", r.getPatient().getUsername());
                m.put("fileId", r.getTargetFile().getId());
                m.put("status", r.getStatus().toString());
                m.put("createdAt", r.getCreatedAt().toString());
                if (r.getResolvedAt() != null) {
                    m.put("resolvedAt", r.getResolvedAt().toString());
                }
                return m;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    private Doctor authenticateRequest(HttpServletRequest request) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) return null;
        try {
            return doctorAuthService.authenticateDoctor(cert);
        } catch (Exception e) {
            return null;
        }
    }

    private X509Certificate extractCertificate(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs != null && certs.length > 0) {
            return certs[0];
        }
        return null;
    }
}
