package ssd.medivault.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Patient;

import java.util.Optional;

@Repository
public interface PatientRepository extends CrudRepository<Patient, Long> {
    Optional<Patient> findByUsername(String username);
    Optional<Patient> findByHandle(byte[] handle);
}
