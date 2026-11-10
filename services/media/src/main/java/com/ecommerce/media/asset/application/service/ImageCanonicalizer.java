package com.ecommerce.media.asset.application.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import com.ecommerce.media.asset.application.exception.MediaException;
import com.ecommerce.media.asset.application.model.CanonicalImage;
import com.ecommerce.media.asset.application.model.ImagePolicy;

@Component
public class ImageCanonicalizer {
    private final ImagePolicy policy;
    private final Semaphore permits;

    public ImageCanonicalizer(ImagePolicy policy, Semaphore imageDecodePermits) {
        this.policy = policy;
        this.permits = imageDecodePermits;
    }

    public CanonicalImage canonicalize(InputStream input) throws IOException {
        byte[] source = input.readNBytes(policy.maxInputBytes() + 1);
        if (source.length == 0) {
            throw MediaException.invalidImage("The uploaded file is empty.");
        }
        if (source.length > policy.maxInputBytes()) {
            throw MediaException.tooLarge();
        }
        String format = sniffFormat(source);
        if (format == null) {
            throw MediaException.unsupported();
        }
        if (!permits.tryAcquire()) {
            throw MediaException.processingBusy();
        }
        try {
            return decodeAndEncode(source, format);
        } finally {
            permits.release();
        }
    }

    private CanonicalImage decodeAndEncode(byte[] source, String format) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw MediaException.invalidImage("The uploaded image cannot be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String decodedFormat = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!decodedFormat.equals(format) && !(format.equals("jpeg") && decodedFormat.equals("jpg"))) {
                    throw MediaException.invalidImage("The image signature does not match its encoded content.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > policy.maxSide()
                        || height > policy.maxSide()
                        || (long) width * height > policy.maxPixels()) {
                    throw MediaException.invalidImage("The image dimensions exceed the configured limit.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw MediaException.invalidImage("The uploaded image cannot be decoded.");
                }
                int orientation = format.equals("jpeg") ? exifOrientation(source) : 1;
                BufferedImage normalized = orient(decoded, orientation);
                byte[] canonical = encode(normalized, format);
                String mediaType = format.equals("jpeg") ? "image/jpeg" : "image/png";
                return new CanonicalImage(
                        canonical,
                        mediaType,
                        format.equals("jpeg") ? "jpg" : "png",
                        normalized.getWidth(),
                        normalized.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (MediaException exception) {
            throw exception;
        } catch (Exception exception) {
            if (hasCause(exception, OutputLimitException.class)) {
                throw MediaException.tooLarge();
            }
            throw MediaException.invalidImage("The uploaded image is corrupt or cannot be safely decoded.");
        }
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> expected) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (expected.isInstance(current)) return true;
        }
        return false;
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IllegalStateException("The platform has no image writer for the accepted format.");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutput =
                ImageIO.createImageOutputStream(new BoundedOutputStream(output, policy.maxOutputBytes()))) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpeg") && params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.9f);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            imageOutput.flush();
            return output.toByteArray();
        } catch (OutputLimitException exception) {
            throw MediaException.tooLarge();
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage destination = new BufferedImage(
                swap ? height : width,
                swap ? width : height,
                source.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : source.getType());
        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> {
                transform.translate(width, 0);
                transform.scale(-1, 1);
            }
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {
                transform.translate(0, height);
                transform.scale(1, -1);
            }
            case 5 -> {
                transform.rotate(Math.PI / 2);
                transform.scale(1, -1);
            }
            case 6 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                transform.translate(height, width);
                transform.rotate(Math.PI / 2);
                transform.scale(-1, 1);
            }
            case 8 -> {
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
                return source;
            }
        }
        Graphics2D graphics = destination.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, transform, null);
        } catch (ImagingOpException exception) {
            throw MediaException.invalidImage("The image orientation metadata is invalid.");
        } finally {
            graphics.dispose();
        }
        return destination;
    }

    private String sniffFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) {
            return "jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        if (bytes.length >= png.length) {
            for (int index = 0; index < png.length; index++) {
                if (bytes[index] != png[index]) return null;
            }
            return "png";
        }
        return null;
    }

    private int exifOrientation(byte[] bytes) {
        int offset = 2;
        while (offset + 4 < bytes.length && (bytes[offset] & 255) == 0xff) {
            int marker = bytes[offset + 1] & 255;
            int length = ((bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
            if (length < 2 || offset + 2 + length > bytes.length) return 1;
            int data = offset + 4;
            if (marker == 0xe1
                    && length >= 10
                    && bytes[data] == 'E'
                    && bytes[data + 1] == 'x'
                    && bytes[data + 2] == 'i'
                    && bytes[data + 3] == 'f') {
                return readTiffOrientation(bytes, data + 6, offset + 2 + length);
            }
            if (marker == 0xda || marker == 0xd9) break;
            offset += length + 2;
        }
        return 1;
    }

    private int readTiffOrientation(byte[] bytes, int start, int end) {
        if (start + 8 > end) return 1;
        boolean little = bytes[start] == 'I' && bytes[start + 1] == 'I';
        boolean big = bytes[start] == 'M' && bytes[start + 1] == 'M';
        if (!little && !big) return 1;
        int ifd = start + (int) readUnsigned(bytes, start + 4, 4, little);
        if (ifd + 2 > end) return 1;
        int count = (int) readUnsigned(bytes, ifd, 2, little);
        for (int index = 0; index < count; index++) {
            int entry = ifd + 2 + index * 12;
            if (entry + 12 > end) return 1;
            if (readUnsigned(bytes, entry, 2, little) == 0x0112) {
                int value = (int) readUnsigned(bytes, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private long readUnsigned(byte[] bytes, int start, int count, boolean little) {
        long value = 0;
        for (int index = 0; index < count; index++) {
            int byteIndex = little ? start + count - index - 1 : start + index;
            value = (value << 8) | (bytes[byteIndex] & 255L);
        }
        return value;
    }

    private static final class BoundedOutputStream extends java.io.FilterOutputStream {
        private final int max;
        private int written;

        BoundedOutputStream(ByteArrayOutputStream output, int max) {
            super(output);
            this.max = max;
        }

        @Override
        public void write(int value) throws IOException {
            if (++written > max) throw new OutputLimitException();
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if ((long) written + length > max) throw new OutputLimitException();
            written += length;
            out.write(bytes, offset, length);
        }
    }

    private static final class OutputLimitException extends IOException {}
}
