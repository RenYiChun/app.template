package com.lrenyi.template.flow;

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试用数据项：用于双流配对或单流覆盖场景。
 * id 用作 joinKey，value/side 可选。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PairItem {
    private String id;
    private String value;
    private String side;
    
    public PairItem(String id) {
        this.id = id;
        this.value = null;
        this.side = null;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PairItem pairItem = (PairItem) o;
        return Objects.equals(id, pairItem.id);
    }
}
