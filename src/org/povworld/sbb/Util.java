package org.povworld.sbb;


import java.text.DecimalFormat;
import java.util.Comparator;

import org.povworld.collection.Collection;
import org.povworld.collection.CollectionUtil;
import org.povworld.collection.mutable.ArrayList;

public class Util {

	private Util() {}
	
	public static final double WEIGHT_EPS = 1e-8;
	private static final double PENALTY_EPS = 1e-6;
	
	public static final Double ZERO = Double.valueOf(0.0);

	public static String pathIdsString(Collection<Path> paths) {
		StringBuilder p = new StringBuilder();
		ArrayList<Path> sortedPaths = CollectionUtil.sort(paths, ArrayList.<Path>newBuilder(),
				new Comparator<Path>() {
					@Override
					public int compare(Path p1, Path p2) {
						return Integer.compare(p1.getId(), p2.getId());
					}
				});
		int lastId = Integer.MIN_VALUE;
		boolean merged = false;
		for(Path path: sortedPaths) {
			int id = path.getId();
			if (id == lastId + 1) {
				merged = true;
				// skip
			} else if (lastId != Integer.MIN_VALUE) {
				if (merged) {
					p.append("-");
					p.append(lastId);
				}
				p.append('/');
				p.append(id);
				merged = false;
			} else {
				p.append('/');
				p.append(id);
				merged = false;
			}
			lastId = id;
		}
		if (merged) {
			p.append("-");
			p.append(lastId);
		}
		
		if (p.length() > 30) {
			return "/*("+paths.size()+")";
		}
		
		return p.toString();
	}

	public static boolean weightEquals(double w1, double w2) {
		return Math.abs(w1 - w2) < WEIGHT_EPS;
	}
	
	public static boolean penaltyEquals(double p1, double p2) {
		if (!Double.isFinite(p1)) {
			return p1 == p2;
		} else {
			return Math.abs(p1 - p2) < PENALTY_EPS;
		} 
	}
	
	public static String printDensity(double density) {
		return new DecimalFormat("#.##").format(density);
	}

	public static boolean penaltyLess(double penalty1, double penalty2) {
		return penalty1 - penalty2 < PENALTY_EPS;
	}
	
}
