# Template Entity Platform

实体驱动业务平台内核：统一 Controller、Action 扩展、权限/日志/OpenAPI。与 template-fastgen 无依赖，独立使用。

## 约定

- **URL**：CRUD 使用 REST `GET/POST/PUT/DELETE /api/{entity}`、`/api/{entity}/{id}`；Action 使用 `POST /api/{entity}/{id}/{actionName}`。
- **主键**：默认 `Long`。
- **元数据**：注解 `@PlatformEntity`、`@EntityAction`，启动时扫描注册。
- **响应体**：成功与异常均使用 `template-core` 的 **Result&lt;T&gt;** 包装（`code`、`data`、`message`）；成功时 `data` 为实体或列表，删除成功时 `data` 为 `null`。若希望 `data` 为响应 DTO 而非实体，可在自定义 `EntityCrudService` 中做实体→DTO 转换后返回；请求 DTO 可由调用方按接口约定自行定义，或使用下方生成的 CRUD DTO。
- **CRUD DTO 自动生成**：编译期根据 `@PlatformEntity` 在实体包下生成 `dto` 子包中的 **CreateDTO**（创建请求）、**UpdateDTO**（更新请求）、**ResponseDTO**（响应 data）。可通过 `@PlatformEntity(generateDtos = false)` 关闭。**字段由谁组成**：默认取实体全部非 static/final 字段；创建/更新 DTO 自动排除 `id`；在字段上使用 **`@DtoExcludeFrom(DtoType.CREATE)`** / **`DtoType.UPDATE`** / **`DtoType.RESPONSE`** 可指定该字段不参与哪些 DTO（例如密码字段加 `@DtoExcludeFrom(DtoType.RESPONSE)` 避免响应带出密码）。Action 的请求/响应 DTO 在自定义 Action 时手写并在 `@EntityAction` 的 `requestType`/`responseType` 中指定。

## 配置

```yaml
app:
  entity-platform:
    api-prefix: /api
    scan-packages: com.example.your.package   # 扫描 @PlatformEntity 的包，多个用逗号分隔
    enabled: true
    permission-enabled: true
    default-allow-if-no-permission: true
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

## 覆盖增删改查（特殊业务逻辑）

当部分实体需要特殊逻辑（如创建前校验、删除软删、查询过滤）时，有两种方式：

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

继承 `DelegatingEntityCrudService`，只重写需要改的方法（或方法内再按实体分支），其余自动委托给默认实现。

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

## 使用

1. 在实体类上使用 `@PlatformEntity(pathSegment = "users", displayName = "用户")`；若用 JPA，实体类同时加 `@Entity`。
2. 实现 `EntityActionExecutor` 并在类上使用 `@EntityAction(entity = User.class, actionName = "resetPassword", ...)`，将该类注册为 Spring Bean（如 `@Component`）。
3. 启动应用后即可访问：
   - `GET/POST /api/users`、`GET/PUT/DELETE /api/users/{id}`（CRUD）
   - `POST /api/users/{id}/resetPassword`（Action）
   - `GET /api/docs`（简单 OpenAPI 风格文档）

## 可运行示例

```bash
mvn spring-boot:run -pl template-entity-platform-sample
```

然后可请求：`GET http://localhost:8080/api/users`、`POST http://localhost:8080/api/users`（body: `{"username":"a","email":"a@b.c"}`）、`POST http://localhost:8080/api/users/1/resetPassword`（body: `{"newPassword":"xxx"}`）、`GET http://localhost:8080/api/docs`。
