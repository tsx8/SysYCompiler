package top.tsxb.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.tsxb.compiler.driver.CompilerConfig;

public class TestcasesImporter {
    private static final String ANS_FILE = "ans.txt";
    private static final String TEST_FILE = "testfile.txt";
    private static final String INPUT_FILE = "in.txt";
    private static final Path ZIP_PATH = Paths.get(CompilerConfig.CURRENT_HOMEWORK + ".zip");
    private static final Path DEST_DIR = Paths.get("testcases/" + CompilerConfig.CURRENT_HOMEWORK);

    public static void main(String[] args) {
        Path zipPath = ZIP_PATH;
        Path baseDestinationDir = DEST_DIR;

        if (!Files.exists(zipPath)) {
            System.err.println("错误：Zip文件不存在: " + zipPath.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("开始处理Zip文件: " + zipPath.toAbsolutePath());
        System.out.println("测试用例将被导入到: " + baseDestinationDir.toAbsolutePath());

        try {
            processZipFile(zipPath, baseDestinationDir);
            System.out.println("\n处理完成！");
        } catch (IOException e) {
            System.err.println("处理文件时发生IO错误: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static void processZipFile(Path zipPath, Path baseDestinationDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {

            Map<String, Set<String>> directoryContents = findTestcaseDirectoriesInZip(zipFile);

            if (directoryContents.isEmpty()) {
                System.out.println("在zip文件中没有找到任何同时包含 'ans.txt' 和 'testfile.txt' 的目录。");
                return;
            }

            int testcaseCount = 0;
            // 排序以保证 testcase 文件夹按顺序命名
            List<String> sortedDirs = new ArrayList<>(directoryContents.keySet());
            Collections.sort(sortedDirs);

            for (String dirPath : sortedDirs) {
                Set<String> files = directoryContents.get(dirPath);

                if (files.contains(ANS_FILE) && files.contains(TEST_FILE)) {
                    testcaseCount++;
                    System.out.println("\n找到一个测试用例 (Testcase " + testcaseCount + ") 在目录: '" + dirPath + "'");

                    Path destinationDir = baseDestinationDir.resolve("testcase" + testcaseCount);

                    // 2. 实现强制覆盖逻辑
                    if (Files.exists(destinationDir)) {
                        System.out.println("  -> 目标目录已存在，正在强制覆盖...");
                        deleteDirectory(destinationDir); // 先删除旧目录
                    }

                    // 重新创建目录
                    Files.createDirectories(destinationDir);
                    System.out.println("  -> 已创建目标目录: " + destinationDir);

                    // 提取文件
                    extractFile(zipFile, dirPath + ANS_FILE, destinationDir.resolve(ANS_FILE));
                    extractFile(zipFile, dirPath + TEST_FILE, destinationDir.resolve(TEST_FILE));
                    extractFile(zipFile, dirPath + INPUT_FILE, destinationDir.resolve(INPUT_FILE));
                }
            }
        }
    }

    /**
     * 在Zip文件中查找包含测试文件的目录结构。
     */
    private static Map<String, Set<String>> findTestcaseDirectoriesInZip(ZipFile zipFile) {
        Map<String, Set<String>> directoryContents = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                Path path = Paths.get(entry.getName());
                Path parent = path.getParent();

                String dirPath = (parent == null) ? "" : parent.toString().replace('\\', '/') + "/";
                String fileName = path.getFileName().toString();

                directoryContents.computeIfAbsent(dirPath, k -> new HashSet<>()).add(fileName);
            }
        }
        return directoryContents;
    }

    /**
     * 提取单个文件到指定路径。
     */
    private static void extractFile(ZipFile zipFile, String entryName, Path destPath) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            System.err.println("  -> 警告: 无法在zip文件中找到条目: " + entryName);
            return;
        }

        // 使用 try-with-resources 确保流被正确关闭
        try (InputStream is = zipFile.getInputStream(entry); OutputStream fos = Files.newOutputStream(destPath)) {

            byte[] buffer = new byte[4096]; // 4KB 缓冲区
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
        System.out.println("  -> 已提取文件: " + entryName + " 到 " + destPath);
    }

    /**
     * 4. 新增辅助方法：递归删除目录及其所有内容。 这是实现“强制覆盖”的关键。
     * 
     * @param path 要删除的目录的路径
     * @throws IOException 如果删除过程中发生错误
     */
    private static void deleteDirectory(Path path) throws IOException {
        // 使用 Files.walk 遍历目录下的所有文件和子目录
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()) // 逆序排序，确保先删除文件再删除目录
                .forEach(p -> {
                    try {
                        Files.delete(p);
                        System.out.println("    - 已删除: " + p);
                    } catch (IOException e) {
                        // 封装为非受检异常，以便在 lambda 中使用
                        throw new RuntimeException("无法删除 " + p, e);
                    }
                });
        } catch (RuntimeException e) {
            // 解包并重新抛出原始的 IOException
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            }
            throw e;
        }
    }
}