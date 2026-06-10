package com.darshan.documind.service;

import com.darshan.documind.exception.IngestionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Thin wrapper around Apache PDFBox so the rest of the pipeline depends on a
 * single seam — easy to mock in tests, easy to extend with OCR for scanned
 * PDFs later.
 */
@Component
public class PdfTextExtractor {

    public String extract(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // keeps multi-column layouts in reading order
            return stripper.getText(document);
        } catch (IOException e) {
            throw new IngestionException("Failed to extract text from " + pdfPath, e);
        }
    }
}
