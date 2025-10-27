package top.tsxb.compiler.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The type Packaging.
 */
public class Packaging {
    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        try {
            createSubmissionZip();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    /**
     * Create submission zip.
     *
     * @throws IOException the io exception
     */
    public static void createSubmissionZip() throws IOException {
        Path sourceDir = Paths.get("src");
        Path targetDir = Paths.get("out", "submit");
        Files.createDirectories(targetDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String zipFileName = String.format("homework_%s.zip", timestamp);
        Path zipFilePath = targetDir.resolve(zipFileName);

        Map<String, String> configData = new HashMap<>();
        configData.put("programming language", "java");
        configData.put("object code", "mips");
        String configJson = createConfigJson(configData);

        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
            ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry configEntry = new ZipEntry("config.json");
            zos.putNextEntry(configEntry);
            zos.write(configJson.getBytes());
            zos.closeEntry();

            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    try {
                        Path relativePath = sourceDir.relativize(path);
                        ZipEntry javaFileEntry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                        zos.putNextEntry(javaFileEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException("Error adding file to zip: " + path, e);
                    }
                });
            }
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            }
            throw e;
        }
    }

    private static String createConfigJson(Map<String, String> configData) {
        return "{\n" + "  \"programming language\": \"" + configData.get("programming language") + "\",\n"
            + "  \"object code\": \"" + configData.get("object code") + "\"\n" + "}";
    }
}
