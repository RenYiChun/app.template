package com.lrenyi.template.dataforge.jpa.domain;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.UUID;
import org.hibernate.HibernateException;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * 平台实体主键生成器，按主键类型分发：
 * <ul>
 *   <li>Long/Integer：使用数据库序列 dataforge_entity_seq</li>
 *   <li>UUID：随机生成</li>
 *   <li>String：由业务在持久化前赋值</li>
 * </ul>
 */
public class DataforgeIdGenerator implements IdentifierGenerator {
    
    private static final String DATAFORGE_SEQUENCE = "dataforge_entity_seq";
    
    private Type idType;
    private IdentifierGenerator sequenceDelegate;
    
    @Override
    public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) {
        this.idType = type;
        Class<?> javaType = type.getReturnedClass();
        
        if (javaType == Long.class || javaType == long.class || javaType == Integer.class || javaType == int.class) {
            SequenceStyleGenerator seq = new SequenceStyleGenerator();
            Properties seqParams = new Properties();
            seqParams.put(SequenceStyleGenerator.SEQUENCE_PARAM, DATAFORGE_SEQUENCE);
            seq.configure(type, seqParams, serviceRegistry);
            this.sequenceDelegate = seq;
        } else {
            this.sequenceDelegate = null;
        }
    }
    
    @Override
    public void registerExportables(Database database) {
        if (sequenceDelegate != null) {
            sequenceDelegate.registerExportables(database);
        }
    }
    
    @Override
    public void initialize(SqlStringGenerationContext context) {
        if (sequenceDelegate != null) {
            sequenceDelegate.initialize(context);
        }
    }
    
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        Class<?> javaType = idType.getReturnedClass();
        
        // 优先检查是否已赋值
        Object currentId = getCurrentId(object);
        if (currentId != null) {
            return currentId;
        }
        
        if (javaType == Long.class || javaType == long.class || javaType == Integer.class || javaType == int.class) {
            return sequenceDelegate.generate(session, object);
        }
        if (javaType == UUID.class) {
            return UUID.randomUUID();
        }
        
        throw new HibernateException("主键类型 " + javaType.getName() + " 必须由业务在持久化前赋值");
    }
    
    private static Object getCurrentId(Object entity) {
        Class<?> c = entity.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField("id");
                f.setAccessible(true);
                return f.get(entity);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new HibernateException("无法获取实体主键", e);
            }
        }
        return null;
    }
}
