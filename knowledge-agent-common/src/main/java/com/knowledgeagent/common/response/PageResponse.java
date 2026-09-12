package com.knowledgeagent.common.response;

import java.util.List;

/**
 * 通用分页结果。
 *
 * @param items 当前页数据
 * @param total 总记录数
 * @param page 当前页码
 * @param size 每页数量
 * @param pages 总页数
 */
public record PageResponse<T>(
    List<T> items,
    long total,
    long page,
    long size,
    long pages) {}
