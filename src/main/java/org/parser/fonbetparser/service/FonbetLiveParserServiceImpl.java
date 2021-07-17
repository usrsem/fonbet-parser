package org.parser.fonbetparser.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClientBuilder;
import org.parser.fonbetparser.domain.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class FonbetLiveParserServiceImpl implements FonbetLiveParserService {

    private String sportName;
    private Set<JsonObject> sportWithCurrentName;
    private Set<SportEvent> targetSportEvents;
    private Set<Child> level2sports;
    private Set<Child> level3sports;

    /**
     * Deserialize JSON from fonbet/live
     */
    @Override
    public void deserialize() {
        JsonArray sports, events, factors;
        JsonObject currentLine;

        try {
            currentLine = getCurrentLine();

            sports = currentLine.getAsJsonArray("sports");
            events = currentLine.getAsJsonArray("events");
            factors = currentLine.getAsJsonArray("customFactors");

            collectSportsByName(sports);
            collectEvents(events, factors);

        } catch (IOException e) {
            log.error("Error while getting JSON from server " + e);
        }
    }

    /**
     * Collect JSON line to custom object
     * @param sportName name of parsed sport
     * @return deserialized JSON
     */
    @Override
    public LiveLine getTargetSportEvents(String sportName) {
        this.sportName = sportName;
        LocalDateTime start = LocalDateTime.now();
        deserialize();
        LocalDateTime end = LocalDateTime.now();
        log.info("Total time for parsing: " + ChronoUnit.MILLIS.between(start, end) + "\n\n");

        return LiveLine.builder()
                .sportEvents(targetSportEvents)
                .bookmaker("Fonbet")
                .lineType(LineType.LIVE)
                .startTime(start)
                .endTime(end)
                .build();
    }

    /**
     * Parse events from JSON and collect in to SportEvent objects
     * @param events all events from JSON
     * @param factors all customFactors from JSON
     */
    private void collectEvents(JsonArray events, JsonArray factors) {
        JsonObject eventObject, sportObject;
        SportEvent sportEvent;
        Set<JsonObject> targetFactors;
        int level;
        targetSportEvents = new HashSet<>();
        level2sports = new HashSet<>();
        level3sports = new HashSet<>();

        LocalDateTime start = LocalDateTime.now();
        for (JsonElement event : events) {
            eventObject = event.getAsJsonObject();
            int eventSportId = eventObject.get("sportId").getAsInt();
            int eventId = eventObject.get("id").getAsInt();
            level = eventObject.get("level").getAsInt();

            sportObject = sportWithCurrentName.stream()
                    .filter(jsonObject -> jsonObject.get("id").getAsInt() == eventSportId)
                    .findFirst().orElse(null);

            if (sportObject != null) {
                targetFactors = StreamSupport.stream(factors.spliterator(), true)
                        .map(JsonElement::getAsJsonObject)
                        .filter(jsonObject -> jsonObject.get("e").getAsInt() == eventId)
                        .collect(Collectors.toSet());

                if (level == 2) {
                    level2sports.add(Child.builder()
                            .id(eventId)
                            .parentId(eventObject.get("parentId").getAsInt())
                            .name(eventObject.get("name").getAsString())
                            .coefficients(collectCoefficientsForEvent(targetFactors))
                            .build()
                    );
                    continue;
                } else if (level == 3) {
                    level3sports.add(Child.builder()
                            .id(eventId)
                            .parentId(eventObject.get("parentId").getAsInt())
                            .name(eventObject.get("name").getAsString())
                            .coefficients(collectCoefficientsForEvent(targetFactors))
                            .build()
                    );
                    continue;
                }

                sportEvent = buildSportEvent(sportObject, eventObject, targetFactors);
                targetSportEvents.add(sportEvent);
            }
        }
        LocalDateTime end = LocalDateTime.now();
        log.info("Total time for adding events: " + ChronoUnit.MILLIS.between(start, end));

        collectChildren();
    }

    /**
     * Collects to one set all of events with level = 2 or level = 3 and then
     * create Set of SportEvents with this objects
     */
    private void collectChildren() {
        LocalDateTime start1 = LocalDateTime.now();
        int childId, parentId;
        Set<Child> children = new HashSet<>(level2sports);
        if (level3sports.size() > 0) {
            log.info("level 3 has objects");
            for (Child level3 : level3sports) {
                for (Child level2 : level2sports) {
                    parentId = level3.getParentId();
                    childId = level2.getId();
                    if (childId == parentId) {
                        children.add(Child.builder()
                                .id(level3.getId())
                                .parentId(childId)
                                .name(level3.getName())
                                .build()
                        );
                    }
                }
            }
        }

        Set<SportEvent> sportEventChildren = new HashSet<>();
        int childParentId, eventId;
        for (Child child : children) {
            for (SportEvent event : targetSportEvents) {
                childParentId = child.getParentId();
                eventId = event.getEventId();

                if (childParentId == eventId) {
                    sportEventChildren.add(
                            SportEvent.builder()
                                    .eventId(child.getId())
                                    .league(event.getLeague())
                                    .countryName(event.getCountryName())
                                    .sportType(event.getSportType())
                                    .coefficients(child.getCoefficients())
                                    .sportTeam(event.getSportTeam())
                                    .name(child.getName())
                                    .build()
                    );
                }
            }
        }

        targetSportEvents.addAll(sportEventChildren);
        LocalDateTime end1 = LocalDateTime.now();
        log.info("Total time for adding children: " + ChronoUnit.MILLIS.between(start1, end1));

    }

    /**
     * Builds SportEvent object
     * @param sportObject target object from sports
     * @param eventObject target object from events
     * @param factors target customFactors
     * @return new SportEvent Object
     */
    private SportEvent buildSportEvent(JsonObject sportObject, JsonObject eventObject, Set<JsonObject> factors) {
        List<String> eventMainInfo = getEventMainInfo(sportObject);

        return  SportEvent.builder()
                .eventId(eventObject.get("id").getAsInt())
                .name(sportObject.get("name").getAsString())
                .sportTeam(SportTeam.builder()
                        .team1(eventObject.get("team1").getAsString())
                        .team2(eventObject.get("team2").getAsString())
                        .build())
                .sportType(eventMainInfo.get(0))
                .countryName(eventMainInfo.get(1))
                .league(eventMainInfo.get(2))
                .coefficients(collectCoefficientsForEvent(factors))
                .build();
    }


    /**
     * Get from name of event sportType, country and league
     * @param sportObject target object from sports
     * @return List of 3 events, where
     * 0 -> sport Name
     * 1 -> country
     * 2 -> league
     */
    private List<String> getEventMainInfo(JsonObject sportObject) {
        List<String> res = new ArrayList<>();
        String mainLine = sportObject.get("name").getAsString();

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
            res.add(mainLine);
            res.add(mainLine);
            res.add(mainLine);
        }
        return res;
    }

    /**
     * Collect coefficient, doubleChance, handicap and totals for current event
     * @return Coefficients for domain
     */
    private Coefficients collectCoefficientsForEvent(Set<JsonObject> targetFactors) {
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

        for (JsonObject factor : targetFactors) {
            factorNumber = factor.get("f").getAsInt();

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

        return coefficients;
    }

    /**
     * Load JSON from fonbet in text
     * @return json
     * @throws IOException error
     */
    private JsonObject getCurrentLine() throws IOException {
        // TODO: add URL_STRING to application.properties
        LocalDateTime start = LocalDateTime.now();
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(
                HttpClientBuilder.create().build());
        String url = "https://line32.bkfon-resources.com/live/currentLine/ru?scopeMarket=1600&sysId=1";
        RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory);

        String response = restTemplate.getForObject(url, String.class);

        LocalDateTime end = LocalDateTime.now();
        log.info("Total time for download currentLine: " + ChronoUnit.MILLIS.between(start, end));

        if (response != null)
            return JsonParser.parseString(response).getAsJsonObject();
        throw new IOException("No JSON");
    }

    /**
     * Collect sports of current type
     * @param sports sports of all types
     */
    private void collectSportsByName(JsonArray sports) {
        sportWithCurrentName = new HashSet<>();
        Matcher matcher;
        JsonObject sportObject;
        Pattern pattern = Pattern.compile("(" + sportName + ").*");

        for (JsonElement sport : sports) {

            sportObject = sport.getAsJsonObject();

            if (sportObject.get("kind").getAsString().equals("segment")) {

                matcher = pattern.matcher(sportObject.get("name").toString());

                if (matcher.find()) {
                    sportWithCurrentName.add(sportObject);
                }

            }
        }
    }

}
