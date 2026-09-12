package com.knowledgeagent.common.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 保存知识库原始文件的本地存储配置。
 *
 * @param rootDirectory 所有知识库原始文件共同使用的本地根目录
 */
@ConfigurationProperties("app.file-storage")
public record FileStorageProperties(Path rootDirectory) {}
