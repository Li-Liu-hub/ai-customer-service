package com.knowledgeagent.common.util;

import com.knowledgeagent.common.exception.error.KnowledgeError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.web.multipart.MultipartFile;

/** 将受支持的知识文件解析成普通文本。 */
public final class FileParserUtil {

  private FileParserUtil() {}

  /**
   * 根据文件扩展名选择解析方式并返回文件中的原始文本。
   *
   * @param file 已通过文件校验的知识文件
   * @return 从文件中提取的原始文本
   * @throws com.knowledgeagent.common.exception.BusinessException 文件类型不受支持或解析失败时抛出
   */
  public static String parse(MultipartFile file) {
    String extension = FormatUtil.fileExtension(file.getOriginalFilename());
    if (!extension.equals("pdf")
        && !extension.equals("docx")
        && !extension.equals("txt")
        && !extension.equals("md")) {
      throw KnowledgeError.FILE_TYPE_NOT_SUPPORTED.exception();
    }

    try {
      return switch (extension) {
        case "pdf" -> parsePdf(file);
        case "docx" -> parseDocx(file);
        case "txt", "md" -> parseText(file);
        default -> throw KnowledgeError.FILE_TYPE_NOT_SUPPORTED.exception();
      };
    } catch (IOException | RuntimeException exception) {
      throw KnowledgeError.FILE_PARSE_FAILED.exception();
    }
  }

  /**
   * 使用 PDFBox 提取 PDF 文件中的文本。
   *
   * @param file PDF 文件
   * @return PDF 中的原始文本
   * @throws IOException 读取或解析 PDF 失败时抛出
   */
  private static String parsePdf(MultipartFile file) throws IOException {
    try (PDDocument document = Loader.loadPDF(file.getBytes())) {
      return new PDFTextStripper().getText(document);
    }
  }

  /**
   * 使用 Apache POI 按文档顺序提取 DOCX 中的段落和表格文本。
   *
   * @param file DOCX 文件
   * @return DOCX 中的原始文本
   * @throws IOException 读取或解析 DOCX 失败时抛出
   */
  private static String parseDocx(MultipartFile file) throws IOException {
    StringBuilder text = new StringBuilder();
    try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
      for (IBodyElement element : document.getBodyElements()) {
        if (element instanceof XWPFParagraph paragraph) {
          appendLine(text, paragraph.getText());
        } else if (element instanceof XWPFTable table) {
          appendTable(text, table);
        }
      }
    }
    return text.toString();
  }

  /**
   * 按 UTF-8 编码读取 TXT 或 Markdown 文件。
   *
   * @param file 文本文件
   * @return 文件中的原始文本
   * @throws IOException 读取文本文件失败时抛出
   */
  private static String parseText(MultipartFile file) throws IOException {
    return new String(file.getBytes(), StandardCharsets.UTF_8);
  }

  /**
   * 将 DOCX 表格的单元格内容追加到解析结果。
   *
   * @param text 文本结果容器
   * @param table DOCX 表格
   */
  private static void appendTable(StringBuilder text, XWPFTable table) {
    for (XWPFTableRow row : table.getRows()) {
      for (int index = 0; index < row.getTableCells().size(); index++) {
        XWPFTableCell cell = row.getTableCells().get(index);
        if (index > 0) {
          text.append('\t');
        }
        text.append(cell.getText().trim());
      }
      text.append('\n');
    }
  }

  /**
   * 将非空段落追加到解析结果并补充换行符。
   *
   * @param text 文本结果容器
   * @param line 要追加的段落文本
   */
  private static void appendLine(StringBuilder text, String line) {
    if (line != null && !line.isBlank()) {
      text.append(line).append('\n');
    }
  }
}
