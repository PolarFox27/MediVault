package ssd.medivault.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
import ssd.medivault.utils.EncodingUtils;
import com.yubico.webauthn.data.exception.HexException;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private static final Logger logger = LoggerFactory.getLogger(DoctorController.class);
    
    private final DoctorX509AuthService doctorAuthService;
    private final PatientRepository patientRepository;
    private final EncryptedFileRepository fileRepository;
    private final FileChangeRequestRepository changeRequestRepository;

    public DoctorController(
            DoctorX509AuthService doctorAuthService,
            PatientRepository patientRepository,
            EncryptedFileRepository fileRepository,
            FileChangeRequestRepository changeRequestRepository) {
        this.doctorAuthService = doctorAuthService;
        this.patientRepository = patientRepository;
        this.fileRepository = fileRepository;
        this.changeRequestRepository = changeRequestRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) {
            logger.warn("No certificate found in request");
            return "redirect:/";
        }

        try {
            Doctor doctor = doctorAuthService.authenticateDoctor(cert);
            model.addAttribute("doctor", doctor);
            model.addAttribute("certSubject", cert.getSubjectX500Principal().getName());
            model.addAttribute("certIssuer", cert.getIssuerX500Principal().getName());
            model.addAttribute("certExpiry", cert.getNotAfter());
            
            List<Patient> patients = patientRepository.findAllByOrganization(doctor.getOrganization());
            model.addAttribute("patients", patients);
            model.addAttribute("patientCount", patients.size());
            
            return "pages/doctor/dashboard";
        } catch (Exception e) {
            logger.error("Doctor authentication failed", e);
            return "redirect:/";
        }
    }

    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDoctorInfo(HttpServletRequest request) {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) {
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
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Doctor API call failed", e);
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/patients")
    @ResponseBody
    public ResponseEntity<?> getPatients(HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }

        List<Patient> patients = patientRepository.findAllByOrganization(doctor.getOrganization());
        List<Map<String, Object>> patientList = patients.stream()
            .map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("username", p.getUsername());
                return m;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(patientList);
    }

    @GetMapping("/api/patients/{patientId}/files")
    @ResponseBody
    public ResponseEntity<?> getPatientFiles(@PathVariable Long patientId, HttpServletRequest request) {
        Doctor doctor = authenticateRequest(request);
        if (doctor == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }

        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (!doctor.getOrganization().equals(patient.getOrganization())) {
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

        StreamingResponseBody stream = outputStream -> {
            outputStream.write(file.getData());
            outputStream.flush();
        };

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

        logger.info("Doctor {} deleted file {} for patient {}", doctor.getFullName(), fileId, patient.getUsername());
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
        logger.info("Doctor {} created change request {} for patient {} file {}", 
            doctor.getFullName(), saved.getId(), patient.getUsername(), fileId);

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
            logger.error("Doctor authentication failed", e);
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
