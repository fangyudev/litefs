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
 * MySQL数据库元数据存储
 * 使用MySQL存储文件元数据，适用于多机共享元数据场景
 * 
 * <p>特点：</p>
 * <ul>
 *   <li>支持多节点共享元数据</li>
 *   <li>支持表前缀，可与业务表共存于同一数据库</li>
 *   <li>支持 DataSource 注入或 JDBC 参数配置</li>
 * </ul>
 */
public class MySqlMetadataStore implements MetadataStore {

    private static final Logger log = LoggerFactory.getLogger(MySqlMetadataStore.class);

    /** 表名前缀 */
    private final String tablePrefix;
    
    /** 完整表名 */
    private final String tableName;

    /** JDBC连接URL（用于旧构造函数） */
    private final String jdbcUrl;

    /** 用户名（用于旧构造函数） */
    private final String username;

    /** 密码（用于旧构造函数） */
    private final String password;

    /** 数据源（用于新构造函数） */
    private final DataSource dataSource;

    /** 数据库连接（用于旧构造函数） */
    private Connection connection;

    /**
     * 构造函数（使用 DataSource，推荐）
     *
     * @param dataSource 数据源
     * @param tablePrefix 表名前缀
     */
    public MySqlMetadataStore(DataSource dataSource, String tablePrefix) {
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
    public MySqlMetadataStore(DataSource dataSource) {
        this(dataSource, "litefs_");
    }

    /**
     * 构造函数（使用 JDBC 参数，向后兼容）
     *
     * @param jdbcUrl JDBC连接URL
     * @param username 用户名
     * @param password 密码
     */
    public MySqlMetadataStore(String jdbcUrl, String username, String password) {
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
    public MySqlMetadataStore(String jdbcUrl, String username, String password, String tablePrefix) {
        this.dataSource = null;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix != null && !tablePrefix.isEmpty() ? tablePrefix : "litefs_";
        this.tableName = this.tablePrefix + "file_metadata";
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
            createTables();
            log.info("MySQL metadata store initialized with table: {}", tableName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize MySQL metadata store", e);
        }
    }

    /**
     * 创建表和索引
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, tableName);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }
        createIndexIfAbsent("idx_" + tablePrefix + "file_name", tableName, "file_name");
        createIndexIfAbsent("idx_" + tablePrefix + "status", tableName, "status");
        createIndexIfAbsent("idx_" + tablePrefix + "create_time", tableName, "create_time");
    }

    private void createIndexIfAbsent(String indexName, String tableName, String columnName) throws SQLException {
        String checkSql = "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(checkSql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, indexName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String createSql = "CREATE INDEX " + indexName + " ON " + tableName + "(" + columnName + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createSql);
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
                log.info("MySQL metadata store shutdown");
            } catch (SQLException e) {
                log.error("Failed to close MySQL connection", e);
            }
        }
    }

    /**
     * 保存文件元数据
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
     */
    @Override
    public void update(FileMetadata metadata) {
        String sql = String.format("""
            UPDATE %s SET 
            file_name = ?, content_type = ?, file_size = ?, checksum = ?, storage_node_id = ?,
            storage_path = ?, metadata = ?, status = ?, update_time = ?, expire_time = ?, thumbnail_id = ?, visibility = ?
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
     */
    @Override
    public List<FileMetadata> query(FileQuery query) {
        StringBuilder sql = new StringBuilder(String.format("SELECT * FROM %s WHERE 1=1", tableName));
        List<Object> params = new ArrayList<>();

        if (query != null) {
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
            sql.append(" LIMIT ?");
            params.add(query.getPageSize());
            sql.append(" OFFSET ?");
            params.add(query.getOffset());
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<FileMetadata> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapToMetadata(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query metadata", e);
        }
    }

    /**
     * 统计文件数量
     */
    @Override
    public long count(FileQuery query) {
        StringBuilder sql = new StringBuilder(String.format("SELECT COUNT(*) FROM %s WHERE 1=1", tableName));
        List<Object> params = new ArrayList<>();

        if (query != null) {
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
            throw new RuntimeException("Failed to check file existence: " + fileId, e);
        }
    }

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

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private Map<String, String> deserializeMetadata(String metadataStr) {
        Map<String, String> metadata = new HashMap<>();
        if (metadataStr == null || metadataStr.isEmpty()) {
            return metadata;
        }
        String[] pairs = metadataStr.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                metadata.put(keyValue[0], keyValue[1]);
            }
        }
        return metadata;
    }
}