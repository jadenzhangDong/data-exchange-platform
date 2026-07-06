package com.dex.plugin.file;

import com.dex.plugin.api.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * 文件 Sink 插件，支持写入 CSV 格式文件
 * 配置参数：
 * - path: 文件路径（必填）
 * - format: 格式，目前仅支持 "csv"
 * - delimiter: 分隔符，默认 ","
 * - writeHeader: 是否写入表头，默认 true
 * - encoding: 编码，默认 UTF-8
 * - append: 是否追加模式，默认 false（覆盖）
 */
public class FileSink implements Sink<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(FileSink.class);

    private String path;
    private String delimiter;
    private boolean writeHeader;
    private String encoding;
    private boolean append;

    private BufferedWriter writer;
    private boolean headerWritten = false;

    @Override
    public void open(Map<String, Object> config) throws Exception {
        this.path = (String) config.get("path");
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("必须指定 path 参数");
        }
        String format = (String) config.getOrDefault("format", "csv");
        if (!"csv".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("目前仅支持 csv 格式");
        }
        this.delimiter = (String) config.getOrDefault("delimiter", ",");
        this.writeHeader = (Boolean) config.getOrDefault("writeHeader", true);
        this.encoding = (String) config.getOrDefault("encoding", "UTF-8");
        this.append = (Boolean) config.getOrDefault("append", false);

        File file = new File(path);
        // 如果父目录不存在则创建
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append), Charset.forName(encoding)));
        log.info("FileSink 打开成功, path={}, delimiter={}, append={}", path, delimiter, append);
    }

    @Override
    public void write(List<Map<String, Object>> data) throws Exception {
        if (data == null || data.isEmpty()) {
            return;
        }

        // 收集所有列名（按出现顺序）
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, Object> row : data) {
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);

        // 写入表头
        if (writeHeader && !headerWritten) {
            writer.write(String.join(delimiter, columns));
            writer.newLine();
            headerWritten = true;
        }

        // 写入数据行
        for (Map<String, Object> row : data) {
            List<String> values = new ArrayList<>();
            for (String col : columns) {
                Object val = row.get(col);
                String str = val != null ? val.toString() : "";
                // 如果包含分隔符或引号，则用引号包裹
                if (str.contains(delimiter) || str.contains("\"")) {
                    str = "\"" + str.replace("\"", "\"\"") + "\"";
                }
                values.add(str);
            }
            writer.write(String.join(delimiter, values));
            writer.newLine();
        }
        writer.flush();
        log.debug("写入 {} 行数据", data.size());
    }

    @Override
    public void close() throws Exception {
        if (writer != null) {
            writer.close();
        }
        log.info("FileSink 关闭");
    }
}