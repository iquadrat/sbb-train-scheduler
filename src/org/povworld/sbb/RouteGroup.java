package org.povworld.sbb;

import org.povworld.collection.Collection;
import org.povworld.collection.Set;
import org.povworld.collection.common.PreConditions;
import org.povworld.collection.mutable.HashSet;

public final class RouteGroup {
    private final String intention;
    private final double boost;
    private final HashSet<Path> paths;
    
    public RouteGroup(String intention, double boost, Collection<Path> initialPaths) {
        PreConditions.conditionCheck("Paths must not be empty!", !initialPaths.isEmpty());
        this.intention = intention;
        this.boost = boost;
        this.paths = new HashSet<>(initialPaths.size());
        paths.addAll(initialPaths);
    }
    
    public double getBoostedWeight() {
        return boost / paths.size();
    }
    
    public double getBoost() {
        return boost;
    }
    
    public void addPath(Path path) {
        if (!paths.add(path)) {
            throw new IllegalArgumentException(path + " already contained!");
        }
    }
    
    public Set<Path> getPaths() {
        return paths;
    }
    
    public boolean isEmpty() {
        return paths.isEmpty();
    }
    
    public String getIntention() {
        return intention;
    }
    
    public int size() {
        return paths.size();
    }
}