package edu.duke.chpir;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class QRDecoder {
    private static final String DELIMITER = ",";
    private static final String SEPARATOR = "\n";
    private static final String HEADER = "PDF_Name,Page_Number,Decoded_ID";
    private static final float[] FACTORS = {2.0f, 0.5f, 0.25f};
    private static int numberOfPages = 0;
    private static ArrayList<String> csvRows = new ArrayList<>();

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

    private static void writeCSV(String outputFolder) {
        FileWriter fileWriter = null;
        String fileName = outputFolder + "/codes.csv";
        try {
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
                processPdDocument(file, pdDocument);
                try {
                    pdDocument.close();
                } catch (IOException e) {
                    System.out.println("Unable to close PDDocument");
                }
            }
        }
        writeCSV(outputFolder);
        long endTime = System.nanoTime();
        long timeTaken = TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
        System.out.println("Number of pages processed: " + numberOfPages);
        System.out.println("Number of seconds taken: " + timeTaken);
    }

    private static void processPdDocument(File file, PDDocument pdDocument) {
        PDPageTree pdPageTree = pdDocument.getPages();
        PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
        for (int k = 0; k < pdPageTree.getCount(); k++) {
            String row = file.getName().replace(".pdf", "") + DELIMITER + (k + 1) + DELIMITER;
            try {
                String decodedQRCode = decodeImage(pdfRenderer.renderImage(k));
                int index = 0;
                while (decodedQRCode.isEmpty() && index < FACTORS.length) {
                    decodedQRCode = decodeImage(pdfRenderer.renderImage(k, FACTORS[index]));
                    index += 1;
                }
                numberOfPages += 1;
                System.out.println("Result: " + decodedQRCode);
                csvRows.add(row + decodedQRCode);
            } catch (IOException e) {
                System.out.println("Unable to convert page to image: " + e);
            }
        }
    }

}
