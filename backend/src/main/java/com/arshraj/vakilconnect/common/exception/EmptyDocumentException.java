package com.arshraj.vakilconnect.common.exception;

/**
 * The uploaded file has no bytes. Maps to HTTP 400 with code DOCUMENT_EMPTY.
 *
 * A DISTINCT CODE RATHER THAN A GENERIC 400, because the cause is almost always
 * mechanical rather than a user mistake: a form submitted with no file
 * selected, a drag-and-drop that dropped a folder, a stream that closed early.
 * The frontend can say "choose a file" instead of "that file is not valid",
 * which are different instructions.
 *
 * Also enforced at the database by ck_ai_documents_size_positive, so a
 * zero-byte row cannot exist even if it reached the table by another route.
 * A document with nothing in it would otherwise sit in the AI-2 queue failing
 * extraction forever.
 *
 * The message is fixed at the exception, never assembled from caller input.
 */
public class EmptyDocumentException extends RuntimeException {

    public static final String CODE = "DOCUMENT_EMPTY";

    public EmptyDocumentException() {
        super("The uploaded file is empty.");
    }
}
