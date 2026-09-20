"use client";

import { UploadCloud } from "lucide-react";
import { useRef } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { useUploadDocument } from "@/features/ai-documents/hooks/use-upload-document";
import { isApiError } from "@/types";

/** Accepted by the backend's magic-byte detector (AI-1). Advisory only - the
 *  server re-checks the actual bytes regardless of what the browser reports. */
const ACCEPT = ".pdf,.docx,.txt,application/pdf,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/**
 * Upload control for a new document.
 *
 * A single hidden file input rather than a drag-and-drop zone: this feature's
 * value is the pipeline behind it, not the widget, and a plain input is fully
 * keyboard- and screen-reader-accessible for free.
 */
export function DocumentUploadCard() {
  const inputRef = useRef<HTMLInputElement>(null);
  const upload = useUploadDocument();

  function handleFiles(files: FileList | null) {
    const file = files?.[0];
    if (!file) return;

    upload.mutate(file, {
      onSuccess: (document) => {
        toast.success("Document uploaded", {
          description: `${document.filename} is ready to be processed.`,
        });
      },
      onError: (error: unknown) => {
        const description = isApiError(error)
          ? error.message
          : "The upload could not be completed.";
        toast.error("Upload failed", { description });
      },
    });

    // Allow re-selecting the same file after a failed or completed upload.
    if (inputRef.current) inputRef.current.value = "";
  }

  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
        <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
          <UploadCloud className="size-6" aria-hidden />
        </span>

        <div className="space-y-1">
          <p className="font-medium">Upload a document</p>
          <p className="mx-auto max-w-sm text-sm text-muted-foreground">
            PDF, Word or plain text. Stored privately and visible only to you.
          </p>
        </div>

        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          className="hidden"
          onChange={(event) => handleFiles(event.target.files)}
          aria-label="Upload a document"
        />

        <Button
          onClick={() => inputRef.current?.click()}
          disabled={upload.isPending}
        >
          {upload.isPending ? (
            <>
              <Spinner size="sm" /> Uploading
            </>
          ) : (
            "Choose file"
          )}
        </Button>
      </CardContent>
    </Card>
  );
}
