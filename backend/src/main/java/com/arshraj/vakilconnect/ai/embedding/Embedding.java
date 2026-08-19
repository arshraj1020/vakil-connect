package com.arshraj.vakilconnect.ai.embedding;

import java.util.Locale;

/**
 * One vector, and the model that produced it.
 *
 * @param values float[], NOT double[] or List<Double>. pgvector stores
 *               single-precision floats, so anything wider is precision that is
 *               discarded on write while costing twice the heap on the way
 *               there - and a chunked document holds hundreds of these at once.
 * @param model  What actually produced it. Not merely an echo of configuration:
 *               when retrieval quality changes with no code change, the first
 *               question is whether the model did, and this is the evidence.
 */
public record Embedding(float[] values, String model) {

    public Embedding {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("an embedding must have values");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }

    public int dimension() {
        return values.length;
    }

    /**
     * pgvector's text form: {@code [0.1,0.2,0.3]}.
     *
     * BUILT HERE RATHER THAN AT THE CALL SITE so exactly one place knows the
     * wire format. Locale.ROOT is not optional - a JVM with a European default
     * locale formats floats with a decimal COMMA, which would produce
     * `[0,1,0,2]`: a syntactically valid vector of twice the length and
     * entirely wrong values. Silent, and only visible as bad retrieval.
     */
    public String toPgVectorLiteral() {
        StringBuilder out = new StringBuilder(values.length * 12 + 2);
        out.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(String.format(Locale.ROOT, "%.7g", values[i]));
        }
        return out.append(']').toString();
    }

    /**
     * REDACTED. A record prints every component, and float[] would render as an
     * array identity - useless - or, if someone "improved" it with
     * Arrays.toString, as several thousand numbers derived from the user's
     * document. Neither belongs in a log line.
     */
    @Override
    public String toString() {
        return "Embedding{model=" + model + ", dimension=" + values.length + ", values=<not shown>}";
    }
}
