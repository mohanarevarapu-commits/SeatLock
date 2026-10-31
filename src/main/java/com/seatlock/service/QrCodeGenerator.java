package com.seatlock.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Draws QR codes as PNG images for tickets. A ticket's QR code holds its booking reference
 * (e.g. {@code SL-7KQ2MX}), which is what a venue looks a booking up by.
 */
@Component
public class QrCodeGenerator {

    /** Side of the image in pixels: large enough to scan from a phone screen or a printout. */
    public static final int SIZE = 320;

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    public byte[] png(String content) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2));
        } catch (WriterException ex) {
            throw new IllegalArgumentException("Cannot draw a QR code for this text", ex);
        }
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", png);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return png.toByteArray();
    }
}
