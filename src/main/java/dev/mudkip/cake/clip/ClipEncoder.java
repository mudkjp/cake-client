package dev.mudkip.cake.clip;

import java.util.List;

public record ClipEncoder(
        ClipCodec codec,
        String name,
        List<String> inputArgs,
        List<String> filterArgs,
        List<String> outputArgs
) {
    public ClipEncoder {
        inputArgs = List.copyOf(inputArgs);
        filterArgs = List.copyOf(filterArgs);
        outputArgs = List.copyOf(outputArgs);
    }
}
