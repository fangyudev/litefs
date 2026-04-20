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

package io.github.fangyudev.litefs.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * LiteFS 安全检查器
 * 
 * <p>在应用启动后执行安全检查，检测潜在的安全风险配置。</p>
 * 
 * <p>检查项目：</p>
 * <ul>
 *   <li>签名密钥是否使用默认值</li>
 *   <li>节点ID是否在分布式模式下使用默认值</li>
 *   <li>生产环境配置警告</li>
 * </ul>
 */
public class LiteFsSecurityChecker implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(LiteFsSecurityChecker.class);

    private static final String DEFAULT_SECRET_KEY = "default-secret-key";
    private static final String DEFAULT_NODE_ID = "node-1";
    private static final String PRODUCTION_ENV = "prod";
    private static final String DEV_ENV = "dev";

    private final LiteFsProperties properties;
    private final Environment environment;

    public LiteFsSecurityChecker(LiteFsProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        checkSecretKey(warnings, errors);
        checkNodeId(warnings, errors);
        checkReplicationConfig(warnings);
        checkEnvironment(warnings);

        printSecurityReport(warnings, errors);
    }

    /**
     * 检查签名密钥配置
     */
    private void checkSecretKey(List<String> warnings, List<String> errors) {
        if (!properties.getAccess().getSigned().isEnabled()) {
            return;
        }

        String secretKey = properties.getAccess().getSigned().getSecretKey();
        if (DEFAULT_SECRET_KEY.equals(secretKey)) {
            String env = getActiveEnvironment();
            if (isProductionEnvironment(env)) {
                errors.add(String.format(
                    "签名密钥使用默认值 '%s'，在生产环境中这是严重的安全风险！",
                    DEFAULT_SECRET_KEY
                ));
            } else {
                warnings.add(String.format(
                    "签名密钥使用默认值 '%s'，生产环境请修改配置项 litefs.access.signed.secret-key",
                    DEFAULT_SECRET_KEY
                ));
            }
        }

        if (secretKey != null && secretKey.length() < 16) {
            warnings.add("签名密钥长度小于16位，建议使用更长的密钥以提高安全性");
        }
    }

    /**
     * 检查节点ID配置
     */
    private void checkNodeId(List<String> warnings, List<String> errors) {
        String nodeId = properties.getNodeId();
        boolean isDistributedMode = properties.getRemote().isEnabled();

        if (DEFAULT_NODE_ID.equals(nodeId) && isDistributedMode) {
            warnings.add(String.format(
                "在分布式模式下使用默认节点ID '%s'，可能导致节点冲突。请为每个节点配置唯一的 litefs.node-id",
                DEFAULT_NODE_ID
            ));
        }
    }

    /**
     * 检查复制配置
     */
    private void checkReplicationConfig(List<String> warnings) {
        boolean replicationEnabled = properties.getReplication().isEnabled();
        boolean remoteEnabled = properties.getRemote().isEnabled();

        if (replicationEnabled && !remoteEnabled) {
            warnings.add("复制功能已启用但远程访问未启用，单机模式下复制功能不会生效。如需分布式复制，请设置 litefs.remote.enabled=true");
        }
    }

    /**
     * 检查环境配置
     */
    private void checkEnvironment(List<String> warnings) {
        String env = getActiveEnvironment();
        
        if (isProductionEnvironment(env)) {
            if ("h2".equals(properties.getStorage().getMetadataType())) {
                warnings.add("生产环境使用H2嵌入式数据库，建议切换到MySQL等生产级数据库");
            }
            
            if ("static".equals(properties.getRegistry().getType()) && 
                properties.getRegistry().getStaticConfig().getNodes().isEmpty()) {
                warnings.add("生产环境使用静态注册中心但未配置节点列表，分布式功能可能无法正常工作");
            }
        }
    }

    /**
     * 打印安全报告
     */
    private void printSecurityReport(List<String> warnings, List<String> errors) {
        if (errors.isEmpty() && warnings.isEmpty()) {
            log.info("LiteFS 安全检查通过，未发现配置风险");
            return;
        }

        StringBuilder sb = new StringBuilder("\n");
        sb.append("╔════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    LiteFS 安全检查报告                          ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");

        if (!errors.isEmpty()) {
            sb.append("║ [严重错误] 必须修复以下问题：                                   ║\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append(String.format("║   %d. %s%n", i + 1, truncate(errors.get(i), 56)));
            }
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("║ [警告] 建议修复以下问题：                                       ║\n");
            for (int i = 0; i < warnings.size(); i++) {
                sb.append(String.format("║   %d. %s%n", i + 1, truncate(warnings.get(i), 56)));
            }
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        }

        sb.append("║ 配置文档：https://github.com/fangyudev/litefs/wiki/Configuration ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════╝\n");

        if (!errors.isEmpty()) {
            log.error(sb.toString());
        } else {
            log.warn(sb.toString());
        }
    }

    /**
     * 获取当前激活的环境
     */
    private String getActiveEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return activeProfiles[0].toLowerCase();
        }
        return DEV_ENV;
    }

    /**
     * 判断是否为生产环境
     */
    private boolean isProductionEnvironment(String env) {
        return env != null && (
            env.contains(PRODUCTION_ENV) || 
            env.equals("production") ||
            env.equals("online")
        );
    }

    /**
     * 截断字符串以适应日志格式
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}