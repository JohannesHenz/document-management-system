package com.swen3.dms.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    private final AtomicInteger ocrCalls = new AtomicInteger();
    private final OcrEngine fakeOcr = image -> "recognized page " + ocrCalls.incrementAndGet();
    private final WorkerProperties properties =
            new WorkerProperties("tesseract", "eng", 150, 2, Duration.ofSeconds(10));
    private final TextExtractor extractor = new TextExtractor(fakeOcr, properties);

    @Test
    void readsTextLayerOfDigitalPdfWithoutOcr() throws Exception {
        byte[] pdf = TestDocuments.textPdf("Invoice number 4711", "Total amount 120 EUR");

        String text = extractor.extract(pdf, "application/pdf");

        assertThat(text).contains("Invoice number 4711").contains("Total amount 120 EUR");
        assertThat(ocrCalls).hasValue(0);
    }

    @Test
    void runsOcrOnEveryPageOfScannedPdf() throws Exception {
        byte[] pdf = TestDocuments.scannedPdf("first", "second");

        String text = extractor.extract(pdf, "application/pdf");

        assertThat(text).isEqualTo("recognized page 1\n\nrecognized page 2");
    }

    @Test
    void stopsAfterMaxPages() throws Exception {
        byte[] pdf = TestDocuments.scannedPdf("one", "two", "three");

        extractor.extract(pdf, "application/pdf");

        assertThat(ocrCalls).hasValue(properties.maxPages());
    }

    @Test
    void runsOcrOnImages() throws Exception {
        assertThat(extractor.extract(TestDocuments.png("hello"), "image/png")).isEqualTo("recognized page 1");
    }

    @Test
    void rejectsUnreadableAndUnsupportedFiles() {
        assertThatThrownBy(() -> extractor.extract(new byte[] {1, 2, 3}, "image/png"))
                .isInstanceOf(OcrException.class);
        assertThatThrownBy(() -> extractor.extract(new byte[] {1}, "text/plain"))
                .isInstanceOf(OcrException.class);
    }

    @Test
    void normalizesWhitespace() {
        assertThat(TextExtractor.normalize("  line one   \r\n\n\n\n\fline two \n"))
                .isEqualTo("line one\n\nline two");
    }
}
