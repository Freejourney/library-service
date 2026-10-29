package com.elibrary.catalog.domain;

/** 目录条目上架状态。 */
public enum ShelfStatus {
    ON_SHELF,
    OFF_SHELF,
    REMOVED;

    public boolean borrowable() {
        return this == ON_SHELF;
    }
}