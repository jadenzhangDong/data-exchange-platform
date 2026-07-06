package com.dex.plugin.cdc;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;

public class CdcPluginDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "cdc";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Source<?>> getSourceClass() {
        return CdcSource.class;
    }

    @Override
    public Class<? extends Sink<?>> getSinkClass() {
        return null;
    }
}