package com.knowledgeagent.knowledge.controller;

import com.knowledgeagent.common.response.ApiResponse;
import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeBaseVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeFileVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 知识库REST接口：向外部暴露RAG入库与检索能力（Agent工具在ai模块内复用同一Service）。 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Validated
public class KnowledgeController {

  /** 知识库能力。 */
  private final KnowledgeService knowledgeService;

  /**
   * 上传并入库一个知识文件。
   *
   * @param file 上传文件（pdf/docx/txt/md）
   * @param kbName 所属知识库名称
   * @param documentType 文档业务类型（RAW_DOC / QA_SET）
   * @param source 素材来源，可选
   * @return 入库结果
   */
  @PostMapping(value = "/files", consumes = "multipart/form-data")
  public ApiResponse<KnowledgeFileVO> ingestFile(
      @RequestPart("file") MultipartFile file,
      @RequestParam("kbName") String kbName,
      @RequestParam("documentType") String documentType,
      @RequestParam(value = "source", required = false) String source) {
    return ApiResponse.success(knowledgeService.ingestFile(file, kbName, documentType, source));
  }

  /**
   * 查询知识文件列表。
   *
   * @param kbName 知识库名称，可选
   * @return 文件列表
   */
  @GetMapping("/files")
  public ApiResponse<List<KnowledgeFileVO>> listFiles(
      @RequestParam(value = "kbName", required = false) String kbName) {
    return ApiResponse.success(knowledgeService.listFiles(kbName));
  }

  /**
   * 删除知识文件及其全部分块。
   *
   * @param fileId 文件ID
   * @return 空结果
   */
  @DeleteMapping("/files/{fileId}")
  public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
    knowledgeService.deleteFile(fileId);
    return ApiResponse.success(null);
  }

  /**
   * 枚举全部知识库（名称、文件数、分块数）。
   *
   * @return 知识库聚合列表
   */
  @GetMapping("/bases")
  public ApiResponse<List<KnowledgeBaseVO>> listKnowledgeBases() {
    return ApiResponse.success(knowledgeService.listKnowledgeBases());
  }

  /**
   * 语义检索知识分块。
   *
   * @param dto 检索请求
   * @return 命中分块列表
   */
  @PostMapping("/search")
  public ApiResponse<List<KnowledgeChunkVO>> searchKnowledge(
      @Valid @RequestBody KnowledgeSearchDTO dto) {
    return ApiResponse.success(knowledgeService.searchKnowledge(dto));
  }
}
