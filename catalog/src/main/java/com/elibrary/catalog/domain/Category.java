package com.elibrary.catalog.domain;

import java.util.UUID;

/** 分类。 */
public record Category(UUID categoryId, String name, String status, int sortOrder) {}