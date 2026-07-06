package com.dex.worker.plugin;

import com.dex.common.model.task.PluginConfig;
import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;
import com.dex.plugin.mock.MockPluginDescriptor;
import com.dex.worker.infra.ManagementDataSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PluginManager {

    private final ConcurrentHashMap<String, PluginDescriptor> descriptors = new ConcurrentHashMap<>();

    @Autowired
    private ManagementDataSourceProvider dataSourceProvider;

    @PostConstruct
    public void loadPlugins() throws Exception {
        File pluginsDir = Paths.get(System.getProperty("user.dir"), "plugins").toFile();
        boolean hasExternal = false;

        if (pluginsDir.exists() && pluginsDir.isDirectory()) {
            File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jar : jarFiles) {
                    try {
                        URLClassLoader loader = new URLClassLoader(
                                new URL[]{jar.toURI().toURL()},
                                Thread.currentThread().getContextClassLoader()
                        );
                        ServiceLoader<PluginDescriptor> serviceLoader = ServiceLoader.load(PluginDescriptor.class, loader);
                        for (PluginDescriptor desc : serviceLoader) {
                            descriptors.put(desc.getType(), desc);
                            log.info("加载外部插件: type={}, version={}, jar={}", desc.getType(), desc.getVersion(), jar.getName());
                            hasExternal = true;
                        }
                    } catch (Exception e) {
                        log.error("加载插件失败: {}", jar.getName(), e);
                    }
                }
            }
        }

        if (!hasExternal) {
            log.warn("未发现外部插件，注册 Mock 插件");
            descriptors.put("mock", new MockPluginDescriptor());
        } else {
            log.info("共加载 {} 个外部插件", descriptors.size());
        }
    }

    /**
     * 创建 Source 插件，自动注入管理库 DataSource 和 taskId
     */
    public Source<?> createSource(PluginConfig config, String taskId) throws Exception {
        PluginDescriptor desc = descriptors.get(config.getType());
        if (desc == null) {
            throw new IllegalArgumentException("未找到插件: " + config.getType());
        }
        Source<?> source = desc.getSourceClass().getDeclaredConstructor().newInstance();

        Map<String, Object> params = new HashMap<>(config.getParams());
        // 注入基础设施
        params.put("_managementDataSource", dataSourceProvider.getDataSource());
        // 注入 taskId（如果存在）
        if (taskId != null && !taskId.isEmpty()) {
            params.put("taskId", taskId);
        } else {
            log.warn("taskId 为空，水位线持久化可能不可用");
        }

        source.open(params);
        return source;
    }

    public Sink<?> createSink(PluginConfig config) throws Exception {
        PluginDescriptor desc = descriptors.get(config.getType());
        if (desc == null) {
            throw new IllegalArgumentException("未找到插件: " + config.getType());
        }
        Sink<?> sink = desc.getSinkClass().getDeclaredConstructor().newInstance();
        sink.open(config.getParams());
        return sink;
    }
}