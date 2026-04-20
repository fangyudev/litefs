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

package io.github.fangyudev.litefs.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LiteFS 示例应用
 * 
 * <p>包含多个示例：</p>
 * <ul>
 *   <li>quickstart - 快速入门示例，演示核心功能</li>
 *   <li>更多示例敬请期待...</li>
 * </ul>
 * 
 * <p>启动后访问：</p>
 * <ul>
 *   <li>快速入门：http://localhost:8080/quickstart/</li>
 * </ul>
 */
@SpringBootApplication
public class LiteFsExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiteFsExampleApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("LiteFS 示例应用启动成功！");
        System.out.println("========================================");
        System.out.println("快速入门示例: http://localhost:8080/quickstart/index.html");
        System.out.println("========================================\n");
    }
}
