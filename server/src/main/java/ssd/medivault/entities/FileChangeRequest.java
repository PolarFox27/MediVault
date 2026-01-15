package ssd.medivault.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents a change request from a doctor to modify a patient's medical file.
 * 
 * Security: This allows doctors to suggest changes without directly modifying
 * patient files. Patients must approve changes before they take effect.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class FileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(optional = false)
    private Doctor doctor;

    @ManyToOne(optional = false)
    private EncryptedFile targetFile;

    @ManyToOne(optional = false)
    private Patient patient;

    /**
     * Encrypted new file content (encrypted with patient's UMK).
     * Null if doctor is suggesting deletion.
     */
    @Lob
    @Column
    private byte[] encryptedNewData;

    @Lob
    @Column
    private byte[] encryptedNewDataIv;

    /**
     * Encrypted new FEK (File Encryption Key) for the new data.
     */
    @Lob
    @Column
    private byte[] encryptedFek;

    @Lob
    @Column
    private byte[] encryptedFekIv;

    /**
     * Doctor's comment explaining the suggested change (encrypted with patient's UMK).
     */
    @Lob
    @Column
    private byte[] encryptedComment;

    @Lob
    @Column
    private byte[] encryptedCommentIv;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeRequestStatus status = ChangeRequestStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant resolvedAt;

    public enum ChangeRequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
