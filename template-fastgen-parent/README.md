# Template Fastgen Parent

声明即应用（fastgen）的聚合父模块，包含：

| 模块 | 说明 |
|------|------|

| **template-fastgen** | 核心：注解、APT 处理器、元数据模型、Freemarker 生成器 |
| **template-fastgen-example** | 示例：@Domain / @Page 使用示例 |
| **template-fastgen-maven-plugin** | Maven 插件：`fastgen:generate`，根据 snapshot 生成代码 |

构建顺序由父 POM 指定；在仓库根目录执行 `mvn -pl template-fastgen-parent install` 即可构建三者。
