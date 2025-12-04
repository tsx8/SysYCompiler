package top.tsxb.compiler.common;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import top.tsxb.compiler.driver.CompilerConfig;

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

        String bootstrap = createBootstrap();
        String configJson = createConfigJson();

        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
            ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry configEntry = new ZipEntry("config.json");
            zos.putNextEntry(configEntry);
            zos.write(configJson.getBytes());
            zos.closeEntry();

            ZipEntry compilerEntry = new ZipEntry("Compiler.java");
            zos.putNextEntry(compilerEntry);
            zos.write(bootstrap.getBytes());
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

    private static String createConfigJson() {
        return "{\n" + "  \"programming language\": \"" + CompilerConfig.PROGRAMMING_LANGUAGE + "\",\n"
            + "  \"object code\": \"" + CompilerConfig.OBJECT_CODE + "\"\n" + "}";
    }

    private static String createBootstrap() {
        return """
            public class Compiler {
                public static void main(String[] args) {
                    // 调用实际的、带有包路径的主类
                    top.tsxb.compiler.driver.Compiler.main(args);
                }
            }
            """;
    }
}
