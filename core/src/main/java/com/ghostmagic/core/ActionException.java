package com.ghostmagic.core;
public class ActionException extends Exception {
    public static final String UNSUPPORTED="UNSUPPORTED", PERMISSION_REQUIRED="PERMISSION_REQUIRED", INVALID_CONFIG="INVALID_CONFIG", EXECUTION_FAILED="EXECUTION_FAILED";
    private final String code;
    public ActionException(String code,String message) { super(message); this.code=code; }
    public String code() { return code; }
}
