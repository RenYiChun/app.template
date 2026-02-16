# Template Entity Platform

实体驱动业务平台内核：统一 Controller、Action 扩展、权限/日志/OpenAPI。与 template-fastgen 无依赖，独立使用。

## 约定

- **URL**：CRUD 使用 REST `GET/POST/PUT/DELETE /api/{entity}`、`/api/{entity}/{id}`；Action 使用 `POST /api/{entity}/{id}/{actionName}`。
- **主键**：默认 `Long`。
- **元数据**：注解 `@PlatformEntity`、`@EntityAction`，启动时扫描注册。每个实体的默认 CRUD/导出接口可单独开关：`enableList`、`enableGet`、`enableCreate`、`enableUpdate`、`enableUpdateBatch`、`enableDelete`、`enableDeleteBatch`、`enableExport`（均默认 true），关闭某项则对应接口返回 404；OpenAPI 文档仅展示已启用的接口。
- **响应体**：成功与异常均使用 `template-core` 的 **Result&lt;T&gt;** 包装（`code`、`data`、`message`）；成功时 `data` 为实体或列表，删除成功时 `data` 为 `null`。若希望 `data` 为响应 DTO 而非实体，可在自定义 `EntityCrudService` 中做实体→DTO 转换后返回；请求 DTO 可由调用方按接口约定自行定义，或使用下方生成的 CRUD DTO。
- **CRUD DTO 自动生成**：编译期根据 `@PlatformEntity` 在实体包下生成 `dto` 子包中的 **CreateDTO**（创建请求）、**UpdateDTO**（更新请求）、**ResponseDTO**（响应 data）。可通过 `@PlatformEntity(generateDtos = false)` 关闭。**字段由谁组成**：默认取实体全部非 static/final 字段；创建/更新 DTO 自动排除 `id`；在字段上使用 **`@DtoExcludeFrom(DtoType.CREATE)`** / **`DtoType.UPDATE`** / **`DtoType.RESPONSE`** 可指定该字段不参与哪些 DTO（例如密码字段加 `@DtoExcludeFrom(DtoType.RESPONSE)` 避免响应带出密码）。Action 的请求/响应 DTO 在自定义 Action 时手写并在 `@EntityAction` 的 `requestType`/`responseType` 中指定。

## 配置

配置前缀为 `app.platform`（与代码中 `@ConfigurationProperties(prefix = "app.platform")` 一致）：

```yaml
app:
  platform:
    api-prefix: /api
    scan-packages: com.example.your.package   # 扫描 @PlatformEntity 的包，多个用逗号分隔
    enabled: true
    permission-enabled: true
    default-allow-if-no-permission: true
    docs-ui-enabled: true   # 是否启用内嵌 API 文档界面（Scalar），默认 true
    docs-ui-path: /docs     # 文档界面入口路径，默认 /docs
    # 可选：rbac-cache-ttl-minutes、rbac-init-permissions 等见下方 RBAC 小节
```

## 通用 JPA DAO（可选）

引入 `spring-boot-starter-data-jpa` 后，框架会**自动**使用基于 `EntityManager` 的通用 CRUD（`JpaEntityCrudService`），无需为每个实体写 Repository。实体类需为 JPA `@Entity`，主键为 `Long`。未引入 JPA 时仍使用内存实现（仅演示用）。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

若需自定义持久化逻辑，在应用中提供自己的 `EntityCrudService` Bean 即可覆盖。

## RBAC 动态权限（可选）

引入 JPA 后，平台可托管 RBAC 表并**按请求实时解析**当前用户权限，使角色/权限变更在下次请求（或缓存失效后）即生效，不依赖登录时 Token 快照。

1. **实体扫描**：RBAC 实体（Permission、Role、RolePermission、UserRole）已与 OperationLog 同处 `com.lrenyi.template.platform.domain`，若应用已扫描该包（如 `@EntityScan("com.lrenyi.template.platform.domain")`）则无需额外配置。
2. **表结构**：框架提供四张表：`sys_permission`（权限字符串，如 `users:create`）、`sys_role`、`sys_role_permission`、`sys_user_role`（userId 与认证用户标识一致，如 username）。需自行建表或通过 JPA 自动建表后初始化数据。
3. **权限标识**：`@PlatformEntity` 未显式指定 `permissionCreate`/`permissionRead`/`permissionUpdate`/`permissionDelete` 时，按 `pathSegment` 自动生成（如 `pathSegment:create`、`pathSegment:read`），与 `sys_permission.permission` 一致即可用于角色分配。
4. **权限自动初始化**：启用 JPA 且应用扫描了 platform.domain 时，启动后会根据已注册实体向 `sys_permission` 补齐缺失的权限记录（仅插入不存在的），便于直接为角色分配权限。可通过 `app.platform.rbac.init-permissions=false` 关闭。
5. **配置**：`app.platform.rbac-cache-ttl-minutes` 控制用户权限缓存分钟数，&lt;=0 表示不缓存；&gt;0 时需引入 Caffeine 依赖以启用缓存。

未使用平台托管 RBAC 时（未扫描 platform.domain 或不提供 `UserPermissionResolver`），仍使用基于 `Authentication.getAuthorities()` 的默认校验，行为不变。

## API 文档界面

- **OpenAPI JSON**：`GET ${api-prefix}/docs` 返回标准 OpenAPI 3.0 文档（JSON），供程序或文档 UI 使用。
- **内嵌文档页**：当 `app.platform.docs-ui-enabled` 为 true（默认）时，访问 `docs-ui-path`（默认 `/docs`）可打开内嵌的 **Scalar API Reference** 页面，自动加载上述 OpenAPI 文档，左侧按实体分组导航、右侧展示接口说明与试调。文档页与 `/api/docs` 受应用现有 Spring Security 配置约束（通常需认证后访问）。
- 关闭文档界面：配置 `app.platform.docs-ui-enabled: false`。自定义入口路径：`app.platform.docs-ui-path: /api-docs`。

## 覆盖增删改查（特殊业务逻辑）

当部分实体需要特殊逻辑（如创建前校验、删除软删、查询过滤）时，可采用以下方式之一。若希望**按实体拆分实现**（每个实体一个类，无需在方法里 if 分支），优先用**方式三**。

### 方式一：委托 + 按实体分支

1. 定义自己的 `EntityCrudService` 实现类，并加上 `@Primary`。
2. 注入默认实现：`@Qualifier("defaultEntityCrudService") EntityCrudService defaultService`。
3. 在 `list/get/create/update/delete` 中根据 `entityMeta.getPathSegment()` 或 `entityMeta.getEntityName()` 判断：需要特殊逻辑的走自己的代码，其余调用 `defaultService.list(entityMeta, pageable)` 等。

```java
@Primary
@Component
public class MyEntityCrudService implements EntityCrudService {
    private final EntityCrudService defaultService;

    public MyEntityCrudService(@Qualifier("defaultEntityCrudService") EntityCrudService defaultService) {
        this.defaultService = defaultService;
    }

    @Override
    public Object create(EntityMeta entityMeta, Object body) {
        if ("users".equals(entityMeta.getPathSegment())) {
            // 用户创建前校验、加密等
            return ...;
        }
        return defaultService.create(entityMeta, body);
    }
    // list/get/update/delete 同理：特殊实体自己实现，其余 defaultService.xxx(...)
}
```

### 方式二：继承 DelegatingEntityCrudService

继承 `DelegatingEntityCrudService`，只重写需要改的方法（或方法内再按实体分支），其余自动委托给默认实现。需在实现类上加 `@Primary`，且所有实体请求都会先进入该类。

```java
@Primary
@Component
public class MyEntityCrudService extends DelegatingEntityCrudService {
    public MyEntityCrudService(@Qualifier("defaultEntityCrudService") EntityCrudService defaultService) {
        super(defaultService);
    }

    @Override
    public Object create(EntityMeta entityMeta, Object body) {
        if ("orders".equals(entityMeta.getPathSegment())) {
            // 订单创建特殊逻辑
            return ...;
        }
        return defaultService.create(entityMeta, body);
    }
}
```

### 方式三：按实体覆盖（PathSegmentAwareCrudService）

为**不同实体**提供独立实现类，由框架按 `pathSegment` 路由到对应实现，无需在一个类里对多实体做 if-else。同一 pathSegment 仅应有一个实现。

1. 实现接口 `PathSegmentAwareCrudService`（继承 `EntityCrudService`），实现 `getPathSegment()` 返回该类负责的实体 pathSegment（如 `"users"`、`"orders"`）。
2. 通常继承 `DelegatingEntityCrudService` 并注入 `@Qualifier("defaultEntityCrudService") EntityCrudService`，只重写该实体需要的方法，其余自动走默认实现。
3. 将实现类注册为 Spring Bean（如 `@Component`），无需 `@Primary`。框架会收集所有 `PathSegmentAwareCrudService` 并注入 `EntityCrudServiceRouter`，未注册的实体仍走默认实现。

示例：仅对 `users` 自定义创建逻辑（如密码加密），其余方法用默认。

```java
@Component
public class UsersCrudService extends DelegatingEntityCrudService implements PathSegmentAwareCrudService {

    public UsersCrudService(@Qualifier("defaultEntityCrudService") EntityCrudService defaultService) {
        super(defaultService);
    }

    @Override
    public String getPathSegment() {
        return "users";
    }

    @Override
    public Object create(EntityMeta entityMeta, Object body) {
        // 仅 users 的创建逻辑，如密码加密后再调用默认
        // ...
        return defaultService.create(entityMeta, body);
    }
}
```

再为 `orders` 定义 `OrdersCrudService`，`getPathSegment()` 返回 `"orders"`，只重写需要的方法（如 `delete` 做软删）即可。

## 使用

1. 在实体类上使用 `@PlatformEntity(pathSegment = "users", displayName = "用户")`；若用 JPA，实体类同时加 `@Entity`。
2. 实现 `EntityActionExecutor` 并在类上使用 `@EntityAction(entity = User.class, actionName = "resetPassword", ...)`，将该类注册为 Spring Bean（如 `@Component`）。
3. 启动应用后即可访问：
   - `GET/POST /api/users`、`GET/PUT/DELETE /api/users/{id}`（CRUD）
   - `POST /api/users/{id}/resetPassword`（Action）
   - `GET /api/docs`（OpenAPI 3.0 JSON）、`GET /docs`（内嵌 Scalar 文档界面）

## 可运行示例

```bash
mvn spring-boot:run -pl template-entity-platform-sample
```

然后可请求：`GET http://localhost:8080/api/users`、`POST http://localhost:8080/api/users`（body: `{"username":"a","email":"a@b.c"}`）、`POST http://localhost:8080/api/users/1/resetPassword`（body: `{"newPassword":"xxx"}`）、`GET http://localhost:8080/api/docs`（OpenAPI JSON）；在浏览器打开 `http://localhost:8080/docs` 可查看内嵌 API 文档界面（Scalar）。
