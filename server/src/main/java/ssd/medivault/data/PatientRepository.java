package ssd.medivault.data;

import com.yubico.webauthn.data.ByteArray;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Patient;

@Repository
public interface PatientRepository extends CrudRepository<Patient, Long> {
    Patient findByUsername(String name);
    Patient findByHandle(ByteArray handle);
}
