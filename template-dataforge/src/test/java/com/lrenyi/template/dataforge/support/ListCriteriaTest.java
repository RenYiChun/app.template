package com.lrenyi.template.dataforge.support;

import java.util.List;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListCriteriaTest {
    
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_CREATE_TIME = "createTime";
    
    @Test
    void emptyReturnsEmptyFiltersAndSort() {
        ListCriteria c = ListCriteria.empty();
        assertThat(c.getFilters()).isEmpty();
        assertThat(c.getSortOrders()).isEmpty();
    }
    
    @Test
    void fromNullRequestReturnsEmpty() {
        EntityMeta meta = metaWithFields(FIELD_USERNAME, "status");
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
    void fromEmptyFiltersValidSort() {
        EntityMeta meta = metaWithFields(FIELD_USERNAME, FIELD_CREATE_TIME);
        SearchRequest req = new SearchRequest(List.of(), List.of(new SortOrder(FIELD_CREATE_TIME, "desc")), 0, 20);
        ListCriteria c = ListCriteria.from(req, meta);
        assertThat(c.getFilters()).isEmpty();
        assertThat(c.getSortOrders()).hasSize(1);
        assertThat(c.getSortOrders().getFirst().field()).isEqualTo(FIELD_CREATE_TIME);
        assertThat(c.getSortOrders().getFirst().dir()).isEqualTo("desc");
    }
    
    @Test
    void fromFiltersOnlyAllowedFields() {
        EntityMeta meta = metaWithFields(FIELD_USERNAME, "status");
        SearchRequest req = new SearchRequest(List.of(new FilterCondition(FIELD_USERNAME, Op.LIKE, "john"),
                                                      new FilterCondition("unknown", Op.EQ, "x")
        ), List.of(), 0, 20
        );
        ListCriteria c = ListCriteria.from(req, meta);
        assertThat(c.getFilters()).hasSize(1);
        assertThat(c.getFilters().get(0).field()).isEqualTo(FIELD_USERNAME);
        assertThat(c.getFilters().get(0).op()).isEqualTo(Op.LIKE);
    }
    
    @Test
    void fromNullEntityMetaReturnsEmpty() {
        SearchRequest req = new SearchRequest(List.of(new FilterCondition("a", Op.EQ, "b")), List.of(), 0, 20);
        ListCriteria c = ListCriteria.from(req, null);
        assertThat(c.getFilters()).isEmpty();
    }

    @Test
    void ofBuildsCriteriaWithFiltersAndSort() {
        List<FilterCondition> filters = List.of(new FilterCondition("name", Op.LIKE, "%x%"));
        List<SortOrder> sortOrders = List.of(new SortOrder("name", "asc"));
        ListCriteria c = ListCriteria.of(filters, sortOrders);
        assertThat(c.getFilters()).hasSize(1);
        assertThat(c.getFilters().get(0).field()).isEqualTo("name");
        assertThat(c.getFilters().get(0).op()).isEqualTo(Op.LIKE);
        assertThat(c.getSortOrders()).hasSize(1);
        assertThat(c.getSortOrders().get(0).field()).isEqualTo("name");
        assertThat(c.getSortOrders().get(0).dir()).isEqualTo("asc");
    }
}
