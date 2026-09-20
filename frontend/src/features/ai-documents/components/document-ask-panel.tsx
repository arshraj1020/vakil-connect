"use client";

import { FileQuestion, Search } from "lucide-react";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ErrorState } from "@/components/common/error-state";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { useAskQuestion } from "@/features/ai-documents/hooks/use-ask-question";

const MAX_QUESTION_CHARS = 4000;

/**
 * Grounded Q&A across every document the caller owns (AI-3).
 *
 * `grounded: false` is rendered as a distinct, calm state - not an error - to
 * match the backend's own framing: retrieval finding nothing relevant is a
 * successful answer, not a failure.
 *
 * Sources are shown as short excerpts with their originating document and
 * chunk, exactly what the backend sends - nothing here re-derives or
 * re-ranks them, since the citation list is the model's ONLY provenance.
 */
export function DocumentAskPanel() {
  const [question, setQuestion] = useState("");
  const ask = useAskQuestion();

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed) return;
    ask.mutate(trimmed);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <FileQuestion className="size-4" aria-hidden />
          Ask your documents
        </CardTitle>
      </CardHeader>

      <CardContent className="space-y-4">
        <form onSubmit={handleSubmit} className="space-y-2">
          <Textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="e.g. What is the notice period for terminating this lease?"
            maxLength={MAX_QUESTION_CHARS}
            rows={3}
            disabled={ask.isPending}
          />
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-foreground">
              Searches every document you&rsquo;ve uploaded and processed.
            </p>
            <Button type="submit" size="sm" disabled={ask.isPending || !question.trim()}>
              {ask.isPending ? <Spinner size="sm" /> : <Search aria-hidden />}
              Ask
            </Button>
          </div>
        </form>

        {ask.isError ? <ErrorState error={ask.error} /> : null}

        {ask.data ? (
          <div className="space-y-3 rounded-lg border border-border bg-muted/30 p-4">
            {ask.data.grounded ? (
              <>
                <p className="text-sm leading-relaxed">{ask.data.answer}</p>

                {ask.data.truncated ? (
                  <p className="text-xs text-muted-foreground">
                    Some retrieved evidence was left out to stay within the
                    model&rsquo;s context limit.
                  </p>
                ) : null}

                {ask.data.sources.length > 0 ? (
                  <div className="space-y-2 border-t border-border pt-3">
                    <p className="text-xs font-medium text-muted-foreground">Sources</p>
                    <ul className="space-y-2">
                      {ask.data.sources.map((source, index) => (
                        <li
                          key={`${source.documentId}-${source.chunkIndex}-${index}`}
                          className="rounded-md bg-background px-3 py-2 text-xs"
                        >
                          <p className="font-medium text-foreground">
                            {source.documentName}{" "}
                            <span className="font-normal text-muted-foreground">
                              (excerpt {source.chunkIndex + 1})
                            </span>
                          </p>
                          <p className="mt-0.5 text-muted-foreground">{source.excerpt}</p>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </>
            ) : (
              <p className="text-sm text-muted-foreground">{ask.data.answer}</p>
            )}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
