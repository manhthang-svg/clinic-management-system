package clinic.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByOrderByDepartmentNameAsc();

    Optional<Department> findByDepartmentCode(String departmentCode);
}
