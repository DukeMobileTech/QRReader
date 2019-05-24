package edu.duke.chpir;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static Result[] getQRResults(BinaryBitmap bitmap) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        BarcodeFormat[] formats = {BarcodeFormat.QR_CODE};
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(formats));
        return new QRCodeMultiReader().decodeMultiple(bitmap, hints);
    }

    private static String[] decodeImage(BufferedImage qrCodeImage) {
        try {
            BinaryBitmap bitmap = getBinaryBitmap(qrCodeImage);
            Result[] results = getQRResults(bitmap);
            String[] qrs = new String[results.length];
            for (int k = 0; k < results.length; k++) {
                qrs[k] = results[k].getText();
            }
            return qrs;
        } catch (NotFoundException e) {
            System.err.println("NotFoundException: Unable to decode BinaryBitmap. " + e);
            return null;
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

    private static void moveFileToProcessed(String destination, File file, String rootSource) {
        try {
            String parentFolder = file.getParent();
            Path difference = Paths.get("");
            if (parentFolder != null) {
                Path rootPath = Paths.get(rootSource);
                Path filePath = Paths.get(file.getParent());
                difference = rootPath.relativize(filePath);
            }

            File destinationFolder = new File(destination + "/PROCESSED/" + difference);
            if (!destinationFolder.exists()) {
                Files.createDirectories(destinationFolder.toPath());
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
        processFilesAndFolders(outputFolder, scanSource.listFiles(), scansFolder);
        long endTime = System.nanoTime();
        long timeTaken = TimeUnit.SECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
        System.out.println("Number of pages processed: " + numberOfPages);
        System.out.println("Number of seconds taken: " + timeTaken);
    }

    private static void processFilesAndFolders(String outputFolder, File[] scanFiles, String rootSource) {
        for (File file : scanFiles) {
            if (file.isDirectory()) {
                System.out.println("Traverse sub-directory: " + file.getName());
                processFilesAndFolders(outputFolder, file.listFiles(), rootSource);
            } else if (file.getName().contains(".pdf")) {
                PDDocument pdDocument = getPDDocument(file.getAbsolutePath());
                processPdDocument(file, pdDocument, outputFolder);
                try {
                    pdDocument.close();
                } catch (IOException e) {
                    System.out.println("Unable to close PDDocument");
                }
                moveFileToProcessed(outputFolder, file, rootSource);
            }
        }
    }

    private static boolean isEmpty(String[] array) {
        if (array == null) return true;
        for (String s : array) {
            if (s != null && !s.isEmpty()) {
                return false;
            }
        }
        return true;
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
                String[] decodedQRCodes = decodeImage(image);
                int index = 0;
                while ((decodedQRCodes == null || isEmpty(decodedQRCodes)) && index < FACTORS.length) {
                    decodedQRCodes = decodeImage(pdfRenderer.renderImage(k, FACTORS[index]));
                    index += 1;
                }
                if (isEmpty(decodedQRCodes)) {
                    int width = DIMENSION, height = DIMENSION, xOrigin = X, yOrigin = Y;
                    if (image.getWidth() < DIMENSION + X || image.getHeight() < DIMENSION + Y) {
                        width = image.getWidth();
                        height = image.getHeight();
                        xOrigin = 0;
                        yOrigin = 0;
                    }
                    BufferedImage croppedImage = image.getSubimage(xOrigin, yOrigin, width, height);
                    decodedQRCodes = decodeImage(croppedImage);
                }
                numberOfPages += 1;

                if (!isEmpty(decodedQRCodes)) {
                    for (String decodedQRCode : decodedQRCodes) {
                        System.out.println("Result: " + decodedQRCode);
                        csvRows.add(row + decodedQRCode);
                        int count = decodedQRCode.length() - decodedQRCode.replace("-", "").length();
                        if (decodedQRCode.length() == 5) {
                            //Write to CARDS folder
                            writePage(outputFolder, "/CARDS/", "", pdPageTree.get(k), decodedQRCode);
                        } else if (count == 2) {
                            //Green folder
                            writePage(outputFolder, "/GREEN/", "", pdPageTree.get(k), decodedQRCode);
                        } else {
                            //Write to IDs folder
                            writePage(outputFolder, "/IDS/", decodedQRCode.substring(0, 8), pdPageTree.get(k), decodedQRCode);
                        }
                    }
                } else {
                    //Write to NO_QR folder
                    writePage(outputFolder, "/NOQRS/", baseName, pdPageTree.get(k), (k + 1) + "");
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e);
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

    private static void writePage(String outputFolder, String subFolder1, String subFolder2, PDPage page, String imageName) {
        PDDocument document = new PDDocument();
        document.addPage(page);
        try {
            File destinationFolder = new File(outputFolder + "/" + subFolder1 + "/" + subFolder2);
            if (!destinationFolder.exists()) {
                destinationFolder.mkdirs();
            }
            File pageFile = new File(destinationFolder.getAbsolutePath() + "/" + imageName + ".pdf");
            document.save(pageFile);
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
