package ssd.medivault.auth;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import org.springframework.stereotype.Repository;
import lombok.Getter;
import ssd.medivault.data.PatientAuthenticatorRepository;
import ssd.medivault.data.PatientRepository;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Repository
public class WebAuthnCredentialService implements CredentialRepository {

    private final PatientRepository patientRepository;
    private final PatientAuthenticatorRepository authRepository;

    /**
     * Constructor for the WebauthnService class
     *
     * @param patientRepository the JPA repository storing patient information
     * @param authRepository the JPA repository storing patient authentication keys
     */
    public WebAuthnCredentialService(PatientRepository patientRepository,
                                     PatientAuthenticatorRepository authRepository) {
        this.patientRepository = patientRepository;
        this.authRepository = authRepository;
    }

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
    public void updateSignatureCount(ByteArray credentialId, Long newCount){
        Optional<PatientAuthenticator> patientAuthOpt = authRepository.findByCredentialId(credentialId.getBytes());
        if(patientAuthOpt.isEmpty())
            return;

        PatientAuthenticator patientAuth = patientAuthOpt.get();
        patientAuth.setCount(newCount);
        authRepository.save(patientAuth);
    }
}
