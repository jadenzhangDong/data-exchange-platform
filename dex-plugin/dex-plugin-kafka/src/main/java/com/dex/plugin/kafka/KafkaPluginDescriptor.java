package com.dex.plugin.kafka;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;

public class KafkaPluginDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Source<?>> getSourceClass() {
        return KafkaSource.class;
    }

    @Override
    public Class<? extends Sink<?>> getSinkClass() {
        return KafkaSink.class;
    }
}