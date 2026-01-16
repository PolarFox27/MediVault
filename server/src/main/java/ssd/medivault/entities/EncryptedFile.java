package ssd.medivault.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import ssd.medivault.utils.EncodingUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Getter
@Setter
public class EncryptedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Lob
    @Column(nullable = false)
    private byte[] dataIv;

    @Lob
    @Column(nullable = false)
    private byte[] filename;

    @Lob
    @Column(nullable = false)
    private byte[] filenameIv;

    @Lob
    @Column(nullable = false)
    private byte[] fek;

    @Lob
    @Column(nullable = false)
    private byte[] fekIv;

    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne
    private Patient patient;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "doctor_keys",
            joinColumns = @JoinColumn(name = "entity_id")
    )
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    @Lob
    private Map<Long, byte[]> doctorKeys = new HashMap<>();

    public EncryptedFile() {
        this.data = new byte[]{};
        this.dataIv = new byte[]{};
        this.filename = new byte[]{};
        this.filenameIv = new byte[]{};
        this.fek = new byte[]{};
        this.fekIv = new byte[]{};
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        this.updatedAt = Instant.now();
    }


    @Data
    public static class EncryptedFileDTO {

        private Long id;
        private byte[] data;
        private byte[] dataIv;

        public static EncryptedFileDTO from(EncryptedFile f) {
            EncryptedFileDTO dto = new EncryptedFileDTO();
            dto.setId(f.getId());
            dto.setData(f.getData());
            dto.setDataIv(f.getDataIv());
            return dto;
        }
    }

    public record FileData(String fek, String fekIv,
                                  String name, String nameIv,
                                  String updatedAt, Long id) {}

    public FileData toRecord(){
        return new FileData(EncodingUtils.toHex(this.fek), EncodingUtils.toHex(this.fekIv),
                EncodingUtils.toHex(this.filename), EncodingUtils.toHex(this.filenameIv),
                this.updatedAt.toString(), this.id);
    }
}
