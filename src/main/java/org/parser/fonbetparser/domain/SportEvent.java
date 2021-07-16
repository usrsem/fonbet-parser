package org.parser.fonbetparser.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SportEvent {
    private Integer eventId;
    private String sportType;
    private String countryName;
    private String league;
    private String name;
    private SportTeam sportTeam;
    private Coefficients coefficients;

}
