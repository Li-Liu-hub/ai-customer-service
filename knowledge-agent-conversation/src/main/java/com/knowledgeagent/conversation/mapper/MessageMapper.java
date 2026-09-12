package com.knowledgeagent.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgeagent.conversation.pojo.entity.Message;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话消息表数据访问，复杂SQL见MessageMapper.xml。 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

  /**
   * 查询某会话中创建时间晚于指定时间点后的消息，按创建时间正序返回。
   *
   * @param conversationId 会话ID
   * @param afterTime 历史分界时间，仅返回create_time晚于该时间的消息
   * @return 按创建时间正序排列的活跃消息列表
   */
  List<Message> selectAfterTime(
      @Param("conversationId") Long conversationId, @Param("afterTime") OffsetDateTime afterTime);

  /**
   * 回填某条消息的AI回复。
   *
   * @param id 消息ID
   * @param aiMessage AI回复内容
   */
  void updateAssistantMessage(@Param("id") Long id, @Param("aiMessage") String aiMessage);
}