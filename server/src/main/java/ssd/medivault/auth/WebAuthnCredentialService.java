package ssd.medivault.auth;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.data.exception.HexException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Repository;
import lombok.Getter;
import org.springframework.web.server.ResponseStatusException;
import ssd.medivault.data.PatientAuthenticatorRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;
import ssd.medivault.entities.PatientPrivateDetails;
import ssd.medivault.logging.AuditLogger;
import ssd.medivault.utils.EncodingUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Repository
@RequiredArgsConstructor
public class WebAuthnCredentialService implements CredentialRepository {

    private final PatientRepository patientRepository;
    private final PatientAuthenticatorRepository authRepository;
    private final AuditLogger logger;

    /**
     * This function looks up in the database to find the matching user handle for the provided username.
     *
     * @param username the patient username
     * @return the corresponding user handle if it exists
     */
    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return patientRepository.findByUsername(username)
                .map(Patient::getHandle)
                .map(ByteArray::new);
    }

    /**
     * This function looks up in the database to find the matching username for the provided user handle.
     * @param userHandle the patient user handle
     * @return the corresponding username if it exists
     */
    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return patientRepository.findByHandle(userHandle.getBytes())
                .map(Patient::getUsername);
    }

    /**
     * Looks up the list of patient credentials associated with the given username in the database.
     * If the username does not exist, an empty list is returned.
     *
     * @param username the username to look up
     * @return the list of matching patient credentials.
     */
    public List<PatientAuthenticator> getAuthenticatorsForUsername(String username) {
        return patientRepository.findByUsername(username)
                .map(authRepository::findAllByPatient)
                .orElse(List.of());
    }

    /**
     * This function retrieves the set of public keys associated with the given username.
     *
     * @param username the patient username
     * @return the set of public key associated with that patient.
     */
    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return getAuthenticatorsForUsername(username).stream()
                .map(credential ->
                        PublicKeyCredentialDescriptor.builder()
                                .id(new ByteArray(credential.getCredentialId()))
                                .build())
                .collect(Collectors.toSet());
    }

    /**
     * This function looks up in the database to find the credential associated with the given credential ID and patient user handle.
     *
     * @param credentialId the credential ID
     * @param userHandle the patient user handle
     * @return the corresponding credential if it exists
     */
    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<PatientAuthenticator> auth = authRepository.findByCredentialId(credentialId.getBytes());
        return auth.map(credential ->
                RegisteredCredential.builder()
                        .credentialId(new ByteArray(credential.getCredentialId()))
                        .userHandle(new ByteArray(credential.getPatient().getHandle()))
                        .publicKeyCose(new ByteArray(credential.getPublicKey()))
                        .signatureCount(credential.getCount())
                        .build()
        );
    }

    /**
     * This function looks up in the database to find all credentials matching the given credential ID.
     *
     * @param credentialId the credential ID to look up
     * @return the set of matching credentials
     */
    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        List<PatientAuthenticator> auth = authRepository.findAllByCredentialId(credentialId.getBytes());
        return auth.stream()
                .map(credential ->
                        RegisteredCredential.builder()
                                .credentialId(new ByteArray(credential.getCredentialId()))
                                .userHandle(new ByteArray(credential.getPatient().getHandle()))
                                .publicKeyCose(new ByteArray(credential.getPublicKey()))
                                .signatureCount(credential.getCount())
                                .build())
                .collect(Collectors.toSet());
    }

    /**
     * This function updates the signature count of a credential in the database after a successful authentication from a patient.
     *
     * @param credentialId the ID of the credential to update in the database
     * @param newCount the new signature count
     */
    public PatientAuthenticator updateSignatureCount(ByteArray credentialId, Long newCount){
        Optional<PatientAuthenticator> patientAuthOpt = authRepository.findByCredentialId(credentialId.getBytes());
        if(patientAuthOpt.isEmpty())
            return null;

        PatientAuthenticator patientAuth = patientAuthOpt.get();
        patientAuth.setCount(newCount);
        return authRepository.save(patientAuth);
    }

    /**
     * This function removes a patient authentication key from the database based on the given credential ID.
     *
     * @param credentialId the ID of the credential to remove
     */
    @Transactional
    public void deleteCredentials(String credentialId) {
        authRepository.deleteAllByCredentialId(credentialId);
    }

    /**
     * This functions searches for the corresponding Patient from a given credential ID.
     *
     * @param credentialId the credentials for which the patient is retrieved
     * @return the patient found
     */
    public Optional<Patient> getPatientForCredentials(String credentialId){
        return this.authRepository.findByCredentialId(credentialId).map(PatientAuthenticator::getPatient);
    }

    /**
     * This function checks whether two credentials are authentication methods for the same patient.
     * If one of the credential does not exist, false is returned.
     *
     * @param credential1 the first credential
     * @param credential2 the second credential
     * @return true if the credentials belong to the same patient
     */
    public boolean areCredentialsFromSamePatient(String credential1, String credential2){
        Optional<Patient> patient1 = getPatientForCredentials(credential1);
        Optional<Patient> patient2 = getPatientForCredentials(credential2);
        if(patient1.isEmpty() || patient2.isEmpty()){
            return false;
        }
        return Arrays.equals(patient1.get().getHandle(), patient2.get().getHandle());
    }

    /**
     * This function saves the provided encrypted UMK (User Master Key) in the PatientAuthenticator object.
     * It returns false if the credential ID is invalid or if the base64 encoding is invalid.
     *
     * @param request the request object containing the encrypted UMK
     * @return true if the operation was successful
     */
    public boolean setUmk(PatientAuthenticator.EncryptedUmk request) {
        PatientAuthenticator auth = this.getAuthRepository().findByCredentialId(request.credentialId()).orElse(null);
        if(auth == null)
            return false;
        try {
            auth.setEncryptedUmk(EncodingUtils.fromHex(request.encryptedUmk()));
            auth.setIv(EncodingUtils.fromHex(request.iv()));
            this.authRepository.save(auth);
            return true;
        } catch (HexException e) {
            return false;
        }

    }

    /**
     * This function saves the provided encrypted patient details in the database.
     *
     * @param details the object containing the encrypted private patient details.
     * @param patient the patient whose details are updated.
     * @return true if the operation was successful
     */
    public boolean setPrivateDetails(PatientPrivateDetails.PatientPrivateDetailsRecord details, Patient patient) {

        if(patient == null)
            return false;

        try {
            patient.getDetails().setDob(EncodingUtils.fromHex(details.dob()));
            patient.getDetails().setName(EncodingUtils.fromHex(details.name()));
            patient.getDetails().setDobIv(EncodingUtils.fromHex(details.dobIv()));
            patient.getDetails().setNameIv(EncodingUtils.fromHex(details.nameIv()));
            patient.getDetails().setFek(EncodingUtils.fromHex(details.fek()));
            patient.getDetails().setFekIv(EncodingUtils.fromHex(details.fekIv()));
            patient.getDoctorKeys().clear();
            this.patientRepository.save(patient);
            return true;
        } catch (HexException e) {
            return false;
        }

    }


    /**
     * Helper function that extracts the patient identity from an authentication token.
     * If the token is invalid or the patient doesn't exist. HTTP 401 Unauthorized is thrown
     *
     * @param token the authentication token
     * @param action the action for which the patient is extracted (for logging purposes)
     * @param request the HTTP request object (for logging purposes)
     * @return the patient object
     */
    public Patient extractPatient(Authentication token, String action, HttpServletRequest request) {
        Optional<Patient> patient = this.patientRepository.findByUsername(String.valueOf(token.getPrincipal()));

        if(patient.isEmpty()) {
            logger.logAction(AuditLogger.Level.WARN, request.getRemoteAddr(), "Non-authenticated User", action, null, "Unauthorized");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return patient.get();
    }
}
