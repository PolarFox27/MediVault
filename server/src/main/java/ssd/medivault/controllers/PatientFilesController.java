package ssd.medivault.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.data.DoctorRepository;
import ssd.medivault.data.EncryptedFileRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.dto.DoctorInfoDTO;
import ssd.medivault.entities.EncryptedFile;
import ssd.medivault.entities.Patient;
import ssd.medivault.logging.AuditLogger;
import ssd.medivault.utils.EncodingUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class PatientFilesController {

    private final PatientRepository patientRepository;
    private final EncryptedFileRepository fileRepository;
    private final DoctorRepository doctorRepository;
    private final WebAuthnCredentialService credentialService;
    private final AuditLogger logger;

    /**
     * This endpoint handles encrypted file upload from patients.
     * Encrypted files are then stored in the database.
     * The patient identity is extracted from the authentication token.
     *
     * @param data encrypted file data
     * @param dataIv IV for the encrypted file data
     * @param filename encrypted file name
     * @param filenameIv IV for the encryption file name
     * @param fek encrypted FEK (File Encryption Key)
     * @param fekIv IV for the encrypted FEK
     * @param authentication authentication token
     * @param request the HTTP request object
     * @return the ID of the file stored in the database
     */
    @PostMapping(path = "/patient/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Long uploadFile(
            @RequestPart("data") MultipartFile data,
            @RequestPart("dataIv") MultipartFile dataIv,
            @RequestPart("filename") MultipartFile filename,
            @RequestPart("filenameIv") MultipartFile filenameIv,
            @RequestPart("fek") MultipartFile fek,
            @RequestPart("fekIv") MultipartFile fekIv,
            @RequestParam Long id,
            Authentication authentication,
            HttpServletRequest request) {

        Optional<Patient> patient = patientRepository.findByUsername(String.valueOf(authentication.getPrincipal()));

        if(patient.isEmpty()) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", "PATIENT_UPLOAD_FILE", null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        EncryptedFile f = fileRepository.findById(id).orElse(new EncryptedFile());
        try {
            f.setData(data.getBytes());
            f.setDataIv(dataIv.getBytes());
            f.setFilename(filename.getBytes());
            f.setFilenameIv(filenameIv.getBytes());
            f.setFek(fek.getBytes());
            f.setFekIv(fekIv.getBytes());
            f.setPatient(patient.get());
        }
        catch (IOException e) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.get().getUsername(), "PATIENT_UPLOAD_FILE", null, "Bad Request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }


        EncryptedFile saved = fileRepository.save(f);
        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.get().getUsername(), "PATIENT_UPLOAD_FILE", "File:" + saved.getId());
        return saved.getId();

    }

    /**
     * This endpoint is used to retrieve the information of all the files from a patient medical record:
     * File names, FEKs, IDs and Last modification dates
     * The patient identity is extracted from the authentication token
     *
     * @param authentication patient authentication token
     * @param request the HTTP request object
     * @return the list of file data
     */
    @GetMapping("/patient/files")
    public List<EncryptedFile.FileData> getFileList(Authentication authentication, HttpServletRequest request) {

        Patient patient = credentialService.extractPatient(authentication, "PATIENT_GET_FILE_LIST", request);


        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_GET_FILE_LIST", null);

        return fileRepository.findAllByPatient(patient)
                .stream()
                .map(EncryptedFile::toRecord)
                .toList();
    }

    /**
     * This endpoint is used by the patient to download the full content of a file based on the file id.
     *
     * @param id the file id
     * @param auth the patient authentication token
     * @param request the HTTP request object
     * @return the encrypted file object as an octet stream for best efficiency
     */
    @GetMapping(
            value = "/patient/files/{id}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ResponseEntity<StreamingResponseBody> downloadFile(@PathVariable Long id, Authentication auth, HttpServletRequest request) {

        Patient patient = credentialService.extractPatient(auth, "PATIENT_DOWNLOAD_FILE", request);

        EncryptedFile f = fileRepository.findById(id)
                .orElseThrow(() -> {
                    logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_DOWNLOAD_FILE", "File:" + id, "Not Found");
                    return new ResponseStatusException(HttpStatus.NOT_FOUND);
                });

        if (!f.getPatient().equals(patient)) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_DOWNLOAD_FILE", "File:" + id, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        StreamingResponseBody stream = outputStream -> {
            outputStream.write(f.getData());     // encrypted bytes
            outputStream.flush();
        };

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_DOWNLOAD_FILE", "File:" + id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"encrypted.bin\"")
                .header("X-File-IV", EncodingUtils.toHex(f.getDataIv()))
                .contentLength(f.getData().length)
                .body(stream);
    }

    /**
     * This endpoint is used by the patient to delete a file based on the file id.
     *
     * @param id the file id
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     */
    @DeleteMapping("/patient/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {

        Patient patient = credentialService.extractPatient(authentication, "PATIENT_DELETE_FILE", request);

        EncryptedFile f = fileRepository.findById(id)
                .orElseThrow(() -> {
                    logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_DELETE_FILE", "File:" + id, "Not Found");
                    return new ResponseStatusException(HttpStatus.NOT_FOUND);
                });

        if (!f.getPatient().equals(patient)) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_DELETE_FILE", "File:" + id, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_DELETE_FILE", "File:" + id);
        fileRepository.delete(f);

    }

    /**
     * Get list of all available doctors.
     * Security: Only authenticated patients can access this endpoint.
     * Returns minimal information via DTO (id, name, organization only).
     *
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     * @return list of doctors with safe, public information only
     */
    @GetMapping("/patient/api/doctors")
    public List<DoctorInfoDTO> getAvailableDoctors(Authentication authentication, HttpServletRequest request) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_GET_DOCTORS", request);
        
        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_GET_DOCTORS", null);
        
        return doctorRepository.findAll().stream()
                .map(d -> new DoctorInfoDTO(d.getId(), d.getFullName(), d.getOrganization()))
                .toList();
    }
}
