package com.knowledgeagent.common.util;

import com.knowledgeagent.common.config.FileStorageProperties;
import com.knowledgeagent.common.exception.error.KnowledgeError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/** 将用户上传的知识文件保存到项目配置的本地永久目录。 */
public final class FileStorageUtil {

  private FileStorageUtil() {}

  /**
   * 按照“文件ID.扩展名”的结构永久保存上传文件。
   *
   * @param file Spring接收到的上传文件
   * @param fileId 为本次上传生成的文件ID
   * @param properties 本地文件存储根目录配置
   * @return 写入文件表fileLocation字段的相对路径
   * @throws com.knowledgeagent.common.exception.BusinessException 文件参数、存储配置或磁盘写入发生错误时抛出
   */
  public static String saveKnowledgeFile(
      MultipartFile file, Long fileId, FileStorageProperties properties) {
    validateParameters(file, fileId, properties);

    String extension = FormatUtil.fileExtension(file.getOriginalFilename());
    if (extension.isEmpty()) {
      throw KnowledgeError.FILE_NAME_INVALID.exception();
    }

    Path rootDirectory = properties.rootDirectory().toAbsolutePath().normalize();
    Path relativePath = Path.of(fileId + "." + extension);
    Path targetPath = rootDirectory.resolve(relativePath).normalize();

    if (!targetPath.startsWith(rootDirectory)) {
      throw KnowledgeError.FILE_STORAGE_CONFIGURATION_INVALID.exception();
    }

    try {
      Files.createDirectories(targetPath.getParent());
      file.transferTo(targetPath);
      return relativePath.toString().replace('\\', '/');
    } catch (IOException | IllegalStateException exception) {
      deletePartialFile(targetPath, exception);
      throw KnowledgeError.FILE_SAVE_FAILED.exception(exception);
    }
  }

  /**
   * 删除一次失败入库已经保存的本地原始文件。
   *
   * @param fileLocation 文件表使用的相对存储路径
   * @param properties 本地文件存储根目录配置
   * @throws com.knowledgeagent.common.exception.BusinessException 路径越界或磁盘删除失败时抛出
   */
  public static void deleteKnowledgeFile(
      String fileLocation, FileStorageProperties properties) {
    if (fileLocation == null
        || fileLocation.isBlank()
        || properties == null
        || properties.rootDirectory() == null) {
      throw KnowledgeError.FILE_STORAGE_CONFIGURATION_INVALID.exception();
    }

    try {
      Path rootDirectory = properties.rootDirectory().toAbsolutePath().normalize();
      Path relativePath = Path.of(fileLocation);
      Path targetPath = rootDirectory.resolve(relativePath).normalize();
      if (relativePath.isAbsolute() || !targetPath.startsWith(rootDirectory)) {
        throw KnowledgeError.FILE_STORAGE_CONFIGURATION_INVALID.exception();
      }
      Files.deleteIfExists(targetPath);
    } catch (com.knowledgeagent.common.exception.BusinessException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw KnowledgeError.FILE_DELETE_FAILED.exception(exception);
    }
  }

  /**
   * 尝试删除保存过程中产生的不完整文件，并把清理错误附加到原始异常。
   *
   * @param targetPath 可能已经写入部分内容的目标文件
   * @param originalException 保存文件时捕获的原始异常
   */
  private static void deletePartialFile(Path targetPath, Throwable originalException) {
    try {
      Files.deleteIfExists(targetPath);
    } catch (IOException cleanupException) {
      originalException.addSuppressed(cleanupException);
    }
  }

  /**
   * 检查保存上传文件所需的参数是否完整有效。
   *
   * @param file Spring接收到的上传文件
   * @param fileId 为本次上传生成的文件ID
   * @param properties 本地文件存储根目录配置
   * @throws com.knowledgeagent.common.exception.BusinessException 任一必要参数无效时抛出
   */
  private static void validateParameters(
      MultipartFile file, Long fileId, FileStorageProperties properties) {
    if (file == null) {
      throw KnowledgeError.FILE_REQUIRED.exception();
    }
    if (file.isEmpty()) {
      throw KnowledgeError.FILE_EMPTY.exception();
    }
    if (fileId == null || fileId <= 0) {
      throw KnowledgeError.FILE_SAVE_FAILED.exception();
    }
    if (properties == null || properties.rootDirectory() == null) {
      throw KnowledgeError.FILE_STORAGE_CONFIGURATION_INVALID.exception();
    }
  }
}
