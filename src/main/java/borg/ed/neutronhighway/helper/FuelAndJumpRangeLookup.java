package borg.ed.neutronhighway.helper;

import java.util.SortedMap;
import java.util.TreeMap;

public class FuelAndJumpRangeLookup {

	private final int maxFuelTons;
	private final int maxFuelKg;
	private final float maxFuelPerJump;
	private final float jumpRangeFuelFull;
	private final float jumpRangeFuelOpt;
	private final SortedMap<Integer, Float> fuelUsageByJumpPercent;
	private final SortedMap<Integer, Float> jumpRangeByFuelKg;

	public FuelAndJumpRangeLookup(int maxFuelTons, float maxFuelPerJump, float jumpRangeFuelFull, float jumpRangeFuelOpt) {
		this.maxFuelTons = maxFuelTons;
		this.maxFuelKg = maxFuelTons * 1000;
		this.maxFuelPerJump = maxFuelPerJump;
		this.jumpRangeFuelFull = jumpRangeFuelFull;
		this.jumpRangeFuelOpt = jumpRangeFuelOpt;
		this.fuelUsageByJumpPercent = buildFuelUsageLUT(maxFuelPerJump);
		this.jumpRangeByFuelKg = buildJumpRangeLUT(maxFuelTons, maxFuelPerJump, jumpRangeFuelFull, jumpRangeFuelOpt);
	}

	public int getMaxFuelTons() {
		return this.maxFuelTons;
	}

	int getMaxFuelKg() {
		return this.maxFuelKg;
	}

	public float getMaxFuelPerJump() {
		return this.maxFuelPerJump;
	}

	public float getJumpRangeFuelFull() {
		return this.jumpRangeFuelFull;
	}

	public float getJumpRangeFuelOpt() {
		return this.jumpRangeFuelOpt;
	}

	public float lookupMaxJumpRange(float fuelLevel) {
		int kgRounded = ((int) (fuelLevel * 10f)) * 100;
		if (kgRounded < 0) {
			return 0f;
		} else if (kgRounded > this.maxFuelKg) {
			return this.jumpRangeByFuelKg.get(this.maxFuelKg);
		} else {
			return this.jumpRangeByFuelKg.get(kgRounded);
		}
	}

	/**
	 * Convenience method which lookups the max jump range by the given fuel level, then simply
	 * calls {@link #lookupFuelUsageWithKnownMaxJumpRange(float, float)}. If you already know your
	 * current max jump range use the other method directly.
	 */
	public float lookupFuelUsage(float jumpDistance, float fuelLevel) {
		return this.lookupFuelUsageWithKnownMaxJumpRange(jumpDistance, this.lookupMaxJumpRange(fuelLevel));
	}

	/**
	 * @param jumpDistance
	 *      The distance you want to jump in Ly (0 &lt; jumpDistance &lt;= currentMaxJumpRange)
	 * @param currentMaxJumpRange
	 *      The maximum jump range you currently have in Ly
	 * @return
	 *      Fuel usage in tons
	 */
	public float lookupFuelUsageWithKnownMaxJumpRange(float jumpDistance, float currentMaxJumpRange) {
		int jumpPercent = ((int) (jumpDistance / currentMaxJumpRange * 1000f));
		if (jumpPercent < 0) {
			return 0f;
		} else if (jumpPercent > 1000) {
			return this.fuelUsageByJumpPercent.get(1000);
		} else {
			return this.fuelUsageByJumpPercent.get(jumpPercent);
		}
	}

	/**
	 * percent of max current jump range -&gt; fuel usage in tons
	 */
	private static SortedMap<Integer, Float> buildFuelUsageLUT(float maxFuelPerJump) {
		SortedMap<Integer, Float> result = new TreeMap<>();
		for (int percent = 0; percent <= 1000; percent += 1) {
			result.put(percent, estimateFuelUsage(percent / 1000f, maxFuelPerJump));
		}
		return result;
	}

	/**
	 * current fuel level in kg -&gt; max current jump range
	 */
	private static SortedMap<Integer, Float> buildJumpRangeLUT(int maxFuelTons, float maxFuelPerJump, float jumpRangeFuelFull, float jumpRangeFuelOpt) {
		SortedMap<Integer, Float> result = new TreeMap<>();
		for (int kg = 0; kg <= (maxFuelTons * 1000); kg += 100) {
			result.put(kg, estimateCurrentJumpRange(kg / 1000f, maxFuelTons, maxFuelPerJump, jumpRangeFuelFull, jumpRangeFuelOpt));
		}
		return result;
	}

	public static float estimateFuelUsage(float jumpDistance, float currentMaxJumpRange, float maxFuelPerJump) {
		return estimateFuelUsage(jumpDistance / currentMaxJumpRange, maxFuelPerJump);
	}

	private static float estimateFuelUsage(float jumpDistancePercentOfMax, float maxFuelPerJump) {
		return maxFuelPerJump * (float) Math.pow(jumpDistancePercentOfMax, 2.5);
	}

	public static float estimateCurrentJumpRange(float currentFuelLevel, int maxFuelTons, float maxFuelPerJump, float jumpRangeFuelFull,
			float jumpRangeFuelOpt) {
		if (currentFuelLevel >= maxFuelPerJump) {
			float fuelPercentOfMax = (currentFuelLevel - maxFuelPerJump) / (maxFuelTons - maxFuelPerJump);
			float extraJumpRange = jumpRangeFuelOpt - jumpRangeFuelFull;
			return jumpRangeFuelFull + ((1 - fuelPercentOfMax) * extraJumpRange);
		} else {
			// TODO: Need formula for less than maxFuelPerJump in tank
			float fuelPercentOfOpt = currentFuelLevel / maxFuelPerJump;
			return (float) Math.pow(fuelPercentOfOpt, 2.5) * jumpRangeFuelOpt;
		}
	}

}
