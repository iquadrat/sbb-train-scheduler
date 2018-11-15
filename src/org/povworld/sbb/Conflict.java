package org.povworld.sbb;

import javax.annotation.CheckForNull;

import org.povworld.collection.common.Interval;

public class Conflict {
	public final double badness;
	
	// TODO use polymorphism
	
	// For a Resource conflict:
	@CheckForNull 
	public final String resource;
	
	@CheckForNull 
	public final Interval range;
	
	// For a Connection conflict:
	@CheckForNull 
	public final ConnectionOccupation connectionOccupation;
	
	public Conflict(double badness, String resource, Interval range) {
		this(badness, resource, range, null);
	}
	
	public Conflict(double badness, ConnectionOccupation occupation) {
		this(badness, null, null, occupation);
	}
	
	private Conflict(double badness, @CheckForNull String resource, @CheckForNull Interval range, @CheckForNull ConnectionOccupation connectionOccupation) {
		this.badness = badness;
		this.resource = resource;
		this.range = range;
		this.connectionOccupation = connectionOccupation;
	}

	public String getResource() {
		return resource;
	}
	
	@Override
	public String toString() {
		if (resource != null) {
			return resource + " " + range + " (" + badness + ")";
		} else {
			return connectionOccupation.connection + " " + connectionOccupation + " (" + badness + ")";
		}
	}
}