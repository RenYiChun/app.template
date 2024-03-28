package com.lrenyi.template.service.pojo.web;

import com.lrenyi.template.core.util.Result;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<List<T>> {
    private int count;
    private int currentPage;
    private int pageSize;
    private boolean hasNextPage;
    
    public PageResult(Collection<T> ds, int count) {
        super.setData(new ArrayList<>(ds));
        this.count = count;
    }
    
    public static <T> PageResult<T> getSuccess(Collection<T> ds, int count, int currentPage, int pageSize) {
        PageResult<T> pageResult = new PageResult<>(new ArrayList<>(ds), count);
        pageResult.setCurrentPage(currentPage);
        pageResult.setPageSize(pageSize);
        return pageResult;
    }
    
    public static <T> PageResult<T> getSuccess(Page<T> page) {
        PageResult<T> pageResult = new PageResult<>(page.getContent(), (int) page.getTotalElements());
        pageResult.setCurrentPage(page.getNumber());
        pageResult.setPageSize(page.getNumberOfElements());
        if (page instanceof PageImpl) {
            pageResult.setHasNextPage(((PageImpl<?>) page).isHasNextPage());
        }
        return pageResult;
    }
}
