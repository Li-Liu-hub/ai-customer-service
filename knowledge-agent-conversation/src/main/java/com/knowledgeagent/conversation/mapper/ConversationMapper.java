package com.knowledgeagent.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgeagent.conversation.pojo.entity.Conversation;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话表数据访问，复杂SQL见ConversationMapper.xml。 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

  /**
   * 按更新时间倒序查询最近的会话列表。
   *
   * @param limit 最大返回数量
   * @return 最近会话列表（按更新时间倒序）
   */
  List<Conversation> selectRecent(@Param("limit") int limit);

  /**
   * 写入新的会话摘要与摘要水位线（水位线只进不退）。
   *
   * @param id 会话ID
   * @param summary 新的会话摘要
   * @param summarizedUntilId 摘要水位线：ID不超过该值的消息已折叠进摘要
   * @param summaryTokens 摘要的Token数量
   */
  void updateSummaryAndWatermark(
      @Param("id") Long id,
      @Param("summary") String summary,
      @Param("summarizedUntilId") Long summarizedUntilId,
      @Param("summaryTokens") Integer summaryTokens);

  /**
   * 更新会话标题。
   *
   * @param id 会话ID
   * @param title 会话标题
   */
  void updateTitle(@Param("id") Long id, @Param("title") String title);

  /**
   * 尝试为会话抢占一轮处理权（同一会话并发保护）：仅当当前无进行中的轮次、
   * 或上一轮为超时僵尸锁（发起时间早于staleBefore且未完成）时才能抢占成功。
   *
   * @param id 会话ID
   * @param now 当前时间，作为本轮的发起时间
   * @param staleBefore 僵尸锁判定边界：发起时间早于该值且未完成的轮次视为失效
   * @return 更新行数，1表示抢占成功，0表示已有进行中的轮次
   */
  int tryAcquireProcessing(
      @Param("id") Long id,
      @Param("now") OffsetDateTime now,
      @Param("staleBefore") OffsetDateTime staleBefore);

  /**
   * 标记会话本轮处理已完成（成功或失败均调用，释放处理权）。
   *
   * @param id 会话ID
   * @param now 完成时间
   */
  void completeProcessing(@Param("id") Long id, @Param("now") OffsetDateTime now);
}