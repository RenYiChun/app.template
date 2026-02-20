package com.lrenyi.template.dataforge.meta;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 实体扩展动作元数据。
 */
@Getter
@Setter
public class ActionMeta {

    private String actionName;
    private String entityName;
    /** HTTP 方法，默认 POST。 */
    private RequestMethod method = RequestMethod.POST;
    private Class<?> requestType;
    private Class<?> responseType;
    private String summary;
    private String description;
    private boolean requireId = true;
    private List<String> permissions = new ArrayList<>();

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions != null ? permissions : new ArrayList<>();
    }
}
