package com.dex.plugin.file;

import com.dex.plugin.api.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * 文件 Source 插件，支持读取 CSV 格式文件
 * 配置参数：
 * - path: 文件路径（必填）
 * - format: 格式，目前仅支持 "csv"（默认 csv）
 * - delimiter: 分隔符，默认 ","
 * - hasHeader: 是否包含表头，默认 true
 * - encoding: 编码，默认 UTF-8
 * - skipRows: 跳过前 N 行（不计入数据），默认 0
 * - batchSize: 每次读取行数，默认 1000
 */
public class FileSource implements Source<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(FileSource.class);

    private String path;
    private String format;
    private String delimiter;
    private boolean hasHeader;
    private String encoding;
    private int skipRows;
    private int batchSize;

    private BufferedReader reader;
    private String[] header;
    private int lineNumber = 0;
    private boolean finished = false;

    @Override
    public void open(Map<String, Object> config) throws Exception {
        this.path = (String) config.get("path");
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("必须指定 path 参数");
        }
        this.format = (String) config.getOrDefault("format", "csv");
        if (!"csv".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("目前仅支持 csv 格式");
        }
        this.delimiter = (String) config.getOrDefault("delimiter", ",");
        this.hasHeader = (Boolean) config.getOrDefault("hasHeader", true);
        this.encoding = (String) config.getOrDefault("encoding", "UTF-8");
        this.skipRows = (Integer) config.getOrDefault("skipRows", 0);
        this.batchSize = (Integer) config.getOrDefault("batchSize", 1000);

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("文件不存在: " + path);
        }

        reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName(encoding)));

        // 跳过指定行
        for (int i = 0; i < skipRows; i++) {
            reader.readLine();
            lineNumber++;
        }

        // 读取表头
        if (hasHeader) {
            String headerLine = reader.readLine();
            if (headerLine != null) {
                header = parseLine(headerLine);
                lineNumber++;
                log.info("读取表头: {}", Arrays.toString(header));
            } else {
                throw new RuntimeException("文件为空或表头不存在");
            }
        } else {
            // 无表头，将在 read 中动态生成列名
            header = null;
        }
        log.info("FileSource 打开成功, path={}, delimiter={}, hasHeader={}", path, delimiter, hasHeader);
    }

    @Override
    public List<Map<String, Object>> read(int batchSize) throws Exception {
        if (finished) {
            return Collections.emptyList();
        }
        if (batchSize <= 0) batchSize = this.batchSize;

        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        String line = null;  // ✅ 初始化为 null
        while (count < batchSize && (line = reader.readLine()) != null) {
            lineNumber++;
            if (line.trim().isEmpty()) continue; // 跳过空行
            String[] parts = parseLine(line);
            // 如果无表头，动态生成列名
            String[] columns = header;
            if (columns == null) {
                columns = new String[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    columns[i] = "col" + (i + 1);
                }
                header = columns; // 缓存
            }
            // 构建 Map
            Map<String, Object> row = new LinkedHashMap<>();
            int colCount = Math.min(columns.length, parts.length);
            for (int i = 0; i < colCount; i++) {
                row.put(columns[i], parts[i]);
            }
            // 如果 parts 少于列数，缺失的列值为 null
            if (parts.length < columns.length) {
                for (int i = parts.length; i < columns.length; i++) {
                    row.put(columns[i], null);
                }
            }
            result.add(row);
            count++;
        }
        if (line == null) {
            finished = true;
        }
        log.debug("读取 {} 行数据", result.size());
        return result;
    }

    /**
     * 解析一行 CSV 数据，支持简单的引号处理（不处理转义）
     */
    private String[] parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                values.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString());
        return values.toArray(new String[0]);
    }

    @Override
    public void close() throws Exception {
        if (reader != null) {
            reader.close();
        }
        log.info("FileSource 关闭");
    }
}