package com.swen3.dms.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Runs the real Tesseract binary; skipped where it is not installed. CI and the Docker image have it. */
@EnabledIf("tesseractInstalled")
class TesseractOcrEngineTest {

    private final WorkerProperties properties =
            new WorkerProperties("tesseract", "eng", 300, 10, Duration.ofSeconds(60));

    @Test
    void recognizesRenderedText() throws Exception {
        String text = new TesseractOcrEngine(properties).recognize(TestDocuments.image("Invoice 4711 paid"));

        assertThat(text).contains("Invoice").contains("4711");
    }

    @Test
    void extractsTextFromScannedPdf() throws Exception {
        TextExtractor extractor = new TextExtractor(new TesseractOcrEngine(properties), properties);

        String text = extractor.extract(TestDocuments.scannedPdf("Rental agreement 2026"), "application/pdf");

        assertThat(text).containsIgnoringCase("agreement").contains("2026");
    }

    static boolean tesseractInstalled() {
        try {
            return new ProcessBuilder("tesseract", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
