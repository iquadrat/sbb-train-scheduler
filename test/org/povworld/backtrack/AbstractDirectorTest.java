package org.povworld.backtrack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.annotation.CheckForNull;

import org.junit.Test;
import org.povworld.collection.List;
import org.povworld.collection.Set;
import org.povworld.collection.common.ObjectUtil;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.persistent.PersistentCollections;
import org.povworld.collection.persistent.PersistentMap;
import org.povworld.collection.persistent.PersistentTreeMap;
import org.povworld.collection.persistent.PersistentSet;

public abstract class AbstractDirectorTest {
	
	protected abstract <S,C,O> Director<S,C,O> createDirector(Executor<S,C,O> executor, @CheckForNull Booster<C> booster);

	private static class NumberConflict {
	}

	private static class Numbers {
		final PersistentSet<Integer> available;
		final PersistentSet<Integer> selected;

		Numbers(PersistentSet<Integer> available, PersistentSet<Integer> selected) {
			this.available = available;
			this.selected = selected;
		}

		int product() {
			int product = 1;
			for (int i : selected) {
				product *= i;
			}
			return product;
		}

		@Override
		public String toString() {
			return "selected: " + selected;
		}
	}

	private static class SelectNumber {
		final int number;

		SelectNumber(int number) {
			this.number = number;
		}

		@Override
		public String toString() {
			return String.valueOf(number);
		}
	}

	private static class NumberExecutor implements Executor<Numbers, NumberConflict, SelectNumber> {
		private int target;
		private int n;

		public NumberExecutor(int target, int n) {
			this.target = target;
			this.n = n;
		}

		@Override
		public List<SelectNumber> getOptions(Numbers state, NumberConflict conflict) {
			if (state.selected.size() >= n) {
				throw new IllegalStateException("Already selected " + n + " states!");
			}
			int current = state.product();
			ArrayList<SelectNumber> options = new ArrayList<>();
			for (int i : state.available) {
				if (current * i <= target) {
					options.push(new SelectNumber(i));
				}
			}
			return options;
		}

		@Override
		@CheckForNull
		public Numbers apply(Numbers state, SelectNumber option) {
			Numbers newState = new Numbers(state.available.without(option.number), state.selected.with(option.number));
			if (newState.selected.size() == n && state.product() != target) {
				return null;
			}
			return newState;
		}

		@Override
		public NumberConflict getWorstConflict(Numbers state) {
			if (state.selected.size() == n) {
				return null;
			}
			return new NumberConflict();
		}
	}

	@Test
	public void testNumberSelectionFindsSolution() {
		int target = 7 * 3 * 4 * 9 * 14;
		NumberExecutor executor = new NumberExecutor(target, 5);
		Director<Numbers, NumberConflict, SelectNumber> director = createDirector(executor, new Booster.SimpleBooster<>(1));

		Numbers solution = director.work(
				new Numbers(PersistentCollections.setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
						PersistentCollections.setOf()));

		assertEquals(target, solution.product());
	}

	@Test
	public void testNumberSelectionInsolvable() {
		int target = 7 * 3 * 4 * 9 * 17;
		NumberExecutor executor = new NumberExecutor(target, 5);
		Director<Numbers, NumberConflict, SelectNumber> director = createDirector(executor, new Booster.SimpleBooster<>(0));

		Numbers solution = director.work(
				new Numbers(PersistentCollections.setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
						PersistentCollections.setOf()));
		assertNull(solution);
	}

	private static class Conflict implements Comparable<Conflict> {
		final int n;
		final int fanout;
		final boolean critical;

		Conflict(int n, int fanout, boolean critical) {
			this.n = n;
			this.fanout = fanout;
			this.critical = critical;
		}

		@Override
		public int compareTo(Conflict o) {
			return Integer.compare(n, o.n);
		}
	}

	private static class SelectionState {
		final int selectedCritical;
		final int selectedBad;
		final PersistentMap<Integer, Conflict> available;

		SelectionState(int selectedCritcial, int selectedBad, PersistentMap<Integer, Conflict> available) {
			this.selectedCritical = selectedCritcial;
			this.selectedBad = selectedBad;
			this.available = available;
		}
	}
	
	private static class SelectOption {
		final int n;
		final boolean critical;
		final boolean bad;
		SelectOption(int n, boolean critical, boolean bad) {
			this.n = n;
			this.critical = critical;
			this.bad = bad;
		}
	}

	private static class SelectionExecutor implements Executor<SelectionState, Integer, SelectOption> {
		
		final Booster<Integer> booster;
		final int badMax;

		SelectionExecutor(Booster<Integer> booster, int badMax) {
			this.booster = booster;
			this.badMax = badMax;
		}
		
		@Override
		@CheckForNull
		public Integer getWorstConflict(SelectionState state) {
			if (state.available.isEmpty()) {
				return null;
			}
			Integer result = null;
			double worst = 0;
			for(Integer i: state.available.keys()) {
				double score = booster.getBoost(i) / i;
				if (score > worst) {
					result = i;
					worst = score;
				}
			}
			return result;
		}

		@Override
		public List<SelectOption> getOptions(SelectionState state, Integer conflict) {
			Conflict c = ObjectUtil.checkNotNull(state.available.get(conflict));
			ArrayList<SelectOption> result = new ArrayList<>(c.fanout);
			if (c.critical) {
				for (int i = 0; i < c.fanout; ++i) {
					result.push(new SelectOption(c.n, c.critical, i != c.fanout - 1));
				}
			} else {
				for (int i = 0; i < c.fanout; ++i) {
					result.push(new SelectOption(c.n, c.critical, false));
				}
			}
			return result;
		}

		@Override
		@CheckForNull
		public SelectionState apply(SelectionState state, SelectOption option) {
			int newCritical = state.selectedCritical + (option.critical ? 1 : 0);
			int newBad = state.selectedBad + (option.bad ? 1 : 0);
			if (newCritical == badMax && state.selectedBad > 0) {
				return null;
			}
			if (!state.available.containsKey(option.n)) {
				throw new IllegalStateException("Already decided on conflict " + option.n);
			}
			return new SelectionState(newCritical, newBad, state.available.without(option.n));
		}

	}
	
	private PersistentMap<Integer, Conflict> createConflicts(int n, int fanout, Set<Integer> badOnes) {
		PersistentMap<Integer, Conflict> result = PersistentTreeMap.empty(Integer.class);
		for(int i=0; i<n; ++i) {
			result = result.with(i, new Conflict(i, fanout, badOnes.contains(i)));
		}
		return result;
	}
	
	private final Booster<Integer> booster = new Booster.SimpleBooster<>(1.1);

	@Test
	public void testSelectAvoid2BadsClose() {
		PersistentMap<Integer, Conflict> conflicts = createConflicts(300, 4, ImmutableCollections.setOf(141, 146));
		SelectionExecutor executor = new SelectionExecutor(booster, 2);
		Director<SelectionState, Integer, SelectOption> director = createDirector(executor, booster);
		
		SelectionState solution = director.work(new SelectionState(0, 0, conflicts));
		assertEquals(0, solution.selectedBad);
	}
	
	@Test
	public void testSelectAvoid3BadsClose() {
		PersistentMap<Integer, Conflict> conflicts = createConflicts(300, 4,
				ImmutableCollections.setOf(111, 113, 117));
		SelectionExecutor executor = new SelectionExecutor(booster, 3);
		Director<SelectionState, Integer, SelectOption> director = createDirector(executor, booster);
		
		SelectionState solution = director.work(new SelectionState(0, 0, conflicts));
		assertEquals(0, solution.selectedBad);
	}
	
	@Test
	public void testSelectAvoid2BadsSpread() {
		PersistentMap<Integer, Conflict> conflicts = createConflicts(1000, 4, ImmutableCollections.setOf(141, 718));
		SelectionExecutor executor = new SelectionExecutor(booster, 2);
		Director<SelectionState, Integer, SelectOption> director = createDirector(executor, booster);
		
		SelectionState solution = director.work(new SelectionState(0, 0, conflicts));
		assertEquals(0, solution.selectedBad);
	}
	
	@Test
	public void testSelectAvoid3BadsSpread() {
		PersistentMap<Integer, Conflict> conflicts = createConflicts(1000, 4,
				ImmutableCollections.setOf(15, 321, 716));
		SelectionExecutor executor = new SelectionExecutor(booster, 3);
		Director<SelectionState, Integer, SelectOption> director = createDirector(executor, booster);
		
		SelectionState solution = director.work(new SelectionState(0, 0, conflicts));
		assertEquals(0, solution.selectedBad);
	}
	
	@Test
	public void testSelectAvoid4BadsSpread() {
		PersistentMap<Integer, Conflict> conflicts = createConflicts(1000, 4,
				ImmutableCollections.setOf(15, 333, 781, 833));
		SelectionExecutor executor = new SelectionExecutor(booster, 4);
		Director<SelectionState, Integer, SelectOption> director = createDirector(executor, booster);
		
		SelectionState solution = director.work(new SelectionState(0, 0, conflicts));
		assertEquals(0, solution.selectedBad);
	}
	
	@Test
	public void testSelectAvoid6BadsSpread() {
		PersistentMap<Integer, Conflict> conflicts = createConflicts(1000, 4,
				ImmutableCollections.setOf(15, 888, 778, 445, 999, 891));
		SelectionExecutor executor = new SelectionExecutor(booster, 6);
		Director<SelectionState, Integer, SelectOption> director = createDirector(executor, booster);
		
		SelectionState solution = director.work(new SelectionState(0, 0, conflicts));
		assertEquals(0, solution.selectedBad);
	}

}
