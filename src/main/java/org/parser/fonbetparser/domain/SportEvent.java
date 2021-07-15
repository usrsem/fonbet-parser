package org.parser.fonbetparser.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SportEvent {
    private Integer eventId;
    private String sportType;
    private String countryName;
    private String league;
    private SportTeam sportTeam;
    private Map<String, Float> coefficient;
    private Map<String, Float> doubleChance;
    private Map<String, Float> handicap;
    private Map<String, Float> totals;
}
