package com.ghostmagic.core;
import java.util.Map;
/** Platform calls must return promptly and respect the worker thread's interrupt flag. */
public interface ActionHost {
    void execute(String type, Map<String,String> config, Map<String,String> variables) throws ActionException;
    default String readText(Map<String,String> config, Cancellation cancellation) throws ActionException {
        throw new ActionException(ActionException.UNSUPPORTED,"OCR is unavailable on this host");
    }
    int batteryPercent() throws ActionException;
}
