package com.lrenyi.template.core.config.redis;

import com.lrenyi.template.core.config.properties.CustomRedisProperties;
import com.lrenyi.template.core.util.StringUtils;
import jakarta.annotation.Resource;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class PrefixRedisSerializer extends StringRedisSerializer {
    @Resource
    private CustomRedisProperties customRedisProperties;
    
    @Override
    public byte[] serialize(String value) {
        if (value == null) {
            return new byte[0];
        }
        // 这里加上你需要加上的key前缀
        String realKey = customRedisProperties.getKeyPrefix() + ":" + value;
        return super.serialize(realKey);
    }
    
    @Override
    public String deserialize(byte[] bytes) {
        String originalKey = super.deserialize(bytes);
        if (StringUtils.hasLength(originalKey)) {
            return originalKey.replaceFirst(customRedisProperties.getKeyPrefix() + ":", "");
        } else {
            return new String(bytes);
        }
    }
}
