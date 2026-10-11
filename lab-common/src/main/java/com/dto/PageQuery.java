package com.lab.common.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 分页请求参数，并做归一化。
 *
 * <p>存在的意义是"防呆"：如果直接信任调用方传来的 pageSize，
 * 一句 {@code LIMIT 0, 100000000} 就能把整表拉进内存。
 * 这里把页码压到 ≥ 1、页大小收敛到 [1, 100]，并对非法值做兜底。</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页大小上限：超过此值的请求会被截断，避免一次查全表 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private Integer pageNum = 1;

    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public PageQuery(Integer pageNum, Integer pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /** 归一化后的页码，恒 ≥ 1 */
    public int getSafePageNum() {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    /** 归一化后的页大小，恒落在 [1, 100] */
    public int getSafePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** SQL 的 OFFSET，等价于 {@code LIMIT offset, size} 中的 offset */
    public int getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
