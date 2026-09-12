package com.knowledgeagent.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgeagent.conversation.pojo.entity.Message;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话消息表数据访问，复杂SQL见MessageMapper.xml。 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

  /**
   * 查询某会话中消息ID大于摘要水位线后的消息，按ID正序返回。
   *
   * @param conversationId 会话ID
   * @param afterId 摘要水位线；null表示从最早开始
   * @return 按ID正序排列的活跃消息列表
   */
  List<Message> selectAfterId(
      @Param("conversationId") Long conversationId, @Param("afterId") Long afterId);

  /**
   * 回填某条消息的AI回复。
   *
   * @param id 消息ID
   * @param aiMessage AI回复内容
   */
  void updateAssistantMessage(@Param("id") Long id, @Param("aiMessage") String aiMessage);
}