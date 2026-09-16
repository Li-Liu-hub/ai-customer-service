package com.knowledgeagent.knowledge.mapper;

import com.knowledgeagent.knowledge.pojo.entity.KnowledgeChunk;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 知识切片表数据访问接口：批量写入、按文件删除、向量相似检索与关键词检索均走XML。 */
@Mapper
public interface KnowledgeChunkMapper {

  /**
   * 批量写入知识切片。
   *
   * @param chunks 切片实体列表（embedding为向量字面量字符串）
   * @return 写入行数
   */
  int batchInsert(@Param("chunks") List<KnowledgeChunk> chunks);

  /**
   * 删除指定文件的全部切片。
   *
   * @param fileId 文件ID
   * @return 删除行数
   */
  int deleteByFileId(@Param("fileId") Long fileId);

  /**
   * 向量相似检索：子查询内用HNSW索引取最近候选（候选量放大以抵消知识库过滤），
   * 外层JOIN文件表按知识库过滤后截取topK。
   *
   * @param queryVector 查询向量字面量字符串
   * @param kbName 知识库名称；为null时不过滤
   * @param candidateLimit 内层HNSW候选数量
   * @param topK 最终返回数量
   * @return 携带来源信息的分块结果（按余弦距离升序）
   */
  List<KnowledgeChunkVO> selectSimilar(
      @Param("queryVector") String queryVector,
      @Param("kbName") String kbName,
      @Param("candidateLimit") int candidateLimit,
      @Param("topK") int topK);

  /**
   * 关键词粗筛检索（混合检索的词法通道）：将查询词组展开为行，统计各分块命中词数作为词法得分。
   *
   * @param grams PostgreSQL text[] 数组字面量（如 {"退货","货运"}）
   * @param kbName 知识库名称；为null时不过滤
   * @param limit 返回数量
   * @return 命中分块ID（按命中词数降序）
   */
  List<Long> selectByKeywordGrams(
      @Param("grams") String grams,
      @Param("kbName") String kbName,
      @Param("limit") int limit);

  /**
   * 按ID列表查询分块及其来源信息（混合检索融合后取回详情）。
   *
   * @param ids 分块ID列表
   * @return 分块视图列表
   */
  List<KnowledgeChunkVO> selectByIds(@Param("ids") List<Long> ids);
}
