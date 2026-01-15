package ssd.medivault.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Doctor;
import ssd.medivault.entities.FileChangeRequest;
import ssd.medivault.entities.Patient;

import java.util.List;

@Repository
public interface FileChangeRequestRepository extends CrudRepository<FileChangeRequest, Long> {
    
    List<FileChangeRequest> findAllByDoctor(Doctor doctor);
    
    List<FileChangeRequest> findAllByPatient(Patient patient);
    
    List<FileChangeRequest> findAllByPatientAndStatus(Patient patient, FileChangeRequest.ChangeRequestStatus status);
    
    List<FileChangeRequest> findAllByDoctorAndStatus(Doctor doctor, FileChangeRequest.ChangeRequestStatus status);
}
