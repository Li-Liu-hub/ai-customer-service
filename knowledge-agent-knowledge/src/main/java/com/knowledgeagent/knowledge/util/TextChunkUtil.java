package com.knowledgeagent.knowledge.util;

import com.knowledgeagent.common.config.TextChunkProperties;
import com.knowledgeagent.common.exception.error.KnowledgeError;
import com.knowledgeagent.knowledge.pojo.enums.DocumentType;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块工具：按文档业务类型采用不同切块策略。
 * QA_SET：一个"## "标题块切成一个分块，不跨问题合并，保证问答条目自包含；
 * RAW_DOC：先按标题/水平线切成结构块，再贪心合并相邻块到目标长度，超长块按句切分并保留重叠。
 */
public final class TextChunkUtil {

  /** 句子边界正则：中文句号/问号/叹号/分号、英文对应符号与换行。 */
  private static final String SENTENCE_BOUNDARY = "[。！？!?；;\n]";

  private TextChunkUtil() {}

  /**
   * 把清洗后的正文按文档类型切块。
   *
   * @param text 清洗后的正文
   * @param props 切块长度配置
   * @param type 文档业务类型（决定切块策略）
   * @return 分块文本列表（至少一块）
   * @throws com.knowledgeagent.common.exception.BusinessException 配置不合法或文本无法生成有效切片时抛出
   */
  public static List<String> chunk(String text, TextChunkProperties props, DocumentType type) {
    validate(props);
    if (text == null || text.isBlank()) {
      throw KnowledgeError.TEXT_CHUNK_FAILED.exception();
    }
    List<String> blocks = splitIntoBlocks(text);
    if (blocks.isEmpty()) {
      throw KnowledgeError.TEXT_CHUNK_FAILED.exception();
    }

    List<String> chunks = new ArrayList<>();
    if (type == DocumentType.QA_SET) {
      // 问答集：一个问题块对应一个分块，块内超长才切分
      for (String block : blocks) {
        appendChunk(chunks, block, props);
      }
    } else {
      // 原始文档：贪心合并相邻结构块到目标长度后成块
      chunks = mergeBlocks(blocks, props);
    }
    if (chunks.isEmpty()) {
      throw KnowledgeError.TEXT_CHUNK_FAILED.exception();
    }
    return chunks;
  }

  /**
   * 校验切块配置合法性。
   *
   * @param props 切块配置
   * @throws com.knowledgeagent.common.exception.BusinessException 配置不合法时抛出
   */
  private static void validate(TextChunkProperties props) {
    if (props == null
        || props.targetLength() <= 0
        || props.maxLength() <= 0
        || props.overlapLength() < 0
        || props.targetLength() > props.maxLength()) {
      throw KnowledgeError.CHUNK_CONFIGURATION_INVALID.exception();
    }
  }

  /**
   * 把正文按结构边界切成块：markdown标题行（#开头）开启新块，水平线（---）视为块分隔。
   *
   * @param text 清洗后的正文
   * @return 结构块列表（已去除空块）
   */
  private static List<String> splitIntoBlocks(String text) {
    List<String> blocks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : text.split("\n", -1)) {
      String trimmed = line.strip();
      boolean isHeading = trimmed.startsWith("#");
      boolean isDivider = trimmed.matches("-{3,}");
      if ((isHeading || isDivider) && current.length() > 0) {
        blocks.add(current.toString().strip());
        current.setLength(0);
      }
      if (!isDivider) {
        current.append(line).append('\n');
      }
    }
    if (current.length() > 0) {
      blocks.add(current.toString().strip());
    }
    blocks.removeIf(String::isBlank);
    return blocks;
  }

  /**
   * 贪心合并相邻结构块：累计长度不超过目标长度时并入当前块，否则结算成块。
   *
   * @param blocks 结构块列表
   * @param props 切块配置
   * @return 合并后的分块列表
   */
  private static List<String> mergeBlocks(List<String> blocks, TextChunkProperties props) {
    List<String> chunks = new ArrayList<>();
    StringBuilder acc = new StringBuilder();
    for (String block : blocks) {
      if (acc.length() > 0 && acc.length() + block.length() + 1 > props.targetLength()) {
        chunks.add(acc.toString().strip());
        acc.setLength(0);
      }
      acc.append(block).append('\n');
    }
    if (acc.length() > 0) {
      chunks.add(acc.toString().strip());
    }
    // 合并后仍超长的块按句切分
    List<String> result = new ArrayList<>();
    for (String chunk : chunks) {
      appendChunk(result, chunk, props);
    }
    return result;
  }

  /**
   * 把单块追加到结果列表：块长在最大长度内直接追加，超长则按句切分并保留重叠。
   *
   * @param chunks 分块结果列表
   * @param text 待追加块
   * @param props 切块配置
   */
  private static void appendChunk(List<String> chunks, String text, TextChunkProperties props) {
    if (text.length() <= props.maxLength()) {
      chunks.add(text);
      return;
    }
    List<String> sentences = splitSentences(text);
    StringBuilder current = new StringBuilder();
    for (String sentence : sentences) {
      if (current.length() > 0 && current.length() + sentence.length() > props.maxLength()) {
        chunks.add(current.toString().strip());
        // 下一块开头保留上一块尾部的重叠内容
        String tail = current.substring(Math.max(0, current.length() - props.overlapLength()));
        current.setLength(0);
        current.append(tail.strip());
      }
      current.append(sentence);
    }
    if (current.length() > 0 && !current.toString().isBlank()) {
      chunks.add(current.toString().strip());
    }
  }

  /**
   * 按句子边界切分文本，边界符号保留在句尾。
   *
   * @param text 待切分文本
   * @return 句子列表
   */
  private static List<String> splitSentences(String text) {
    List<String> sentences = new ArrayList<>();
    String[] parts = text.split(String.format("(?<=%s)", SENTENCE_BOUNDARY), -1);
    for (String part : parts) {
      if (!part.isBlank()) {
        sentences.add(part.strip());
      }
    }
    return sentences;
  }
}
