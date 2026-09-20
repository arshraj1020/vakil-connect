package com.arshraj.vakilconnect.ai.compare;

import com.arshraj.vakilconnect.ai.analysis.AnalysisContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bounds ONE document's chunks for a comparison prompt.
 *
 * THE SAME STRATEGY AS AI-4's AnalysisContextBuilder - leading chunks in
 * document order, whole chunks only, stop at the budget rather than skip
 * ahead - documented in full there. Duplicated rather than reused because the
 * budget is per-document here (`maxContextCharactersPerDocument`) rather than
 * a single corpus-wide ceiling, and because this method is called TWICE per
 * request, once per document, with each call independent of the other's
 * result - reusing AI-4's builder would mean either constructing a throwaway
 * AiAnalysisProperties just to satisfy its constructor, or widening that
 * class's constructor for one caller outside its own feature. Producing the
 * same {@link AnalysisContext} value type keeps {@link ComparisonPromptBuilder}
 * and {@link DocumentComparisonServiceImpl} identical in shape to AI-4's,
 * which is the reuse that actually matters here.
 */
@Component
public class ComparisonContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ComparisonContextBuilder.class);

    private final AiComparisonProperties properties;

    public ComparisonContextBuilder(AiComparisonProperties properties) {
        this.properties = properties;
    }

    public AnalysisContext build(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new AnalysisContext("", 0, false);
        }

        StringBuilder rendered = new StringBuilder();
        int included = 0;
        boolean truncated = false;
        int budget = properties.maxContextCharactersPerDocument();

        for (String chunk : chunks) {
            String block = render(included + 1, chunk);

            if (rendered.length() + block.length() > budget) {
                truncated = true;
                break;
            }

            rendered.append(block);
            included++;
        }

        if (truncated) {
            log.debug("Comparison context bounded to {} of {} chunks ({} character budget)",
                    included, chunks.size(), budget);
        }

        return new AnalysisContext(rendered.toString(), included, truncated);
    }

    private String render(int part, String chunk) {
        return "[Excerpt " + part + "]\n" + chunk + "\n\n";
    }
}
