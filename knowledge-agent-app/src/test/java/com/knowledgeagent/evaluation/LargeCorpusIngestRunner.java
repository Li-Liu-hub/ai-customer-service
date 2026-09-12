package com.knowledgeagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeFileVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 大规模语料（DuReader-retrieval抽样4万段）入库Runner：按config.json声明的
 * 知识库名、文档类型与语料目录，把eval-data/large-corpus下的分片md文件灌入数据库。
 * 语料格式由config.json区分：实验分支QA格式（QA_SET），基线分支原文格式（RAW_DOC）。
 * 运行方式：RUN_EVAL=true mvn test -pl knowledge-agent-app -Dtest=LargeCorpusIngestRunner
 * 幂等性：入库前删除目标知识库下全部已有文件。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class LargeCorpusIngestRunner {

  /** 知识库能力。 */
  @Autowired private KnowledgeService knowledgeService;

  /** JSON读写。 */
  @Autowired private ObjectMapper objectMapper;

  /** 语料配置：知识库名、文档类型、语料目录、评估报告标签。 */
  record CorpusConfig(String kbName, String documentType, String corpusDir, String label) {}

  /**
   * 按当前分支的config.json执行大规模语料入库。
   */
  @Test
  void ingestLargeCorpus() throws Exception {
    CorpusConfig config =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("evaluation/large_corpus/config.json"),
            CorpusConfig.class);
    Path corpusDir = locateEvalData().resolve("large-corpus").resolve(config.corpusDir());
    if (!Files.isDirectory(corpusDir)) {
      throw new IllegalStateException("语料目录不存在：" + corpusDir);
    }

    // 幂等：先清空目标知识库
    for (KnowledgeFileVO old : knowledgeService.listFiles(config.kbName())) {
      knowledgeService.deleteFile(old.fileId());
    }

    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(corpusDir, "*.md")) {
      stream.forEach(files::add);
    }
    files.sort(Path::compareTo);
    if (files.isEmpty()) {
      throw new IllegalStateException("语料目录为空：" + corpusDir);
    }

    long totalChunks = 0;
    long start = System.currentTimeMillis();
    for (Path file : files) {
      long fileStart = System.currentTimeMillis();
      MockMultipartFile multipart =
          new MockMultipartFile(
              "file", file.getFileName().toString(), "text/markdown", Files.readAllBytes(file));
      KnowledgeFileVO vo =
          knowledgeService.ingestFile(
              multipart, config.kbName(), config.documentType(), "dureader");
      totalChunks += vo.chunkCount();
      System.out.printf(
          "入库完成：%s（分块%d个，耗时%ds）%n",
          file.getFileName(), vo.chunkCount(), (System.currentTimeMillis() - fileStart) / 1000);
    }
    System.out.printf(
        "=== %s 入库汇总：%d文件 %d分块 总耗时%d秒 ===%n",
        config.kbName(), files.size(), totalChunks, (System.currentTimeMillis() - start) / 1000);
  }

  /**
   * 定位eval-data目录：优先项目根（工作目录为app模块时取上级），回退当前目录。
   *
   * @return eval-data目录路径
   */
  private Path locateEvalData() {
    Path parent = Path.of("..", "eval-data");
    if (Files.isDirectory(parent)) {
      return parent.normalize();
    }
    Path local = Path.of("eval-data");
    if (Files.isDirectory(local)) {
      return local;
    }
    throw new IllegalStateException("未找到eval-data目录，请在项目根或app模块目录下运行");
  }
}
