package com.ghostmagic.core;
@FunctionalInterface public interface Cancellation {
    boolean isCancelled();
    Cancellation NEVER = () -> false;
}
