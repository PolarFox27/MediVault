package ssd.medivault.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Patient;
import ssd.medivault.entities.PatientAuthenticator;

@Repository
public interface PatientAuthenticatorRepository extends CrudRepository<PatientAuthenticator, Long> {
    Optional<PatientAuthenticator> findByCredentialId(byte[] credentialId);
    List<PatientAuthenticator> findAllByPatient (Patient patient);
    List<PatientAuthenticator> findAllByCredentialId(byte[] credentialId);
}
