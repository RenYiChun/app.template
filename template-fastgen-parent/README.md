# Template Fastgen Parent

声明即应用（fastgen）的聚合父模块，包含：

| 模块 | 说明 |
|------|------|
| **template-fastgen** | 核心库 + Maven 插件：注解、APT 处理器、Freemarker 生成器，以及 `fastgen:generate` 插件 |
| **template-fastgen-scaffold** | 脚手架：根据配置生成可运行项目 |

构建顺序由父 POM 指定；在仓库根目录执行 `mvn -pl template-fastgen-parent install` 即可构建。
