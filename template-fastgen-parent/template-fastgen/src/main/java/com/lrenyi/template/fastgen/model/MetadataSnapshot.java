package com.lrenyi.template.fastgen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 元数据快照，APT 输出与生成器输入的契约（.fastgen/snapshot 格式）。
 */
public class MetadataSnapshot {

    private String version = "1";
    private List<EntityMetadata> entities = new ArrayList<>();
    private List<PageMetadata> pages = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<EntityMetadata> getEntities() {
        return entities;
    }

    public void setEntities(List<EntityMetadata> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public List<PageMetadata> getPages() {
        return pages;
    }

    public void setPages(List<PageMetadata> pages) {
        this.pages = pages != null ? pages : new ArrayList<>();
    }
}
