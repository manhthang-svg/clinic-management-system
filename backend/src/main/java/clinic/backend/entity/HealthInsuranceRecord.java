package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "health_insurance_records")
public class HealthInsuranceRecord {
    @Id
    private UUID id;

    @Column(name = "ma_so_bhxh", nullable = false, length = 10)
    private String socialInsuranceNumber;

    @Column(name = "ma_the_bhyt", length = 15)
    private String cardNumber;

    @Column(name = "identity_number", length = 12)
    private String identityNumber;

    @Column(name = "ho_ten", nullable = false, length = 100)
    private String fullName;

    @Column(name = "ngay_sinh", nullable = false, length = 8)
    private String dateOfBirth;

    @Column(name = "loai_ngay_sinh", nullable = false)
    private short dateOfBirthType;

    @Column(name = "gioi_tinh", nullable = false)
    private short gender;

    @Column(name = "ma_cskcb_bd", nullable = false, length = 5)
    private String primaryCareFacilityCode;

    @Column(name = "ten_cskcb_bd", nullable = false, length = 255)
    private String primaryCareFacilityName;

    @Column(name = "gt_the_tu", nullable = false)
    private LocalDate validFrom;

    @Column(name = "gt_the_den", nullable = false)
    private LocalDate validTo;

    @Column(name = "ti_le_huong", nullable = false)
    private short coverageRate;

    @Column(name = "trang_thai", nullable = false)
    private short status;

    @Column(name = "error_code", nullable = false, length = 5)
    private String resultCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
