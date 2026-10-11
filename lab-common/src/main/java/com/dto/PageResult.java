package com.lab.common.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果载体（自研分页的返回值）。
 *
 * <p>与 MyBatis-Plus 的 {@code Page} 是两条独立路线：
 * 本类由"COUNT + LIMIT 两条 SQL"自行组装，页数由代码计算，
 * 好处是"分页到底怎么算的"完全可见，出问题时能直接对照总数与当前页数据。</p>
 *
 * @param <T> 记录类型
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int pageNum;

    /** 页大小 */
    private int pageSize;

    /** 当前页数据 */
    private List<T> records = Collections.emptyList();

    public PageResult(long total, int pageNum, int pageSize, List<T> records) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records == null ? Collections.emptyList() : records;
    }

    /**
     * 总页数。
     *
     * <p>刻意写成"先加 size 再除"的向上取整：若用 {@code total / size + 1}，
     * 当 total 恰好是 size 的整数倍时会多算一页。</p>
     */
    public int getPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) ((total + pageSize - 1) / pageSize);
    }

    /** 是否还有下一页 */
    public boolean isHasNext() {
        return pageNum < getPages();
    }
}
