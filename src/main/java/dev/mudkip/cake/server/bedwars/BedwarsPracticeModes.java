package dev.mudkip.cake.server.bedwars;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

final class BedwarsPracticeModes {
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("bridgessingle", "1v1 Bed Bridge Fight"),
            Map.entry("bridgesdouble", "2v2 Bed Bridge Fight"),
            Map.entry("bridgesingle", "1v1 Bed Bridge Fight"),
            Map.entry("bridgedouble", "2v2 Bed Bridge Fight"),
            Map.entry("compbridgesingle", "1v1 Competitive Bridge Fight"),
            Map.entry("sumo", "Block Sumo"),
            Map.entry("sumoduelssolo", "1v1 Block Sumo Duels"),
            Map.entry("sumoduelsdouble", "2v2 Block Sumo Duels"),
            Map.entry("betasumo", "Beta Block Sumo"),
            Map.entry("obstaclesingle", "1v1 Obstacles"),
            Map.entry("resourcesingle", "1v1 Resource Collect"),
            Map.entry("resourcedouble", "2v2 Resource Collect"),
            Map.entry("voidsingle", "1v1 Void Fight"),
            Map.entry("voiddouble", "2v2 Void Fight"),
            Map.entry("bedwarsnormalsingle", "1v1 Bedwars Normal"),
            Map.entry("bedwarsnormaldouble", "2v2 Bedwars Normal"),
            Map.entry("bedwarsmegasolo", "Bedwars Mega Solo"),
            Map.entry("bedwarsmegadouble", "Bedwars Mega Doubles"),
            Map.entry("bedwarslatesingle", "1v1 Bedwars Late Game"),
            Map.entry("bedwarslatedouble", "2v2 Bedwars Late Game"),
            Map.entry("bedwarsrushsolo", "1v1 Bedwars Rush Duels"),
            Map.entry("bedwarsrushdouble", "2v2 Bedwars Rush Duels"),
            Map.entry("stickfightsingle", "1v1 Stick Fight"),
            Map.entry("stickfightdouble", "2v2 Stick Fight"),
            Map.entry("flatfightsingle", "1v1 Flat Fight"),
            Map.entry("flatfightdouble", "2v2 Flat Fight"),
            Map.entry("ladderfightsingle", "1v1 Ladder Fight"),
            Map.entry("ladderfightdouble", "2v2 Ladder Fight"),
            Map.entry("partygames", "Party Games"),
            Map.entry("rankedfourspractice", "4v4 Ranked Fours Practice"),
            Map.entry("bowfightsingle", "1v1 Bow Fight"),
            Map.entry("bowfightdouble", "2v2 Bow Fight"),
            Map.entry("pearlfightsingle", "1v1 Pearl Fight"),
            Map.entry("pearlfightdouble", "2v2 Pearl Fight"),
            Map.entry("bedrushsingle", "1v1 Bed Rush"),
            Map.entry("bedrushdouble", "2v2 Bed Rush"),
            Map.entry("miniwarssolo", "Miniwars Solo"),
            Map.entry("miniwarsdouble", "Miniwars Double"),
            Map.entry("bedwalls", "Bedwalls"),
            Map.entry("bedwarseightsolo", "Bedwars Eight Team"),
            Map.entry("bedwarseighttourney", "Bedwars Four Team"),
            Map.entry("fourwaybridgesingle", "Four Way Bridge Fight"),
            Map.entry("groundsingle", "1v1 Ground Fight"),
            Map.entry("grounddouble", "2v2 Ground Fight"),
            Map.entry("resourceoldsingle", "1v1 Old Resource Collect"),
            Map.entry("resourceolddouble", "2v2 Old Resource Collect"),
            Map.entry("wallfightsolo", "Wall Fight Solo")
    );

    private BedwarsPracticeModes() {
    }

    static String name(String value) {
        var key = value.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(" ", "")
                .replace("-", "");
        var mapped = BedwarsPracticeModes.NAMES.get(key);

        if (mapped == null) {
            mapped = BedwarsPracticeModes.closest(key);
        }

        return mapped != null ? mapped : BedwarsPracticeModes.displayName(value);
    }

    static String displayName(String value) {
        var text = value.replace('_', ' ').trim();
        var builder = new StringBuilder(text.length() + 4);

        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);

            if (index > 0 && Character.isUpperCase(character)) {
                var previous = text.charAt(index - 1);
                var next = index + 1 < text.length() ? text.charAt(index + 1) : 0;

                if (Character.isLowerCase(previous) || (Character.isUpperCase(previous) && Character.isLowerCase(next))) {
                    builder.append(' ');
                }
            }

            builder.append(character);
        }

        if (!builder.isEmpty()) {
            builder.setCharAt(0, Character.toUpperCase(builder.charAt(0)));
        }

        return builder.toString();
    }

    private static @Nullable String closest(String key) {
        @Nullable String best = null;
        var bestDistance = 2;

        for (var entry : BedwarsPracticeModes.NAMES.entrySet()) {
            var distance = BedwarsPracticeModes.distance(key, entry.getKey());

            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getValue();
            } else if (distance == bestDistance && best != null && !best.equals(entry.getValue())) {
                best = null;
            }
        }

        return best;
    }

    private static int distance(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) {
            return 2;
        }

        var previous = new int[right.length() + 1];
        var current = new int[right.length() + 1];

        for (var index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }

        for (var leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;

            for (var rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                var cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }

            var swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }
}
