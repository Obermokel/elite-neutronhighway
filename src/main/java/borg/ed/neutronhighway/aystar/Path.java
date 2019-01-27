package borg.ed.neutronhighway.aystar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import borg.ed.universe.exceptions.NonUniqueResultException;
import borg.ed.universe.model.StarSystem;
import borg.ed.universe.service.UniverseService;

/**
 * Path
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class Path implements Comparable<Path> {

	private Path prev = null;
	private MinimizedStarSystem minimizedStarSystem = null;
	private int totalJumps = 0;
	private float travelledDistanceLy = 0;
	private float remainingDistanceLy = 0;
	private float fuelLevel = 0;

	public Path(MinimizedStarSystem minimizedStarSystem, float remainingDistanceLy, float fuelLevel) {
		this.setMinimizedStarSystem(minimizedStarSystem);
		this.setRemainingDistanceLy(remainingDistanceLy);
		this.setFuelLevel(fuelLevel);
	}

	/**
	 * @param extraTravelledDistanceLy
	 *            From prev to starSystem, NOT in total
	 */
	public Path(Path prev, MinimizedStarSystem minimizedStarSystem, float remainingDistanceLy, float extraTravelledDistanceLy, float fuelLevel) {
		this.setPrev(prev);
		this.setMinimizedStarSystem(minimizedStarSystem);
		this.setRemainingDistanceLy(remainingDistanceLy);
		this.setTotalJumps(prev.getTotalJumps() + 1);
		this.setTravelledDistanceLy(prev.getTravelledDistanceLy() + extraTravelledDistanceLy);
		this.setFuelLevel(fuelLevel);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Path other = (Path) obj;
		if (!this.minimizedStarSystem.getName().equals(other.minimizedStarSystem.getName())) {
			return false;
		}
		//        if (this.totalJumps != other.totalJumps) {
		//            return false;
		//        }
		//        if (Float.floatToIntBits(this.travelledDistanceLy) != Float.floatToIntBits(other.travelledDistanceLy)) {
		//            return false;
		//        }
		return true;
	}

	@Override
	public int hashCode() {
		//        final int prime = 31;
		//        int result = 1;
		//        result = prime * result + (int) (this.minimizedStarSystem.getId().longValue() ^ (this.minimizedStarSystem.getId().longValue() >>> 32));
		//        result = prime * result + this.totalJumps;
		//        result = prime * result + Float.floatToIntBits(this.travelledDistanceLy);
		//        return result;
		return this.minimizedStarSystem.getName().hashCode();
	}

	@Override
	public int compareTo(Path other) {
		int byDistance = new Float(this.getTravelledDistanceLy()).compareTo(other.getTravelledDistanceLy());
		if (byDistance != 0) {
			return byDistance;
		} else {
			int byJumps = new Integer(this.getTotalJumps()).compareTo(other.getTotalJumps());
			return byJumps;
		}
	}

	public List<Path> toSortedList() {
		List<Path> sortedPaths = new ArrayList<>();
		Path p = this;
		while (p != null) {
			sortedPaths.add(p);
			p = p.getPrev();
		}
		Collections.reverse(sortedPaths);
		return sortedPaths;
	}

	public StarSystem getStarSystem(UniverseService universeService) throws NonUniqueResultException {
		return universeService.findStarSystemByName(this.getMinimizedStarSystem().getName());
	}

	public Path getPrev() {
		return this.prev;
	}

	public void setPrev(Path prev) {
		this.prev = prev;
	}

	public MinimizedStarSystem getMinimizedStarSystem() {
		return this.minimizedStarSystem;
	}

	public void setMinimizedStarSystem(MinimizedStarSystem minimizedStarSystem) {
		this.minimizedStarSystem = minimizedStarSystem;
	}

	public int getTotalJumps() {
		return this.totalJumps;
	}

	public void setTotalJumps(int totalJumps) {
		this.totalJumps = totalJumps;
	}

	public float getTravelledDistanceLy() {
		return this.travelledDistanceLy;
	}

	public void setTravelledDistanceLy(float travelledDistanceLy) {
		this.travelledDistanceLy = travelledDistanceLy;
	}

	public float getRemainingDistanceLy() {
		return this.remainingDistanceLy;
	}

	public void setRemainingDistanceLy(float remainingDistanceLy) {
		this.remainingDistanceLy = remainingDistanceLy;
	}

	public float getFuelLevel() {
		return this.fuelLevel;
	}

	public void setFuelLevel(float fuelLevel) {
		this.fuelLevel = fuelLevel;
	}

}
