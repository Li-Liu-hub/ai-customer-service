package com.knowledgeagent.common.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;

/**
 * Token估算工具：基于jtokkit（GPT-4o-mini对应的o200k_base编码）近似统计文本Token数，
 * 供上下文压缩预算与文本切块统计共用；中文常见字约1-1.5 token/字。
 */
public final class TokenEstimateUtil {

  /** jtokkit编码注册表，用于获取模型对应的Token编码器。 */
  private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();

  /** token估算编码器（GPT-4o-mini对应的o200k_base，模型tokenizer的近似）。 */
  private static final Encoding TOKEN_ENCODING =
      ENCODING_REGISTRY.getEncodingForModel(ModelType.GPT_4O_MINI);

  private TokenEstimateUtil() {}

  /**
   * 估算文本Token数。
   *
   * @param text 文本；null或空白返回0
   * @return 估算Token数
   */
  public static int countTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return TOKEN_ENCODING.countTokens(text);
  }
}
