package ssd.medivault.data;

import java.util.List;
import java.util.Optional;

import com.yubico.webauthn.data.ByteArray;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;

@Repository
public interface PatientAuthenticatorRepository extends CrudRepository<PatientAuthenticator, Long> {
    Optional<PatientAuthenticator> findByCredentialId(ByteArray credentialId);
    List<PatientAuthenticator> findAllByUser (Patient user);
    List<PatientAuthenticator> findAllByCredentialId(ByteArray credentialId);
}
