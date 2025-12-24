package ssd.medivault.data;

import java.util.List;
import java.util.Optional;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.exception.HexException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;

@Repository
public interface PatientAuthenticatorRepository extends CrudRepository<PatientAuthenticator, Long> {
    Optional<PatientAuthenticator> findByCredentialId(byte[] credentialId);
    List<PatientAuthenticator> findAllByPatient (Patient patient);
    List<PatientAuthenticator> findAllByCredentialId(byte[] credentialId);

    default Optional<PatientAuthenticator> findByCredentialId(String credentialId){
        try {
            return this.findByCredentialId(ByteArray.fromHex(credentialId).getBytes());
        } catch (HexException e) {
            return Optional.empty();
        }
    }

    default List<PatientAuthenticator> findAllByCredentialId(String credentialId){
        try {
            return this.findAllByCredentialId(ByteArray.fromHex(credentialId).getBytes());
        } catch (HexException e) {
            return List.of();
        }
    }
}
