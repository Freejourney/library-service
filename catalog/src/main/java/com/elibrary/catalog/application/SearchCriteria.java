package com.elibrary.catalog.application;

import java.util.UUID;

/** 目录检索条件。 */
public record SearchCriteria(
        String query,
        UUID categoryId,
        String type,
        Boolean availability,
        int page,
        int pageSize,
        String sort) {}