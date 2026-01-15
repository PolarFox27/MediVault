package ssd.medivault.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ssd.medivault.entities.Doctor;

import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    
    Optional<Doctor> findByCertificateSerialNumber(String certificateSerialNumber);
    
    Optional<Doctor> findByFullNameAndOrganization(String fullName, String organization);
}
