package com.lrenyi.template.dataforge.backend.repository;

import java.util.Optional;
import com.lrenyi.template.dataforge.backend.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** 根部门：parentId 为 null，取 sortOrder 最小的一个 */
    Optional<Department> findFirstByParentIdIsNullOrderBySortOrderAsc();
}
