package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.request.CreateMedicalServiceRequest;
import clinic.backend.dto.request.LabParameterSelectionRequest;
import clinic.backend.dto.request.UpdateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServicePriceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceStatusRequest;
import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PerformingRoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.LabParameter;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ServiceGroupType;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicalServiceMapper;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.LabParameterRepository;
import clinic.backend.repository.LabReferenceRangeRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.ServiceLabParameterRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalServiceServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final StaffPrincipal PRINCIPAL = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Mock MedicalServiceRepository serviceRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ServiceGroupRepository serviceGroupRepository;
    @Mock LabParameterRepository labParameterRepository;
    @Mock LabReferenceRangeRepository labReferenceRangeRepository;
    @Mock ServiceLabParameterRepository serviceLabParameterRepository;
    @Mock RoomRepository roomRepository;
    @Mock ParaclinicalOrderDetailRepository orderDetailRepository;
    @Mock AuditLogService auditLogService;
    @Mock MedicalServiceMapper serviceMapper;
    @Mock UuidV7Generator ids;
    @Mock Clock clock;
    @InjectMocks MedicalServiceServiceImpl service;

    @Test
    void createsActiveLaboratoryServiceWithOrderedParameters() {
        UUID serviceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        UUID parameterId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Department department = new Department(
                departmentId, "K-XN", "Khoa xet nghiem", DepartmentType.PARACLINICAL,
                null, true, NOW);
        ServiceGroup group = mock(ServiceGroup.class);
        LabParameter parameter = mock(LabParameter.class);
        when(group.isActive()).thenReturn(true);
        when(group.getType()).thenReturn(ServiceGroupType.LAB);
        when(departmentRepository.findById(departmentId)).thenReturn(java.util.Optional.of(department));
        when(serviceGroupRepository.findById(groupId)).thenReturn(java.util.Optional.of(group));
        when(roomRepository.existsByServiceGroupIdAndStatus(groupId, ActivationStatus.ACTIVE)).thenReturn(true);
        when(labParameterRepository.findAllByIdInAndActiveTrue(java.util.Set.of(parameterId)))
                .thenReturn(List.of(parameter));
        when(ids.next()).thenReturn(serviceId, mappingId);
        when(clock.instant()).thenReturn(NOW);
        CreateMedicalServiceRequest request = new CreateMedicalServiceRequest(
                " xn-100 ", " Xet nghiem mau ", MedicalServiceType.LAB,
                groupId, departmentId, BigDecimal.valueOf(120000), null, " Mo ta ",
                List.of(new LabParameterSelectionRequest(parameterId, 1)));

        var response = service.createService(PRINCIPAL, request);

        assertThat(response.serviceId()).isEqualTo(serviceId);
        ArgumentCaptor<MedicalService> captor = ArgumentCaptor.forClass(MedicalService.class);
        verify(serviceRepository).saveAndFlush(captor.capture());
        MedicalService created = captor.getValue();
        assertThat(created.getCode()).isEqualTo("XN-100");
        assertThat(created.getTenchidinh()).isEqualTo("Xet nghiem mau");
        assertThat(created.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(created.getInsurancePrice()).isNull();
        verify(serviceLabParameterRepository).saveAllAndFlush(any());
    }

    @Test
    void rejectsServiceWhoseGroupHasNoActiveRoom() {
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Department department = new Department(
                departmentId, "K-XN", "Khoa xet nghiem", DepartmentType.PARACLINICAL,
                null, true, NOW);
        ServiceGroup group = mock(ServiceGroup.class);
        when(group.isActive()).thenReturn(true);
        when(group.getType()).thenReturn(ServiceGroupType.LAB);
        when(departmentRepository.findById(departmentId)).thenReturn(java.util.Optional.of(department));
        when(serviceGroupRepository.findById(groupId)).thenReturn(java.util.Optional.of(group));
        when(roomRepository.existsByServiceGroupIdAndStatus(groupId, ActivationStatus.ACTIVE)).thenReturn(false);
        CreateMedicalServiceRequest request = new CreateMedicalServiceRequest(
                "XN-100", "Xet nghiem mau", MedicalServiceType.LAB,
                groupId, departmentId, BigDecimal.valueOf(120000), null, null,
                List.of(new LabParameterSelectionRequest(UUID.randomUUID(), 1)));

        assertThatThrownBy(() -> service.createService(PRINCIPAL, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SERVICE_GROUP_HAS_NO_ACTIVE_ROOM));
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateServiceCodeBeforeSaving() {
        when(serviceRepository.existsByCode("XN-100")).thenReturn(true);
        CreateMedicalServiceRequest request = new CreateMedicalServiceRequest(
                "xn-100", "Xet nghiem mau", MedicalServiceType.LAB,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, null, null, List.of());

        assertThatThrownBy(() -> service.createService(PRINCIPAL, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SERVICE_CODE_ALREADY_EXISTS));
    }

    @Test
    void rejectsServiceGroupWhoseTypeDoesNotMatchService() {
        UUID departmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Department department = new Department(
                departmentId, "K-XN", "Khoa xet nghiem", DepartmentType.PARACLINICAL,
                null, true, NOW);
        ServiceGroup group = mock(ServiceGroup.class);
        when(group.isActive()).thenReturn(true);
        when(group.getType()).thenReturn(ServiceGroupType.IMAGING);
        when(departmentRepository.findById(departmentId)).thenReturn(java.util.Optional.of(department));
        when(serviceGroupRepository.findById(groupId)).thenReturn(java.util.Optional.of(group));
        CreateMedicalServiceRequest request = new CreateMedicalServiceRequest(
                "XN-100", "Xet nghiem mau", MedicalServiceType.LAB,
                groupId, departmentId, BigDecimal.ONE, null, null, List.of());

        assertThatThrownBy(() -> service.createService(PRINCIPAL, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SERVICE_ASSIGNMENT_INVALID));
    }

    @Test
    void rejectsServiceGroupWhoseDepartmentDoesNotMatchService() {
        UUID departmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Department department = new Department(
                departmentId, "K-XN", "Khoa xet nghiem", DepartmentType.PARACLINICAL,
                null, true, NOW);
        ServiceGroup group = mock(ServiceGroup.class);
        when(group.isActive()).thenReturn(true);
        when(group.getType()).thenReturn(ServiceGroupType.LAB);
        when(group.getDepartmentId()).thenReturn(otherDepartmentId);
        when(departmentRepository.findById(departmentId)).thenReturn(java.util.Optional.of(department));
        when(serviceGroupRepository.findById(groupId)).thenReturn(java.util.Optional.of(group));
        CreateMedicalServiceRequest request = new CreateMedicalServiceRequest(
                "XN-101", "Xet nghiem mau", MedicalServiceType.LAB,
                groupId, departmentId, BigDecimal.ONE, null, null, List.of());

        assertThatThrownBy(() -> service.createService(PRINCIPAL, request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SERVICE_ASSIGNMENT_INVALID));
    }

    @Test
    void updatesOnlyEditableInformation() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        MedicalServiceDetailResponse detail = new MedicalServiceDetailResponse(
                serviceId, "XN-01", "New name", MedicalServiceType.LAB,
                null, null, null, null, BigDecimal.TEN, null,
                "New description", ActivationStatus.ACTIVE, 4L, List.of(), List.of());
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getTenchidinh()).thenReturn("Old name");
        when(entity.getVersion()).thenReturn(3L);
        when(clock.instant()).thenReturn(NOW);
        when(serviceMapper.toDetail(entity, null, null, List.of(), List.of())).thenReturn(detail);

        var response = service.updateService(
                PRINCIPAL, serviceId, new UpdateMedicalServiceRequest(" New name ", " New description ", 3L));

        assertThat(response).isEqualTo(detail);
        verify(entity).updateInformation("New name", "New description", NOW);
        verify(serviceRepository).saveAndFlush(entity);
    }

    @Test
    void unchangedUpdateDoesNotWriteEvenWithOlderVersion() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getTenchidinh()).thenReturn("Same name");
        when(entity.getDescription()).thenReturn(null);

        service.updateService(PRINCIPAL, serviceId, new UpdateMedicalServiceRequest(" Same name ", " ", 4L));

        verify(entity, never()).updateInformation(any(), any(), any());
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void changedUpdateRejectsStaleVersion() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getTenchidinh()).thenReturn("Old name");
        when(entity.getVersion()).thenReturn(5L);

        assertThatThrownBy(() -> service.updateService(
                PRINCIPAL, serviceId, new UpdateMedicalServiceRequest("New name", null, 4L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesChangedPrices() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getClinicPrice()).thenReturn(BigDecimal.valueOf(120000));
        when(entity.getVersion()).thenReturn(2L);
        when(clock.instant()).thenReturn(NOW);

        service.updatePrices(
                serviceId, new UpdateMedicalServicePriceRequest(BigDecimal.valueOf(150000), null, 2L));

        verify(entity).updatePrices(BigDecimal.valueOf(150000), null, NOW);
        verify(serviceRepository).saveAndFlush(entity);
    }

    @Test
    void unchangedPricesDoNotWrite() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getClinicPrice()).thenReturn(BigDecimal.valueOf(120000));
        when(entity.getInsurancePrice()).thenReturn(null);

        service.updatePrices(
                serviceId, new UpdateMedicalServicePriceRequest(new BigDecimal("120000.00"), null, 0L));

        verify(entity, never()).updatePrices(any(), any(), any());
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void changedPricesRejectStaleVersion() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getClinicPrice()).thenReturn(BigDecimal.valueOf(120000));
        when(entity.getVersion()).thenReturn(3L);

        assertThatThrownBy(() -> service.updatePrices(
                serviceId, new UpdateMedicalServicePriceRequest(BigDecimal.valueOf(150000), null, 2L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivationCheckReturnsUnfinishedOrderCount() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getType()).thenReturn(MedicalServiceType.LAB);
        when(entity.getStatus()).thenReturn(ActivationStatus.ACTIVE);
        when(orderDetailRepository.countByServiceIdAndStatusIn(any(), any())).thenReturn(3L);

        var response = service.checkDeactivation(serviceId);

        assertThat(response.currentStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(response.targetStatus()).isEqualTo(ActivationStatus.INACTIVE);
        assertThat(response.unfinishedOrderCount()).isEqualTo(3);
    }

    @Test
    void rejectsDeactivationOfLastActiveExaminationService() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getId()).thenReturn(serviceId);
        when(entity.getType()).thenReturn(MedicalServiceType.EXAMINATION);

        assertThatThrownBy(() -> service.checkDeactivation(serviceId))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LAST_ACTIVE_EXAMINATION_SERVICE));
        verifyNoInteractions(orderDetailRepository);
    }

    @Test
    void rejectsActivationWhenServiceGroupHasNoActiveRoom() {
        UUID serviceId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getType()).thenReturn(MedicalServiceType.LAB);
        when(entity.getServiceGroupId()).thenReturn(groupId);

        assertThatThrownBy(() -> service.checkActivation(serviceId))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SERVICE_GROUP_HAS_NO_ACTIVE_ROOM));
    }

    @Test
    void changesServiceStatus() {
        UUID serviceId = UUID.randomUUID();
        MedicalService entity = mock(MedicalService.class);
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(entity.getType()).thenReturn(MedicalServiceType.LAB);
        when(entity.getStatus()).thenReturn(ActivationStatus.ACTIVE);
        when(entity.getVersion()).thenReturn(2L);
        when(clock.instant()).thenReturn(NOW);

        service.updateStatus(
                serviceId, new UpdateMedicalServiceStatusRequest(ActivationStatus.INACTIVE, 2L));

        verify(entity).changeStatus(ActivationStatus.INACTIVE, NOW);
        verify(serviceRepository).saveAndFlush(entity);
    }

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
                List.of(performingRoom), List.of());
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
        when(serviceMapper.toDetail(
                entity, "Xét nghiệm máu", "Khoa Xét nghiệm", List.of(performingRoom), List.of()))
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
                null, ActivationStatus.ACTIVE, 0L, List.of(), List.of());
        when(serviceRepository.findById(serviceId)).thenReturn(java.util.Optional.of(entity));
        when(serviceMapper.toDetail(entity, null, null, List.of(), List.of())).thenReturn(detail);

        assertThat(service.getService(serviceId).performingRooms()).isEmpty();
        verifyNoInteractions(roomRepository, departmentRepository, serviceGroupRepository);
    }
}
