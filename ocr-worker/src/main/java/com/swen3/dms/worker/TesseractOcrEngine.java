package com.swen3.dms.worker;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Runs the Tesseract command-line tool. Using the CLI instead of native bindings keeps the JVM independent of
 * the installed Tesseract library version.
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private final WorkerProperties properties;

    public TesseractOcrEngine(WorkerProperties properties) {
        this.properties = properties;
    }

    @Override
    public String recognize(BufferedImage image) throws OcrException {
        Path input = null;
        try {
            input = Files.createTempFile("ocr-", ".png");
            ImageIO.write(image, "png", input.toFile());
            return run(input);
        } catch (IOException e) {
            throw new OcrException("Could not run " + properties.tesseractCommand(), e);
        } finally {
            deleteQuietly(input);
        }
    }

    private String run(Path input) throws IOException, OcrException {
        Path errors = Files.createTempFile("ocr-", ".log");
        try {
            Process process = new ProcessBuilder(List.of(
                    properties.tesseractCommand(), input.toString(), "stdout", "-l", properties.languages()))
                    .redirectError(errors.toFile())
                    .start();
            // read stdout before waiting, so a full pipe buffer cannot block Tesseract
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(properties.timeoutPerPage().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new OcrException("Tesseract timed out after " + properties.timeoutPerPage());
            }
            if (process.exitValue() != 0) {
                throw new OcrException("Tesseract failed: " + Files.readString(errors).strip());
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("Interrupted while running Tesseract", e);
        } finally {
            deleteQuietly(errors);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // temp files are cleaned up by the OS eventually
            }
        }
    }
}
