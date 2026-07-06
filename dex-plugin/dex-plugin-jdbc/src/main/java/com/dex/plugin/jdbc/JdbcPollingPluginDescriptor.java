package com.dex.plugin.jdbc;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;

public class JdbcPollingPluginDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "jdbc-polling";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Source<?>> getSourceClass() {
        return JdbcPollingSource.class;
    }

    @Override
    public Class<? extends Sink<?>> getSinkClass() {
        return JdbcSink.class;
    }
}