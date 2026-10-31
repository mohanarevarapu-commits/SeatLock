package com.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatlock.support.QrCodes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    void ticketQrCodeIsAPngThatScansBackToTheReference() throws Exception {
        byte[] png = generator.png("SL-7KQ2MX");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(QrCodeGenerator.SIZE);
        assertThat(image.getHeight()).isEqualTo(QrCodeGenerator.SIZE);
        assertThat(QrCodes.decode(png)).isEqualTo("SL-7KQ2MX");
    }
}
