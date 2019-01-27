package borg.ed.neutronhighway.aystar;

import java.util.Comparator;

/**
 * LeastJumpsComparator
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class LeastJumpsComparator implements Comparator<Path> {

	private final float directDistance;
	private final float maxJumpRangeBoosted;

	public LeastJumpsComparator(float directDistance, float maxJumpRangeBoosted) {
		this.directDistance = directDistance;
		this.maxJumpRangeBoosted = maxJumpRangeBoosted;
	}

	@Override
	public int compare(Path p1, Path p2) {
		float lyRemaining1 = p1.getRemainingDistanceLy();
		float lyRemaining2 = p2.getRemainingDistanceLy();
		int jumpsRemaining1 = (int) (lyRemaining1 / this.maxJumpRangeBoosted);
		int jumpsRemaining2 = (int) (lyRemaining2 / this.maxJumpRangeBoosted);
		Integer totalJumps1 = p1.getTotalJumps() + jumpsRemaining1;
		Integer totalJumps2 = p2.getTotalJumps() + jumpsRemaining2;

		return totalJumps1.compareTo(totalJumps2);

		//        float percentRemaining1 = lyRemaining1 / this.directDistance; // This can be > 1 if we travelled away from the destination
		//        float percentRemaining2 = lyRemaining2 / this.directDistance; // This can be > 1 if we travelled away from the destination
		//        Float rating1 = totalJumps1 + percentRemaining1;
		//        Float rating2 = totalJumps2 + percentRemaining2;
		//
		//        return rating1.compareTo(rating2);
	}

}
