package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static cn.axg.platform.PlatformService.str;

@Component
final class DocumentPreview {
    private static final Set<String> OFFICE = Set.of("doc", "docx", "docm", "dot", "dotx", "dotm",
        "ppt", "pptx", "pptm", "pps", "ppsx", "ppsm", "pot", "potx", "potm");
    private final Semaphore workers = new Semaphore(2);
    private final Path cache;
    private final String office;

    DocumentPreview(@Value("${app.data-dir}") String dataDir, @Value("${app.office.executable}") String executable) {
        cache = Path.of(dataDir, "pdf-previews").toAbsolutePath().normalize();
        office = executable.isBlank() ? findOffice() : executable;
    }

    static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String findOffice() {
        if (!System.getProperty("os.name").startsWith("Windows")) return "soffice";
        for (Path candidate : new Path[] {
            Path.of(".runtime", "office", "extracted", "program", "soffice.com"),
            Path.of("..", ".runtime", "office", "extracted", "program", "soffice.com"),
            Path.of(Objects.toString(System.getenv("ProgramFiles"), "C:/Program Files"), "LibreOffice", "program", "soffice.com")
        }) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize().toString();
        }
        return "soffice.com";
    }

    byte[] pdf(JsonNode file, byte[] source) throws IOException {
        String ext = file.path("sourceExtension").asText(extension(str(file, "name")));
        if (!ext.equals("pdf") && !OFFICE.contains(ext)) {
            ext = switch (str(file, "type").toLowerCase(Locale.ROOT)) {
                case "application/pdf" -> "pdf";
                case "application/msword" -> "doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
                case "application/vnd.ms-powerpoint" -> "ppt";
                case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
                default -> ext;
            };
        }
        if (ext.equals("pdf")) {
            if (!isPdf(source)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "PDF 内容无效");
            return source;
        }
        if (!OFFICE.contains(ext)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "此格式暂不支持文档预览");

        String id = str(file, "id");
        if (!id.matches("[A-Za-z0-9_-]{1,60}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        Path output = cache.resolve(id + ".pdf");
        if (Files.isRegularFile(output)) return Files.readAllBytes(output);
        if (!workers.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "预览转换繁忙，请稍后重试");
        try {
            Files.createDirectories(cache);
            if (!Files.isRegularFile(output)) convert(ext, source, output);
            return Files.readAllBytes(output);
        } finally {
            workers.release();
        }
    }

    private void convert(String ext, byte[] source, Path output) throws IOException {
        Path work = Files.createTempDirectory(cache, "convert-");
        Process process = null;
        try {
            Path input = Files.write(work.resolve("document." + ext), source);
            Path profile = work.resolve("profile");
            Files.createDirectories(profile.resolve("user"));
            Files.writeString(profile.resolve("user/registrymodifications.xcu"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <oor:items xmlns:oor="http://openoffice.org/2001/registry">
                  <item oor:path="/org.openoffice.Office.Common/Security/Scripting">
                    <prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop>
                  </item>
                  <item oor:path="/org.openoffice.Office.Writer/Content/Update">
                    <prop oor:name="Link" oor:op="fuse"><value>0</value></prop>
                  </item>
                </oor:items>
                """);
            try {
                process = new ProcessBuilder(office, "-env:UserInstallation=" + profile.toUri(), "--headless",
                    "--norestore", "--convert-to", "pdf", "--outdir", work.toString(), input.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文档转换服务不可用，请检查 LibreOffice 配置", e);
            }
            if (!process.waitFor(55, TimeUnit.SECONDS))
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "文档转换超时，请稍后重试");
            Path pdf = work.resolve("document.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf) || Files.size(pdf) > 100L * 1024 * 1024)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "无法预览此文档，请检查文件是否损坏、加密或过大");
            try (var contents = Files.newInputStream(pdf)) {
                if (!isPdf(contents.readNBytes(5)))
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "文档转换未能生成 PDF");
            }
            Files.move(pdf, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("文档转换中断", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            try (var paths = Files.walk(work)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                }
            } catch (IOException ignored) { }
        }
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }
}
