package ssd.medivault.auth;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
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
public class WebauthnRegistrationService implements CredentialRepository {

    private final PatientRepository patientRepository;
    private final PatientAuthenticatorRepository authRepository;

    public WebauthnRegistrationService(PatientRepository patientRepository, PatientAuthenticatorRepository authRepository) {
        this.patientRepository = patientRepository;
        this.authRepository = authRepository;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        Patient patient = patientRepository.findByUsername(username);
        List<PatientAuthenticator> auth = authRepository.findAllByPatient(patient);
        return auth.stream()
                .map(credential ->
                        PublicKeyCredentialDescriptor.builder()
                                .id(new ByteArray(credential.getCredentialId()))
                                .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        Patient user = patientRepository.findByUsername(username);
        return Optional.of(new ByteArray(user.getHandle()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        Patient user = patientRepository.findByHandle(userHandle.getBytes());
        return Optional.of(user.getUsername());
    }

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
}
