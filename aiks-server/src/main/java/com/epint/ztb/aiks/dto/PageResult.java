package com.epint.ztb.aiks.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 通用分页返回
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private long page;
    private long size;
    private List<T> list;
}
