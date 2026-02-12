package ${basePackage}.${entity.simpleName?lower_case};

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Generated from @Domain ${entity.simpleName}.
 * REST API for ${entity.displayName}.
 * Replace ${entity.simpleName}Service bean to customize logic (Strategy C).
 */
@RestController
@RequestMapping("/api/${entity.simpleName?lower_case}")
public class ${entity.simpleName}Controller {

    private final ${entity.simpleName}Service ${entity.simpleName?uncap_first}Service;

    public ${entity.simpleName}Controller(${entity.simpleName}Service ${entity.simpleName?uncap_first}Service) {
        this.${entity.simpleName?uncap_first}Service = ${entity.simpleName?uncap_first}Service;
    }

    /**
     * 分页查询${entity.displayName}列表。
     */
    @GetMapping
    public ResponseEntity<List<${entity.simpleName}>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        List<${entity.simpleName}> list = ${entity.simpleName?uncap_first}Service.listByPage(page, size);
        return ResponseEntity.ok(list);
    }

    /**
     * 根据 ID 查询${entity.displayName}。
     */
    @GetMapping("/{id}")
    public ResponseEntity<${entity.simpleName}> getById(@PathVariable Long id) {
        ${entity.simpleName} entity = ${entity.simpleName?uncap_first}Service.getById(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(entity);
    }

    /**
     * 新增${entity.displayName}。
     */
    @PostMapping
    public ResponseEntity<${entity.simpleName}> create(@RequestBody ${entity.simpleName} entity) {
        ${entity.simpleName} saved = ${entity.simpleName?uncap_first}Service.save(entity);
        return ResponseEntity.ok(saved);
    }

    /**
     * 更新${entity.displayName}。
     */
    @PutMapping("/{id}")
    public ResponseEntity<${entity.simpleName}> update(@PathVariable Long id, @RequestBody ${entity.simpleName} entity) {
        entity.setId(id);
        ${entity.simpleName} updated = ${entity.simpleName?uncap_first}Service.update(entity);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除${entity.displayName}。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ${entity.simpleName?uncap_first}Service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
