package com.elibrary.common;

/** 借阅/归还等写操作的结果载体：HTTP 状态码、响应数据、是否幂等重放。 */
public record OperationResult(int status, Object data, boolean replay) {}