package com.lrenyi.template.service.pojo.web;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Pageable;

@Getter
@Setter
public class PageImpl<T> extends org.springframework.data.domain.PageImpl<T> {
    private boolean hasNextPage;
    
    public PageImpl(List<T> content, Pageable pageable, long total) {
        super(content, pageable, total);
    }
}
