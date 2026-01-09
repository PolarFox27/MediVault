package ssd.medivault.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.EncryptedFile;
import ssd.medivault.entities.Patient;

import java.util.List;

@Repository
public interface EncryptedFileRepository extends CrudRepository<EncryptedFile, Long> {
    List<EncryptedFile> findAllByPatient(Patient patient);
}
