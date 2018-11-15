package org.povworld.sbb;

import org.povworld.collection.CollectionUtil;
import org.povworld.collection.List;
import org.povworld.collection.Map;
import org.povworld.collection.mutable.HashSet;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.Input.Scenario;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.Output.Solution;
import org.povworld.sbb.Output.TrainRun;
import org.povworld.sbb.Output.TrainRunSection;
import org.povworld.sbb.RouteGraph.Edge;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

public class OutputBuilder {

	public static Solution createSolution(Problem problem, Map<String, PathSchedule> schedules) {
		Scenario senario = problem.getScenario();
		Solution.Builder builder = Solution.newBuilder()
			.setProblemInstanceLabel(senario.getLabel())
			.setProblemInstanceHash(senario.getHash())
			.setHash(1);
		
		List<String> sortedIntentions = CollectionUtil.sort(schedules.keys());
		for(String intention: sortedIntentions) {
		    PathSchedule schedule = schedules.get(intention);
			builder.addTrainRuns(createTrainRun(problem, problem.getServiceIntention(intention), schedule));
		}
		return builder.build();
	}
	
	private static TrainRun createTrainRun(Problem problem, ServiceIntention serviceIntention, PathSchedule schedule) {
		TrainRun.Builder builder = TrainRun.newBuilder().setServiceIntentionId(serviceIntention.getId());
		int node = 0;
		String route = serviceIntention.getRoute();
		HashSet<String> sectionRequirements = new HashSet<>();
		for(SectionRequirement sr: serviceIntention.getSectionRequirementsList()) {
			sectionRequirements.add(sr.getSectionMarker());
		}
		for(Edge edge: schedule.getPath().getEdges()) {
			if (edge.start.isSource() || edge.end.isSink()) {
				continue;
			}
			RouteSection section = edge.section;
			TrainRunSection.Builder trs = TrainRunSection.newBuilder()
				.setEntryTime(TimeUtil.unparseTime(schedule.getNodeTime(node)))
				.setExitTime(TimeUtil.unparseTime(schedule.getNodeTime(node + 1)))
				.setRoute(route)
				.setRouteSectionId(route + "#" + section.getSequenceNumber())
				.setSequenceNumber(node + 1)
				.setRoutePath(problem.getRoutePathId(section));
			String marker = problem.getSectionMarker(section);
			if (marker != null && sectionRequirements.contains(marker)) {
				trs.setSectionRequirement(marker);
			}
			builder.addTrainRunSections(trs);
			node++;
		}
		return builder.build();
	}

	public static String serializeSolution(Solution solution) throws InvalidProtocolBufferException {
		return JsonFormat.printer().preservingProtoFieldNames().print(solution);
	}

}
