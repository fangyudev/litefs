# LiteFS Example

本模块包含 LiteFS 的使用示例，帮助你快速理解和集成 LiteFS。

## 示例列表

| 示例 | 说明 | 访问地址 |
|------|------|----------|
| [quickstart](src/main/resources/doc/quickstart/quickstart-guide.md) | 快速入门，演示核心功能 | http://localhost:8080/quickstart/ |
| 更多示例 | 敬请期待... | - |

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.6+

### 启动步骤

```bash
# 1. 编译整个项目
cd litefs
mvn clean install -DskipTests

# 2. 启动示例应用
cd litefs-example
mvn spring-boot:run

# 3. 访问示例页面
# 浏览器打开: http://localhost:8080/quickstart/
```

## 项目结构

```
litefs-example/
├── src/main/java/
│   └── io/github/fangyudev/litefs/example/
│       ├── LiteFsExampleApplication.java    # 启动类
│       └── quickstart/                      # 快速入门示例
│           └── QuickstartController.java    # 控制器
├── src/main/resources/
│   ├── application.yml                      # 配置文件
│   ├── doc/                                 # 文档目录
│   │   └── quickstart/
│   │       └── quickstart-guide.md          # 快速入门指南
│   └── static/                              # 静态资源
│       └── quickstart/
│           └── index.html                   # 示例页面
└── pom.xml
```

## 添加新示例

如果你需要添加新的示例，请按以下结构组织：

```
src/main/java/
└── io/github/fangyudev/litefs/example/
    └── your-example/                        # 新示例包名
        └── YourController.java

src/main/resources/
├── doc/
│   └── your-example/
│       └── your-guide.md                    # 示例文档
└── static/
    └── your-example/
        └── index.html                       # 示例页面
```

## 配置说明

编辑 `application.yml` 修改配置：

```yaml
litefs:
  enabled: true
  node-id: example-node-1
  storage:
    type: local
    path: ./data/files                       # 修改存储路径
  access:
    gateway:
      base-url: http://localhost:8080        # 修改网关地址
    signed:
      secret-key: your-secret-key            # 修改签名密钥
```

## 相关文档

- [LiteFS 设计文档](../../docs/DESIGN.md)
- [API 文档](../../docs/API.md)
