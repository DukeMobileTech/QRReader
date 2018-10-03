package edu.duke.chpir;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class QRDecoder {
    private static final String DELIMITER = ",";
    private static final String SEPARATOR = "\n";
    private static final String HEADER = "PDF_Name,Page_Number,Decoded_ID";
    private static final double FACTOR_HALF = 0.5;
    private static final int FACTOR_DOUBLE = 2;

    private static BinaryBitmap getBinaryBitmap(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        return new BinaryBitmap(new HybridBinarizer(source));
    }

    private static File createDirectory(String directoryName) {
        File directory = new File(directoryName);
        if (!directory.exists()) {
            directory.mkdir();
        }
        return directory;
    }

    private static BufferedImage getBufferedImage(File file) {
        BufferedImage image = null;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            System.err.println("IOException! Cannot read file. " + e.getMessage());
        }
        return image;
    }

    private static String decodeScaled(File qrCodeImage, double factor) {
        String returnValue = "";
        BufferedImage before = getBufferedImage(qrCodeImage);
        int width = before.getWidth(), height = before.getHeight();
        BufferedImage after = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        AffineTransform affineTransform = new AffineTransform();
        affineTransform.scale(factor, factor);
        AffineTransformOp scaleOp = new AffineTransformOp(affineTransform, AffineTransformOp.TYPE_BILINEAR);
        after = scaleOp.filter(before, after);
        BinaryBitmap bitmap = getBinaryBitmap(after);
        try {
            returnValue = getQRText(bitmap);
        } catch (NotFoundException e) {
            System.err.println("NotFoundException: Unable to decode BinaryBitmap. " + e);
        }
        return returnValue;
    }

    private static String getQRText(BinaryBitmap bitmap) throws NotFoundException {
        Map<DecodeHintType, Object>  hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        BarcodeFormat[] formats = { BarcodeFormat.QR_CODE };
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(formats));
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }

    private static String decodeNormal(File qrCodeImage) {
        String returnValue = "";
        try {
            BufferedImage bufferedQRCodeImage = getBufferedImage(qrCodeImage);
            BinaryBitmap bitmap = getBinaryBitmap(bufferedQRCodeImage);
            returnValue = getQRText(bitmap);
        } catch (NotFoundException e) {
            System.err.println("NotFoundException: Unable to decode BinaryBitmap. " + e);
        }
        return returnValue;
    }

    private static String pdfToImage(String pdfFile, String outputFolder) {
        String value = "";
        try {
            createDirectory(outputFolder);
            File sourceFile = new File(pdfFile);
            if (sourceFile.exists()) {
                File destinationSubDir = createDirectory(outputFolder + "/" + sourceFile.getName().replace(".pdf", ""));
                PDDocument document = PDDocument.load(pdfFile);
                List<PDPage> pages = document.getDocumentCatalog().getAllPages();

                int pageNumber = 1;
                for (PDPage page : pages) {
                    BufferedImage image = page.convertToImage();
                    File outputFile = new File(destinationSubDir.getAbsolutePath() + "/" + pageNumber + ".png");
                    ImageIO.write(image, "png", outputFile);
                    pageNumber++;
                }
                document.close();
                value = destinationSubDir.getAbsolutePath();
            } else {
                System.err.println(sourceFile.getName() + " does not exist");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private static void writeCSV(List<String> rows, String outputFolder) {
        FileWriter fileWriter = null;
        String fileName = outputFolder + "/decodedQRCodes.csv";
        try {
            fileWriter = new FileWriter(fileName);
            fileWriter.append(HEADER);
            fileWriter.append(SEPARATOR);
            for (String row : rows) {
                fileWriter.append(row);
                fileWriter.append(SEPARATOR);
            }
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                System.err.println("IOException: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        String scansFolder = "";
        String outputFolder = "";
        if (args.length != 2) {
            System.out.println("Incorrect number of arguments");
            System.exit(0);
        } else {
            scansFolder = args[0];
            outputFolder = args[1];
        }

        File scanSource = new File(scansFolder);
        ArrayList<String> csvRows = new ArrayList<>();
        for (File file : scanSource.listFiles()) {
            if (file.getName().contains(".pdf")) {
                String pngDestination = pdfToImage(file.getAbsolutePath(), outputFolder);
                File imageFolder = new File(pngDestination);
                for (File imageFile : imageFolder.listFiles()) {
                    if (imageFile.getName().contains(".png")) {
                        String row = file.getName().replace(".pdf", "") + DELIMITER
                                + imageFile.getName().replace(".png", "") + DELIMITER;
                        String decodedQRCode = decodeNormal(imageFile);
                        if (decodedQRCode.isEmpty()) {
                            decodedQRCode = decodeScaled(imageFile, FACTOR_HALF);
                            if (decodedQRCode.isEmpty()) {
                                decodedQRCode = decodeScaled(imageFile, FACTOR_DOUBLE);
                            }
                        }
                        System.out.println("Result: " + decodedQRCode);
                        csvRows.add(row + decodedQRCode);
                    }
                }
            }
        }
        writeCSV(csvRows, outputFolder);
    }
}
