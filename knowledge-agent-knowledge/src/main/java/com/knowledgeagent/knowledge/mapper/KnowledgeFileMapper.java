package com.knowledgeagent.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgeagent.knowledge.pojo.entity.KnowledgeFile;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeBaseVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeFileVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 知识文件表数据访问接口：简单CRUD走BaseMapper，聚合与列表查询走XML。 */
@Mapper
public interface KnowledgeFileMapper extends BaseMapper<KnowledgeFile> {

  /**
   * 按知识库名称聚合统计全部知识库的文件数与分块数。
   *
   * @return 知识库聚合列表（按名称排序）
   */
  List<KnowledgeBaseVO> aggregateBases();

  /**
   * 按知识库名称查询文件列表。
   *
   * @param kbName 知识库名称；为null时查询全部文件
   * @return 文件视图列表（按创建时间倒序）
   */
  List<KnowledgeFileVO> selectFiles(@Param("kbName") String kbName);
}
