package com.lrenyi.template.service.pojo.web;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Slf4j
public class Range implements Serializable {
    private String field;
    private Object from;
    private Object to;
    private Boolean includeNull;
    private String typeName;
    
    public Range() {
    }
    
    public Range(String field) {
        this.field = field;
    }
    
    public Range(String field, Object from, Object to) {
        this.field = field;
        this.from = from;
        this.to = to;
    }
    
    public Range(String field, Object from, Object to, Boolean includeNull) {
        this.field = field;
        this.from = from;
        this.to = to;
        this.includeNull = includeNull;
    }
    
    public Object getFrom() {
        Class type = getType();
        if (type != null && this.from != null) {
            this.from = convertType(type, this.from);
        }
        return this.from;
    }
    
    public Class<?> getType() {
        Class<?> type = null;
        if (!StringUtils.hasLength(typeName)) {
            return null;
        }
        try {
            type = Class.forName(this.typeName);
        } catch (ClassNotFoundException var3) {
            log.warn(var3.getLocalizedMessage());
        }
        return type;
    }
    
    private static <T> T convertType(Class<T> cls, T obj) {
        String dateFormat = "yyyy-MM-dd HH:mm:ss";
        if (cls.equals(Date.class)) {
            if (obj instanceof Date) {
                return obj;
            }
            if (obj instanceof String && !obj.toString().isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
                try {
                    return (T) sdf.parse(obj.toString());
                } catch (ParseException var7) {
                    log.error(var7.getLocalizedMessage());
                    throw new RuntimeException(var7);
                }
            }
        } else if (cls.equals(LocalDateTime.class)) {
            if (obj instanceof LocalDateTime) {
                return obj;
            }
            if (obj instanceof String && !obj.toString().isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
                LocalDateTime parse1 = LocalDateTime.parse(obj.toString(), formatter);
                return (T) parse1;
            }
        } else if (obj instanceof Number) {
            Number integer;
            try {
                integer = Integer.valueOf(obj.toString());
            } catch (NumberFormatException var6) {
                log.error(var6.getLocalizedMessage());
                throw new RuntimeException(var6);
            }
            return (T) integer;
        }
        return null;
    }
    
    public Object getTo() {
        Class type = getType();
        if (type != null && to != null) {
            to = convertType(type, to);
        }
        return to;
    }
}
