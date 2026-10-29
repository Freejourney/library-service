package com.elibrary.common;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiException notFound(String code) { return new ApiException(404, code, code); }
    public static ApiException conflict(String code) { return new ApiException(409, code, code); }
}
