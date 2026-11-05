package com.ecommerce.media.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.ecommerce.media.asset.application.exception.MediaException;
import com.ecommerce.media.asset.application.model.CanonicalImage;
import com.ecommerce.media.configuration.MediaProperties;

class ImageCanonicalizerTest {
    @Test
    void reencodesPngAndDerivesMetadataFromDecodedImage() throws Exception {
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", source);
        source.write("EXIF-AND-OTHER-TRAILER".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        ImageCanonicalizer canonicalizer = new ImageCanonicalizer(new MediaProperties(), new Semaphore(1));

        CanonicalImage image = canonicalizer.canonicalize(new ByteArrayInputStream(source.toByteArray()));

        assertThat(image.mediaType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(3);
        assertThat(image.height()).isEqualTo(2);
        assertThat(ImageIO.read(new ByteArrayInputStream(image.bytes()))).isNotNull();
        assertThat(new String(image.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("EXIF-AND-OTHER-TRAILER");
    }

    @Test
    void rejectsUnsupportedSignaturesBeforeInvokingAnImageDecoder() {
        ImageCanonicalizer canonicalizer = new ImageCanonicalizer(new MediaProperties(), new Semaphore(1));

        assertThatThrownBy(() -> canonicalizer.canonicalize(new ByteArrayInputStream("not an image".getBytes())))
                .isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void boundsBytesReadFromTheMultipartStream() {
        MediaProperties properties = new MediaProperties();
        properties.setMaxInputBytes(16);
        ImageCanonicalizer canonicalizer = new ImageCanonicalizer(properties, new Semaphore(1));
        InputStream oversized = new ByteArrayInputStream(new byte[17]);

        assertThatThrownBy(() -> canonicalizer.canonicalize(oversized))
                .isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void rejectsImagesThatExceedThePixelBudgetBeforeDecodingPixels() throws Exception {
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", source);
        MediaProperties properties = new MediaProperties();
        properties.setMaxPixels(8);
        ImageCanonicalizer canonicalizer = new ImageCanonicalizer(properties, new Semaphore(1));

        assertThatThrownBy(() -> canonicalizer.canonicalize(new ByteArrayInputStream(source.toByteArray())))
                .isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo("INVALID_MEDIA_IMAGE");
    }

    @Test
    void appliesJpegExifOrientationBeforeReportingDimensions() throws Exception {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
        byte[] original = jpeg.toByteArray();
        byte[] exif = {
            'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0,
            0, 0
        };
        int segmentLength = exif.length + 2;
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(original, 0, 2);
        withExif.write(0xff);
        withExif.write(0xe1);
        withExif.write((segmentLength >>> 8) & 0xff);
        withExif.write(segmentLength & 0xff);
        withExif.write(exif);
        withExif.write(original, 2, original.length - 2);

        ImageCanonicalizer canonicalizer = new ImageCanonicalizer(new MediaProperties(), new Semaphore(1));
        CanonicalImage image = canonicalizer.canonicalize(new ByteArrayInputStream(withExif.toByteArray()));

        assertThat(image.mediaType()).isEqualTo("image/jpeg");
        assertThat(image.width()).isEqualTo(2);
        assertThat(image.height()).isEqualTo(4);
    }
}
