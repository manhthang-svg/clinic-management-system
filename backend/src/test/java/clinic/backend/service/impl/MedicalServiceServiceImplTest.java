package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PerformingRoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicalServiceMapper;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalServiceServiceImplTest {

    @Mock MedicalServiceRepository serviceRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ServiceGroupRepository serviceGroupRepository;
    @Mock RoomRepository roomRepository;
    @Mock MedicalServiceMapper serviceMapper;
    @InjectMocks MedicalServiceServiceImpl service;

    @Test
    void listsServicesWithResolvedDepartmentAndGroupNames() {
        UUID serviceId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        Department department = mock(Department.class);
        ServiceGroup group = mock(ServiceGroup.class);
        when(entity.getDepartmentId()).thenReturn(departmentId);
        when(entity.getServiceGroupId()).thenReturn(groupId);
        when(department.getId()).thenReturn(departmentId);
        when(department.getDepartmentName()).thenReturn("Khoa Xét nghiệm");
        when(group.getId()).thenReturn(groupId);
        when(group.getServiceGroupName()).thenReturn("Xét nghiệm máu");
        when(serviceRepository.findAll(org.mockito.ArgumentMatchers.<Specification<MedicalService>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));
        when(departmentRepository.findAllById(any())).thenReturn(List.of(department));
        when(serviceGroupRepository.findAllById(any())).thenReturn(List.of(group));
        MedicalServiceSummaryResponse summary = new MedicalServiceSummaryResponse(
                serviceId, "XN.001", "Tổng phân tích tế bào máu", MedicalServiceType.LAB,
                groupId, "Xét nghiệm máu", departmentId, "Khoa Xét nghiệm",
                BigDecimal.valueOf(120000), null, ActivationStatus.ACTIVE, 0L);
        when(serviceMapper.toSummary(entity, "Xét nghiệm máu", "Khoa Xét nghiệm")).thenReturn(summary);

        var response = service.listServices(null, null, null, null, null, 0, 10, "code", false);

        assertThat(response.content()).containsExactly(summary);
        assertThat(response.page().totalElements()).isEqualTo(1);
        verify(serviceMapper).toSummary(entity, "Xét nghiệm máu", "Khoa Xét nghiệm");
    }

    @Test
    void rejectsUnsupportedPageSize() {
        assertThatThrownBy(() -> service.listServices(null, null, null, null, null, 0, 15, "code", false))
                .isInstanceOf(AppException.class);
    }

    @Test
    void returnsServiceDetailWithActivePerformingRooms() {
        UUID serviceId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        Department department = mock(Department.class);
        ServiceGroup group = mock(ServiceGroup.class);
        Room room = mock(Room.class);
        PerformingRoomResponse performingRoom = new PerformingRoomResponse(
                "XN-01", "Phòng xét nghiệm", "Tầng 2");
        MedicalServiceDetailResponse detail = new MedicalServiceDetailResponse(
                serviceId, "XN.001", "Tổng phân tích tế bào máu", MedicalServiceType.LAB,
                groupId, "Xét nghiệm máu", departmentId, "Khoa Xét nghiệm",
                BigDecimal.valueOf(120000), null, "Mô tả", ActivationStatus.ACTIVE, 0L,
                List.of(performingRoom));
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getDepartmentId()).thenReturn(departmentId);
        when(entity.getServiceGroupId()).thenReturn(groupId);
        when(departmentRepository.findById(departmentId)).thenReturn(java.util.Optional.of(department));
        when(serviceGroupRepository.findById(groupId)).thenReturn(java.util.Optional.of(group));
        when(department.getDepartmentName()).thenReturn("Khoa Xét nghiệm");
        when(group.getServiceGroupName()).thenReturn("Xét nghiệm máu");
        when(roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(groupId, ActivationStatus.ACTIVE))
                .thenReturn(List.of(room));
        when(serviceMapper.toPerformingRoom(room)).thenReturn(performingRoom);
        when(serviceMapper.toDetail(entity, "Xét nghiệm máu", "Khoa Xét nghiệm", List.of(performingRoom)))
                .thenReturn(detail);

        assertThat(service.getService(serviceId)).isEqualTo(detail);
    }

    @Test
    void reportsMissingService() {
        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getService(serviceId))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SERVICE_NOT_FOUND));
    }

    @Test
    void examinationServiceHasNoPerformingRooms() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        MedicalServiceDetailResponse detail = new MedicalServiceDetailResponse(
                serviceId, "KHAM", "Khám bệnh", MedicalServiceType.EXAMINATION,
                null, null, null, null, BigDecimal.valueOf(100000), BigDecimal.valueOf(30000),
                null, ActivationStatus.ACTIVE, 0L, List.of());
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(serviceMapper.toDetail(entity, null, null, List.of())).thenReturn(detail);

        assertThat(service.getService(serviceId).performingRooms()).isEmpty();
        verifyNoInteractions(roomRepository, departmentRepository, serviceGroupRepository);
    }
}
