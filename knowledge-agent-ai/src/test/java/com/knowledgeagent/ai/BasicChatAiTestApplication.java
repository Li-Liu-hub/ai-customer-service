package com.knowledgeagent.ai;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 仅用于本地对话验证的测试启动类：组件扫描只覆盖com.knowledgeagent.ai包，
 * 并排除依赖会话存储的多轮编排Bean，从而不需要数据库即可验证基本对话。
 * knowledge模块的KnowledgeService由测试类通过@MockitoBean提供桩实现。
 * 数据库相关自动配置在测试属性中通过spring.autoconfigure.exclude关闭。
 */
@SpringBootApplication
@ComponentScan(
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*[Cc]onversation.*"))
public class BasicChatAiTestApplication {}