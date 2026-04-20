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

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用文件存储功能注解
 * 在Spring Boot启动类上添加此注解，启用LiteFS自动配置
 * 
 * <p>注意：由于使用了Spring Boot 3.x的自动配置机制，
 * 通常不需要显式添加此注解，只需确保litefs-spring-boot-starter在类路径中即可。</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableFileStorage
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(LiteFsAutoConfiguration.class)
public @interface EnableFileStorage {
}
