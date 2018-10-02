package edu.duke.chpir;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class QRDecoder {
    private static final String DELIMITER = ",";
    private static final String SEPARATOR = "\n";
    private static final String HEADER = "PDF_Name,Page_Number,Decoded_ID";
    public static final int DIMENSION = 200;
    public static final int X = 150;
    public static final int Y = 50;

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

    private static String decodeQRCode(File qrCodeImage) {
        String returnValue = "";
        BufferedImage bufferedQRCodeImage = getBufferedImage(qrCodeImage);
        BinaryBitmap bitmap = getBinaryBitmap(bufferedQRCodeImage);
        try {
            Result result = new MultiFormatReader().decode(bitmap);
            returnValue = result.getText();
        } catch (NotFoundException e) {
            int width = DIMENSION, height = DIMENSION;
            int xOrigin = X, yOrigin = Y;
            if (bufferedQRCodeImage.getWidth() < DIMENSION + X || bufferedQRCodeImage.getHeight() < DIMENSION + Y) {
                width = bufferedQRCodeImage.getWidth();
                height = bufferedQRCodeImage.getHeight();
                xOrigin = 0;
                yOrigin = 0;
            }
            BufferedImage croppedImage = bufferedQRCodeImage.getSubimage(xOrigin, yOrigin, width, height);
            File croppedDir = createDirectory(qrCodeImage.getAbsolutePath().replace(".png", ""));
            File outputFile = new File(croppedDir.getAbsolutePath() + "/" + "QUADRANT_ONE" + ".png");
            BinaryBitmap croppedBitmap = getBinaryBitmap(croppedImage);
            try {
                ImageIO.write(croppedImage, "png", outputFile);
                Result result = new MultiFormatReader().decode(croppedBitmap);
                returnValue = result.getText();
            } catch (IOException e1) {
                System.err.println("IOException: Cannot write image to file. " + e1.getMessage());
            } catch (NotFoundException e2) {
                System.err.println("NotFoundException: Unable to decode BinaryBitmap. " + e2);
            }
        }
        int dashCount = StringUtils.countMatches(returnValue, "-");
        if (dashCount != 4) {
            // TODO: 10/2/18 Decoded QR does not match expected format
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
                        String decodedQRCode = decodeQRCode(imageFile);
                        System.out.println("Result: " + decodedQRCode);
                        csvRows.add(row + decodedQRCode);
                    }
                }
            }
        }
        writeCSV(csvRows, outputFolder);
    }
}
