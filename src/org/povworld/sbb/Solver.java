package org.povworld.sbb;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.povworld.backtrack.BoostingDirector;
import org.povworld.backtrack.Director;
import org.povworld.backtrack.HardCollectingDirector;
import org.povworld.backtrack.PriorityConflictDirector;
import org.povworld.collection.Map;
import org.povworld.collection.Set;
import org.povworld.collection.common.Interval;
import org.povworld.collection.common.ObjectUtil;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashSet;
import org.povworld.sbb.ConnectionRepository.Connection;
import org.povworld.sbb.Input.Resource;
import org.povworld.sbb.Input.Scenario;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.Output.Solution;
import org.povworld.sbb.RouteGraph.Edge;

import com.google.common.base.Stopwatch;

public class Solver {
    
    private static final Logger logger = Logger.getGlobal();
    
    private final Problem problem;
    private final double maxPenalty;
    private final ConnectionRepository connections;
    
    private static class Args {
        String inputFile = "";
        String outputFile = "";
        double maxPenalty = 0;
        
        double maxPenaltyPerIntention = 14;
        double connectionBadnessFactor = 7.5;
        
        DirectorType directorType = DirectorType.HARD_COLLECTING;
        
        @Override
        public String toString() {
			return "input=" + inputFile + ", output=" + outputFile +
					", max_penalty_per_intention=" + maxPenaltyPerIntention +
					", max_penalty=" + maxPenalty +
					", connection_badness_factor="+connectionBadnessFactor +
					", director_type="+directorType;
        }
    }
    
    public Solver(Problem problem, double maxPenalty) {
        this.problem = problem;
        this.connections = ConnectionRepository.create(problem.getScenario());
        this.maxPenalty = maxPenalty;
    }
    
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int files = 0;
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                switch (arg.substring(2)) {
                    case "max_penalty":
                        i++;
                        result.maxPenalty = Double.parseDouble(args[i]);
                        break;
                    case "max_penalty_per_intention": 
                    	i++;
                    	result.maxPenaltyPerIntention = Double.parseDouble(args[i]);
                    	break;
                    case "connection_badness_factor":
                    	i++;
                    	result.connectionBadnessFactor = Double.parseDouble(args[i]);
                    	break;
                    case "director_type":
                    	i++;
                    	result.directorType = DirectorType.valueOf(args[i]);
                    	break;
                    default:
                        throw new IllegalArgumentException("Unknown command line flag " + arg);
                }
            } else {
                files++;
                if (files == 1) {
                    result.inputFile = arg;
                } else if (files == 2) {
                    result.outputFile = arg;
                } else {
                    throw new IllegalArgumentException("Too many non-flag arguments!");
                }
            }
        }
        return result;
    }
    
    public static void main(String[] args) throws Exception {
        Args arguments = parseArgs(args);
        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.log(Level.INFO, "Started at {0}", LocalDateTime.now());
        logger.log(Level.INFO, arguments.toString());
        
        Debug.MAX_PENALTY_PER_INTENTION = arguments.maxPenaltyPerIntention;
        Debug.CONNECTION_LATE_PROBABILITY_TO_BADNESS_FACTOR = arguments.connectionBadnessFactor;
        
        Scenario scenario = InputParser.parseScenario(new File(arguments.inputFile));
        logger.log(Level.INFO, "Parsed input file " + arguments.inputFile);
        Problem problem = new Problem(scenario);
        Solution solution = new Solver(problem, arguments.maxPenalty).solve(arguments.directorType);
        if (!arguments.outputFile.isEmpty()) {
            try (FileWriter writer = new FileWriter(new File(arguments.outputFile))) {
                writer.write(OutputBuilder.serializeSolution(solution));
            }
            logger.log(Level.INFO, "Wrote solution to " + arguments.outputFile);
        }
        logger.log(Level.INFO, "Used " + stopwatch.elapsed(TimeUnit.SECONDS) + "s.");
    }
    
    enum DirectorType {
    	PRIORITY_CONFLICT,
    	BOOSTING,
    	HARD_COLLECTING,
    }
    
	private Director<State, Conflict, ConflictSchedule> createDirector(DirectorType directorType, SbbExecutor executor,
			ConflictBooster conflictBooster) {
		switch (directorType) {
		case PRIORITY_CONFLICT:
			return new PriorityConflictDirector<>(executor, Debug.SOLVER, Debug.MAX_PRIORITY_LIST_SIZE);
		case BOOSTING:
			return new BoostingDirector<>(executor, conflictBooster, 6, Debug.SOLVER);
		case HARD_COLLECTING:
			return new HardCollectingDirector<>(executor, Debug.SOLVER);
		default:
			throw new RuntimeException("Unknown director type: " + directorType);
		}
	}
    
    public Output.Solution solve(DirectorType directorType) {
        ConflictBooster conflictBooster = new ConflictBooster();
        SbbExecutor executor = new SbbExecutor(problem, maxPenalty, connections, conflictBooster);
        State initialState = executor.createInitialState();
        if (initialState == null) {
            throw new RuntimeException("unsolveable!");
        }
        
        Director<State, Conflict, ConflictSchedule> director = createDirector(directorType, executor, conflictBooster);
        
        State solution = director.work(initialState);
        if (solution == null) {
            throw new RuntimeException("Unsolvable problem!");
        }
        logger.log(Level.INFO, "Successfully resolved all conflicts!");
        
        if (Debug.PRINT_SCHEDULE_AND_PLANS) {
            for (ResourcePlan plan: solution.resourcePlans.values()) {
                plan.printOccupations();
            }
        }
        
        checkConnectionTimes(solution);
        
        Map<String, PathSchedule> schedules = scheduleIntentions(solution);
        
        double pathPenalty = 0.0;
        double totalPenalty = 0.0;
        double maxPenaltyPerIntention = 0;
        for (PathSchedule schedule: schedules.values()) {
            pathPenalty += schedule.getPathPenalty();
            totalPenalty += schedule.getTotalPenalty();
            maxPenaltyPerIntention = Math.max(maxPenaltyPerIntention, schedule.getTotalPenalty());
        }
        logger.info("Penalty: path=" + pathPenalty + " total=" + totalPenalty + " max per intention="
                + maxPenaltyPerIntention);
        
        return OutputBuilder.createSolution(problem, schedules);
    }
    
    private void checkConnectionTimes(State state) {
        for (Connection connection: connections.getAll()) {
            ConnectionOccupation occupation = ConnectionOccupation.create(connection, state.graphConstraints);
            if (occupation.earliestDeparture - occupation.latestArrival < connection.minConnectionTime) {
                throw new RuntimeException(
                        "Connection time not guaranteed for " + connection + " (" + occupation + ")");
            }
        }
    }
    
    private Map<String, PathSchedule> scheduleIntentions(State state) {
        HashMap<String, ResourceAllocator> resourceAllocators = new HashMap<>();
        for (Resource resource: problem.getResources()) {
            resourceAllocators.put(resource.getId(),
                    new ResourceAllocator(resource.getId(), resource.getReleaseTimeSeconds()));
        }
        
        HashMap<String, PathSchedule> schedules = new HashMap<>();
        for (ServiceIntention si: problem.getScenario().getServiceIntentionsList()) {
            String intention = si.getId();
            GraphConstraints constraints = state.graphConstraints.get(intention);
            PathSchedule schedule = constraints.scheduleMinimumPenaltyPath();
            registerSchedule(resourceAllocators, si.getRoute(), schedule);
            schedules.put(intention, schedule);
        }
        return schedules;
    }
    
    private void registerSchedule(Map<String, ResourceAllocator> resourceAllocators, String route, PathSchedule schedule) {
        if (Debug.PRINT_SCHEDULE_AND_PLANS) {
            System.out.println("Schedule for route " + route + "\n" + schedule + "\n");
        }
        RouteGraph graph = problem.getRouteGraph(route);
        int i = -1;
        HashMap<String, Integer> occupied = new HashMap<>();
        for (Edge edge: schedule.getPath().getEdges()) {
            Set<String> current = graph.getEdgeResourceOccupations(edge);
            HashSet<String> ending = new HashSet<>();
            for (String resource: occupied.keys()) {
                if (current.contains(resource)) {
                    continue;
                }
                ending.add(resource);
            }
            for (String resource: ending) {
                // End of occupation.
                int startNode = occupied.remove(resource);
                int entry = schedule.getNodeTime(startNode);
                int exit = schedule.getNodeTime(i);
                if (!resourceAllocators.get(resource).occupy(new Interval(entry, exit), route, schedule)) {
                    logger.log(Level.SEVERE, "Failed to occupy resource " + resource + " for route " + route + " (" + schedule + ")");
//					throw new RuntimeException(
//							"Failed to occupy resource " + resource + " for route " + route + " (" + schedule + ")");
                }
            }
            for (String resource: current) {
                ObjectUtil.checkNotNull(resource);
                if (!occupied.containsKey(resource)) {
                    occupied.put(resource, i);
                }
            }
            i++;
        }
    }
    
}
