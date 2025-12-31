package ssd.medivault.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import ssd.medivault.utils.EncodingUtils;

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

    public PatientPrivateDetails() {
        this.dob = new byte[]{};
        this.name = new byte[]{};
        this.dobIv = new byte[]{};
        this.nameIv = new byte[]{};
    }

    public record PatientPrivateDetailsRecord(String dob, String name, String dobIv, String nameIv) {}

    public PatientPrivateDetailsRecord toRecord(){
        return new PatientPrivateDetailsRecord(EncodingUtils.toHex(this.dob),
                EncodingUtils.toHex(this.name),
                EncodingUtils.toHex(this.dobIv),
                EncodingUtils.toHex(this.nameIv));
    }
}
