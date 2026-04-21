# LiteFS

 轻量级分布式文件存储组件，为微服务架构而生

## 💡 缘起

早些年创业时，我曾为寻找一款**安全、可商用**的分布式文件系统而苦恼。市面上流行的方案要么存在许可证风险（如 GPL、AGPL），要么维护成本高昂，最终我决定自己动手实现——这就是 LiteFS 的由来。

时过境迁，这个问题依然困扰着众多初创企业和中小企业。考虑到很多企业技术积累有限，自研一套系统的门槛依然不低，所以我选择将 LiteFS 开源，希望为开源社区贡献一份力量。

LiteFS 遵循**最低依赖原则**，充分利用分布式系统中现有的 Redis、MySQL 等基础设施，不重复造轮子，代码轻量。同时采用 **SPI 架构**，将核心组件抽象分离，按需加载，方便扩展。

## 🚀 核心特点

- **最低依赖设计** - 核心包不到1MB，采用 SPI 架构，可根据需要选择组件，充分利用微服务中现有的 Redis、MySQL 等基础设施
- **可嵌入业务服务** - 作为业务服务的一部分运行，无需独立部署，开发更灵活，运维更简单
- **企业级协议** - 采用 Apache 2.0 协议，无商用风险，可安全用于企业生产环境
- **极简集成** - 提供 Spring Boot Starter，开箱即用，极容易嵌入业务服务中
- **纯 Java 实现** - 全 Java 代码，与 Java 生态深度集成，开发体验更佳
- **分布式架构** - 支持多节点集群，自动数据分片与负载均衡
- **高可用性** - 内置故障转移机制，节点故障自动恢复
- **灵活的存储引擎** - 支持本地文件系统存储，通过 HTTP 协议实现节点间通信
- **丰富的 API** - 提供完整的文件操作、元数据管理、URL 生成、缩略图、分块上传等企业级功能
- **安全可靠** - 完整的权限控制、数据校验和传输加密

## 🎯 主要功能

### 📤 文件操作
- **上传** - 简单上传 / 带选项上传（权限、过期时间、自定义元数据），选项可覆盖全局配置，同一系统内灵活适配不同场景
- **下载** - 支持故障转移，自动切换到健康副本
- **删除** - 物理删除，同时清理副本和缩略图
- **复制** - 生成新文件 ID，继承原文件属性
- **重命名** - 仅修改显示名，不影响存储路径

### 📋 元数据管理
- **获取/更新** - 文件基本信息及自定义元数据
- **查询** - 支持分页、多条件过滤（文件名、类型、大小、时间范围）

### 🔗 URL 与访问控制
- **永久 URL** - 直接访问
- **签名 URL** - 临时访问 URL，支持过期时间
- **下载 URL** - 触发浏览器下载
- **权限控制** - PUBLIC（公开）/ PRIVATE（私有）/ TEMPORARY（临时）

### 🖼️ 缩略图
- **自动生成** - 图片上传时异步生成
- **多尺寸** - small (200px) / medium (400px) / large (800px)
- **长边优先** - 保持宽高比

### 📦 分块上传
- **断点续传** - 支持暂停后继续
- **并行上传** - 多分片同时上传
- **自动清理** - 过期会话自动清理

### 🔄 分布式能力
- **副本策略** - NONE / MINIMAL(2份) / STANDARD(3份) / HIGH(5份) / ALL_NODES
- **一致性** - 同步复制（强一致）/ 异步复制（高性能）
- **故障转移** - 主节点故障自动切换到健康副本
- **负载均衡** - 轮询 / 容量优先节点选择
- **服务发现** - 静态配置 / Nacos 注册中心

### 🛠️ 存储后端（SPI 可扩展）
- **文件存储** - 本地文件系统
- **元数据** - H2 数据库 / MySQL
- **缓存** - 本地缓存 / Redis
- **消息队列** - 内存队列 / Redis

### ✅ 数据安全
- **MD5 校验** - 上传自动校验
- **权限隔离** - 私有文件需签名验证

## 📊 与主流存储方案对比

| 特性 | LiteFS | FastDFS | MinIO |
|------|--------|---------|-------|
| **核心定位** | 轻量级分布式文件存储（可嵌入业务服务） | 高性能文件存储（独立服务） | 对象存储服务（独立服务） |
| **许可证** | Apache 2.0（无商用风险） | GPL v3（有商用风险） | AGPL v3（强"传染性"） |
| | - 可自由商用<br>- 修改无需开源<br>- 只需保留版权声明 | - 可商用，但有严格限制<br>- 修改后必须开源<br>- 与其他软件组合可能受限 | - 最严格的开源协议<br>- 网络使用也需开源<br>- 修改或调用均需开源<br>- 国内外大厂均避免使用 |
| **部署方式** | 内嵌模式，作为业务服务的一部分 | 独立部署 Tracker/Storage 集群 | 独立部署对象存储服务 |
| **集成难度** | 极低（Spring Boot 自动配置） | 中等（需配置客户端） | 中等（需 API 对接） |
| **开发语言** | 纯 Java 实现 | C 语言开发 | Go 语言开发 |
| **学习曲线** | 平缓（Java 原生 API） | 较陡（自定义协议） | 中等（S3 协议） |
| **运维复杂度** | 极低（共享业务服务运维） | 高（需手动管理 Group/节点，无原生监控） | 中等（命令行运维，无企业级管理界面） |
| **资源占用** | 低（共享业务服务资源） | 中（独立集群） | 中（独立服务） |
| **开发灵活性** | 高（可嵌入业务服务，与业务代码紧密集成） | 低（独立服务，需跨服务调用） | 低（独立服务，需跨服务调用） |
| **数据一致性** | 强一致/最终一致（可配置） | 最终一致（异步复制） | 强一致（Erasure Coding） |
| **扩展性** | 模块化 SPI 设计，易于扩展 | 扩展复杂，需修改核心代码 | 扩展灵活，基于插件系统 |
| **适用场景** | 中小型微服务、快速集成项目、企业内部系统 | 大型文件存储系统 | 云原生对象存储、大数据场景 |
| **核心优势** | 轻量嵌入、协议安全、Java 生态集成 | 高性能、适合大文件 | 云原生、S3 兼容 |

## 🖼️ 效果预览

LiteFS 提供完整的示例项目，启动后即可通过 Web 界面体验文件上传、分片上传、文件管理和 URL 生成等功能：

![LiteFS 示例界面](docs/images/screencapture-example.png)

## 📖 快速开始

> **📝 注意：** LiteFS 正在准备发布到 Maven 中央仓库，在此之前可通过以下方式本地安装使用。

### 1. 本地安装（临时）

```bash
# 克隆代码
git clone https://gitee.com/fangyudev/litefs.git
cd litefs

# 编译安装到本地 Maven 仓库
mvn clean install -DskipTests
```

安装完成后，即可在项目中添加依赖。

### 2. 添加依赖

```xml
<!-- Spring Boot 项目 -->
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 非 Spring Boot 项目 -->
<dependency>
    <groupId>io.github.fangyudev</groupId>
    <artifactId>litefs-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 启用存储服务

```java
// Spring Boot 项目 - 无需注解，自动启用
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**说明：** LiteFS 使用 Spring Boot 3.x 的自动配置机制，只需在类路径中包含 `litefs-spring-boot-starter` 依赖，就会自动启用。

> 特殊情况下（如配置文件设置 `litefs.enabled=false` 后仍需启用），可手动添加 `@EnableFileStorage` 注解强制启用。

### 4. 简单使用

```java
@Autowired
private FileClient fileClient;

// 上传文件
FileUploadRequest request = FileUploadRequest.builder()
    .file(file)
    .visibility(FileVisibility.PUBLIC)
    .build();
String fileId = fileClient.upload(request);

// 获取文件URL
String fileUrl = fileClient.getFileUrl(fileId);
```

## 📚 详细文档

- **[完整文档中心](docs/README.md)** - 包含安装配置、API 指南、最佳实践等
- **[快速入门](docs/getting-started/QUICK_START.md)** - 5 分钟上手教程
- **[API 参考](docs/user-guide/API_GUIDE.md)** - 详细 API 使用说明
- **[架构设计](docs/developer-guide/ARCHITECTURE.md)** - 系统架构与设计理念

## 🔧 技术栈

- **核心语言**: Java 17+
- **构建工具**: Maven
- **Spring Boot**: 3.0+


## 📄 许可证

本项目采用 **Apache License 2.0** 开源协议，无商用风险，可自由用于商业项目。

## 🌟 为什么选择 LiteFS？

LiteFS 专为 **Java 微服务**场景设计，解决以下痛点：
- 不想引入独立文件存储服务，增加运维负担
- 需要安全合规的存储方案，避免许可证风险
- 希望代码轻量、依赖少、易于维护

---

<div align="center">
  <p>💡 轻量存储，无限可能</p>
  <p>Made with ❤️ by <a href="https://github.com/fangyudev">方郁 (fangyu)</a></p>
</div>