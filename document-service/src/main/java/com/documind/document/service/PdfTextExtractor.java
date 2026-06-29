package com.documind.document.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Apache PDFBox behind one method: a stored PDF path -&gt; its extracted text. */
@Component
public class PdfTextExtractor {

    public String extract(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract text from " + path, e);
        }
    }
}
