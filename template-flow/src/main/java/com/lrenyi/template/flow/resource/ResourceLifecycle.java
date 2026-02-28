package com.lrenyi.template.flow.resource;

/**
 * 资源生命周期管理接口
 * 用于统一管理资源的初始化和关闭
 */
public interface ResourceLifecycle {
    
    /**
     * 初始化资源
     *
     * @throws ResourceInitializationException 初始化失败时抛出
     */
    void initialize() throws ResourceInitializationException;
    
    /**
     * 关闭资源
     *
     * @throws ResourceShutdownException 关闭失败时抛出
     */
    void shutdown() throws ResourceShutdownException;
    
    /**
     * 检查资源是否已初始化
     *
     * @return true 如果已初始化，false 否则
     */
    boolean isInitialized();
    
    /**
     * 检查资源是否已关闭
     *
     * @return true 如果已关闭，false 否则
     */
    boolean isShutdown();
    
    /**
     * 获取资源名称，用于日志和错误报告
     *
     * @return 资源名称
     */
    String getResourceName();
}
