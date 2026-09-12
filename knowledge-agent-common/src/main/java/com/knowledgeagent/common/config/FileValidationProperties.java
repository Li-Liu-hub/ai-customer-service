package com.knowledgeagent.common.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 保存知识文件校验所需的可调整限制。
 *
 * @param maxFileSizeBytes 单个文件允许的最大字节数
 * @param supportedExtensions 允许上传的文件扩展名
 * @param maxFilenameLength 文件名允许的最大字符数
 * @param textSampleSize 检测文本编码时读取的最大字节数
 * @param maxZipEntries 检测 DOCX 文件结构时允许扫描的最大 ZIP 条目数
 */
@ConfigurationProperties("app.file-validation")
public record FileValidationProperties(
    long maxFileSizeBytes,
    Set<String> supportedExtensions,
    int maxFilenameLength,
    int textSampleSize,
    int maxZipEntries) {}
