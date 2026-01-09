package ssd.medivault.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.auth.AuthenticationToken;
import ssd.medivault.data.EncryptedFileRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.EncryptedFile;
import ssd.medivault.entities.Patient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class PatientFilesController {

    private final PatientRepository patientRepository;
    private final EncryptedFileRepository fileRepository;

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
            Authentication authentication) {

        Optional<Patient> patient = patientRepository.findByUsername(String.valueOf(authentication.getPrincipal()));

        if(patient.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        EncryptedFile f = new EncryptedFile();
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }


        EncryptedFile saved = fileRepository.save(f);
        return saved.getId();

    }

    /**
     * This endpoint is used to retrieve the information of all the files from a patient medical record:
     * File names, FEKs, IDs and Last modification dates
     * The patient identity is extracted from the authentication token
     *
     * @param authentication patient authentication token
     * @return the list of file data
     */
    @GetMapping("/patient/files")
    public List<EncryptedFile.FileData> getFileList(Authentication authentication) {

        Patient patient = AuthenticationToken.extractPatient(authentication, patientRepository);

        return fileRepository.findAllByPatient(patient)
                .stream()
                .map(EncryptedFile::toRecord)
                .toList();
    }

    /**
     * This endpoint is used by the patient to download the full content of a file based on the file id.
     *
     * @param id the file id
     * @param authentication the patient authentication token
     * @return the encrypted file object
     */
    @GetMapping("/patient/files/{id}")
    public EncryptedFile.EncryptedFileDTO downloadFile(@PathVariable Long id, Authentication authentication) {

        Patient patient = AuthenticationToken.extractPatient(authentication, patientRepository);

        EncryptedFile f = fileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!f.getPatient().equals(patient))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        return EncryptedFile.EncryptedFileDTO.from(f);
    }

    /**
     * This endpoint is used by the patient to delete a file based on the file id.
     *
     * @param id the file id
     * @param authentication the patient authentication token
     */
    @DeleteMapping("/patient/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable Long id, Authentication authentication) {

        Patient patient = AuthenticationToken.extractPatient(authentication, patientRepository);

        EncryptedFile f = fileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!f.getPatient().equals(patient))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        fileRepository.delete(f);

    }
}
