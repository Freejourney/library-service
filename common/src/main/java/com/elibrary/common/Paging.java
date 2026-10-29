package com.elibrary.common;

import java.util.List;

/** 分页响应载体。 */
public record Paging<T>(List<T> items, int page, int pageSize, long total, String sort) {}