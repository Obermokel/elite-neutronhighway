package borg.ed.neutronhighway;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.util.CloseableIterator;

import borg.ed.neutronhighway.aystar.AyStar;
import borg.ed.neutronhighway.aystar.MinimizedStarSystem;
import borg.ed.neutronhighway.aystar.Path;
import borg.ed.neutronhighway.helper.FuelAndJumpRangeLookup;
import borg.ed.universe.UniverseApplication;
import borg.ed.universe.constants.StarClass;
import borg.ed.universe.data.Coord;
import borg.ed.universe.exceptions.NonUniqueResultException;
import borg.ed.universe.model.Body;
import borg.ed.universe.model.StarSystem;
import borg.ed.universe.service.UniverseService;

@Configuration
@Import(UniverseApplication.class)
public class NeutronHighwayApplication {

	static final Logger logger = LoggerFactory.getLogger(NeutronHighwayApplication.class);

	private static final ApplicationContext APPCTX = new AnnotationConfigApplicationContext(NeutronHighwayApplication.class);

	private static final File ROUTES_DIR = new File(System.getProperty("user.home"), "Google Drive\\Elite Dangerous\\Routes");

	public static void main(String[] args) throws NonUniqueResultException, IOException {
		//		final String fromName = "Sphiesi RR-N d6-0";
		//		final String toName = "Sphiesi HX-L d7-0";
		final String fromName = "Maridal";
		final String toName = "Colonia";

		// Type-10 Stock
		final int maxFuelTons = 96;
		final float maxFuelPerJump = 8.0f;
		final float jumpRangeFuelFull = 70.8f;
		final float jumpRangeFuelOpt = 81.59f;
		final FuelAndJumpRangeLookup fuelJumpLUT = new FuelAndJumpRangeLookup(maxFuelTons, maxFuelPerJump, jumpRangeFuelFull, jumpRangeFuelOpt);

		final UniverseService universeService = APPCTX.getBean(UniverseService.class);

		// Lookup source and destination
		StarSystem fromSystem = universeService.findStarSystemByName(fromName);
		StarSystem toSystem = universeService.findStarSystemByName(toName);
		logger.debug(String.format("%s → %s: %.0f Ly", fromSystem.toString(), toSystem.toString(), fromSystem.distanceTo(toSystem)));

		// Write a simple waypoints file
		final String baseFilename = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()) + " " + fromSystem.getName().replaceAll("[^\\w\\s\\-\\+\\.]", "_") + " to "
				+ toSystem.getName().replaceAll("[^\\w\\s\\-\\+\\.]", "_");
		writeWaypointsFile(fromSystem, toSystem, universeService);

		// Try to find a route
		Set<MinimizedStarSystem> minimizedNeutronStarSystems = new HashSet<>();
		Set<MinimizedStarSystem> minimizedScoopableStarSystems = new HashSet<>();
		minimizedScoopableStarSystems.add(new MinimizedStarSystem(fromSystem));
		minimizedScoopableStarSystems.add(new MinimizedStarSystem(toSystem));
		logger.debug("Loading known entry stars...");
		try (CloseableIterator<Body> stream = universeService.streamStarsWithin(-100000f, 100000f, -100000f, 100000f, -100000f, 100000f, /* isMainStar = */ true, null)) {
			while (stream.hasNext()) {
				Body star = stream.next();
				if (StringUtils.isNotEmpty(star.getStarSystemName()) && star.getCoord() != null && star.getStarClass() != null) {
					if (StarClass.N.equals(star.getStarClass())) {
						minimizedNeutronStarSystems.add(new MinimizedStarSystem(star));
					} else if (!StarClass.AEBE.equals(star.getStarClass()) && !star.getStarClass().name().startsWith("C") && !star.getStarClass().name().startsWith("D")
							&& !star.getStarClass().name().startsWith("W") && !StarClass.H.equals(star.getStarClass()) && !StarClass.L.equals(star.getStarClass())
							&& !StarClass.MS.equals(star.getStarClass()) && !StarClass.S.equals(star.getStarClass()) && !StarClass.T.equals(star.getStarClass())
							&& !StarClass.TTS.equals(star.getStarClass()) && !StarClass.Y.equals(star.getStarClass())) {
						minimizedScoopableStarSystems.add(new MinimizedStarSystem(star));
					}
				}
			}
		}
		logger.debug("Loading known systems...");
		try (CloseableIterator<StarSystem> stream = universeService.streamAllSystemsWithin(-100000f, 100000f, -100000f, 100000f, -100000f, 100000f)) {
			while (stream.hasNext()) {
				minimizedScoopableStarSystems.add(new MinimizedStarSystem(stream.next()));
			}
		}
		logger.debug("Total known neutron stars: " + minimizedNeutronStarSystems.size());

		AyStar ayStar = new AyStar();
		ayStar.initialize(fromSystem, toSystem, minimizedNeutronStarSystems, minimizedScoopableStarSystems, fuelJumpLUT);
		final long start = System.currentTimeMillis();
		Path path = null;
		while ((path = ayStar.findPath()) != null && !path.getMinimizedStarSystem().getName().equals(toSystem.getName())) {
			logger.debug("...searching...");
			//            routeViewPanel.updatePath(path);
			//            topViewPanel.updatePath(path);
			//            leftViewPanel.updatePath(path);
			//            frontViewPanel.updatePath(path);
		}
		final long end = System.currentTimeMillis();
		logger.info("Took " + DurationFormatUtils.formatDuration(end - start, "H:mm:ss"));
		if (path == null) {
			logger.warn("No path found");
			return;
		} else {
			//            routeViewPanel.updatePath(path);
			//            topViewPanel.updatePath(path);
			//            leftViewPanel.updatePath(path);
			//            frontViewPanel.updatePath(path);
			logger.info("Found path with " + path.getTotalJumps() + " jumps");
		}
		List<Path> sortedPaths = path.toSortedList();
		float lastTravelledDistance = 0f;
		float maxJumpDistance = 0f;
		for (Path p : sortedPaths) {
			float jumpDistance = path.getTravelledDistanceLy() - lastTravelledDistance;
			maxJumpDistance = Math.max(maxJumpDistance, jumpDistance);
			logger.info(String.format(Locale.US, "%-30s %,.1f Ly (+%,.1f Ly)", p.getMinimizedStarSystem().getName(), path.getTravelledDistanceLy(), jumpDistance));
			lastTravelledDistance = path.getTravelledDistanceLy();
		}
		logger.info("maxJumpDistance=" + maxJumpDistance);

		Route route = Route.fromPath(sortedPaths, fuelJumpLUT/*, journal*/, universeService);

		// Write route as VoiceAttack TXT file
		FileUtils.write(new File(ROUTES_DIR, baseFilename + " Route.txt"), route.toVoiceAttackTxt(), "UTF-8");

		// Write route as human readable HTML file
		Date eddbDumpDate = new Date(new File(System.getProperty("user.home"), ".eddbdata/systems.csv").lastModified());
		int nKnownArrivalNeutronStars = minimizedNeutronStarSystems.size();
		FileUtils.write(new File(ROUTES_DIR, baseFilename + " Route.html"), route.toHumanReadableHtml(eddbDumpDate, nKnownArrivalNeutronStars), "UTF-8");
	}

	private static void writeWaypointsFile(StarSystem fromSystem, StarSystem toSystem, UniverseService universeService) throws IOException {
		float directDistance = fromSystem.distanceTo(toSystem);
		if (directDistance > 999) {
			File waypointsFile = new File(ROUTES_DIR, new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()) + " Waypoints "
					+ fromSystem.getName().replaceAll("[^\\w\\s\\-\\+\\.]", "_") + " to " + toSystem.getName().replaceAll("[^\\w\\s\\-\\+\\.]", "_") + ".txt");
			int waypointsNeeded = (int) (directDistance / 2000) + 1;
			float waypointSeparation = directDistance / waypointsNeeded;
			FileUtils.write(waypointsFile,
					String.format(Locale.US, "Direct distance: %.0f Ly\nWaypoints needed: %d\nWaypoint separation: %.0f Ly\n\n", directDistance, waypointsNeeded, waypointSeparation), "UTF-8",
					false);
			Coord fromCoord = fromSystem.getCoord();
			Coord toCoord = toSystem.getCoord();
			float stepX = (toCoord.getX() - fromCoord.getX()) / waypointsNeeded;
			float stepY = (toCoord.getY() - fromCoord.getY()) / waypointsNeeded;
			float stepZ = (toCoord.getZ() - fromCoord.getZ()) / waypointsNeeded;
			for (int wp = 1; wp <= waypointsNeeded; wp++) {
				Coord coord = new Coord(fromCoord.getX() + wp * stepX, fromCoord.getY() + wp * stepY, fromCoord.getZ() + wp * stepZ);
				StarSystem closestSystem = universeService.findNearestSystem(coord);
				FileUtils.write(waypointsFile, String.format(Locale.US, "Waypoint %2d: %s\n", wp, closestSystem.getName()), "UTF-8", true);
			}
		}
	}

	public static class Route implements Serializable {

		private static final long serialVersionUID = 8127296548648983823L;

		private final List<RouteElement> elements = new ArrayList<>();
		private final FuelAndJumpRangeLookup fuelJumpLUT;
		//		private final Journal journal;

		public static Route fromPath(List<Path> sortedPaths, FuelAndJumpRangeLookup fuelJumpLUT/*, Journal journal*/, UniverseService universeService) throws NonUniqueResultException {
			Route route = new Route(fuelJumpLUT/*, journal*/);

			Path prevPath = null;
			for (Path currPath : sortedPaths) {
				if (prevPath != null) {
					StarSystem fromSystem = universeService.findStarSystemByName(prevPath.getMinimizedStarSystem().getName());
					List<Body> fromBodies = universeService.findBodiesByStarSystemName(prevPath.getMinimizedStarSystem().getName());

					StarSystem toSystem = universeService.findStarSystemByName(currPath.getMinimizedStarSystem().getName());
					List<Body> toBodies = universeService.findBodiesByStarSystemName(currPath.getMinimizedStarSystem().getName());

					route.add(new RouteElement(route, currPath.getTotalJumps(), fromSystem, fromBodies, toSystem, toBodies, currPath.getFuelLevel(), currPath.getTravelledDistanceLy(),
							currPath.getRemainingDistanceLy()));
				}
				prevPath = currPath;
			}

			return route.markDryPeriods();
		}

		private void add(RouteElement e) {
			this.elements.add(e);
		}

		public Route(FuelAndJumpRangeLookup fuelJumpLUT/*, Journal journal*/) {
			this.fuelJumpLUT = fuelJumpLUT;
			//			this.journal = journal;
		}

		private Route markDryPeriods() {
			int nUnboostedJumps = 0;
			for (int index = 0; index < this.getElements().size(); index++) {
				RouteElement e = this.getElements().get(index);
				if (!"NS".equals(e.getFromSpectralClass())) {
					nUnboostedJumps++;
				} else {
					if (nUnboostedJumps >= 3) {
						// Example for 3 unboosted jumps:
						// index    = NS -> *
						// index-1  = !NS -> NS: keep (jump to ns, exact fuel usage)
						// index-2  = !NS -> !NS: keep (last refuel before jump to ns)
						// index-3  = !NS -> !NS: skip
						// index-4  = NS -> !NS: keep (make use of boost)
						this.getElements().get(index - 2).setDryPeriod(DryPeriod.END);
						for (int skip = 3; skip <= nUnboostedJumps; skip++) {
							this.getElements().get(index - skip).setDryPeriod(DryPeriod.PART_OF);
						}
						if (index >= (nUnboostedJumps + 1)) {
							this.getElements().get(index - (nUnboostedJumps + 1)).setDryPeriod(DryPeriod.START);
						}
					}
					nUnboostedJumps = 0;
				}
			}
			return this;
		}

		public List<RouteElement> getElements() {
			return this.elements;
		}

		public FuelAndJumpRangeLookup getFuelJumpLUT() {
			return this.fuelJumpLUT;
		}

		//		public Journal getJournal() {
		//			return this.journal;
		//		}

		public String toVoiceAttackTxt() {
			StringBuilder txt = new StringBuilder();

			RouteElement last = null;
			for (RouteElement curr : this.getElements()) {
				if (curr.getDryPeriod() != DryPeriod.PART_OF) {
					txt.append(curr.toVoiceAttackRow(last));
					last = curr;
				}
			}

			return txt.toString();
		}

		public String toHumanReadableHtml(Date eddbDumpDate, int nKnownArrivalNeutronStars) {
			StringBuilder html = new StringBuilder();

			StarSystem fromSystem = this.getElements().get(0).getFromSystem();
			StarSystem toSystem = this.getElements().get(this.getElements().size() - 1).getToSystem();
			String fromName = fromSystem.getName();
			String toName = toSystem.getName();
			float directDistance = fromSystem.distanceTo(toSystem);
			int jumpsUsingHighway = this.getElements().get(this.getElements().size() - 1).getJumpNo();
			int jumpsTraditional = Math.round(directDistance / fuelJumpLUT.getJumpRangeFuelFull());
			int jumpsSaved = jumpsTraditional - jumpsUsingHighway;
			float jumpsSavedPercent = 100f * jumpsSaved / jumpsTraditional;

			String title = StringEscapeUtils.escapeHtml(String.format(Locale.US, "%s → %s (%.0f Ly, %d jumps)", fromName, toName, directDistance, jumpsUsingHighway));
			String h2 = String.format(Locale.US, "EDDB data from %td-%tb-%tY, %d known neutron star systems", eddbDumpDate, eddbDumpDate, eddbDumpDate, nKnownArrivalNeutronStars);
			String h3 = String.format(Locale.US, "Jump range: %.1f to %.1f Ly | Fuel usage: Max %.2f of %d tons | Jumps saved: %d of %d (%.0f%%)", fuelJumpLUT.getJumpRangeFuelFull(),
					fuelJumpLUT.getJumpRangeFuelOpt(), fuelJumpLUT.getMaxFuelPerJump(), fuelJumpLUT.getMaxFuelTons(), jumpsSaved, jumpsTraditional, jumpsSavedPercent);

			html.append("<html>\n");
			html.append("<head>\n");
			html.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" />\n");
			html.append("<link href=\"route.css\" rel=\"stylesheet\" type=\"text/css\" />\n");
			html.append("<title>").append(title).append("</title>\n");
			html.append("</head>\n");
			html.append("<body>\n");
			html.append("<h1>").append(title).append("</h1>\n");
			html.append("<h2>").append(h2).append("</h2>\n");
			html.append("<h3>").append(h3).append("</h3>\n");
			html.append("<table id=\"jumpTable\">\n");
			html.append(RouteElement.toHtmlTableHeadline());
			for (RouteElement e : this.getElements()) {
				html.append(e.toHtmlTableRow());
			}
			html.append("</table>\n");
			html.append("</body>\n");
			html.append("</html>");

			return html.toString();
		}

	}

	public static class RouteElement implements Serializable {

		private static final long serialVersionUID = -2544933509448009207L;

		private final Route route;

		private final int jumpNo;

		private final StarSystem fromSystem;
		private final List<Body> fromBodies;
		private final Body fromStar;
		private final String fromSpectralClass;

		private final StarSystem toSystem;
		private final List<Body> toBodies;
		private final Body toStar;
		private final String toSpectralClass;

		private final float fuelLevelOnArrival;
		private final float travelledLy;
		private final float remainingLy;

		private DryPeriod dryPeriod = DryPeriod.NOT_PART_OF;

		public RouteElement(Route route, int jumpNo, StarSystem fromSystem, List<Body> fromBodies, StarSystem toSystem, List<Body> toBodies, float fuelLevelOnArrival, float travelledLy,
				float remainingLy) {
			this.route = route;

			this.jumpNo = jumpNo;

			this.fromSystem = fromSystem;
			this.fromBodies = Collections.unmodifiableList(fromBodies);
			Optional<Body> optionalFrom = fromBodies.stream().filter(b -> Boolean.TRUE.equals(b.getDistanceToArrival() != null && b.getDistanceToArrival().doubleValue() == 0)).findFirst();
			if (optionalFrom.isPresent()) {
				this.fromStar = optionalFrom.get();
				this.fromSpectralClass = this.fromStar.getStarClass().name().replaceAll("N", "NS");
			} else {
				this.fromStar = null;
				this.fromSpectralClass = "?";
			}

			this.toSystem = toSystem;
			this.toBodies = Collections.unmodifiableList(toBodies);
			Optional<Body> optionalTo = toBodies.stream().filter(b -> Boolean.TRUE.equals(b.getDistanceToArrival() != null && b.getDistanceToArrival().doubleValue() == 0)).findFirst();
			if (optionalTo.isPresent()) {
				this.toStar = optionalTo.get();
				this.toSpectralClass = this.toStar.getStarClass().name().replaceAll("N", "NS");
			} else {
				this.toStar = null;
				this.toSpectralClass = "?";
			}

			this.fuelLevelOnArrival = fuelLevelOnArrival;
			this.travelledLy = travelledLy;
			this.remainingLy = remainingLy;
		}

		public String toVoiceAttackRow(RouteElement last) {
			float jumpDistance = last != null ? last.getToSystem().distanceTo(this.getToSystem()) : this.getFromSystem().distanceTo(this.getToSystem());
			String flags = "";
			if (this.getDryPeriod() == DryPeriod.END) {
				flags += "R";
			}
			if (this.getToSystem().getName().replaceAll("[^\\-]", "").length() < 2) {
				flags += "N"; // Pron name
			}
			if (this.getFuelLevelOnArrival() <= (this.getRoute().getFuelJumpLUT().getMaxFuelPerJump() * 1.25f)) {
				flags += "F";
			}
			//			List<Body> valuableBodies = findValuableBodies(this.getToBodies());
			//			if (valuableBodies.size() > 0) {
			//				for (Body body : valuableBodies) {
			//					if (!this.getRoute().getJournal().getScannedBodies().contains(body.getName())) {
			//						flags += "P"; // Planets
			//						break;
			//					}
			//				}
			//			}
			return String.format(Locale.US, "%-50s%5.0f%10s%10s\n", this.getToSystem().getName().replace("'", " "), jumpDistance, this.getToSpectralClass(), flags);
		}

		public static String toHtmlTableHeadline() {
			StringBuilder html = new StringBuilder();

			html.append("<tr>");
			html.append("<th class=\"numeric jumpNo\">#</th>");
			html.append("<th class=\"starName\">From</th>");
			html.append("<th class=\"starClass\">Class</th>");
			html.append("<th class=\"numeric jumpDistance\">Jump</th>");
			html.append("<th class=\"starClass\">Class</th>");
			html.append("<th class=\"starName\">To</th>");
			html.append("<th class=\"notes\">Notes</th>");
			html.append("<th class=\"numeric distance\">Dist</th>");
			html.append("</tr>\n");

			return html.toString();
		}

		public String toHtmlTableRow() {
			StringBuilder html = new StringBuilder();

			String evenOddCss = this.getJumpNo() % 2 == 0 ? "even" : "odd";
			String neutronJumpCss = "NS".equals(this.getFromSpectralClass()) ? "neutronJump" : "normalJump";
			//			String firstDiscoveredCss = this.getFromStar() != null && this.getRoute().getJournal().getFirstDiscoveries().contains(this.getFromStar().getName())
			//					? " firstDiscovered"
			//					: "";
			String firstDiscoveredCss = "";
			//			String prevKnownCss = this.getRoute().getJournal().getVisitedSystems().contains(this.getFromSystem().getName()) ? " known" : "";
			//			String currKnownCss = this.getRoute().getJournal().getVisitedSystems().contains(this.getToSystem().getName()) ? " known" : "";
			String prevKnownCss = "";
			String currKnownCss = "";
			String flags = "";
			String notes = "";
			if (this.getDryPeriod() == DryPeriod.END) {
				flags += "R";
				notes += "&lt;Route END&gt;&nbsp;";
			} else if (this.getDryPeriod() == DryPeriod.START) {
				//flags += "R";
				notes += "&lt;Route START&gt;&nbsp;";
			} else if (this.getDryPeriod() == DryPeriod.PART_OF) {
				//flags += "R";
				notes += "&lt;Route PART&gt;&nbsp;";
			}
			if (this.getToSystem().getName().replaceAll("[^\\-]", "").length() < 2) {
				flags += "N"; // Pron name
			}
			if (this.getFuelLevelOnArrival() <= (this.getRoute().getFuelJumpLUT().getMaxFuelPerJump() * 1.25f)) {
				flags += "F";
				notes += "<span class=\"fuelWarning\">" + String.format(Locale.US, "%.1ft", this.getFuelLevelOnArrival()) + "</span>";
			}
			//			List<Body> valuableBodies = findValuableBodies(this.getToBodies());
			//			if (valuableBodies.size() > 0) {
			//				int unknown = 0;
			//				for (Body body : valuableBodies) {
			//					if (!this.getRoute().getJournal().getScannedBodies().contains(body.getName())) {
			//						unknown++;
			//					}
			//					String knownCss = this.getRoute().getJournal().getScannedBodies().contains(body.getName()) ? "known" : "";
			//					String typeCss = body.getTypeName() == null ? "" : body.getTypeName().toLowerCase().replaceAll("\\W", "-");
			//					notes += "<span class=\"valuablePlanet " + typeCss + " " + knownCss + "\">"
			//							+ StringEscapeUtils.escapeHtml(body.getName().replace(this.getToSystem().getName() + " ", "")) + "</span>";
			//				}
			//				if (unknown > 0) {
			//					flags += "P"; // Planets
			//				}
			//			}

			html.append("<tr class=\"" + evenOddCss + " " + neutronJumpCss + " " + firstDiscoveredCss + "\">");
			html.append("<td class=\"numeric jumpNo\">" + this.getJumpNo() + "</td>");
			html.append("<td class=\"starName " + prevKnownCss + "\">" + StringEscapeUtils.escapeHtml(this.getFromSystem().getName()) + "</td>");
			html.append("<td class=\"starClass spectralClass-" + this.getFromSpectralClass() + "\">" + this.getFromSpectralClass() + "</td>");
			html.append("<td class=\"numeric jumpDistance\">" + String.format(Locale.US, "%.1f Ly", this.getFromSystem().distanceTo(this.getToSystem())) + "</td>");
			html.append("<td class=\"starClass spectralClass-" + this.getToSpectralClass() + "\">" + this.getToSpectralClass() + "</td>");
			html.append("<td class=\"starName " + currKnownCss + "\">" + StringEscapeUtils.escapeHtml(this.getToSystem().getName()) + "</td>");
			html.append("<td class=\"notes\">" + "[" + flags + "] " + notes + "</td>");
			html.append("<td class=\"numeric distance\">" + String.format(Locale.US, "%.0f Ly", this.getTravelledLy()) + "</td>");
			html.append("</tr>\n");

			return html.toString();
		}

		public Route getRoute() {
			return this.route;
		}

		public int getJumpNo() {
			return this.jumpNo;
		}

		public StarSystem getFromSystem() {
			return this.fromSystem;
		}

		public List<Body> getFromBodies() {
			return this.fromBodies;
		}

		public Body getFromStar() {
			return this.fromStar;
		}

		public String getFromSpectralClass() {
			return this.fromSpectralClass;
		}

		public StarSystem getToSystem() {
			return this.toSystem;
		}

		public List<Body> getToBodies() {
			return this.toBodies;
		}

		public Body getToStar() {
			return this.toStar;
		}

		public String getToSpectralClass() {
			return this.toSpectralClass;
		}

		public float getFuelLevelOnArrival() {
			return this.fuelLevelOnArrival;
		}

		public float getTravelledLy() {
			return this.travelledLy;
		}

		public float getRemainingLy() {
			return this.remainingLy;
		}

		public DryPeriod getDryPeriod() {
			return this.dryPeriod;
		}

		public void setDryPeriod(DryPeriod dryPeriod) {
			this.dryPeriod = dryPeriod;
		}

	}

	public static enum DryPeriod {
		NOT_PART_OF,
		/** This is the last planned jump (NS -> ?) */
		START,
		/** This is part of the dry period (!NS -> !NS) */
		PART_OF,
		/** This is the first planned jump after the dry period (!NS -> scoopable before NS) */
		END;
	}

}
