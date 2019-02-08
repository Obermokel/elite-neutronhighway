package borg.ed.neutronhighway.aystar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import borg.ed.galaxy.data.Coord;
import borg.ed.galaxy.model.StarSystem;
import borg.ed.neutronhighway.helper.FuelAndJumpRangeLookup;

/**
 * AyStar
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class AyStar {

	static final Logger logger = LoggerFactory.getLogger(AyStar.class);

	private static final int SECTOR_SIZE = 50; // ly

	private MinimizedStarSystem goal = null;
	private PriorityQueue<Path> open = null;
	private Map<String, Path> openBySystem = null;
	private Set<String> closed = null;
	private Set<String> neutronStarIDs = null;
	private Map<Coord, List<MinimizedStarSystem>> starSystemsWithNeutronStarsBySector = null;
	private Map<Coord, List<MinimizedStarSystem>> starSystemsWithScoopableStarsBySector = null;
	private FuelAndJumpRangeLookup fuelJumpLUT = null;
	private float maxTotalDistanceLy = 0;
	private float maxJumpRangeBoosted = 0;
	private int counter = 0;
	private Path closestToGoalSoFar = null;

	public void initialize(StarSystem source, StarSystem goal, Set<MinimizedStarSystem> minimizedNeutronStarSystems,
			Set<MinimizedStarSystem> minimizedScoopableStarSystems, FuelAndJumpRangeLookup fuelJumpLUT) {
		if (!minimizedNeutronStarSystems.contains(new MinimizedStarSystem(goal)) && !minimizedScoopableStarSystems.contains(new MinimizedStarSystem(goal))) {
			throw new IllegalArgumentException("goal not in useable star systems");
		} else {
			this.maxJumpRangeBoosted = 4f * fuelJumpLUT.getJumpRangeFuelOpt();
			this.goal = new MinimizedStarSystem(goal);
			this.open = new PriorityQueue<>(new LeastJumpsComparator(source.distanceTo(goal), this.maxJumpRangeBoosted));
			this.openBySystem = new HashMap<>();
			this.closed = new HashSet<>();
			this.neutronStarIDs = minimizedNeutronStarSystems.stream().map(ss -> ss.getName()).collect(Collectors.toSet());
			this.starSystemsWithNeutronStarsBySector = mapBySector(minimizedNeutronStarSystems);
			this.starSystemsWithScoopableStarsBySector = mapBySector(minimizedScoopableStarSystems);
			this.fuelJumpLUT = fuelJumpLUT;
			this.maxTotalDistanceLy = 10.0f * source.distanceTo(goal);
			this.closestToGoalSoFar = null;

			this.open.add(new Path(new MinimizedStarSystem(source), source.distanceTo(goal), fuelJumpLUT.getMaxFuelTons()));
		}
	}

	public Path findPath() {
		while (this.open.size() > 0) {
			Path path = this.open.poll();

			if (path.getMinimizedStarSystem().equals(this.goal)) {
				// We reached our destination
				return path;
			}

			if (this.closed.contains(path.getMinimizedStarSystem().getName())) {
				// We already found a better path
				continue;
			} else {
				// Because we always poll the best path so far, the current path is
				// the best path to this system
				this.closed.add(path.getMinimizedStarSystem().getName());
				this.removeFromSector(path.getMinimizedStarSystem(), this.starSystemsWithScoopableStarsBySector);
				this.removeFromSector(path.getMinimizedStarSystem(), this.starSystemsWithNeutronStarsBySector);
			}

			if (this.closestToGoalSoFar == null || path.getRemainingDistanceLy() < this.closestToGoalSoFar.getRemainingDistanceLy()) {
				this.closestToGoalSoFar = path;
			}

			//            if (this.closed.size() % 10000 == 0) {
			//                // Periodic cleanup. Open queue may still contain systems which in the meantime have been reached. Remove those as they are now useless.
			//                Path[] temp = this.open.toArray(new Path[this.open.size()]);
			//                this.open.clear();
			//                for (Path p : temp) {
			//                    if (!this.closed.contains(p.getMinimizedStarSystem().getId())) {
			//                        this.open.offer(p);
			//                    }
			//                }
			//                logger.debug("Reduced open queue from " + temp.length + " to " + this.open.size() + " entries");
			//
			//                if (logger.isTraceEnabled()) {
			//                    logger.trace(String.format("Open: %,15d / Closed: %,15d || Closest so far: %s with %d jump(s), %.0fly travelled, %.0fly remaining", this.open.size(), this.closed.size(), this.closestToGoalSoFar.getMinimizedStarSystem().toString(),
			//                            this.closestToGoalSoFar.getTotalJumps(), this.closestToGoalSoFar.getTravelledDistanceLy(), this.closestToGoalSoFar.getRemainingDistanceLy()));
			//                }
			//            }

			List<MinimizedStarSystem> neighbours = this.findNeighbours(path);
			//logger.debug(String.format(Locale.US, "%6d neighbours for %s", neighbours.size(), path.getMinimizedStarSystem().getCoord().toString()));

			final float boostValue = this.neutronStarIDs.contains(path.getMinimizedStarSystem().getName()) ? 4.0f : 1.0f;
			for (int i = 0; i < neighbours.size(); i++) {
				MinimizedStarSystem neighbour = neighbours.get(i);
				float remainingDistanceLy = neighbour.distanceTo(this.goal);
				if (remainingDistanceLy >= path.getRemainingDistanceLy()) {
					continue;
				}
				float extraTravelledDistanceLy = path.getMinimizedStarSystem().distanceTo(neighbour);
				float fuelLevel = this.fuelJumpLUT.getMaxFuelTons(); // Scoop until full by default
				if (this.neutronStarIDs.contains(neighbour.getName())) {
					fuelLevel = path.getFuelLevel() - this.fuelJumpLUT.lookupFuelUsage(extraTravelledDistanceLy / boostValue, path.getFuelLevel()); // Subtract from prev
				}
				Path newPath = new Path(path, neighbour, remainingDistanceLy, extraTravelledDistanceLy, fuelLevel);
				if (newPath.getTravelledDistanceLy() + newPath.getRemainingDistanceLy() <= this.maxTotalDistanceLy) {
					Path oldPath = this.openBySystem.get(newPath.getMinimizedStarSystem().getName());
					if (oldPath == null) {
						this.open.offer(newPath);
						this.openBySystem.put(newPath.getMinimizedStarSystem().getName(), newPath);
					} else {
						int totalJumpsOldPath = oldPath.getTotalJumps() + (int) (oldPath.getRemainingDistanceLy() / this.maxJumpRangeBoosted);
						int totalJumpsNewPath = newPath.getTotalJumps() + (int) (newPath.getRemainingDistanceLy() / this.maxJumpRangeBoosted);
						if (totalJumpsNewPath < totalJumpsOldPath) {
							this.open.remove(oldPath);
							this.open.offer(newPath);
							this.openBySystem.put(newPath.getMinimizedStarSystem().getName(), newPath);
						}
					}
				}
			}

			if (++this.counter % 1000 == 0) {
				return this.closestToGoalSoFar;
			}
		}

		return null;
	}

	private List<MinimizedStarSystem> findNeighbours(Path path) {
		final MinimizedStarSystem currentStarSystem = path.getMinimizedStarSystem();
		final Coord currentCoord = currentStarSystem.getCoord();
		float safeFuelLevel = path.getFuelLevel(); // This is what the calculation says, but as we don't know the formula we should add some safety
		if (safeFuelLevel > this.fuelJumpLUT.getMaxFuelPerJump()) {
			safeFuelLevel = Math.min(this.fuelJumpLUT.getMaxFuelTons(), safeFuelLevel + 2.0f); // Add 2 extra tons to reduce the calculated jump distance
		} else {
			safeFuelLevel = Math.max(0.1f, safeFuelLevel - 2.0f); // Subtract 2 tons to reduce the calculated jump distance
		}
		final float currentUnboostedJumpRange = this.fuelJumpLUT.lookupMaxJumpRange(safeFuelLevel);

		// Do we have an overcharged FSD?
		final boolean haveSuperchargedFsd = this.neutronStarIDs.contains(currentStarSystem.getName()) ? true : false;

		// Do we need to scoop?
		boolean mustScoop = path.getFuelLevel() <= fuelJumpLUT.getMaxFuelPerJump();

		// Extra jump range because of empty tank?
		final float currentJumpRange = haveSuperchargedFsd ? 4 * currentUnboostedJumpRange : currentUnboostedJumpRange;

		// Find reachable systems
		List<MinimizedStarSystem> scoopableSystemsInCloseSectors = findSystemsBySector(this.starSystemsWithScoopableStarsBySector, currentCoord,
				currentJumpRange);
		List<MinimizedStarSystem> systemsInRange = new ArrayList<>(scoopableSystemsInCloseSectors.size());
		for (int i = 0; i < scoopableSystemsInCloseSectors.size(); i++) {
			MinimizedStarSystem s = scoopableSystemsInCloseSectors.get(i);
			//            if (!this.closed.contains(s.getId())) {
			if (s.distanceTo(currentStarSystem) <= currentJumpRange) {
				systemsInRange.add(s);
			}
			//            }
		}
		if (!mustScoop) {
			List<MinimizedStarSystem> neutronSystemsInCloseSectors = findSystemsBySector(this.starSystemsWithNeutronStarsBySector, currentCoord,
					currentJumpRange);
			for (int i = 0; i < neutronSystemsInCloseSectors.size(); i++) {
				MinimizedStarSystem s = neutronSystemsInCloseSectors.get(i);
				//                if (!this.closed.contains(s.getId())) {
				if (s.distanceTo(currentStarSystem) <= currentJumpRange) {
					systemsInRange.add(s);
				}
				//                }
			}
		}

		// Finished
		return systemsInRange;
	}

	private static List<MinimizedStarSystem> findSystemsBySector(Map<Coord, List<MinimizedStarSystem>> systemsBySector, Coord currentCoord,
			float currentJumpRange) {
		List<MinimizedStarSystem> result = new ArrayList<>();

		Coord currentSector = coordToSector(currentCoord);
		int nSideSectors = (int) Math.ceil(currentJumpRange / SECTOR_SIZE);
		for (int x = -nSideSectors; x <= nSideSectors; x++) {
			for (int y = -nSideSectors; y <= nSideSectors; y++) {
				for (int z = -nSideSectors; z <= nSideSectors; z++) {
					Coord sector = new Coord(currentSector.getX() + x * SECTOR_SIZE, currentSector.getY() + y * SECTOR_SIZE,
							currentSector.getZ() + z * SECTOR_SIZE);
					List<MinimizedStarSystem> systems = systemsBySector.get(sector);
					if (systems != null) {
						result.addAll(systems);
					}
				}
			}
		}
		//logger.debug("Found " + result.size() + " systems around sector " + currentSector + " with jump range = " + currentJumpRange);

		return result;
	}

	private void removeFromSector(MinimizedStarSystem starSystemToRemove, Map<Coord, List<MinimizedStarSystem>> starSystemsBySector) {
		Coord sector = coordToSector(starSystemToRemove.getCoord());
		List<MinimizedStarSystem> systemsInThisSector = starSystemsBySector.get(sector);
		if (systemsInThisSector != null) {
			systemsInThisSector.remove(starSystemToRemove);
		}
	}

	private Map<Coord, List<MinimizedStarSystem>> mapBySector(Collection<MinimizedStarSystem> starSystems) {
		Map<Coord, List<MinimizedStarSystem>> result = new HashMap<>();
		for (MinimizedStarSystem starSystem : starSystems) {
			Coord sector = coordToSector(starSystem.getCoord());
			List<MinimizedStarSystem> systemsInThisSector = result.get(sector);
			if (systemsInThisSector == null) {
				systemsInThisSector = new ArrayList<>();
				result.put(sector, systemsInThisSector);
			}
			systemsInThisSector.add(starSystem);
		}
		logger.debug(String.format(Locale.US, "Mapped %,d systems into %,d sectors", starSystems.size(), result.size()));
		return result;
	}

	private static Coord coordToSector(Coord coord) {
		return new Coord(((int) coord.getX() / SECTOR_SIZE) * SECTOR_SIZE, ((int) coord.getY() / SECTOR_SIZE) * SECTOR_SIZE,
				((int) coord.getZ() / SECTOR_SIZE) * SECTOR_SIZE);
	}

}
