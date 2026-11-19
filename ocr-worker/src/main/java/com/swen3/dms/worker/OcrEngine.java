package com.swen3.dms.worker;

import java.awt.image.BufferedImage;

/** Recognizes the text in an image. */
public interface OcrEngine {

    String recognize(BufferedImage image) throws OcrException;
}
