package com.knowledgeagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 评估报告输出工具。 */
public final class PathSupport {

  private PathSupport() {}

  /**
   * 把评估报告写入JSON文件（target/evaluation目录）。
   *
   * @param objectMapper JSON序列化
   * @param fileName 报告文件名
   * @param report 报告内容
   */
  public static void writeReport(ObjectMapper objectMapper, String fileName, Map<String, Object> report) {
    try {
      Path dir = Path.of("target", "evaluation");
      Files.createDirectories(dir);
      Path file = dir.resolve(fileName);
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
      System.out.println("报告已写入：" + file.toAbsolutePath());
    } catch (Exception e) {
      throw new IllegalStateException("写评估报告失败", e);
    }
  }

  /**
   * 判断文本是否匹配全部正则关键词（元素间AND）。
   *
   * @param text 待检文本
   * @param patterns 正则列表
   * @return 全部命中返回true
   */
  public static boolean matchesAll(String text, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return true;
    }
    for (String p : patterns) {
      if (!Pattern.compile(p).matcher(text).find()) {
        return false;
      }
    }
    return true;
  }

  /**
   * 构建可排序的 LinkedHashMap。
   *
   * @return 空的LinkedHashMap
   */
  public static <K, V> Map<K, V> linkedMap() {
    return new LinkedHashMap<>();
  }

  /**
   * 新建列表。
   *
   * @return 空ArrayList
   */
  public static <T> List<T> list() {
    return new ArrayList<>();
  }
}
