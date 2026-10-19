package com.ghostmagic.core;
import java.util.*;
public record Node(String id, String type, Map<String,String> config) {
    public Node { Objects.requireNonNull(id); Objects.requireNonNull(type); config = Map.copyOf(config); }
}
