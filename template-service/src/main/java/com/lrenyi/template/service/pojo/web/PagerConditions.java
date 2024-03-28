package com.lrenyi.template.service.pojo.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
@Slf4j
@NoArgsConstructor
public class PagerConditions<T> implements Pageable, Serializable {
    private String[] sortByField = new String[0];
    private String sortType;
    private int rows;
    private int page;
    private String pageType;
    private Object[] pageDataId;
    private String pageMode;
    private String dataType;
    private boolean isDefaultSort;
    private boolean fuzzyQuery;
    private List<Range> ranges;
    private Map<String, List<Object>> entity;
    private Map<String, Object> filter;
    private Map<String, List<Object>> notConditions;
    private Map<String, List<Object>> orConditions;
    
    public PagerConditions(int i, int pageSize) {
        this(i, pageSize, null);
    }
    
    public PagerConditions(int pageNumber, int pageSize, Sort sort) {
        this.page = pageNumber;
        this.rows = pageSize;
    }
    
    @Override
    public int getPageNumber() {
        return page;
    }
    
    @Override
    public int getPageSize() {
        return rows;
    }
    
    @Override
    public long getOffset() {
        return getPage() * rows;
    }
    
    @NonNull
    @Override
    public Sort getSort() {
        if (sortByField == null || sortByField.length == 0) {
            return Sort.unsorted();
        }
        if ("asc".equals(sortType)) {
            return Sort.by(getSorts("asc", sortByField));
        } else {
            return Sort.by(getSorts("desc", sortByField));
        }
    }
    
    private List<Sort.Order> getSorts(String type, String... sortByField) {
        List<Sort.Order> orders = new ArrayList<>();
        for (String prop : sortByField) {
            if ("asc".equals(type)) {
                orders.add(Sort.Order.asc(prop));
            } else {
                orders.add(Sort.Order.desc(prop));
            }
        }
        return orders;
    }
    
    @NonNull
    @Override
    public Pageable next() {
        return new PagerConditions<T>(this.getPageNumber() + 1, this.getPageSize());
    }
    
    @NonNull
    @Override
    public Pageable previousOrFirst() {
        return this.getPageNumber() == 0 ? this :
                new PagerConditions<T>(this.getPageNumber() - 1, this.getPageSize());
    }
    
    @NonNull
    @Override
    public Pageable first() {
        return new PagerConditions<T>(0, this.getPageSize());
    }
    
    @NonNull
    @Override
    public Pageable withPage(int pageNumber) {
        return new PagerConditions<T>(pageNumber, this.getPageSize(), this.getSort());
    }
    
    @Override
    public boolean hasPrevious() {
        return this.page > 0;
    }
    
    public long getPage() {
        //页号开始 默认为0
        String properties = System.getProperty("start.page.index");
        int start = 0;
        try {
            start = Integer.parseInt(properties);
        } catch (NumberFormatException e) {
            log.error(e.getLocalizedMessage());
        }
        return page - start;
    }
}
