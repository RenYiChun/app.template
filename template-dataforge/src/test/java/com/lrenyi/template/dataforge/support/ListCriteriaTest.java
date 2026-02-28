package com.lrenyi.template.dataforge.support;

import java.util.List;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListCriteriaTest {
    
    @Test
    void empty_returnsEmptyFiltersAndSort() {
        ListCriteria c = ListCriteria.empty();
        assertThat(c.getFilters()).isEmpty();
        assertThat(c.getSortOrders()).isEmpty();
    }
    
    @Test
    void from_nullRequest_returnsEmpty() {
        EntityMeta meta = metaWithFields("username", "status");
        ListCriteria c = ListCriteria.from(null, meta);
        assertThat(c.getFilters()).isEmpty();
        assertThat(c.getSortOrders()).isEmpty();
    }
    
    private static EntityMeta metaWithFields(String... names) {
        EntityMeta meta = new EntityMeta();
        for (String n : names) {
            FieldMeta fm = new FieldMeta();
            fm.setName(n);
            fm.setType("String");
            meta.getFields().add(fm);
        }
        return meta;
    }
    
    @Test
    void from_emptyFilters_validSort() {
        EntityMeta meta = metaWithFields("username", "createTime");
        SearchRequest req = new SearchRequest(List.of(), List.of(new SortOrder("createTime", "desc")), 0, 20);
        ListCriteria c = ListCriteria.from(req, meta);
        assertThat(c.getFilters()).isEmpty();
        assertThat(c.getSortOrders()).hasSize(1);
        assertThat(c.getSortOrders().get(0).field()).isEqualTo("createTime");
        assertThat(c.getSortOrders().get(0).dir()).isEqualTo("desc");
    }
    
    @Test
    void from_filtersOnlyAllowedFields() {
        EntityMeta meta = metaWithFields("username", "status");
        SearchRequest req = new SearchRequest(List.of(new FilterCondition("username", Op.LIKE, "john"),
                                                      new FilterCondition("unknown", Op.EQ, "x")
        ), List.of(), 0, 20
        );
        ListCriteria c = ListCriteria.from(req, meta);
        assertThat(c.getFilters()).hasSize(1);
        assertThat(c.getFilters().get(0).field()).isEqualTo("username");
        assertThat(c.getFilters().get(0).op()).isEqualTo(Op.LIKE);
    }
    
    @Test
    void from_nullEntityMeta_returnsEmpty() {
        SearchRequest req = new SearchRequest(List.of(new FilterCondition("a", Op.EQ, "b")), List.of(), 0, 20);
        ListCriteria c = ListCriteria.from(req, null);
        assertThat(c.getFilters()).isEmpty();
    }
}
