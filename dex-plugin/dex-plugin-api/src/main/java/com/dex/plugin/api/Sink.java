package com.dex.plugin.api;

import java.util.List;
import java.util.Map;

public interface Sink<T> {
    void open(Map<String, Object> config) throws Exception;
    void write(List<T> data) throws Exception;
    void close() throws Exception;
}