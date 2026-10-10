package com.zjc.demo.common.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页响应结构。不直接返回 {@link IPage}——它带的 {@code optimizeCountSql}、{@code searchCount}
 * 等内部字段会一起进 JSON，等于把实现细节写进接口契约。
 *
 * @param <T> 当前页记录的类型
 * @author jiancai.zhong
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页记录，无数据时为空列表。
     */
    private List<T> records = new ArrayList<>();

    /**
     * 符合条件的总记录数。
     */
    private long total = 0L;

    /**
     * 当前页码，从 1 开始。
     */
    private long current = 0L;

    /**
     * 每页条数。
     */
    private long size = 0L;

    /**
     * 总页数。
     */
    private long pages = 0L;

    /**
     * 把 MyBatis-Plus 的分页对象转换成响应结构，入参为 {@code null} 时返回空结果。
     *
     * @param page 分页对象，可为 {@code null}
     * @param <T>  记录类型
     * @return 分页响应结构，永不为 {@code null}
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        if (page == null) {
            return result;
        }
        result.setRecords(page.getRecords() == null ? new ArrayList<>() : page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
