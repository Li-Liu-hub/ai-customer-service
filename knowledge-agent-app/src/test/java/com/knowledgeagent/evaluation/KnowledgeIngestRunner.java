package com.knowledgeagent.evaluation;

import com.knowledgeagent.knowledge.pojo.vo.KnowledgeFileVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 知识库入库Runner：把kb-docs下的6个知识库文件（3域×原文/问答两格式）灌入数据库。
 * 运行方式：设置环境变量 RUN_EVAL=true 后执行 mvn test -pl knowledge-agent-app -Dtest=KnowledgeIngestRunner。
 * 幂等性：入库前先删除同名知识库下的已有文件。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class KnowledgeIngestRunner {

  /** 知识库能力。 */
  @Autowired private KnowledgeService knowledgeService;

  /**
   * 入库全部知识库文件。
   */
  @Test
  void ingestAll() {
    Path kbDocs = locateKbDocs();
    ingest(kbDocs.resolve("02-jd-aftersale/jd_aftersale_policy.md"), "退货规则原文库", "RAW_DOC", "jd");
    ingest(kbDocs.resolve("03-taobao-return/taobao_7day_return_policy.md"), "退货规则原文库", "RAW_DOC", "taobao");
    ingest(kbDocs.resolve("04-user-rights/consumer_rights_law.md"), "用户权益原文库", "RAW_DOC", "gov");
    ingest(kbDocs.resolve("05-platform-guide/jd_platform_guide_raw.md"), "平台操作原文库", "RAW_DOC", "jd");
    ingest(kbDocs.resolve("06-qa-sets/return_policy_qa.md"), "退货规则问答库", "QA_SET", "constructed");
    ingest(kbDocs.resolve("06-qa-sets/user_rights_qa.md"), "用户权益问答库", "QA_SET", "constructed");
    ingest(kbDocs.resolve("06-qa-sets/platform_guide_qa.md"), "平台操作问答库", "QA_SET", "jd");
    ingest(kbDocs.resolve("06-qa-sets/customer_service_scripts_zh.md"), "客服话术库", "QA_SET", "scripts");
    System.out.println("=== 全部知识库 ===");
    knowledgeService.listKnowledgeBases().forEach(b -> System.out.printf(
        "%s：文件%d个，分块%d个%n", b.kbName(), b.fileCount(), b.chunkCount()));
  }

  /**
   * 入库单个文件：先删除同知识库下同名旧文件保证幂等（同库可存在多个不同文件）。
   *
   * @param file 文件路径
   * @param kbName 知识库名称
   * @param documentType 文档类型
   * @param source 素材来源
   */
  private void ingest(Path file, String kbName, String documentType, String source) {
    if (!Files.exists(file)) {
      throw new IllegalStateException("知识库文件不存在：" + file);
    }
    String title = file.getFileName().toString();
    List<KnowledgeFileVO> existing = knowledgeService.listFiles(kbName);
    for (KnowledgeFileVO old : existing) {
      if (title.equals(old.title())) {
        knowledgeService.deleteFile(old.fileId());
      }
    }
    try {
      MockMultipartFile multipart =
          new MockMultipartFile(
              "file", file.getFileName().toString(), "text/markdown", Files.readAllBytes(file));
      KnowledgeFileVO vo = knowledgeService.ingestFile(multipart, kbName, documentType, source);
      System.out.printf("入库完成：%s / %s（分块%d个）%n", kbName, vo.title(), vo.chunkCount());
    } catch (Exception e) {
      throw new IllegalStateException("入库失败：" + file, e);
    }
  }

  /**
   * 定位kb-docs目录：优先项目根（工作目录为app模块时取上级），回退当前目录。
   *
   * @return kb-docs目录路径
   */
  private Path locateKbDocs() {
    Path candidates = Path.of("..", "kb-docs");
    if (Files.isDirectory(candidates)) {
      return candidates.normalize();
    }
    Path local = Path.of("kb-docs");
    if (Files.isDirectory(local)) {
      return local;
    }
    throw new IllegalStateException("未找到kb-docs目录，请在项目根或app模块目录下运行");
  }
}
