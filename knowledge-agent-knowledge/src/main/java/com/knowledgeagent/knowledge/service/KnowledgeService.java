package com.knowledgeagent.knowledge.service;

import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeBaseVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeFileVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 知识库能力接口：文档入库、知识库枚举、文件管理与语义检索，向REST接口与Agent工具暴露。 */
public interface KnowledgeService {

  /**
   * 入库一个知识文件：校验、保存、解析、清洗、按文档类型切块、向量化后落库。
   *
   * @param file 上传的原始文件（pdf/docx/txt/md）
   * @param kbName 所属知识库名称（自描述命名）
   * @param documentType 文档业务类型（RAW_DOC / QA_SET）
   * @param source 素材来源标识，可为空
   * @return 入库结果文件视图
   */
  KnowledgeFileVO ingestFile(MultipartFile file, String kbName, String documentType, String source);

  /**
   * 枚举全部知识库：按知识库名称聚合文件数与分块数，供Agent自主选择检索目标。
   *
   * @return 知识库聚合列表
   */
  List<KnowledgeBaseVO> listKnowledgeBases();

  /**
   * 查询知识文件列表。
   *
   * @param kbName 知识库名称；为null时查询全部
   * @return 文件视图列表（按创建时间倒序）
   */
  List<KnowledgeFileVO> listFiles(String kbName);

  /**
   * 语义检索知识分块：按配置依次执行问题重写（可选）、向量检索、关键词混合（可选）与结果重排（可选）。
   *
   * @param dto 检索请求（问题必填，知识库与topK可选）
   * @return 命中分块列表（携带来源信息，按相关性降序）
   */
  List<KnowledgeChunkVO> searchKnowledge(KnowledgeSearchDTO dto);

  /**
   * 删除知识文件及其全部分块，并清理本地原始文件。
   *
   * @param fileId 文件ID
   */
  void deleteFile(Long fileId);
}
