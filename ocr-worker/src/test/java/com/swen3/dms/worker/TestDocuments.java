package com.swen3.dms.worker;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Builds test files in memory. */
final class TestDocuments {

    private TestDocuments() {
    }

    /** A digitally created PDF: one page per line, each with a real text layer. */
    static byte[] textPdf(String... pages) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(font, 14);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            return save(pdf);
        }
    }

    /** A scanned PDF: every page is just an image of the given text, without a text layer. */
    static byte[] scannedPdf(String... pages) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(pdf, image(text));
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.drawImage(image, 50, 600, 500, 100);
                }
            }
            return save(pdf);
        }
    }

    static byte[] png(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image(text), "png", out);
        return out.toByteArray();
    }

    /** Black text on white, large enough for reliable OCR. */
    static BufferedImage image(String text) {
        BufferedImage image = new BufferedImage(1600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 64));
        g.drawString(text, 40, 130);
        g.dispose();
        return image;
    }

    private static byte[] save(PDDocument pdf) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.save(out);
        return out.toByteArray();
    }
}
