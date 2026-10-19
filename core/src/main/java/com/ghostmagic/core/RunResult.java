package com.ghostmagic.core;
import java.util.*;
public record RunResult(Status status, List<TraceEntry> trace, Map<String,String> variables, String errorCode, String message) {
    public enum Status { COMPLETED, FAILED, CANCELLED, TIMED_OUT, STEP_LIMIT, SKIPPED }
    public RunResult { trace=List.copyOf(trace); variables=Map.copyOf(variables); }
    public record TraceEntry(String nodeId, String type, String port, String message) {}
}
