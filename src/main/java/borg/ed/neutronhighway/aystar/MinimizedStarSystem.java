package borg.ed.neutronhighway.aystar;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;

import borg.ed.galaxy.data.Coord;
import borg.ed.galaxy.model.Body;
import borg.ed.galaxy.model.StarSystem;

/**
 * MinimizedStarSystem
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class MinimizedStarSystem implements Serializable {

	private static final long serialVersionUID = -3492099076880874005L;

	private final String name;
	private final Coord coord;

	public MinimizedStarSystem(StarSystem starSystem) {
		this.name = starSystem.getName();
		this.coord = starSystem.getCoord();
	}

	public MinimizedStarSystem(Body star) {
		if (StringUtils.isEmpty(star.getStarSystemName())) {
			throw new IllegalArgumentException("starSystemName must not be empty");
		} else if (star.getCoord() == null) {
			throw new IllegalArgumentException("coord must not be null");
		} else {
			this.name = star.getStarSystemName();
			this.coord = star.getCoord();
		}
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
		MinimizedStarSystem other = (MinimizedStarSystem) obj;
		if (this.name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!this.name.equals(other.name)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return this.name.hashCode();
	}

	@Override
	public String toString() {
		return String.format("%s (%s)", this.getName(), this.getCoord());
	}

	public float distanceTo(MinimizedStarSystem other) {
		return this.getCoord().distanceTo(other.getCoord());
	}

	public String getName() {
		return this.name;
	}

	public Coord getCoord() {
		return this.coord;
	}

}
