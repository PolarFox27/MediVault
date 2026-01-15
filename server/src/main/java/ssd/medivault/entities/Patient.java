package ssd.medivault.entities;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.UserIdentity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    /**
     * The medical organization this patient is registered with.
     * Doctors from the same organization can access patient files.
     */
    @Column
    private String organization;

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
