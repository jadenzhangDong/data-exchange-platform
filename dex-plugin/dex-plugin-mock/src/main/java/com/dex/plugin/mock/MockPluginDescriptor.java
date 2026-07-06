package com.dex.plugin.mock;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;

public class MockPluginDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "mock";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Source<?>> getSourceClass() {
        return MockSource.class;
    }

    @Override
    public Class<? extends Sink<?>> getSinkClass() {
        return MockSink.class;
    }
}