package org.parser.fonbetparser.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SportTeam {
    private String team1;
    private String team2;
}
