/*
 * Copyright 2026 方郁 (Fang Yu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.fangyudev.litefs.impl.store.metadata;

import io.github.fangyudev.litefs.model.FileMetadata;
import io.github.fangyudev.litefs.model.FileQuery;
import io.github.fangyudev.litefs.model.FileStatus;
import io.github.fangyudev.litefs.model.FileVisibility;
import io.github.fangyudev.litefs.spi.MetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H2数据库元数据存储实现。
 * 
 * <p><b>特点：</b></p>
 * <ul>
 *   <li>嵌入式数据库，无需额外部署</li>
 *   <li>支持持久化和内存模式</li>
 *   <li>支持AUTO_SERVER模式，允许多进程访问</li>
 * </ul>
 */
public class H2MetadataStore implements MetadataStore {

    private static final Logger log = LoggerFactory.getLogger(H2MetadataStore.class);

    private final String tablePrefix;
    private final String tableName;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final DataSource dataSource;
    private Connection connection;

    /**
     * 构造函数（使用 DataSource，推荐）
     * 
     * @param dataSource 数据源
     * @param tablePrefix 表名前缀
     */
    public H2MetadataStore(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix != null && !tablePrefix.isEmpty() ? tablePrefix : "litefs_";
        this.tableName = this.tablePrefix + "file_metadata";
        this.jdbcUrl = null;
        this.username = null;
        this.password = null;
    }

    /**
     * 构造函数（使用 DataSource，默认表前缀）
     * 
     * @param dataSource 数据源
     */
    public H2MetadataStore(DataSource dataSource) {
        this(dataSource, "litefs_");
    }

    /**
     * 构造函数（使用 JDBC 参数，向后兼容）
     * 
     * @param jdbcUrl JDBC连接URL
     * @param username 用户名
     * @param password 密码
     */
    public H2MetadataStore(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, "litefs_");
    }

    /**
     * 构造函数（使用 JDBC 参数和表前缀，向后兼容）
     * 
     * @param jdbcUrl JDBC连接URL
     * @param username 用户名
     * @param password 密码
     * @param tablePrefix 表名前缀
     */
    public H2MetadataStore(String jdbcUrl, String username, String password, String tablePrefix) {
        this.dataSource = null;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix != null && !tablePrefix.isEmpty() ? tablePrefix : "litefs_";
        this.tableName = this.tablePrefix + "file_metadata";
    }

    /**
     * 构造函数（使用默认用户名密码）
     * 
     * @param jdbcUrl JDBC连接URL
     */
    public H2MetadataStore(String jdbcUrl) {
        this(jdbcUrl, "sa", "", "litefs_");
    }

    /**
     * 初始化数据库连接和表结构
     */
    @Override
    public void init() {
        try {
            if (dataSource != null) {
                connection = dataSource.getConnection();
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
            }
            connection.setAutoCommit(true);
            createTables();
            log.info("H2 metadata store initialized with table: {}", tableName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize H2 metadata store", e);
        }
    }

    /**
     * 创建表和索引
     * 
     * @throws SQLException SQL异常
     */
    private void createTables() throws SQLException {
        String createTableSql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(64) PRIMARY KEY,
                file_name VARCHAR(255) NOT NULL,
                content_type VARCHAR(128),
                file_size BIGINT NOT NULL,
                checksum VARCHAR(64),
                storage_node_id VARCHAR(64),
                storage_path VARCHAR(512),
                metadata VARCHAR(4096),
                status VARCHAR(16) NOT NULL,
                create_time BIGINT NOT NULL,
                update_time BIGINT NOT NULL,
                expire_time BIGINT,
                thumbnail_id VARCHAR(64),
                visibility VARCHAR(16) DEFAULT 'PRIVATE'
            )
            """, tableName);

        String createIndexSql = String.format("""
            CREATE INDEX IF NOT EXISTS idx_%sfile_name ON %s(file_name);
            CREATE INDEX IF NOT EXISTS idx_%sstatus ON %s(status);
            CREATE INDEX IF NOT EXISTS idx_%screate_time ON %s(create_time)
            """, 
            tablePrefix, tableName,
            tablePrefix, tableName,
            tablePrefix, tableName);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
            for (String indexSql : createIndexSql.split(";")) {
                if (!indexSql.trim().isEmpty()) {
                    stmt.execute(indexSql.trim());
                }
            }
        }
    }

    /**
     * 关闭数据库连接
     */
    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                log.info("H2 metadata store shutdown");
            } catch (SQLException e) {
                log.error("Failed to close H2 connection", e);
            }
        }
    }

    /**
     * 保存文件元数据
     * 
     * @param metadata 文件元数据
     */
    @Override
    public void save(FileMetadata metadata) {
        String sql = String.format("""
            INSERT INTO %s 
            (id, file_name, content_type, file_size, checksum, storage_node_id, storage_path, 
             metadata, status, create_time, update_time, expire_time, thumbnail_id, visibility)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, metadata.getId());
            stmt.setString(2, metadata.getFileName());
            stmt.setString(3, metadata.getContentType());
            stmt.setLong(4, metadata.getFileSize());
            stmt.setString(5, metadata.getChecksum());
            stmt.setString(6, metadata.getStorageNodeId());
            stmt.setString(7, metadata.getStoragePath());
            stmt.setString(8, serializeMetadata(metadata.getMetadata()));
            stmt.setString(9, metadata.getStatus().name());
            stmt.setLong(10, metadata.getCreateTime());
            stmt.setLong(11, metadata.getUpdateTime());
            if (metadata.getExpireTime() != null) {
                stmt.setLong(12, metadata.getExpireTime());
            } else {
                stmt.setNull(12, Types.BIGINT);
            }
            stmt.setString(13, metadata.getThumbnailId());
            stmt.setString(14, metadata.getVisibility() != null ? metadata.getVisibility().name() : "PRIVATE");

            stmt.executeUpdate();
            log.debug("Saved metadata: {}", metadata.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save metadata: " + metadata.getId(), e);
        }
    }

    /**
     * 获取文件元数据
     * 
     * @param fileId 文件ID
     * @return 文件元数据，不存在返回null
     */
    @Override
    public FileMetadata get(String fileId) {
        String sql = String.format("SELECT * FROM %s WHERE id = ?", tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapToMetadata(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get metadata: " + fileId, e);
        }
    }

    /**
     * 删除文件元数据
     * 
     * @param fileId 文件ID
     */
    @Override
    public void delete(String fileId) {
        String sql = String.format("DELETE FROM %s WHERE id = ?", tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            stmt.executeUpdate();
            log.debug("Deleted metadata: {}", fileId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete metadata: " + fileId, e);
        }
    }

    /**
     * 更新文件元数据
     * 
     * @param metadata 文件元数据
     */
    @Override
    public void update(FileMetadata metadata) {
        String sql = String.format("""
            UPDATE %s SET 
            file_name = ?, content_type = ?, file_size = ?, checksum = ?, 
            storage_node_id = ?, storage_path = ?, metadata = ?, status = ?, 
            update_time = ?, expire_time = ?, thumbnail_id = ?, visibility = ?
            WHERE id = ?
            """, tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, metadata.getFileName());
            stmt.setString(2, metadata.getContentType());
            stmt.setLong(3, metadata.getFileSize());
            stmt.setString(4, metadata.getChecksum());
            stmt.setString(5, metadata.getStorageNodeId());
            stmt.setString(6, metadata.getStoragePath());
            stmt.setString(7, serializeMetadata(metadata.getMetadata()));
            stmt.setString(8, metadata.getStatus().name());
            stmt.setLong(9, metadata.getUpdateTime());
            if (metadata.getExpireTime() != null) {
                stmt.setLong(10, metadata.getExpireTime());
            } else {
                stmt.setNull(10, Types.BIGINT);
            }
            stmt.setString(11, metadata.getThumbnailId());
            stmt.setString(12, metadata.getVisibility() != null ? metadata.getVisibility().name() : "PRIVATE");
            stmt.setString(13, metadata.getId());

            stmt.executeUpdate();
            log.debug("Updated metadata: {}", metadata.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update metadata: " + metadata.getId(), e);
        }
    }

    /**
     * 查询文件列表
     * 支持多条件组合查询和分页
     * 
     * @param query 查询条件
     * @return 文件元数据列表
     */
    @Override
    public List<FileMetadata> query(FileQuery query) {
        StringBuilder sql = new StringBuilder(String.format("SELECT * FROM %s WHERE 1=1", tableName));
        List<Object> params = new ArrayList<>();

        // 构建查询条件
        if (query.getStatus() != null) {
            sql.append(" AND status = ?");
            params.add(query.getStatus().name());
        }
        if (query.getFileName() != null && !query.getFileName().isEmpty()) {
            sql.append(" AND file_name LIKE ?");
            params.add("%" + query.getFileName() + "%");
        }
        if (query.getContentType() != null && !query.getContentType().isEmpty()) {
            sql.append(" AND content_type = ?");
            params.add(query.getContentType());
        }
        if (query.getMinSize() != null) {
            sql.append(" AND file_size >= ?");
            params.add(query.getMinSize());
        }
        if (query.getMaxSize() != null) {
            sql.append(" AND file_size <= ?");
            params.add(query.getMaxSize());
        }
        if (query.getStartTime() != null) {
            sql.append(" AND create_time >= ?");
            params.add(query.getStartTime());
        }
        if (query.getEndTime() != null) {
            sql.append(" AND create_time <= ?");
            params.add(query.getEndTime());
        }

        // 添加分页
        sql.append(" ORDER BY create_time DESC LIMIT ? OFFSET ?");
        params.add(query.getPageSize());
        params.add(query.getOffset());

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            List<FileMetadata> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapToMetadata(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query metadata", e);
        }
    }

    /**
     * 统计文件数量
     * 
     * @param query 查询条件
     * @return 文件数量
     */
    @Override
    public long count(FileQuery query) {
        StringBuilder sql = new StringBuilder(String.format("SELECT COUNT(*) FROM %s WHERE 1=1", tableName));
        List<Object> params = new ArrayList<>();

        // 构建查询条件（与query方法相同）
        if (query.getStatus() != null) {
            sql.append(" AND status = ?");
            params.add(query.getStatus().name());
        }
        if (query.getFileName() != null && !query.getFileName().isEmpty()) {
            sql.append(" AND file_name LIKE ?");
            params.add("%" + query.getFileName() + "%");
        }
        if (query.getContentType() != null && !query.getContentType().isEmpty()) {
            sql.append(" AND content_type = ?");
            params.add(query.getContentType());
        }
        if (query.getMinSize() != null) {
            sql.append(" AND file_size >= ?");
            params.add(query.getMinSize());
        }
        if (query.getMaxSize() != null) {
            sql.append(" AND file_size <= ?");
            params.add(query.getMaxSize());
        }
        if (query.getStartTime() != null) {
            sql.append(" AND create_time >= ?");
            params.add(query.getStartTime());
        }
        if (query.getEndTime() != null) {
            sql.append(" AND create_time <= ?");
            params.add(query.getEndTime());
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count metadata", e);
        }
    }

    /**
     * 检查文件是否存在
     * 
     * @param fileId 文件ID
     * @return 存在返回true
     */
    @Override
    public boolean exists(String fileId) {
        String sql = String.format("SELECT 1 FROM %s WHERE id = ?", tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence: " + fileId, e);
        }
    }

    /**
     * 将ResultSet映射为FileMetadata对象
     * 
     * @param rs ResultSet
     * @return FileMetadata对象
     * @throws SQLException SQL异常
     */
    private FileMetadata mapToMetadata(ResultSet rs) throws SQLException {
        FileMetadata metadata = new FileMetadata();
        metadata.setId(rs.getString("id"));
        metadata.setFileName(rs.getString("file_name"));
        metadata.setContentType(rs.getString("content_type"));
        metadata.setFileSize(rs.getLong("file_size"));
        metadata.setChecksum(rs.getString("checksum"));
        metadata.setStorageNodeId(rs.getString("storage_node_id"));
        metadata.setStoragePath(rs.getString("storage_path"));
        metadata.setMetadata(deserializeMetadata(rs.getString("metadata")));
        metadata.setStatus(FileStatus.valueOf(rs.getString("status")));
        metadata.setCreateTime(rs.getLong("create_time"));
        metadata.setUpdateTime(rs.getLong("update_time"));
        long expireTime = rs.getLong("expire_time");
        if (!rs.wasNull()) {
            metadata.setExpireTime(expireTime);
        }
        metadata.setThumbnailId(rs.getString("thumbnail_id"));
        String visibilityStr = rs.getString("visibility");
        if (visibilityStr != null && !visibilityStr.isEmpty()) {
            metadata.setVisibility(FileVisibility.valueOf(visibilityStr));
        } else {
            metadata.setVisibility(FileVisibility.PRIVATE);
        }
        return metadata;
    }

    /**
     * 序列化元数据Map为字符串
     * 使用key=value&key2=value2格式
     * 
     * @param metadata 元数据Map
     * @return 序列化字符串
     */
    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 反序列化字符串为元数据Map
     * 
     * @param str 序列化字符串
     * @return 元数据Map
     */
    private Map<String, String> deserializeMetadata(String str) {
        Map<String, String> metadata = new HashMap<>();
        if (str == null || str.isEmpty()) {
            return metadata;
        }
        String[] pairs = str.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = idx < pair.length() - 1 ? pair.substring(idx + 1) : "";
                metadata.put(key, value);
            }
        }
        return metadata;
    }
}
