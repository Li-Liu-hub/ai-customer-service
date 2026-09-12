package com.knowledgeagent.common.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 集中处理项目中的文本格式清理和标准化规则。 */
public final class FormatUtil {

  private FormatUtil() {}

  /**
   * 从文件名中提取不包含点号的小写扩展名。
   *
   * @param filename 原始文件名
   * @return 小写扩展名；文件名没有有效扩展名时返回空字符串
   */
  public static String fileExtension(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    if (dot <= 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /**
   * 清理大模型返回的会话标题并限制标题字符数量。
   *
   * @param rawTitle 大模型返回的原始标题
   * @param maxLength 标题允许保留的最大Unicode字符数量
   * @return 去除换行、首尾空白、引号和末尾标点后的标题
   */
  public static String normalizeConversationTitle(String rawTitle, int maxLength) {
    if (rawTitle == null || maxLength <= 0) {
      return "";
    }

    String title =
        rawTitle
            .replace('\r', ' ')
            .replace('\n', ' ')
            .strip()
            .replaceAll("^[\\\"'“”‘’]+|[\\\"'“”‘’。！？!?]+$", "")
            .strip();
    int characterCount = title.codePointCount(0, title.length());
    if (characterCount <= maxLength) {
      return title;
    }
    int end = title.offsetByCodePoints(0, maxLength);
    return title.substring(0, end);
  }

  /**
   * 把浮点向量数组序列化为PostgreSQL向量字面量（如"[0.123,0.456]"），供SQL侧CAST为vector类型。
   *
   * @param vector 浮点向量
   * @return 向量字面量字符串；向量为null或空时抛出非法参数异常
   */
  public static String toVectorLiteral(float[] vector) {
    if (vector == null || vector.length == 0) {
      throw new IllegalArgumentException("向量不能为空");
    }
    StringBuilder sb = new StringBuilder(vector.length * 10);
    sb.append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vector[i]);
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * 从查询文本中提取去重的二元词组（bigram），作为关键词检索的词法信号。
   * 只保留包含中文或字母数字的词组，标点与空白不参与组词。
   *
   * @param query 查询文本
   * @return 去重后的二元词组列表（保持出现顺序）；文本为空时返回空列表
   */
  public static List<String> extractKeywordGrams(String query) {
    List<String> grams = new ArrayList<>();
    if (query == null || query.isBlank()) {
      return grams;
    }
    String cleaned = query.replaceAll("[\\s\\p{Punct}]+", "");
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i + 2 <= cleaned.length(); i++) {
      String gram = cleaned.substring(i, i + 2);
      if (gram.matches("[\\p{IsHan}\\p{Alnum}]{2}")) {
        seen.add(gram);
      }
    }
    grams.addAll(seen);
    return grams;
  }

  /**
   * 把字符串列表序列化为PostgreSQL text[]数组字面量（如 '{"退货","货运"}'），
   * 供SQL侧CAST为text[]类型。列表元素中的双引号会被转义。
   *
   * @param values 字符串列表
   * @return 数组字面量字符串；列表为空时返回"{}"
   */
  public static String toPgTextArrayLiteral(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }
    sb.append('}');
    return sb.toString();
  }
}
