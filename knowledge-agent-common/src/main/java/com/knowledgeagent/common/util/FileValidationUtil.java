package com.knowledgeagent.common.util;

import com.knowledgeagent.common.config.FileValidationProperties;
import com.knowledgeagent.common.exception.error.KnowledgeError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;

/** 集中校验项目接收的知识文件。 */
public final class FileValidationUtil {

  private FileValidationUtil() {}

  /**
   * 校验知识文件是否存在、非空、未超过大小限制，并验证文件名和真实内容类型。
   *
   * @param file 用户上传的知识文件
   * @param properties 文件大小、文件名、扩展名和内容检测限制
   * @throws com.knowledgeagent.common.exception.BusinessException 文件不符合入库要求时抛出
   */
  public static void validateKnowledgeFile(
      MultipartFile file, FileValidationProperties properties) {
    if (file == null) {
      throw KnowledgeError.FILE_REQUIRED.exception();
    }
    if (file.isEmpty() || file.getSize() <= 0) {
      throw KnowledgeError.FILE_EMPTY.exception();
    }
    if (file.getSize() > properties.maxFileSizeBytes()) {
      throw KnowledgeError.FILE_TOO_LARGE.exception();
    }

    String filename = file.getOriginalFilename();
    validateFilename(filename, properties.maxFilenameLength());
    String extension = FormatUtil.fileExtension(filename);
    if (extension.isEmpty()) {
      throw KnowledgeError.FILE_NAME_INVALID.exception();
    }
    if (!properties.supportedExtensions().contains(extension)) {
      throw KnowledgeError.FILE_TYPE_NOT_SUPPORTED.exception();
    }

    try {
      if (!contentMatchesExtension(
          file, extension, properties.textSampleSize(), properties.maxZipEntries())) {
        throw KnowledgeError.FILE_CONTENT_TYPE_MISMATCH.exception();
      }
    } catch (IOException exception) {
      throw KnowledgeError.FILE_READ_FAILED.exception();
    }
  }

  /**
   * 校验文件名是否存在、长度合法且不包含路径字符。
   *
   * @param filename 用户上传的原始文件名
   * @param maxFilenameLength 文件名允许的最大字符数
   * @throws com.knowledgeagent.common.exception.BusinessException 文件名不合法时抛出
   */
  private static void validateFilename(String filename, int maxFilenameLength) {
    if (filename == null
        || filename.isBlank()
        || filename.length() > maxFilenameLength
        || filename.contains("..")
        || filename.contains("/")
        || filename.contains("\\")) {
      throw KnowledgeError.FILE_NAME_INVALID.exception();
    }
  }

  /**
   * 根据扩展名检查文件头、DOCX 结构或 UTF-8 文本编码。
   *
   * @param file 用户上传的文件
   * @param extension 已验证为受支持类型的扩展名
   * @param textSampleSize 检测文本编码时读取的最大字节数
   * @param maxZipEntries 检测 DOCX 结构时允许扫描的最大 ZIP 条目数
   * @return 文件真实内容与扩展名匹配时返回 {@code true}
   * @throws IOException 读取文件内容失败时抛出
   */
  private static boolean contentMatchesExtension(
      MultipartFile file, String extension, int textSampleSize, int maxZipEntries)
      throws IOException {
    return switch (extension) {
      case "pdf" -> hasPdfHeader(file);
      case "docx" -> hasDocxStructure(file, maxZipEntries);
      case "txt", "md" -> isUtf8Text(file, textSampleSize);
      default -> false;
    };
  }

  /**
   * 检查文件是否以 PDF 固定标识开头。
   *
   * @param file 用户上传的文件
   * @param maxZipEntries 允许扫描的最大 ZIP 条目数
   * @return 文件头为 {@code %PDF-} 时返回 {@code true}
   * @throws IOException 读取文件头失败时抛出
   */
  private static boolean hasPdfHeader(MultipartFile file) throws IOException {
    byte[] header = readPrefix(file, 5);
    return header.length == 5
        && header[0] == '%'
        && header[1] == 'P'
        && header[2] == 'D'
        && header[3] == 'F'
        && header[4] == '-';
  }

  /**
   * 检查 ZIP 文件中是否同时包含 DOCX 必需的内容类型和正文条目。
   *
   * @param file 用户上传的文件
   * @return 文件具有 DOCX 基础结构时返回 {@code true}
   * @throws IOException 读取压缩文件失败时抛出
   */
  private static boolean hasDocxStructure(MultipartFile file, int maxZipEntries)
      throws IOException {
    boolean contentTypesFound = false;
    boolean documentFound = false;
    int entryCount = 0;
    try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null && entryCount++ < maxZipEntries) {
        String name = entry.getName();
        contentTypesFound |= "[Content_Types].xml".equals(name);
        documentFound |= "word/document.xml".equals(name);
        if (contentTypesFound && documentFound) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 检查文本文件样本是否能够按照 UTF-8 严格解码；采样边界截断多字节序列时丢弃末尾字节重试。
   *
   * @param file 用户上传的文本文件
   * @param textSampleSize 检测编码时读取的最大字节数
   * @return 文本样本是合法 UTF-8 时返回 {@code true}
   * @throws IOException 读取文本样本失败时抛出
   */
  private static boolean isUtf8Text(MultipartFile file, int textSampleSize) throws IOException {
    byte[] sample = readPrefix(file, textSampleSize);
    // 采样边界可能把多字节UTF-8序列切成两半：依次丢弃末尾0-3字节重试，任一对齐即视为合法
    for (int drop = 0; drop <= 3 && sample.length - drop > 0; drop++) {
      if (decodeStrict(java.util.Arrays.copyOf(sample, sample.length - drop))) {
        return true;
      }
    }
    return false;
  }

  /**
   * 严格解码字节序列，任何非法或不可映射字节均视为失败。
   *
   * @param bytes 待解码字节
   * @return 全部字节构成合法 UTF-8 序列时返回 {@code true}
   */
  private static boolean decodeStrict(byte[] bytes) {
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes));
      return true;
    } catch (CharacterCodingException exception) {
      return false;
    }
  }

  /**
   * 读取文件开头指定数量的字节。
   *
   * @param file 用户上传的文件
   * @param maxBytes 最多读取的字节数
   * @return 实际读取到的文件头字节
   * @throws IOException 读取文件失败时抛出
   */
  private static byte[] readPrefix(MultipartFile file, int maxBytes) throws IOException {
    try (InputStream input = file.getInputStream()) {
      return input.readNBytes(maxBytes);
    }
  }
}
