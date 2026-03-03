package com.lrenyi.template.core.json;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.template.web.json-processor-type", havingValue = "jackson", matchIfMissing = true
)
public class DefaultJacksonConfiguration {
    
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    
    // 定义支持多种格式的 LocalDateTime 解析器
    private static final DateTimeFormatter MULTI_FORMAT_DATETIME = new DateTimeFormatterBuilder()
            // 优先尝试标准格式
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
            // 支持纯日期格式（自动补全 00:00:00）
            .appendOptional(DateTimeFormatter.ofPattern(DATE_PATTERN))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            // 默认时间设置
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();
    
    // 定义支持多种格式的 LocalDate 解析器
    private static final DateTimeFormatter MULTI_FORMAT_DATE =
            new DateTimeFormatterBuilder().appendOptional(DateTimeFormatter.ofPattern(DATE_PATTERN))
                                          .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                                          .toFormatter();
    
    // 定义支持多种格式的 LocalTime 解析器
    private static final DateTimeFormatter MULTI_FORMAT_TIME =
            new DateTimeFormatterBuilder().appendOptional(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                          .appendOptional(DateTimeFormatter.ofPattern("HH:mm"))
                                          .toFormatter();
    
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer templateJsonCustomizer() {
        return builder -> {
            // 序列化 (Output): 保持统一的标准格式
            builder.serializers(new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            builder.serializers(new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            builder.serializers(new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
            
            // 反序列化 (Input): 使用兼容多种格式的解析器
            builder.deserializers(new LocalDateTimeDeserializer(MULTI_FORMAT_DATETIME));
            builder.deserializers(new LocalDateDeserializer(MULTI_FORMAT_DATE));
            builder.deserializers(new LocalTimeDeserializer(MULTI_FORMAT_TIME));
        };
    }
}
