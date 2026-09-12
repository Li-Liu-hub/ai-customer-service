package com.knowledgeagent.common.util;

import com.knowledgeagent.common.exception.error.KnowledgeError;

/** 清理文件解析后文本中的无意义格式字符。 */
public final class TextCleaningUtil {

  private TextCleaningUtil() {}

  /**
   * 统一文本换行，删除控制字符和多余空行，同时保留原始语义内容。
   *
   * @param rawText 文件解析器提取的原始文本
   * @return 可以继续切片的清洗后文本
   * @throws com.knowledgeagent.common.exception.BusinessException 原始文本为空或清洗后没有内容时抛出
   */
  public static String clean(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      throw KnowledgeError.TEXT_EMPTY_AFTER_CLEANING.exception();
    }

    String normalizedLineBreaks = rawText.replace("\r\n", "\n").replace('\r', '\n');
    String visibleText = removeControlCharacters(normalizedLineBreaks);
    String cleanedText = cleanLines(visibleText).strip();

    if (cleanedText.isEmpty()) {
      throw KnowledgeError.TEXT_EMPTY_AFTER_CLEANING.exception();
    }
    return cleanedText;
  }

  /**
   * 删除 BOM 和不可见控制字符，保留换行符与制表符。
   *
   * @param text 已统一换行格式的文本
   * @return 删除控制字符后的文本
   */
  private static String removeControlCharacters(String text) {
    StringBuilder result = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == '\uFEFF') {
        continue;
      }
      if (Character.isISOControl(character) && character != '\n' && character != '\t') {
        continue;
      }
      result.append(character);
    }
    return result.toString();
  }

  /**
   * 清理每行首尾空白，并将任意数量的连续空行压缩为一个空行。
   *
   * @param text 已删除控制字符的文本
   * @return 清理行格式后的文本
   */
  private static String cleanLines(String text) {
    StringBuilder result = new StringBuilder(text.length());
    boolean previousLineWasBlank = false;

    for (String line : text.split("\n", -1)) {
      String cleanedLine = line.strip();
      if (cleanedLine.isEmpty()) {
        if (result.length() > 0 && !previousLineWasBlank) {
          result.append('\n');
          previousLineWasBlank = true;
        }
        continue;
      }

      if (result.length() > 0) {
        result.append('\n');
      }
      result.append(cleanedLine);
      previousLineWasBlank = false;
    }
    return result.toString();
  }
}
