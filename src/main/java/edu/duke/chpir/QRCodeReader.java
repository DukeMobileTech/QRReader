package edu.duke.chpir;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QRCodeReader {
    private static final String DELIMITER = ",";
    private static final String SEPARATOR = "\n";
    private static final String HEADER = "page,id";

    private static String decodeQRCode(File qrCodeImage) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(qrCodeImage);
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            System.out.println("There is no QR code in the image");
            return "";
        }
    }

    private static String pdfToImage(String pdfFile, String outputFolder) {
        String value = "";
        try {
            File destinationFile = new File(outputFolder);
            if (!destinationFile.exists()) {
                destinationFile.mkdir();
                System.out.println("Created output folder: " + destinationFile.getAbsolutePath());
            }
            File sourceFile = new File(pdfFile);
            if (sourceFile.exists()) {
                File destinationSubDir = new File(outputFolder + "/" + sourceFile.getName().replace(".pdf", ""));
                if (!destinationSubDir.exists()) {
                    destinationSubDir.mkdir();
                }

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
            e.printStackTrace();
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
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
            System.out.println("File Name: " + file.getName());
            if (file.getName().contains("pdf")) {
                String pngDestination = pdfToImage(file.getAbsolutePath(), outputFolder);
                File imageFolder = new File(pngDestination);
                for (File imageFile : imageFolder.listFiles()) {
                    String row = file.getName().replace(".pdf", "") + "-" +
                            imageFile.getName().replace(".png", "") + DELIMITER;
                    try {
                        String decodedQRCode = decodeQRCode(imageFile);
                        System.out.println("Result: " + decodedQRCode);
                        csvRows.add(row + decodedQRCode);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        writeCSV(csvRows, outputFolder);
    }
}