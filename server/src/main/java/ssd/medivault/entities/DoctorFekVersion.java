package ssd.medivault.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@AllArgsConstructor
@Getter
@Setter
public class DoctorFekVersion {

    @Column
    private Long doctorId;

    @Column
    private String encryptedFek;

    public DoctorFekVersion(){
        this.doctorId = 0L;
        this.encryptedFek = "";
    }
}
