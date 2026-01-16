package ssd.medivault.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import ssd.medivault.utils.EncodingUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Class storing the full name and date of birth of patients in an encrypted form
 */
@Embeddable
@AllArgsConstructor
@Getter
@Setter
public class PatientPrivateDetails {

    @Lob
    @Column(nullable = false)
    private byte[] dob;

    @Lob
    @Column(nullable = false)
    private byte[] name;

    @Lob
    @Column(nullable = false)
    private byte[] dobIv;

    @Lob
    @Column(nullable = false)
    private byte[] nameIv;

    @Lob
    @Column(nullable = false)
    private byte[] fek;

    @Lob
    @Column(nullable = false)
    private byte[] fekIv;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "doctor_keys",
            joinColumns = @JoinColumn(name = "entity_id")
    )
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    @Lob
    private Map<Long, byte[]> doctorKeys = new HashMap<>();

    public PatientPrivateDetails() {
        this.dob = new byte[]{};
        this.name = new byte[]{};
        this.dobIv = new byte[]{};
        this.nameIv = new byte[]{};
        this.fek = new byte[]{};
        this.fekIv = new byte[]{};
    }

    public record PatientPrivateDetailsRecord(String dob, String name, String dobIv, String nameIv, String fek, String fekIv) {}

    public PatientPrivateDetailsRecord toRecord(){
        return new PatientPrivateDetailsRecord(EncodingUtils.toHex(this.dob),
                EncodingUtils.toHex(this.name),
                EncodingUtils.toHex(this.dobIv),
                EncodingUtils.toHex(this.nameIv),
                EncodingUtils.toHex(this.fek),
                EncodingUtils.toHex(this.fekIv));
    }
}
