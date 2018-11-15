package org.povworld.sbb;


import org.junit.Test;
import org.povworld.sbb.Input.Resource;
import org.povworld.sbb.Input.ResourceOccupations;
import org.povworld.sbb.Input.Route;
import org.povworld.sbb.Input.RoutePath;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.Input.Scenario;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.Output.Solution;
import org.povworld.sbb.Solver.DirectorType;

import com.google.common.truth.Truth;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;

public class SolverTest extends GraphTestBase {
	
	@Test
	public void exactlyFitTwoTrains() throws Exception {
		ServiceIntention train1 = ServiceIntention.newBuilder()
				.setId("t1")
				.setRoute("r1")
				.addSectionRequirements(SectionRequirement.newBuilder().setEntryEarliest("07:00:00").setSectionMarker("S1"))
				.addSectionRequirements(SectionRequirement.newBuilder().setExitLatest("07:06:00").setSectionMarker("S2"))
				.build();
		ServiceIntention train2 = ServiceIntention.newBuilder()
				.setId("t2")
				.setRoute("r1")
				.addSectionRequirements(SectionRequirement.newBuilder().setEntryEarliest("07:01:00").setSectionMarker("S1"))
				.addSectionRequirements(SectionRequirement.newBuilder().setExitLatest("07:08:00").setSectionMarker("S2"))
				.build();
		
		Route route1 = Route.newBuilder().setId("r1")
			.addRoutePaths(RoutePath.newBuilder()
					.addRouteSections(RouteSection.newBuilder()
						.addSectionMarker("S1")
						.addResourceOccupations(ResourceOccupations.newBuilder().setResource("R1"))
						.setSequenceNumber(1)
						.setMinimumRunningTimeSeconds(120))
					.addRouteSections(RouteSection.newBuilder()
						.addSectionMarker("S2")
						.setSequenceNumber(2)
						.addResourceOccupations(ResourceOccupations.newBuilder().setResource("R2"))
						.setMinimumRunningTimeSeconds(180)))
			.build();
		
		Scenario scenario =
				Scenario.newBuilder()
					.setLabel("test")
					.setHash(1)
					.addServiceIntentions(train1)
					.addServiceIntentions(train2)
					.addRoutes(route1)
					.addResources(Resource.newBuilder().setId("R1").setReleaseTimeSeconds(0))
					.addResources(Resource.newBuilder().setId("R2").setReleaseTimeSeconds(0))
					.build();
		
		Problem problem = new Problem(scenario);
		Solver solver = new Solver(problem, 0);
		Solution solution = solver.solve(DirectorType.HARD_COLLECTING);
		
		Solution expectedSolution = parseSolution("problem_instance_label: \"test\"\n" + 
				"problem_instance_hash: 1\n" + 
				"hash: 1\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t1\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"07:00:00\"\n" + 
				"    exit_time: \"07:02:00\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"S1\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"07:02:00\"\n" + 
				"    exit_time: \"07:05:00\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#2\"\n" + 
				"    sequence_number: 2\n" + 
				"    section_requirement: \"S2\"\n" + 
				"  }\n" + 
				"}\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t2\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"07:02:00\"\n" + 
				"    exit_time: \"07:05:00\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"S1\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"07:05:00\"\n" + 
				"    exit_time: \"07:08:00\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#2\"\n" + 
				"    sequence_number: 2\n" + 
				"    section_requirement: \"S2\"\n" + 
				"  }\n" + 
				"}\n" + 
				"\n");
		Truth.assertThat(solution).isEqualTo(expectedSolution);
	}

	private Solution parseSolution(String text) throws ParseException {
		Solution.Builder expected = Solution.newBuilder();
		TextFormat.getParser().merge(text, expected);
		Solution expectedSolution = expected.build();
		return expectedSolution;
	}
	
	@Test
	public void needsToTakeAlternativeRoute() throws ParseException {
		ServiceIntention train1 = ServiceIntention.newBuilder()
				.setId("t1")
				.setRoute("r1")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setEntryEarliest("07:00:00")
						.setExitLatest("08:00:00")
						.setSectionMarker("S1"))
				.build();
		ServiceIntention train2 = ServiceIntention.newBuilder()
				.setId("t2")
				.setRoute("r2")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setEntryEarliest("07:00:00")
						.setExitLatest("08:00:00")
						.setSectionMarker("S1"))
				.build();
		
		Route route1 = Route.newBuilder().setId("r1")
			.addRoutePaths(RoutePath.newBuilder()
					.addRouteSections(RouteSection.newBuilder()
						.addSectionMarker("S1")
						.addResourceOccupations(ResourceOccupations.newBuilder().setResource("R1"))
						.setRouteAlternativeMarkerAtEntry("A")
						.setRouteAlternativeMarkerAtExit("B")
						.setSequenceNumber(1)
						.setMinimumRunningTimeSeconds(55*60)))
			.addRoutePaths(RoutePath.newBuilder()
					.addRouteSections(RouteSection.newBuilder()
							.addSectionMarker("S1")
							.addResourceOccupations(ResourceOccupations.newBuilder().setResource("R2"))
							.setRouteAlternativeMarkerAtEntry("A")
							.setRouteAlternativeMarkerAtExit("B")
							.setSequenceNumber(2)
							.setMinimumRunningTimeSeconds(30*60)))
			.build();
		
		Route route2 = Route.newBuilder().setId("r2")
				.addRoutePaths(RoutePath.newBuilder()
						.addRouteSections(RouteSection.newBuilder()
								.addSectionMarker("S1")
								.addResourceOccupations(ResourceOccupations.newBuilder().setResource("R1"))
								.setSequenceNumber(1)
								.setMinimumRunningTimeSeconds(10*60)))
				.build();
		
		Scenario scenario =
				Scenario.newBuilder()
					.setLabel("test")
					.setHash(1)
					.addServiceIntentions(train1)
					.addServiceIntentions(train2)
					.addRoutes(route1)
					.addRoutes(route2)
					.addResources(Resource.newBuilder().setId("R1").setReleaseTimeSeconds(0))
					.addResources(Resource.newBuilder().setId("R2").setReleaseTimeSeconds(0))
					.build();
		
		Problem problem = new Problem(scenario);
		Solver solver = new Solver(problem, 0);
		Solution solution = solver.solve(DirectorType.HARD_COLLECTING);
		
		Truth.assertThat(solution).isEqualTo(parseSolution(
				"problem_instance_label: \"test\"\n" + 
				"problem_instance_hash: 1\n" + 
				"hash: 1\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t1\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"07:00:00\"\n" + 
				"    exit_time: \"07:30:00\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#2\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"S1\"\n" + 
				"  }\n" + 
				"}\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t2\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"07:00:00\"\n" + 
				"    exit_time: \"07:10:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"S1\"\n" + 
				"  }\n" + 
				"}"));
	}
	
	@Test
	public void twoTrainsOnSlowFastPathGraph() throws Exception {
		Route.Builder route1 = Route.newBuilder();
		route1.addRoutePaths(path(
				section(60, "A", "", ""),
				section(1800, "", "", "", "Ra"),
				section(60, "", "", "X1", "Ra", "Rb", "Re"),
				section(60, "", "X1" ,"X2", "Rb", "Rc"),
				section(60, "", "X2", "", "Rc", "Rd", "Rg"),
				section(3600, "", "", "", "Rd"),
				section(60, "B", "", "")));
		route1.addRoutePaths(path(
				section(60, "", "X1", "", "Re"),
				section(600, ""),
				section(60, "", "" ,"X2", "Rg")));
		
		resetSectionSequenceNumber();

		ServiceIntention train1 = ServiceIntention.newBuilder()
				.setId("t1")
				.setRoute("r1")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("A")
						.setEntryEarliest("12:00:00"))
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("B")
						.setExitLatest("14:00:00"))
				.build();
		ServiceIntention train2 = ServiceIntention.newBuilder()
				.setId("t2")
				.setRoute("r2")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("A")
						.setEntryEarliest("11:45:00")
						.setExitLatest("12:00:00"))
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("B")
						.setExitLatest("15:00:00"))
				.build();
		
		Scenario scenario =
				Scenario.newBuilder()
					.setLabel("test")
					.setHash(1)
					.addServiceIntentions(train1)
					.addServiceIntentions(train2)
					.addRoutes(route1.setId("r1").build())
					.addRoutes(route1.setId("r2").build())
					.addResources(Resource.newBuilder().setId("Ra").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rb").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rc").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rd").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Re").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rg").setReleaseTimeSeconds(60))
					.build();
		
		Problem problem = new Problem(scenario);
		Solver solver = new Solver(problem, 0);
		Solution solution = solver.solve(DirectorType.HARD_COLLECTING);
		
		Truth.assertThat(solution).isEqualTo(parseSolution(
				"problem_instance_label: \"test\"\n" + 
				"problem_instance_hash: 1\n" + 
				"hash: 1\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t1\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:00:00\"\n" + 
				"    exit_time: \"12:20:00\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"A\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:20:00\"\n" + 
				"    exit_time: \"12:50:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#2\"\n" + 
				"    sequence_number: 2\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:50:30\"\n" + 
				"    exit_time: \"12:51:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#3\"\n" + 
				"    sequence_number: 3\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:51:30\"\n" + 
				"    exit_time: \"12:52:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#4\"\n" + 
				"    sequence_number: 4\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:52:30\"\n" + 
				"    exit_time: \"12:53:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#5\"\n" + 
				"    sequence_number: 5\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:53:30\"\n" + 
				"    exit_time: \"13:53:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#6\"\n" + 
				"    sequence_number: 6\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"13:53:30\"\n" + 
				"    exit_time: \"13:54:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#7\"\n" + 
				"    sequence_number: 7\n" + 
				"    section_requirement: \"B\"\n" + 
				"  }\n" + 
				"}\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t2\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:45:00\"\n" + 
				"    exit_time: \"11:46:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"A\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:46:00\"\n" + 
				"    exit_time: \"12:16:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#2\"\n" + 
				"    sequence_number: 2\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:16:00\"\n" + 
				"    exit_time: \"12:17:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#3\"\n" + 
				"    sequence_number: 3\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:17:00\"\n" + 
				"    exit_time: \"12:18:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#8\"\n" + 
				"    sequence_number: 4\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:18:00\"\n" + 
				"    exit_time: \"12:56:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#9\"\n" + 
				"    sequence_number: 5\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:56:15\"\n" + 
				"    exit_time: \"13:56:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#10\"\n" + 
				"    sequence_number: 6\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"13:56:15\"\n" + 
				"    exit_time: \"13:57:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#5\"\n" + 
				"    sequence_number: 7\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"13:57:15\"\n" + 
				"    exit_time: \"14:57:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#6\"\n" + 
				"    sequence_number: 8\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"14:57:15\"\n" + 
				"    exit_time: \"14:58:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#7\"\n" + 
				"    sequence_number: 9\n" + 
				"    section_requirement: \"B\"\n" + 
				"  }\n" + 
				"}")); 
	}
	
	@Test
	public void threeTrainsOnSlowFastPathGraph() throws Exception {
		Route.Builder route = Route.newBuilder();
		route.addRoutePaths(path(
				section(60, "A", "", ""),
				section(600, "", "", "", "Ra"),
				section(60, "", "", "X1", "Ra", "Rb", "Re"),
				section(60, "", "X1" ,"X2", "Rb", "Rc"),
				section(60, "", "X2", "", "Rc", "Rd", "Rg"),
				section(3600, "", "", "", "Rd"),
				section(60, "B", "", "")));
		route.addRoutePaths(path(
				section(60, "", "X1", "", "Re"),
				section(1800, ""),
				section(60, "", "" ,"X2", "Rg")));
		
		resetSectionSequenceNumber();

		ServiceIntention train1 = ServiceIntention.newBuilder()
				.setId("t1")
				.setRoute("r1")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("A")
						.setEntryEarliest("12:00:00"))
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("B")
						.setExitLatest("13:25:00"))
				.build();
		ServiceIntention train2 = ServiceIntention.newBuilder()
				.setId("t2")
				.setRoute("r2")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("A")
						.setEntryEarliest("11:55:00")
						.setExitLatest("12:00:00"))
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("B")
						.setExitLatest("17:00:00"))
				.build();
		ServiceIntention train3 = ServiceIntention.newBuilder()
				.setId("t3")
				.setRoute("r3")
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("A")
						.setEntryEarliest("11:45:00")
						.setExitLatest("12:00:00"))
				.addSectionRequirements(SectionRequirement.newBuilder()
						.setSectionMarker("B")
						.setExitLatest("17:00:00"))
				.build();		
		
		Scenario scenario =
				Scenario.newBuilder()
					.setLabel("test")
					.setHash(1)
					.addServiceIntentions(train1)
					.addServiceIntentions(train2)
					.addServiceIntentions(train3)
					.addRoutes(route.setId("r1").build())
					.addRoutes(route.setId("r2").build())
					.addRoutes(route.setId("r3").build())
					.addResources(Resource.newBuilder().setId("Ra").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rb").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rc").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rd").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Re").setReleaseTimeSeconds(60))
					.addResources(Resource.newBuilder().setId("Rg").setReleaseTimeSeconds(60))
					.build();
		
		Problem problem = new Problem(scenario);
		Solver solver = new Solver(problem, 0);
		Solution solution = solver.solve(DirectorType.HARD_COLLECTING);
		
		Truth.assertThat(solution).isEqualTo(parseSolution(
				"problem_instance_label: \"test\"\n" + 
				"problem_instance_hash: 1\n" + 
				"hash: 1\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t1\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:00:00\"\n" + 
				"    exit_time: \"12:10:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"A\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:10:30\"\n" + 
				"    exit_time: \"12:20:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#2\"\n" + 
				"    sequence_number: 2\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:20:30\"\n" + 
				"    exit_time: \"12:21:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#3\"\n" + 
				"    sequence_number: 3\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:21:30\"\n" + 
				"    exit_time: \"12:22:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#4\"\n" + 
				"    sequence_number: 4\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:22:30\"\n" + 
				"    exit_time: \"12:23:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#5\"\n" + 
				"    sequence_number: 5\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:23:30\"\n" + 
				"    exit_time: \"13:23:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#6\"\n" + 
				"    sequence_number: 6\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"13:23:30\"\n" + 
				"    exit_time: \"13:24:30\"\n" + 
				"    route: \"r1\"\n" + 
				"    route_section_id: \"r1#7\"\n" + 
				"    sequence_number: 7\n" + 
				"    section_requirement: \"B\"\n" + 
				"  }\n" + 
				"}\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t2\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:55:00\"\n" + 
				"    exit_time: \"11:58:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"A\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:58:15\"\n" + 
				"    exit_time: \"12:08:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#2\"\n" + 
				"    sequence_number: 2\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:08:15\"\n" + 
				"    exit_time: \"12:09:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#3\"\n" + 
				"    sequence_number: 3\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:09:15\"\n" + 
				"    exit_time: \"12:10:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#8\"\n" + 
				"    sequence_number: 4\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:10:15\"\n" + 
				"    exit_time: \"12:40:15\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#9\"\n" + 
				"    sequence_number: 5\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"12:40:15\"\n" + 
				"    exit_time: \"13:25:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#10\"\n" + 
				"    sequence_number: 6\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"13:25:00\"\n" + 
				"    exit_time: \"13:26:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#5\"\n" + 
				"    sequence_number: 7\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"13:26:00\"\n" + 
				"    exit_time: \"14:26:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#6\"\n" + 
				"    sequence_number: 8\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"14:26:00\"\n" + 
				"    exit_time: \"14:27:00\"\n" + 
				"    route: \"r2\"\n" + 
				"    route_section_id: \"r2#7\"\n" + 
				"    sequence_number: 9\n" + 
				"    section_requirement: \"B\"\n" + 
				"  }\n" + 
				"}\n" + 
				"train_runs {\n" + 
				"  service_intention_id: \"t3\"\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:45:00\"\n" + 
				"    exit_time: \"11:46:00\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#1\"\n" + 
				"    sequence_number: 1\n" + 
				"    section_requirement: \"A\"\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:46:00\"\n" + 
				"    exit_time: \"11:56:00\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#2\"\n" + 
				"    sequence_number: 2\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:56:00\"\n" + 
				"    exit_time: \"11:57:00\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#3\"\n" + 
				"    sequence_number: 3\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:57:00\"\n" + 
				"    exit_time: \"11:58:00\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#8\"\n" + 
				"    sequence_number: 4\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"11:58:00\"\n" + 
				"    exit_time: \"14:14:45\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#9\"\n" + 
				"    sequence_number: 5\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"14:14:45\"\n" + 
				"    exit_time: \"15:12:30\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#10\"\n" + 
				"    sequence_number: 6\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"15:12:30\"\n" + 
				"    exit_time: \"15:13:30\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#5\"\n" + 
				"    sequence_number: 7\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"15:13:30\"\n" + 
				"    exit_time: \"16:13:30\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#6\"\n" + 
				"    sequence_number: 8\n" + 
				"  }\n" + 
				"  train_run_sections {\n" + 
				"    entry_time: \"16:13:30\"\n" + 
				"    exit_time: \"16:14:30\"\n" + 
				"    route: \"r3\"\n" + 
				"    route_section_id: \"r3#7\"\n" + 
				"    sequence_number: 9\n" + 
				"    section_requirement: \"B\"\n" + 
				"  }\n" + 
				"}")); 
		
	}

	

}
