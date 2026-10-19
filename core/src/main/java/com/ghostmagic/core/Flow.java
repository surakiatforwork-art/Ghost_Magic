package com.ghostmagic.core;
import java.util.*;
public record Flow(String id, String name, String startId, List<Node> nodes, List<Edge> edges) {
    public Flow { Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(startId); nodes=List.copyOf(nodes); edges=List.copyOf(edges); }
}
