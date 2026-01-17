package ssd.medivault.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.auth.WebAuthnCredentialService;
import ssd.medivault.data.DoctorRepository;
import ssd.medivault.data.EncryptedFileRepository;
import ssd.medivault.entities.Doctor;
import ssd.medivault.entities.DoctorFekVersion;
import ssd.medivault.entities.EncryptedFile;
import ssd.medivault.entities.Patient;
import ssd.medivault.logging.AuditLogger;

import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
public class PatientDoctorsController {

    private final DoctorRepository doctorRepository;
    private final WebAuthnCredentialService credentialService;
    private final EncryptedFileRepository fileRepository;
    private final AuditLogger logger;

    /**
     * Get list of appointed doctors of a patient.
     * Security: Only authenticated patients can access this endpoint.
     * Returns minimal information via DTO (id, name, organization, public key only).
     *
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     * @return list of doctors with safe, public information only
     */
    @GetMapping("/patient/api/appointed-doctors")
    public List<Doctor.DoctorInfoDTO> getAppointedDoctors(Authentication authentication, HttpServletRequest request) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_GET_APPOINTED_DOCTORS", request);

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_GET_APPOINTED_DOCTORS", null);

        return patient.getAppointedDoctors().stream()
                .map(Doctor::toDTO)
                .toList();
    }

    /**
     * Get list of available doctors that the patient can appoint.
     * Security: Only authenticated patients can access this endpoint.
     * Returns minimal information via DTO (id, name, organization, public key only).
     *
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     * @return list of doctors with safe, public information only
     */
    @GetMapping("/patient/api/doctors")
    public List<Doctor.DoctorInfoDTO> getAvailableDoctors(Authentication authentication, HttpServletRequest request) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_GET_DOCTORS", request);

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_GET_DOCTORS", null);

        return doctorRepository.findAll().stream()
                .filter(d -> !patient.getAppointedDoctors().contains(d))
                .map(Doctor::toDTO)
                .toList();
    }


    /**
     * PUT mapping to appoint a doctor based on its id.
     *
     * @param id the doctor ID
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     * @param feks the FEKs encrypted with the doctor's public key for all files in the patient's medical record
     */
    @PutMapping("/patient/api/doctors/{id}")
    public void appointDoctor(@PathVariable Long id, Authentication authentication, HttpServletRequest request,
                              @RequestBody List<DoctorFekVersion> feks) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_APPOINT_DOCTOR", request);
        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_APPOINT_DOCTOR", null, "Not Found");
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        });

        if(patient.getAppointedDoctors().stream().noneMatch(d -> Objects.equals(d.getId(), id))) {
            patient.getAppointedDoctors().add(doctor);
            doctor.getPatients().add(patient);
            credentialService.getPatientRepository().save(patient);
            doctorRepository.save(doctor);

            System.out.println("GETTING PATIENT FILES (Before appointing)");
            for(EncryptedFile f : fileRepository.findAllByPatient(patient)) {
                System.out.println(f.getUpdatedAt().toEpochMilli() + " => " + f.getDoctorKeys().size());
            }

            for(DoctorFekVersion fek : feks) {
                EncryptedFile f = fileRepository.findById(fek.getDoctorId()).orElse(null);
                if(f == null) continue;

                f.getDoctorKeys().add(new DoctorFekVersion(id, fek.getEncryptedFek()));
                fileRepository.save(f);
            }

            System.out.println("GETTING PATIENT FILES (After appointing)");
            for(EncryptedFile f : fileRepository.findAllByPatient(patient)) {
                System.out.println(f.getUpdatedAt().toEpochMilli() + " => " + f.getDoctorKeys().size());
            }

            logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_APPOINT_DOCTOR", null);
        }
    }


    /**
     * DELETE mapping to un-appoint a doctor based on its id.
     *
     * @param id the doctor ID
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     */
    @DeleteMapping("/patient/api/doctors/{id}")
    public void unappointDoctor(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_UNAPPOINT_DOCTOR", request);

        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_UNAPPOINT_DOCTOR", null, "Not Found");
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        });

        patient.getAppointedDoctors().removeIf(d -> d.getId().equals(id));
        doctor.getPatients().removeIf(p -> p.getId().equals(patient.getId()));

        credentialService.getPatientRepository().save(patient);
        doctorRepository.save(doctor);

        for(EncryptedFile f : fileRepository.findAllByPatient(patient)) {
            f.getDoctorKeys().removeIf(fek -> fek.getDoctorId().equals(id));
            fileRepository.save(f);
        }

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_UNAPPOINT_DOCTOR", null);
    }


    /**
     * PUT mapping to add the FEK versions encrypted with the appointed doctors public keys for the file in the medical record.
     *
     * @param id the file ID
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     * @param feks the FEKs encrypted with all the appointed doctors' public keys
     */
    @PutMapping("/patient/files/{id}/fek")
    public void addFeksToFile(@PathVariable Long id, Authentication authentication, HttpServletRequest request,
                              @RequestBody List<DoctorFekVersion> feks) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_UPLOAD_FILE", request);
        EncryptedFile file = fileRepository.findById(id).orElseThrow(() -> {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_UPLOAD_FILE", "File:" + id, "Not Found");
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        });

        file.getDoctorKeys().clear();
        file.getDoctorKeys().addAll(feks);
        fileRepository.save(file);

        System.out.println("GETTING PATIENT FILES (Uploading file " + id + ")");
        for(EncryptedFile f : fileRepository.findAllByPatient(patient)) {
            System.out.println(f.getUpdatedAt().toEpochMilli() + " => " + f.getDoctorKeys().size());
        }

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_UPLOAD_FILE", "File:" + id);
    }

    /**
     * PUT mapping to add the FEK versions encrypted with the appointed doctors public keys for the patient details.
     *
     * @param authentication the patient authentication token
     * @param request the HTTP request object
     * @param feks the FEKs encrypted with all the appointed doctors' public keys
     */
    @PutMapping("/patient/details/fek")
    public void addFeksToDetails(Authentication authentication, HttpServletRequest request,
                              @RequestBody List<DoctorFekVersion> feks) {
        Patient patient = credentialService.extractPatient(authentication, "PATIENT_SET_DETAILS", request);

        patient.getDoctorKeys().addAll(feks);
        credentialService.getPatientRepository().save(patient);

        logger.logAction(AuditLogger.Level.INFO, request.getRemoteAddr(), "Patient:" + patient.getUsername(), "PATIENT_SET_DETAILS", null);
    }
}
