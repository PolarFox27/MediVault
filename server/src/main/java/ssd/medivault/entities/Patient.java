package ssd.medivault.entities;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.UserIdentity;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

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

    @Lob
    @Column(nullable = false)
    private byte[] encryptedName;

    @Lob
    @Column(nullable = false)
    private byte[] encryptedDOB;

    public Patient(UserIdentity user) {
        this.handle = user.getId().getBytes();
        this.username = user.getName();
        this.encryptedName = new byte[]{};
        this.encryptedDOB = new byte[]{};
    }

    public UserIdentity toUserIdentity() {
        return UserIdentity.builder()
                .name(getUsername())
                .displayName(getUsername())
                .id(new ByteArray(this.handle))
                .build();
    }
}
