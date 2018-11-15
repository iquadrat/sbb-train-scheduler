package org.povworld.sbb;

import java.util.logging.Level;

import org.povworld.sbb.ResourcePlan.ConflictIntervalSelection;

public class Debug {
	public static final boolean ENABLE_DENSITY_MAP_CONSISTENCY_CHECKS = false;
	public static final boolean ENABLE_TIME_CONSTRAINS_CONSISTENCY_CHECKS = false;
	public static final boolean ENABLE_RESOURCE_PLAN_CONSITENCY_CHECKS = false;

	public static final boolean SOLVER_SHOW_DEBUG = false;
	public static final boolean UNPARSE_INTERVAL_TIME = false;
	public static final boolean PRINT_SCHEDULE_AND_PLANS = false;

	public static final boolean ENDABLE_CONNECTIONS = true;
	public static /* final */ double MAX_PENALTY_PER_INTENTION = 28;

	public static final int MAX_PRIORITY_LIST_SIZE = 25;
	
	public static /* final */ double CONNECTION_LATE_PROBABILITY_TO_BADNESS_FACTOR = 7.5;
	public static final double REMOVED_PATH_BADNESS_FACTOR = 2;
	public static final BadnessMetric BADNESS_METRIC = BadnessMetric.ConflictMaxAvgDensity;
	public static final ConflictIntervalSelection CONFLICT_SCHEDULE = ConflictIntervalSelection.PointMidOrMaxIfNotBorderUpdateDensity;
	public static final PathWeightDensityFunction PATH_WEIGHT_DENSITY_FUNCTION = PathWeightDensityFunction.Identity;

	public enum PathWeightDensityFunction {
		Identity,
		Sqrt,
	}
	
	public enum BadnessMetric {
		ConflictMaxAvgDensity, Random,
	}

	public static final Level SOLVER;
	static {
		if (SOLVER_SHOW_DEBUG) {
			SOLVER = Level.INFO;
		} else {
			SOLVER = Level.FINE;
		}
	}
}
