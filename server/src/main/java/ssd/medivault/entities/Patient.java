package ssd.medivault.entities;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.UserIdentity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
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
