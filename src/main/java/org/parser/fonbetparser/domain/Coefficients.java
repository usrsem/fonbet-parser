package org.parser.fonbetparser.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
public class Coefficients {
    private final Map<String, Float> coefficient = new HashMap<>();
    private final Map<String, Float> doubleChance = new HashMap<>();
    private final Map<String, Float> handicap = new HashMap<>();
    private final Map<String, Float> totals = new HashMap<>();
}
