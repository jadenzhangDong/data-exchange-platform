package com.dex.plugin.file;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;

public class FilePluginDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "file";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Source<?>> getSourceClass() {
        return FileSource.class;
    }

    @Override
    public Class<? extends Sink<?>> getSinkClass() {
        return FileSink.class;
    }
}