package ssd.medivault.entities;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.UserIdentity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Lob
    @Column(nullable = false)
    private byte[] handle;

    @ManyToMany
    @JoinTable(
            name = "patient_doctor",
            joinColumns = @JoinColumn(name = "patient_id"),
            inverseJoinColumns = @JoinColumn(name = "doctor_id")
    )
    private Set<Doctor> appointedDoctors = new HashSet<>();


    @ElementCollection
    @CollectionTable(
            name = "doctor_keys_details",
            joinColumns = @JoinColumn(name = "patient_id")
    )
    private List<DoctorFekVersion> doctorKeys = new ArrayList<>();

    @Embedded
    public PatientPrivateDetails details;

    public Patient(UserIdentity user) {
        this.handle = user.getId().getBytes();
        this.username = user.getName();
        this.details = new PatientPrivateDetails();
    }

    public UserIdentity toUserIdentity() {
        return UserIdentity.builder()
                .name(getUsername())
                .displayName("Medivault Patient <" + getUsername() + ">")
                .id(new ByteArray(this.handle))
                .build();
    }
}
