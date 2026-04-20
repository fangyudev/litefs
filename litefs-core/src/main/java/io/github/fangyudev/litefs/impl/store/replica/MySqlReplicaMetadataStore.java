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

package io.github.fangyudev.litefs.impl.store.replica;

import io.github.fangyudev.litefs.model.ReplicaInfo;
import io.github.fangyudev.litefs.spi.ReplicaMetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MySQL副本元数据存储
 * 使用MySQL持久化副本信息，适用于多机共享元数据场景
 * 
 * <p>支持 DataSource 注入或 JDBC 参数配置</p>
 * <p>支持表前缀，可与业务表共存于同一数据库</p>
 */
public class MySqlReplicaMetadataStore implements ReplicaMetadataStore {

    private static final Logger log = LoggerFactory.getLogger(MySqlReplicaMetadataStore.class);

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
    public MySqlReplicaMetadataStore(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix != null && !tablePrefix.isEmpty() ? tablePrefix : "litefs_";
        this.tableName = this.tablePrefix + "replica_metadata";
        this.jdbcUrl = null;
        this.username = null;
        this.password = null;
    }

    /**
     * 构造函数（使用 DataSource，默认表前缀）
     *
     * @param dataSource 数据源
     */
    public MySqlReplicaMetadataStore(DataSource dataSource) {
        this(dataSource, "litefs_");
    }

    /**
     * 构造函数（使用 JDBC 参数，向后兼容）
     *
     * @param jdbcUrl JDBC连接URL
     * @param username 用户名
     * @param password 密码
     */
    public MySqlReplicaMetadataStore(String jdbcUrl, String username, String password) {
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
    public MySqlReplicaMetadataStore(String jdbcUrl, String username, String password, String tablePrefix) {
        this.dataSource = null;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix != null && !tablePrefix.isEmpty() ? tablePrefix : "litefs_";
        this.tableName = this.tablePrefix + "replica_metadata";
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
            log.info("MySQL replica metadata store initialized with table: {}", tableName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize MySQL replica metadata store", e);
        }
    }

    /**
     * 创建表和索引
     */
    private void createTables() throws SQLException {
        String createTableSql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                file_id VARCHAR(64) NOT NULL,
                node_id VARCHAR(64) NOT NULL,
                status VARCHAR(16) NOT NULL,
                sync_time BIGINT NOT NULL,
                checksum VARCHAR(64),
                storage_path VARCHAR(512),
                PRIMARY KEY (file_id, node_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, tableName);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }
        createIndexIfAbsent("idx_" + tablePrefix + "replica_node_id", tableName, "node_id");
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

    @Override
    public void save(ReplicaInfo info) {
        if (info == null || info.getFileId() == null || info.getNodeId() == null) {
            return;
        }
        String sql = String.format("""
            INSERT INTO %s (file_id, node_id, status, sync_time, checksum, storage_path)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                sync_time = VALUES(sync_time),
                checksum = VALUES(checksum),
                storage_path = VALUES(storage_path)
            """, tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            fillStatement(stmt, info);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save replica metadata", e);
        }
    }

    @Override
    public void saveAll(List<ReplicaInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }
        String sql = String.format("""
            INSERT INTO %s (file_id, node_id, status, sync_time, checksum, storage_path)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                sync_time = VALUES(sync_time),
                checksum = VALUES(checksum),
                storage_path = VALUES(storage_path)
            """, tableName);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (ReplicaInfo info : infos) {
                if (info == null || info.getFileId() == null || info.getNodeId() == null) {
                    continue;
                }
                fillStatement(stmt, info);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save replica metadata batch", e);
        }
    }

    @Override
    public List<ReplicaInfo> listByFileId(String fileId) {
        if (fileId == null) {
            return Collections.emptyList();
        }
        String sql = String.format("SELECT * FROM %s WHERE file_id = ?", tableName);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<ReplicaInfo> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapToReplica(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list replica metadata by fileId", e);
        }
    }

    @Override
    public List<ReplicaInfo> listByNodeId(String nodeId) {
        if (nodeId == null) {
            return Collections.emptyList();
        }
        String sql = String.format("SELECT * FROM %s WHERE node_id = ?", tableName);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nodeId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<ReplicaInfo> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapToReplica(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list replica metadata by nodeId", e);
        }
    }

    @Override
    public ReplicaInfo get(String fileId, String nodeId) {
        if (fileId == null || nodeId == null) {
            return null;
        }
        String sql = String.format("SELECT * FROM %s WHERE file_id = ? AND node_id = ?", tableName);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            stmt.setString(2, nodeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapToReplica(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get replica metadata", e);
        }
    }

    @Override
    public void updateStatus(String fileId, String nodeId, ReplicaInfo.ReplicaStatus status, long syncTime, String checksum) {
        if (fileId == null || nodeId == null) {
            return;
        }
        String sql;
        if (checksum == null) {
            sql = String.format("UPDATE %s SET status = ?, sync_time = ? WHERE file_id = ? AND node_id = ?", tableName);
        } else {
            sql = String.format("UPDATE %s SET status = ?, sync_time = ?, checksum = ? WHERE file_id = ? AND node_id = ?", tableName);
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status != null ? status.name() : null);
            stmt.setLong(2, syncTime);
            if (checksum == null) {
                stmt.setString(3, fileId);
                stmt.setString(4, nodeId);
            } else {
                stmt.setString(3, checksum);
                stmt.setString(4, fileId);
                stmt.setString(5, nodeId);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update replica status", e);
        }
    }

    @Override
    public void deleteByFileId(String fileId) {
        if (fileId == null) {
            return;
        }
        String sql = String.format("DELETE FROM %s WHERE file_id = ?", tableName);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete replica metadata by fileId", e);
        }
    }

    @Override
    public void deleteByNodeId(String nodeId) {
        if (nodeId == null) {
            return;
        }
        String sql = String.format("DELETE FROM %s WHERE node_id = ?", tableName);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nodeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete replica metadata by nodeId", e);
        }
    }

    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                log.info("MySQL replica metadata store shutdown");
            } catch (SQLException e) {
                log.error("Failed to close MySQL connection", e);
            }
        }
    }

    private void fillStatement(PreparedStatement stmt, ReplicaInfo info) throws SQLException {
        stmt.setString(1, info.getFileId());
        stmt.setString(2, info.getNodeId());
        stmt.setString(3, info.getStatus() != null ? info.getStatus().name() : null);
        stmt.setLong(4, info.getSyncTime());
        stmt.setString(5, info.getChecksum());
        stmt.setString(6, info.getStoragePath());
    }

    private ReplicaInfo mapToReplica(ResultSet rs) throws SQLException {
        ReplicaInfo info = new ReplicaInfo();
        info.setFileId(rs.getString("file_id"));
        info.setNodeId(rs.getString("node_id"));
        String status = rs.getString("status");
        if (status != null) {
            info.setStatus(ReplicaInfo.ReplicaStatus.valueOf(status));
        }
        info.setSyncTime(rs.getLong("sync_time"));
        info.setChecksum(rs.getString("checksum"));
        info.setStoragePath(rs.getString("storage_path"));
        return info;
    }
}