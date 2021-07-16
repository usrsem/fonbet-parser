package org.parser.fonbetparser.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.parser.fonbetparser.domain.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;


@Service
@Slf4j
@RequiredArgsConstructor
public class FonbetLiveParserServiceImpl implements FonbetLiveParserService {

    private Set<SportEvent> sportEvents = new HashSet<>();
    private String sportName;

    @Override
    public LiveLine getSportEvents(String sportName) {
        this.sportName = sportName;
        LocalDateTime start = LocalDateTime.now();
        deserialize();
        LocalDateTime end = LocalDateTime.now();
        log.info("Total time for parsing: " + ChronoUnit.MILLIS.between(start, end));
        return LiveLine.builder()
                .sportEvents(sportEvents)
                .bookmaker("Fonbet")
                .lineType(LineType.LIVE)
                .startTime(start)
                .endTime(end)
                .build();

    }

    @Override
    public void deserialize() {
        Set<JsonObject> sportsByName, sportEvents, customFactors;
        this.sportEvents = new HashSet<>();

        try {
            JsonObject currentLine = getCurrentLine();
            JsonArray sports = currentLine.getAsJsonArray("sports");
            sportsByName = getSportsByName(sportName, sports);

            JsonArray events = currentLine.getAsJsonArray("events");
            sportEvents = getSportEvents(events, sportsByName);

            JsonArray customFactorsArray = currentLine.getAsJsonArray("customFactors");
            customFactors = getCustomFactors(sportEvents, customFactorsArray);

            collectSportEvents(sportsByName, sportEvents, customFactors);

        } catch (IOException e) {
            log.error("Error while get JSON from server " + e);
        }
    }

    /**
     * Collect sports of current type
     * @param sportName sport type
     * @param sports sports of all types
     * @return sports of current type
     */
    private Set<JsonObject> getSportsByName(String sportName, JsonArray sports) {
        Set<JsonObject> sportsByName = new HashSet<>();
        Matcher matcher;
        JsonObject sportObject;
        Pattern pattern = Pattern.compile("(" + sportName + ").*");

        for (JsonElement sport : sports) {
            sportObject = sport.getAsJsonObject();

            if (sportObject.get("kind").getAsString().equals("segment")) {
                matcher = pattern.matcher(sportObject.get("name").toString());
                if (matcher.find()) {
                    sportsByName.add(sportObject);
                }
            }
        }

        return sportsByName;
    }

    /**
     * Parse all events of current type sport
     * @param events events of all sports
     * @param sportsByName sports of current type
     * @return events of current type sport
     */
    private Set<JsonObject> getSportEvents(JsonArray events, Set<JsonObject> sportsByName) {
        int eventSportId;
        JsonObject eventObject;
        Set<Integer> sportsByNameIds = sportsByName.stream()
                .map(jsonObject -> jsonObject.get("id").getAsInt())
                .collect(Collectors.toSet());

        Set<JsonObject> sportEvents = new HashSet<>();

        for (JsonElement event : events) {
            eventObject = event.getAsJsonObject();
            eventSportId = eventObject.get("sportId").getAsInt();
            if (sportsByNameIds.contains(eventSportId)) {
                sportEvents.add(eventObject);
            }
        }

        return sportEvents;
    }

    /**
     * Collect factors (coefficients) of events of current type sports
     * @param sportEvents sport events of current type sport
     * @param customFactorsArray factors  of all sport types
     * @return factors of events of current type sports
     */
    private Set<JsonObject> getCustomFactors(Set<JsonObject> sportEvents, JsonArray customFactorsArray) {
        Set<JsonObject> customFactors = new HashSet<>();
        JsonObject customFactorObject;
        Set<Integer> sportEventsIds = sportEvents.stream()
                .map(jsonObject -> jsonObject.get("id").getAsInt())
                .collect(Collectors.toSet());

        for (JsonElement customFactor : customFactorsArray) {
            customFactorObject = customFactor.getAsJsonObject();
            if (sportEventsIds.contains(customFactorObject.get("e").getAsInt())) {
                customFactors.add(customFactorObject);
            }
        }

        return customFactors;
    }





    /**
     * Main method for deserialization. Collect all data in set of domain objects
     * @param sportsByName sports of current type
     * @param sportEvents all events of current type sport
     * @param customFactors all customFactors of current type sport
     */
    private void collectSportEvents(Set<JsonObject> sportsByName,
                                    Set<JsonObject> sportEvents,
                                    Set<JsonObject> customFactors) {
        int eventId, eventLevel;
        JsonObject parentEvent;
        String name;
        SportTeam sportTeam;
        Coefficients coefficients;
        List<String> sportCountyLeague;

        for (JsonObject sportEvent : sportEvents) {

            eventId = sportEvent.get("id").getAsInt();
            eventLevel = sportEvent.get("level").getAsInt();

            if (eventLevel == 1) {
                name = "Main";
                parentEvent = sportEvent;
            } else {
                name = sportEvent.get("name").getAsString();
                parentEvent = getFirstLevelEvent(sportEvents, sportEvent);
            }

            sportTeam = SportTeam.builder()
                    .team1(parentEvent.get("team1").getAsString())
                    .team2(parentEvent.get("team2").getAsString())
                    .build();

            sportCountyLeague = getEventMainInfo(sportsByName, sportEvent);

            coefficients = collectCoefficientsForEvent(customFactors, eventId);


            this.sportEvents.add(
              SportEvent.builder()
                      .eventId(eventId)
                      .sportType(sportCountyLeague.get(0))
                      .countryName(sportCountyLeague.get(1))
                      .league(sportCountyLeague.get(2))
                      .sportTeam(sportTeam)
                      .coefficients(coefficients)
                      .name(name)
                      .build()
            );
        }

    }

    private List<String> getEventMainInfo(Set<JsonObject> sportsByName, JsonObject sportEvent) {
        List<String> res = new ArrayList<>();
        String mainLine = sportsByName.stream()
                .filter(jsonObject ->
                        jsonObject.get("id").getAsInt() == sportEvent.get("sportId").getAsInt()
                ).map(jsonObject -> jsonObject.get("name").getAsString())
                .collect(Collectors.toList()).get(0);

        if (sportName.equals("Футбол")) {
            Pattern pattern = Pattern.compile(
                    "^(?<sportType>(Футбол. Жен)\\.?|(Футбол)\\.?)\\s" +
                            "(?<countryName>(Товарищеские матчи)?|(FIFA 21.)?|(COSAFA Cup)\\.?|(.*?)?)\\s" +
                            "(?<league>.*)$");

            Matcher matcher = pattern.matcher(mainLine);

            if (matcher.find()) {
                res.add(matcher.group("sportType"));
                res.add(matcher.group("countryName"));
                res.add(matcher.group("league"));
            }

        } else {
            System.out.println("Nor found :(");
            res.add(mainLine);
            res.add(mainLine);
            res.add(mainLine);
        }
        return res;


    }

    /**
     * Collect coefficient, doubleChance, handicap and totals for current event
     * @param customFactors all factors of current sport type
     * @param eventId id of current event
     * @return Coefficients for domain
     */
    private Coefficients collectCoefficientsForEvent(Set<JsonObject> customFactors, int eventId) {
        int factorNumber;
        Coefficients coefficients = new Coefficients();

        Map<Integer, String> coefficientDict = new HashMap<>();
        coefficientDict.put(921, "1");
        coefficientDict.put(922, "X");
        coefficientDict.put(923, "2");

        Map<Integer, String> doubleChanceDict = new HashMap<>();
        doubleChanceDict.put(924, "1X");
        doubleChanceDict.put(1571, "12");
        doubleChanceDict.put(925, "X2");

        Map<Integer, String> handicapDict = new HashMap<>();
        handicapDict.put(927, "Фора 1");
        handicapDict.put(928, "Фора 2");

        Map<Integer, String> totalsDict = new HashMap<>();
        totalsDict.put(930, "Тотал Б");
        totalsDict.put(931, "Тотал М");

        for (JsonObject factor : customFactors) {
            factorNumber = factor.get("f").getAsInt();

            if (factor.get("e").getAsInt() == eventId) {
                if (coefficientDict.containsKey(factorNumber)) {
                    coefficients.getCoefficient().put(coefficientDict.get(factorNumber), factor.get("v").getAsFloat());
                } else if (doubleChanceDict.containsKey(factorNumber)) {
                    coefficients.getDoubleChance().put(doubleChanceDict.get(factorNumber), factor.get("v").getAsFloat());
                } else if (handicapDict.containsKey(factorNumber)) {
                    coefficients.getHandicap().put(handicapDict.get(factorNumber), factor.get("v").getAsFloat());
                } else if (totalsDict.containsKey(factorNumber)) {
                    coefficients.getTotals().put(totalsDict.get(factorNumber), factor.get("v").getAsFloat());
                }
            }
        }

        return coefficients;
    }

    /**
     * Recursive function for getting "super" for event
     * Need to get team list for event
     * @param events set of all events of current sport
     * @param child current event with level 2 or higher
     * @return "super" for event
     */
    private JsonObject getFirstLevelEvent(Set<JsonObject> events, JsonObject child) {
        if (child.get("level").getAsInt() == 1) {
            return child;
        }
        return getFirstLevelEvent(
                events,
                events.stream()
                        .filter(jsonObject ->
                                jsonObject.get("id").getAsInt() == child.get("parentId").getAsInt())
                .findFirst()
                .orElse(child)
        );
    }

    /**
     * Load JSON from fonbet in text
     * @return json
     * @throws IOException error
     */
    private JsonObject getCurrentLine() throws IOException {
        // TODO: add URL_STRING to application.properties
        String URL_STRING = "https://line32.bkfon-resources.com/live/currentLine/ru?scopeMarket=1600&sysId=1";
        URL oracle = new URL(URL_STRING);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(oracle.openStream()))
        );
        JsonObject res = JsonParser.parseString(in.readLine()).getAsJsonObject();
        in.close();
        return res;
    }
}
