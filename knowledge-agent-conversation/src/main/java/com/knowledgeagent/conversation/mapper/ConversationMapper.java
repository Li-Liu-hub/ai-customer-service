package com.knowledgeagent.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgeagent.conversation.pojo.entity.Conversation;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话表数据访问，复杂SQL见ConversationMapper.xml。 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

  /**
   * 写入新的会话摘要，并把更新时间更新为新的历史分界时间点。
   *
   * @param id 会话ID
   * @param summary 新的会话摘要
   * @param boundaryTime 新的历史分界时间，活跃历史取create_time晚于该时间的消息
   */
  void updateSummary(
      @Param("id") Long id,
      @Param("summary") String summary,
      @Param("boundaryTime") OffsetDateTime boundaryTime);
}