import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PgyerUploader {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("用法：java ci/PgyerUploader.java <APK文件或目录>");
        }

        String apiKey = requiredEnv("PGYER_API_KEY");
        Path apk = findApk(Paths.get(args[0]));
        System.out.println("准备上传：" + apk.toAbsolutePath());

        Map<String, String> tokenParams = new LinkedHashMap<>();
        tokenParams.put("_api_key", apiKey);
        tokenParams.put("buildType", "android");
        tokenParams.put("buildInstallType", env("PGYER_INSTALL_TYPE", "1"));
        tokenParams.put("buildPassword", env("PGYER_INSTALL_PASSWORD", ""));
        tokenParams.put("buildUpdateDescription", env("PGYER_BUILD_DESC", "Jenkins 自动构建"));

        String tokenJson = postForm("https://www.pgyer.com/apiv2/app/getCOSToken", tokenParams);
        requireCodeZero(tokenJson, "获取蒲公英上传凭证失败");
        String endpoint = requiredJsonString(tokenJson, "endpoint");
        String buildKey = requiredJsonString(tokenJson, "key");

        uploadFile(
            endpoint,
            apk,
            buildKey,
            requiredJsonString(tokenJson, "signature"),
            requiredJsonString(tokenJson, "x-cos-security-token")
        );

        for (int attempt = 1; attempt <= 60; attempt++) {
            Thread.sleep(2000);
            String result = postForm(
                "https://www.pgyer.com/apiv2/app/buildInfo",
                Map.of("_api_key", apiKey, "buildKey", buildKey)
            );
            String shortcut = jsonString(result, "buildShortcutUrl", "");
            if (!shortcut.isBlank()) {
                String installUrl = shortcut.startsWith("http")
                    ? shortcut
                    : "https://www.pgyer.com/" + shortcut;
                String output = "{\"buildKey\":\"" + escape(buildKey)
                    + "\",\"installUrl\":\"" + escape(installUrl)
                    + "\",\"response\":" + result + "}";
                Files.writeString(Paths.get("pgyer_result.json"), output, StandardCharsets.UTF_8);
                System.out.println("蒲公英发布完成：" + installUrl);
                return;
            }

            String code = jsonNumber(result, "code", "");
            if (!code.isBlank() && !code.equals("0") && !code.equals("1247")) {
                throw new IOException("蒲公英处理失败：" + result);
            }
            System.out.println("等待蒲公英处理安装包 " + attempt + "/60");
        }

        throw new IOException("蒲公英处理超时，buildKey=" + buildKey);
    }

    private static Path findApk(Path input) throws IOException {
        if (Files.isRegularFile(input) && input.toString().toLowerCase().endsWith(".apk")) {
            return input;
        }
        if (!Files.isDirectory(input)) {
            throw new IOException("APK 路径不存在：" + input.toAbsolutePath());
        }
        try (Stream<Path> paths = Files.walk(input)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".apk"))
                .max(Comparator.comparingLong(PgyerUploader::modifiedTime))
                .orElseThrow(() -> new IOException("目录内未找到 APK：" + input.toAbsolutePath()));
        }
    }

    private static void uploadFile(
        String endpoint,
        Path apk,
        String key,
        String signature,
        String securityToken
    ) throws Exception {
        String boundary = "----JenkinsAndroid" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
            textPart(boundary, "key", key),
            textPart(boundary, "signature", signature),
            textPart(boundary, "x-cos-security-token", securityToken),
            fileHeader(boundary, apk.getFileName().toString()),
            HttpRequest.BodyPublishers.ofFile(apk),
            HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n")
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(body)
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("上传对象存储失败，HTTP " + response.statusCode() + "：" + response.body());
        }
    }

    private static HttpRequest.BodyPublisher textPart(String boundary, String name, String value) {
        return HttpRequest.BodyPublishers.ofString(
            "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n"
        );
    }

    private static HttpRequest.BodyPublisher fileHeader(String boundary, String filename) {
        return HttpRequest.BodyPublishers.ofString(
            "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename.replace("\"", "") + "\"\r\n"
                + "Content-Type: application/vnd.android.package-archive\r\n\r\n"
        );
    }

    private static String postForm(String url, Map<String, String> values) throws Exception {
        StringJoiner body = new StringJoiner("&");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            body.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("蒲公英接口 HTTP " + response.statusCode() + "：" + response.body());
        }
        return response.body();
    }

    private static void requireCodeZero(String json, String message) throws IOException {
        if (!jsonNumber(json, "code", "").equals("0")) {
            throw new IOException(message + "：" + json);
        }
    }

    private static String requiredJsonString(String json, String key) throws IOException {
        String value = jsonString(json, key, "");
        if (value.isBlank()) {
            throw new IOException("蒲公英响应缺少 " + key + "：" + json);
        }
        return value;
    }

    private static String jsonString(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
        ).matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : fallback;
    }

    private static String jsonNumber(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)"
        ).matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String unescape(String value) {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r");
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }
}
