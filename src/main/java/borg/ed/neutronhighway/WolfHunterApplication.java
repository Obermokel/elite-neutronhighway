package borg.ed.neutronhighway;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.util.CloseableIterator;

import borg.ed.universe.UniverseApplication;
import borg.ed.universe.constants.StarClass;
import borg.ed.universe.data.Coord;
import borg.ed.universe.exceptions.NonUniqueResultException;
import borg.ed.universe.model.Body;
import borg.ed.universe.model.StarSystem;
import borg.ed.universe.service.UniverseService;
import borg.ed.universe.util.MiscUtil;

@Configuration
@Import(UniverseApplication.class)
public class WolfHunterApplication {

	static final Logger logger = LoggerFactory.getLogger(WolfHunterApplication.class);

	private static final ApplicationContext APPCTX = new AnnotationConfigApplicationContext(WolfHunterApplication.class);

	public static void main(String[] args) throws NonUniqueResultException {
		final UniverseService universeService = APPCTX.getBean(UniverseService.class);

		//		StarSystem starSystem = universeService.findStarSystemByName("9 Alpha Camelopardalis");
		//		logger.info(starSystem.toString());
		//		Page<StarSystem> nearSystemsPage = universeService.findSystemsNear(starSystem.getCoord(), 200, PageRequest.of(0, 100));
		//		for (StarSystem nearSystem : nearSystemsPage.getContent()) {
		//			logger.info(starSystem.getName() + " -> " + nearSystem.getName() + " = " + nearSystem.getCoord().distanceTo(starSystem.getCoord()));
		//		}

		logger.info("Listing Wolf Rayet stars...");
		int counter = 0;
		Set<String> wolfRayetSectorNames = new HashSet<>();
		List<StarClass> wolfRayetStarClasses = Arrays.asList(StarClass.W, StarClass.WC, StarClass.WN, StarClass.WNC, StarClass.WO);
		try (CloseableIterator<Body> stream = universeService.streamStarsNear(new Coord(), 65000, null, wolfRayetStarClasses)) {
			while (stream.hasNext()) {
				counter++;
				Body wolfRayetStar = stream.next();
				logger.info(String.format("%-40s\t%-40s", wolfRayetStar.getName(), wolfRayetStar.getFirstDiscoveredBy()));
				if (wolfRayetStar.getName().matches(".+ AA-A h\\d+.*")) {
					String sectorName = wolfRayetStar.getName().substring(0, wolfRayetStar.getName().indexOf(" AA-A h"));
					wolfRayetSectorNames.add(sectorName);
					logger.trace(sectorName);
				}
			}
		}
		logger.info("Found " + counter + " Wolf Rayet stars in " + wolfRayetSectorNames.size() + " sectors");

		logger.info("Listing all sector names...");
		Pattern p = Pattern.compile("(.+) [A-Z]{1,2}\\-[A-Z] [a-z][0-9]+\\-?[0-9]+?");
		int systemCounter = 0;
		Map<String, Coord> allSectorNames = new HashMap<>();
		Set<String> aaSectorNames = new HashSet<>();
		try (CloseableIterator<StarSystem> stream = universeService.streamAllSystemsWithin(-100000f, 100000f, -100000f, 100000f, -100000f, 100000f)) {
			while (stream.hasNext()) {
				systemCounter++;
				StarSystem starSystem = stream.next();
				Matcher m = p.matcher(starSystem.getName());
				if (m.find()) {
					String sectorName = m.group(1);
					if (!allSectorNames.containsKey(sectorName)) {
						allSectorNames.put(sectorName, starSystem.getCoord());
						//logger.info(String.format("%-40s\t%-40s", starSystem.getName(), sectorName));
					}
					if (starSystem.getName().contains(" AA-A ")) {
						aaSectorNames.add(sectorName);
					}
				}
			}
		}
		logger.info("Found " + allSectorNames.size() + " sectors in " + systemCounter + " systems");

		logger.info("Searching candidate sectors...");
		Coord fromCoord = new Coord(-3543, 1570, -4932);
		int candidateCounter = 0;
		LinkedHashMap<String, Float> candidateSectorNames = new LinkedHashMap<>();
		for (String candidateSectorName : allSectorNames.keySet()) {
			if (!wolfRayetSectorNames.contains(candidateSectorName) && !aaSectorNames.contains(candidateSectorName)) {
				candidateCounter++;
				Coord candidateCoord = allSectorNames.get(candidateSectorName);
				if (candidateCoord == null) {
					logger.warn("Missing candidate coord");
					candidateCoord = new Coord();
				}
				candidateSectorNames.put(candidateSectorName, candidateCoord.distanceTo(fromCoord));
			}
		}
		MiscUtil.sortMapByValue(candidateSectorNames);
		for (String candidateSectorName : candidateSectorNames.keySet()) {
			logger.info(String.format(Locale.US, "%-40s\t%.0f Ly", candidateSectorName + " AA-A h", candidateSectorNames.get(candidateSectorName)));
		}
		logger.info("Found " + candidateCounter + " candidates");
	}

}
