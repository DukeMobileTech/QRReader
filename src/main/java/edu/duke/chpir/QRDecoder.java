package edu.duke.chpir;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class QRDecoder {
    private static final String DELIMITER = ",";
    private static final String SEPARATOR = "\n";
    private static final String HEADER = "PDF_Name,Page_Number,Decoded_ID";
    private static final float[] FACTORS = {2.0f, 0.5f, 0.25f};
    private static final int DIMENSION = 200;
    private static final int X = 150;
    private static final int Y = 50;
    private static int numberOfPages = 0;

    private static BinaryBitmap getBinaryBitmap(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        return new BinaryBitmap(new HybridBinarizer(source));
    }

    private static String getQRText(BinaryBitmap bitmap) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        BarcodeFormat[] formats = {BarcodeFormat.QR_CODE};
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(formats));
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }

    private static String decodeImage(BufferedImage qrCodeImage) {
        try {
            BinaryBitmap bitmap = getBinaryBitmap(qrCodeImage);
            return getQRText(bitmap);
        } catch (NotFoundException e) {
            System.err.println("NotFoundException: Unable to decode BinaryBitmap. " + e);
            return "";
        }
    }

    private static PDDocument getPDDocument(String pdfFile) {
        PDDocument pdDocument = null;
        File sourceFile = new File(pdfFile);
        if (sourceFile.exists()) {
            try {
                pdDocument = PDDocument.load(sourceFile);
            } catch (IOException e) {
                System.out.println("Unable to get PDDocument");
            }
        } else {
            System.err.println(sourceFile.getName() + " does not exist");
        }
        return pdDocument;
    }

    private static void moveFileToProcessed(String destination, File file) {
        try {
            File destinationFolder = new File(destination + "/PROCESSED/");
            if (!destinationFolder.exists()) {
                destinationFolder.mkdir();
            }
            Files.move(file.toPath(), destinationFolder.toPath().resolve(file.toPath().getFileName()), REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        long startTime = System.nanoTime();
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
        for (File file : scanSource.listFiles()) {
            if (file.getName().contains(".pdf")) {
                PDDocument pdDocument = getPDDocument(file.getAbsolutePath());
                processPdDocument(file, pdDocument, outputFolder);
                try {
                    pdDocument.close();
                } catch (IOException e) {
                    System.out.println("Unable to close PDDocument");
                }
                moveFileToProcessed(outputFolder, file);
            }
        }
        long endTime = System.nanoTime();
        long timeTaken = TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
        System.out.println("Number of pages processed: " + numberOfPages);
        System.out.println("Number of seconds taken: " + timeTaken);
    }

    private static void processPdDocument(File file, PDDocument pdDocument, String outputFolder) {
        PDPageTree pdPageTree = pdDocument.getPages();
        PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
        String baseName = file.getName().replace(".pdf", "");
        List<String> csvRows = new ArrayList<>();
        for (int k = 0; k < pdPageTree.getCount(); k++) {
            String row = baseName + DELIMITER + (k + 1) + DELIMITER;
            try {
                BufferedImage image = pdfRenderer.renderImage(k);
                String decodedQRCode = decodeImage(image);
                int index = 0;
                while (decodedQRCode.isEmpty() && index < FACTORS.length) {
                    decodedQRCode = decodeImage(pdfRenderer.renderImage(k, FACTORS[index]));
                    index += 1;
                }
                if (decodedQRCode.isEmpty()) {
                    int width = DIMENSION, height = DIMENSION, xOrigin = X, yOrigin = Y;
                    if (image.getWidth() < DIMENSION + X || image.getHeight() < DIMENSION + Y) {
                        width = image.getWidth();
                        height = image.getHeight();
                        xOrigin = 0;
                        yOrigin = 0;
                    }
                    BufferedImage croppedImage = image.getSubimage(xOrigin, yOrigin, width, height);
                    decodedQRCode = decodeImage(croppedImage);
                }
                numberOfPages += 1;
                System.out.println("Result: " + decodedQRCode);
                csvRows.add(row + decodedQRCode);
                if (!decodedQRCode.isEmpty()) {
                    if (decodedQRCode.length() == 5) {
                        //Write to CARDS folder
                        writeImage(outputFolder, "/CARDS/", "", image, decodedQRCode);
                    } else {
                        //Write to IDs folder
                        writeImage(outputFolder, "/IDS/", decodedQRCode.substring(0, 8), image, decodedQRCode);
                    }
                } else {
                    //Write to NO_QR folder
                    writeImage(outputFolder, "/NOQRS/", baseName, image, (k + 1) + "");
                }
            } catch (IOException e) {
                System.out.println("Unable to convert page to image: " + e);
            }
        }
        writeCSV(outputFolder + "/CSV/", baseName, csvRows);
    }

    private static void writeCSV(String outputFolder, String filename, List<String> csvRows) {
        FileWriter fileWriter = null;
        try {
            File destinationFolder = new File(outputFolder);
            if (!destinationFolder.exists()) destinationFolder.mkdirs();
            String fileName = destinationFolder + "/" + filename + ".csv";
            File file = new File(fileName);
            file.createNewFile();
            fileWriter = new FileWriter(fileName);
            fileWriter.append(HEADER);
            fileWriter.append(SEPARATOR);
            for (String row : csvRows) {
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

    private static void writeImage(String outputFolder, String subFolder1, String subFolder2, BufferedImage image, String imageName) {
        try {
            File destinationFolder = new File(outputFolder + "/" + subFolder1 + "/" + subFolder2);
            if (!destinationFolder.exists()) {
                destinationFolder.mkdirs();
            }
            File imageFile = new File(destinationFolder.getAbsolutePath() + "/" + imageName + ".png");
            ImageIO.write(image, "png", imageFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
