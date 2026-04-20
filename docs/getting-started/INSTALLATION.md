# LiteFS 安装和配置

> 本文档详细说明 LiteFS 的安装步骤与配置方式

## 前置条件

- JDK 17+
- Maven 3.6+
- Spring Boot 3.x 项目

## 安装步骤

### 1. 添加 Maven 依赖

```xml
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置文件

详见 [配置参考手册](../user-guide/CONFIGURATION.md) 获取完整配置说明。

### 3. 验证安装

启动应用后，访问文件上传接口验证功能是否正常。

## 下一步

- [第一个应用](FIRST_APP.md) - 创建完整的应用示例
- [配置参考手册](../user-guide/CONFIGURATION.md) - 所有配置项说明
