package com.lrenyi.oauth2.service.oauth2.redis;

public abstract class BaseRedisTokenSerializationStrategy implements
        RedisTokenSerializationStrategy {
    private static final byte[] EMPTY_ARRAY = new byte[0];
    
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (isEmpty(bytes)) {
            return null;
        }
        return deserializeInternal(bytes, clazz);
    }
    
    private static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }
    
    protected abstract <T> T deserializeInternal(byte[] bytes, Class<T> clazz);
    
    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return EMPTY_ARRAY;
        }
        return serializeInternal(object);
    }
    
    protected abstract byte[] serializeInternal(Object object);
}
