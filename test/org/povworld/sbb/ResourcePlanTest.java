package org.povworld.sbb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.povworld.collection.List;
import org.povworld.collection.common.Interval;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.collection.mutable.HashSet;
import org.povworld.sbb.GraphResourceOccupations.ResourceOccupation;
import org.povworld.sbb.ResourcePlan.ConflictIntervalSelection;
import org.povworld.sbb.ResourcePlan.Max;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

import com.google.common.truth.Correspondence;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.MapSubject.UsingCorrespondence;
import com.google.common.truth.Truth;

public class ResourcePlanTest {
	
	private static final Node NODE_17 = new Node(17);

	private static final Node NODE_16 = new Node(16);

	private static final Node NODE_15 = new Node(15);

	private static final Node NODE_12 = new Node(12);
	
	private static final Edge EDGE_12_15 = edge(NODE_12, NODE_15);
	private static final Edge EDGE_12_16 = edge(NODE_12, NODE_16);
	private static final Edge EDGE_12_17 = edge(NODE_12, NODE_17);

	private static final double EPS = Util.WEIGHT_EPS;
	
	private static final String RESOURCE = "test-resource";
	
	// Must be set in test!
	private ResourcePlan rp = null;
	
	private ResourcePlan createResourcePlan(int releaseTimeSeconds, ResourceOccupation... occupations) {
		HashMultiMap<String, ResourceOccupation> occupationMap = new HashMultiMap<>();
		for(ResourceOccupation occupation: occupations) {
			occupationMap.put(occupation.intention, occupation);
		}
		return new ResourcePlan(RESOURCE, releaseTimeSeconds, 1.0 /* boost */, occupationMap);		
	}
	
	private OccupationTimes occupation(int entryEarliest, int exitLatest, int minDuration) {
		return occupation(entryEarliest, exitLatest, minDuration, 1.0);
	}
	
	private OccupationTimes occupation(int entryEarliest, int exitLatest, int minDuration, double weight) {
		int entryLatest = exitLatest - minDuration;
		int exitEarliest = entryEarliest + minDuration;
		return new OccupationTimes(
				entryEarliest, 
				exitEarliest,
				entryLatest,
				exitLatest,
				weight);
	}
	
	private static UsingCorrespondence<Number,Number> assertDensity(ResourcePlan rp) {
		return Truth.assertThat(rp.getDensities()).comparingValuesUsing(Correspondence.tolerance(EPS));
	}
	
	private static Edge edge(Node start, Node end) {
		return new Edge(start, end);
	}
	
	private static ResourceOccupation ro(String intention, int minDuration) {
		return new ResourceOccupation(intention, RESOURCE, EDGE_12_15, EDGE_12_15,
				ImmutableCollections.<Edge>listOf(EDGE_12_15), minDuration);
	}
	
	private final ResourceOccupation OCC_1A_50S = 
			new ResourceOccupation("i1", RESOURCE, EDGE_12_15, EDGE_12_15, ImmutableCollections.<Edge>listOf(EDGE_12_15), 50);
	private final ResourceOccupation OCC_1B_40S = 
			new ResourceOccupation("i1", RESOURCE, EDGE_12_16, EDGE_12_16, ImmutableCollections.<Edge>listOf(EDGE_12_16), 40);
	private final ResourceOccupation OCC_1C_40S = 
			new ResourceOccupation("i1", RESOURCE, EDGE_12_17, EDGE_12_17, ImmutableCollections.<Edge>listOf(EDGE_12_17), 40);
	private final ResourceOccupation OCC_2A_40S = 
			new ResourceOccupation("i2", RESOURCE, EDGE_12_17, EDGE_12_17, ImmutableCollections.<Edge>listOf(EDGE_12_17), 40);
	
	private IntentionRestrictionQueue queue = new IntentionRestrictionQueue();
	
	@Test
	public void notHasConflictsSingleOccupation() {
		rp = createResourcePlan(0, OCC_1A_50S);
		assertFalse(rp.hasConflicts());
		rp.set(OCC_1A_50S, occupation(1000, 1400, 100), queue);
		assertFalse(rp.hasConflicts());
		assertNull(rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
	}
	
	@Test
	public void notHasConflictsSameIntention() {
		rp = createResourcePlan(0, OCC_1A_50S, OCC_1B_40S);
		rp.set(OCC_1A_50S, occupation(1000, 1400, 200, 0.5), queue);
		rp.set(OCC_1B_40S, occupation(1100, 1410, 200, 0.5), queue);
		assertFalse(rp.hasConflicts());
		assertNull(rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
	}
	
	@Test
	public void notHasConflictsNonOverlapping() {
		rp = createResourcePlan(0, OCC_1A_50S, OCC_2A_40S);
		rp.set(OCC_1A_50S, occupation(1000, 1400, 200), queue);
		rp.set(OCC_2A_40S, occupation(1700, 1900, 200), queue);
		assertFalse(rp.hasConflicts());
		assertNull(rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));
	}
	
	@Test
	public void hasConflictsOverlapping() {
		rp = createResourcePlan(0, OCC_1A_50S, OCC_2A_40S);
		rp.set(OCC_1A_50S, occupation(1000, 1800, 200), queue);
		rp.set(OCC_2A_40S, occupation(1700, 1900, 200), queue);
		assertTrue(rp.hasConflicts());
		
		assertEquals(new Max(new Interval(1700, 1800), 1.1, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
		assertEquals(new Max(new Interval(1750, 1750), 1.1, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
		assertEquals(new Max(new Interval(1750, 1750), 1.1, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));
	}
	
	@Test
	public void removeSingleOccupation() {
		rp = createResourcePlan(0, OCC_1A_50S, OCC_2A_40S);
		rp.set(OCC_1A_50S, occupation(1000, 1400, 100, 0.5), queue);
		rp.remove(OCC_1A_50S, queue);
		assertFalse(rp.hasConflicts());
		assertNull(rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
	}
	
	@Test
	public void removeConflicting() {
		rp = createResourcePlan(0, OCC_1A_50S, OCC_2A_40S);
		rp.set(OCC_1A_50S, occupation(1000, 1800, 200, 0.75), queue);
		rp.set(OCC_2A_40S, occupation(1700, 1900, 200, 0.75), queue);
		rp.remove(OCC_1A_50S, queue);
		assertFalse(rp.hasConflicts());		
	}
	
	@Test
	public void removeMutliPathOccupation() {
		rp = createResourcePlan(0, OCC_1A_50S, OCC_1B_40S, OCC_1C_40S, OCC_2A_40S);
		rp.set(OCC_1A_50S, occupation(1000, 1400, 200, 0.25), queue);
		rp.set(OCC_1B_40S, occupation(1100, 1410, 200, 0.25), queue);
		rp.set(OCC_1C_40S, occupation(1100, 1400, 200, 0.25), queue);
		rp.set(OCC_2A_40S, occupation(900, 1050, 50), queue);
		rp.remove(OCC_1B_40S, queue);
		assertTrue(rp.hasConflicts());
		rp.remove(OCC_1A_50S, queue);
		assertFalse(rp.hasConflicts());
	}
	
	@Test
	public void calculateDensitiesEmpty() {
		rp = createResourcePlan(0);
		Truth.assertThat(rp.getDensities()).isEmpty();
	}
	
	@Test
	public void calculateDensitiesSingleOccupation() {
		ResourceOccupation ro2 = ro("i2", 50);
		rp = createResourcePlan(0, OCC_1A_50S, ro2);
		rp.set(OCC_1A_50S, occupation(1000, 1400, 50), queue);
		rp.set(ro2, occupation(1000, 1400, 50), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1050, 0.5,
				1350, 0.5,
				1400, 0.0);
	}
	
	@Test
	public void calculateDensitiesSingleOccupationWithReleaseTime() {
		rp = createResourcePlan(10, OCC_1B_40S, OCC_2A_40S);
		rp.set(OCC_1B_40S, occupation(1000, 1390, 40), queue);
		rp.set(OCC_2A_40S, occupation(1000, 1390, 40), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1050, 0.5,
				1350, 0.5,
				1400, 0.0);
	}
	
	@Test
	public void calculateDensitiesConsecutiveOccupations() {
		ResourceOccupation ro1 = ro("i1", 100);
		ResourceOccupation ro2 = ro("i2", 50);
		ResourceOccupation ro3 = ro("i3", 150);
		rp = createResourcePlan(0, ro1, ro2, ro3);
		rp.set(ro1, occupation(1000, 1400, 100), queue);
		rp.set(ro2, occupation(1500, 1600, 50), queue);
		rp.set(ro3, occupation(1000, 1600, 150), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1100, 5.0/6,
				1150, 1.0,
				1300, 1.0,
				1400, 0.5,
				1450, 0.5,
				1500, 1.0/3,
				1550, 7.0/6,
				1600, 0.0);
	}
	
	@Test
	public void calculateDensitiesOverlappingOccupations() {
		ResourceOccupation ro1 = ro("i1", 200);
		ResourceOccupation ro2 = ro("i2", 200);
		rp = createResourcePlan(0, ro1, ro2);
		rp.set(ro1, occupation(1000, 1800, 200), queue);
		rp.set(ro2, occupation(1500, 2000, 200), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1200, 0.5,
				1500, 0.5,
				1600, 0.9,
				1700, 1.05,
				1800, 0.8,
				2000, 0.0);
		
		assertEquals(new Max(new Interval(1600, 1700), 1.05, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
		assertEquals(new Max(new Interval(1650, 1650), 1.05, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
		assertEquals(new Max(new Interval(1700, 1700), 1.05, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));
	}
	
	@Test
	public void calculateDensitiesNestedOccupations() {
		ResourceOccupation ro1 = ro("i1", 250);
		ResourceOccupation ro2 = ro("i2", 300);
		rp = createResourcePlan(0, ro1, ro2);
		rp.set(ro1, occupation(1000, 2000, 250), queue);
		rp.set(ro2, occupation(1200, 1800, 300), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1200, 0.4,
				1250, 2.0/3,
				1500, 1.5,
				1750, 2.0/3,
				1800, 0.4,
				2000, 0.0);
		
		assertEquals(new Max(new Interval(1250, 1500), 1.5, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
		assertEquals(new Max(new Interval(1375, 1375), 1.5, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
		assertEquals(new Max(new Interval(1500, 1500), 1.5, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));		
	}

	@Test
	public void calculateDensitiesIdenticalOccupations() {
		ResourceOccupation ro1 = ro("i1", 250);
		ResourceOccupation ro2 = ro("i2", 500);
		ResourceOccupation ro3 = ro("i3", 25);
		ResourceOccupation ro4 = ro("i4", 25);
		rp = createResourcePlan(0, ro1, ro2, ro3, ro4);
		rp.set(ro1, occupation(1000, 2000, 250), queue);
		rp.set(ro2, occupation(1000, 2000, 500), queue);
		rp.set(ro3, occupation(2100, 2200, 25), queue);
		rp.set(ro4, occupation(2100, 2200, 25), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1250, 1.0,
				1500, 1.5,
				1750, 1.0,
				2000, 0.0,
				2100, 0.0,
				2125, 1.0,
				2175, 1.0,
				2200, 0.0);
		
		assertEquals(new Max(new Interval(1250, 1500), 1.5, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
		assertEquals(new Max(new Interval(1375, 1375), 1.5, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
		assertEquals(new Max(new Interval(1500, 1500), 1.5, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));				
	}
	
	@Test
	public void calculateDensitiesExtendingIntentionOccupation() {
		ResourceOccupation ro1 = ro("i1", 100);
		ResourceOccupation ro2 = ro("i2", 100);
		ResourceOccupation ro3 = ro("i3", 200);
		rp = createResourcePlan(0, ro1, ro2, ro3);
		rp.set(ro1, occupation(1000, 1400, 100), queue);
		rp.set(ro2, occupation(1500, 1700, 100), queue);
		rp.set(ro3, occupation(1200, 1600, 200), queue);
		assertDensity(rp).containsExactly(
				1000, 0.0,
				1100, 0.5,
				1200, 0.5,
				1300, 1.0,
				1400, 1.0,
				1500, 0.5,
				1600, 1.0,
				1700, 0.0);
		
		assertEquals(new Max(new Interval(1300, 1400), 1.0, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
		assertEquals(new Max(new Interval(1350, 1350), 1.0, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
		assertEquals(new Max(new Interval(1350, 1350), 1.0, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));				
	}
	
	// Tests for ConflictSchedules
	
	private IterableSubject assertConflictSchedules(int point) {
		assertEquals(new Interval(point, point), rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder).interval);
		List<ConflictSchedule> schedules = rp.createConflictSchedules(new Interval(point, point));
		return Truth.assertThat(schedules);
	}
	
	@Test
	public void createConflictSchedulePrimaryStartsAtPoint() {
		ResourceOccupation ro1 = ro("i1", 15);
		ResourceOccupation ro2 = ro("i2", 100);
		rp = createResourcePlan(0, ro1, ro2);

		rp.set(ro1, occupation(1000, 1020, 15), queue); // most dense
		rp.set(ro2, occupation(800, 1001, 100), queue);
		
		assertEquals(new Interval(1000, 1000),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder).interval);
		
		assertConflictSchedules(1000)
			.containsExactly(
					schedule(decMax("i2", 1000, ro2)),
					schedule(incMin("i1", 1001, ro1)));
	}
	
	@Test
	public void createConflictScheduleSecondaryStartsAtPoint() {
		ResourceOccupation ro1 = ro("i1", 100);
		ResourceOccupation ro2 = ro("i2", 100);
		rp = createResourcePlan(0, ro1, ro2);
		
		rp.set(ro1, occupation(567, 767, 100), queue); // most dense
		rp.set(ro2, occupation(766, 1766, 100), queue); 
		
		assertEquals(new Max(new Interval(766, 767), 0.01, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.Interval));
		assertEquals(new Max(new Interval(766, 766), 0.01, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointAlwaysMid));
		assertEquals(new Max(new Interval(766, 766), 0.01, 2),
				rp.getMaxDensityRange(ConflictIntervalSelection.PointMidOrMaxIfNotBorder));		
		
		assertConflictSchedules(766)
			.containsExactly(
				schedule(incMin("i2", 767, ro2)),
				schedule(decMax("i1", 766, ro1)));
	}
	
	@Test
	public void createConflictScheduleOneSecondPrimaryInside() {
		ResourceOccupation ro1 = ro("i1", 1);
		ResourceOccupation ro2 = ro("i2", 100);
		rp = createResourcePlan(0, ro1, ro2);

		rp.set(ro1, occupation(1200, 1201, 1), queue); // most dense
		rp.set(ro2, occupation(800, 2000, 100), queue); 
		assertConflictSchedules(1200)
			.containsExactly(
					schedule(decMax("i2", 1200, ro2)),
					schedule(incMin("i2", 1201, ro2)));
	}
	
	@Test
	public void createMinimalConflictSchedulesConflictsOnlyInReleaseTime() {
		ResourceOccupation ro1 = ro("i1", 100);
		ResourceOccupation ro2 = ro("i2", 200);
		rp = createResourcePlan(10, ro1, ro2);
		
		rp.set(ro1, occupation(100, 1100, 100), queue);
		rp.set(ro2, occupation(1109, 2000, 200), queue); // most dense
		assertConflictSchedules(1109)
			.containsExactly(
				// i2 uses the conflict interval. i1 must be before.
				schedule(decMax("i1", 1099, ro1)),
				// i2 is after the conflict interval. no restriction on i1.
				schedule(incMin("i2", 1110, ro2)));
	}
	
//	@Test
//	public void createCondlictScheduleThirdConflictStartsAtMidpoint() {
//		ResourceOccupation ro1 = ro("i1", 100);
//		ResourceOccupation ro2 = ro("i2", 100);
//		ResourceOccupation ro3 = ro("i3", 10);
//		rp = createResourcePlan(0, ro1, ro2, ro3);
//		
//		rp.set(ro1, occupation(360, 500, 100), queue);
//		rp.set(ro2, occupation(400, 1000, 100), queue); // most dense
//		rp.set(ro3, occupation(450, 1000, 10), queue);
//		
//		assertConflictSchedules(450)
//			.containsExactly(
//					schedule(decMax("i1", 450, ro1)),
//					schedule(incMin("i2", 450, ro2)));
//	}
	
	@Test
	public void createMinimalConflictSchedulesSingleSecondConflict() {
		ResourceOccupation ro1 = ro("i1", 112);
		ResourceOccupation ro2 = ro("i2", 137);
		rp = createResourcePlan(0, ro1, ro2);
		
		rp.set(ro1, occupation(488, 761, 112), queue);
		rp.set(ro2, occupation(760, 1001, 137), queue); // most dense
		assertConflictSchedules(760)
			.containsExactly(
				// i2 uses the conflict interval. i1 must be before.
				schedule(decMax("i1", 760, ro1)),
				// i2 is after the conflict interval. no restriction on i1.
				schedule(incMin("i2", 761, ro2)));
	}
	
	@Test
	public void createMinimalConflictSchedulesNoConflictSolution() {
		ResourceOccupation ro1 = ro("i1", 125);
		ResourceOccupation ro2 = ro("i2", 75);
		rp = createResourcePlan(10, ro1, ro2);
		rp.set(ro1, occupation(1000, 1500, 125), queue); // most dense
		rp.set(ro2, occupation(1200, 1800, 75), queue);
		assertConflictSchedules(1330)
			.containsExactly(
				// i1 uses critical interval. i2 can be before or after
				schedule(decMax("i2", 1320, ro2)),
				schedule(incMin("i2", 1330, ro2)),
				// i1 is before critical interval. i2 follows i1.
				schedule(decMax("i1", 1320, ro1)),
				// i1 is after critical interval. i2 before i1.
				schedule(incMin("i1", 1330, ro1))
			);
	}
	
	@Test
	public void createMinimalConflictSchedulesThreeConflictsBorderSchedule() {
		ResourceOccupation ro1 = ro("i1", 250);
		ResourceOccupation ro2 = ro("i2", 50);
		ResourceOccupation ro3 = ro("i3", 500);
		rp = createResourcePlan(10, ro1, ro2, ro3);
		rp.set(ro1, occupation(1000, 1500, 250), queue);
		rp.set(ro2, occupation(1100, 1300, 50), queue); // ignored
		rp.set(ro3, occupation(1200, 2000, 500), queue); // most dense
		assertConflictSchedules(1250)
			.containsExactly(
				// i3 cannot use conflict range. There is no room to fit i1 before or after.
				// i3 is after conflict range. i1, i2 before i3.
				schedule(incMin("i3", 1260, ro3))
				// i3 cannot be before conflict range.
			);
	}
	
	@Test
	public void createMinimalConflictSchedulesThreeConflictsMiddleSchedule() {
		ResourceOccupation ro1 = ro("i1", 110);
		ResourceOccupation ro2 = ro("i2", 100);
		ResourceOccupation ro3 = ro("i3", 40);
		rp = createResourcePlan(10, ro1, ro2, ro3);
		rp.set(ro1, occupation(900, 1500, 110), queue);
		rp.set(ro2, occupation(1100, 1350, 100), queue); // most dense
		rp.set(ro3, occupation(1200, 2000, 40), queue); // ignored
		assertConflictSchedules(1250)
			.containsExactly(
				// i2 uses critical interval. i1 can be before or after. i3 must be after.
				schedule(decMax("i1", 1240, ro1)),
				schedule(incMin("i1", 1250, ro1)),
				// i2 is before the critical interval. i3 is after as does not fit before.
				schedule(decMax("i2", 1240, ro2)),
				// i2 is after the critical interval. i1, i3 can be before or after but not both after.
				schedule(incMin("i2", 1250, ro2))
			);
	}
	
	@Test
	public void createMinimalConflictSchedulesNoSchedules() {
		ResourceOccupation ro1 = ro("i1", 550);
		ResourceOccupation ro2 = ro("i2", 400);
		rp = createResourcePlan(10, ro1, ro2);
		rp.set(ro1, occupation(1000, 2000, 550), queue);
		rp.set(ro2, occupation(1200, 1800, 400), queue); // most dense
		assertConflictSchedules(1505).isEmpty();
	}
	
	@Test
	public void createMinimalConflictSchedulesAlternativePaths() {
		ResourceOccupation ro1a = ro("i1", 200);
		ResourceOccupation ro1b = ro("i1", 200);
		ResourceOccupation ro2 = ro("i2", 150);

		rp = createResourcePlan(10, ro1a, ro1b, ro2);
		rp.set(ro1a, occupation(1000, 1400, 200, 0.5), queue);
		rp.set(ro1b, occupation(1300, 1700, 200, 0.5), queue);
		rp.set(ro2, occupation(1400, 1600, 150), queue); // most dense
		assertConflictSchedules(1475)
			.containsExactly(
				// i2 uses critical interval. roi1b does not fit before.
				schedule(decMax("i1", 1440, ro1b))
			);
	}
	
	@Test
	public void createMinimalConflictSchedulesFixedTrain() {
		ResourceOccupation ro1 = ro("i1", 400);
		ResourceOccupation ro2 = ro("i2", 400);
		rp = createResourcePlan(10, ro1, ro2);
		rp.set(ro1, occupation(1000, 1400, 400), queue); // most dense
		rp.set(ro2, occupation(500, 2000, 400), queue);
		assertConflictSchedules(1205)
			.containsExactly(
				schedule(decMax("i2", 990, ro2)),
				schedule(incMin("i2", 1410, ro2))
			);
	}
	
	private IntentionRestriction incMin(String intention, int time, ResourceOccupation... occupations) {
		HashSet<Edge> edges = new HashSet<>();
		for(ResourceOccupation ro: occupations) {
			edges.add(ro.start);
		}
		return new IntentionRestriction.IncreaseMinTimeRestriction(intention, time, ImmutableCollections.asSet(edges));
	}
	
	private IntentionRestriction decMax(String intention, int time, ResourceOccupation... occupations) {
		HashSet<Edge> edges = new HashSet<>();
		for(ResourceOccupation ro: occupations) {
			edges.add(ro.end);
		}
		return new IntentionRestriction.DecreaseMaxTimeRestriction(intention, time, ImmutableCollections.asSet(edges));
	}
	
	private IntentionRestriction remove(String intention, ResourceOccupation... occupations) {
		HashSet<Edge> edges = new HashSet<>();
		for(ResourceOccupation ro: occupations) {
			edges.addAll(ro.flow);
		}
		return new IntentionRestriction.MarkPathInfeasibleRestriction(intention, ImmutableCollections.asSet(edges));
	}
	
	private ConflictSchedule schedule(IntentionRestriction... restrictions) {
		return new ConflictSchedule(ImmutableCollections.listOf(restrictions), 0.0);
	}
	
}
