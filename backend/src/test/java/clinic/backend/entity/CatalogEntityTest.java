package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.RoomType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-23T05:00:00Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    @Test
    void departmentUsesDomainPropertyNamesAndTracksUpdates() {
        UUID id = UUID.randomUUID();
        Department department = new Department(
                id,
                "K-NOI",
                "Khoa Nội",
                DepartmentType.CLINICAL,
                "Khám nội khoa",
                true,
                CREATED_AT);

        assertThat(department.getId()).isEqualTo(id);
        assertThat(department.getDepartmentCode()).isEqualTo("K-NOI");
        assertThat(department.getDepartmentName()).isEqualTo("Khoa Nội");

        department.update(
                "Khoa Nội tổng hợp",
                DepartmentType.CLINICAL,
                "Khám nội khoa tổng hợp",
                false,
                UPDATED_AT);

        assertThat(department.getDepartmentName()).isEqualTo("Khoa Nội tổng hợp");
        assertThat(department.getDescription()).isEqualTo("Khám nội khoa tổng hợp");
        assertThat(department.isActive()).isFalse();
        assertThat(department.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void roomUsesDomainPropertyNamesAndKeepsStateTransitionsIntact() {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Room room = new Room(
                id,
                CREATED_AT,
                departmentId,
                null,
                "PK-NOI-01",
                "Phòng khám Nội 01",
                "Tầng 1",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE);

        assertThat(room.getId()).isEqualTo(id);
        assertThat(room.getRoomCode()).isEqualTo("PK-NOI-01");
        assertThat(room.getRoomName()).isEqualTo("Phòng khám Nội 01");

        room.update(
                departmentId,
                "Phòng khám Nội tổng hợp",
                "Tầng 2",
                RoomType.EXAMINATION,
                ActivationStatus.ACTIVE,
                UPDATED_AT);

        assertThat(room.getRoomName()).isEqualTo("Phòng khám Nội tổng hợp");
        assertThat(room.getLocation()).isEqualTo("Tầng 2");
        assertThat(room.getUpdatedAt()).isEqualTo(UPDATED_AT);

        Instant deactivatedAt = UPDATED_AT.plusSeconds(60);
        room.setStatus(ActivationStatus.INACTIVE, deactivatedAt);

        assertThat(room.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
        assertThat(room.getUpdatedAt()).isEqualTo(deactivatedAt);
    }
}
