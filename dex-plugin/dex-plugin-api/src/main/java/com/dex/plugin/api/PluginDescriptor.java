package com.dex.plugin.api;

public interface PluginDescriptor {
    String getType();
    String getVersion();

    default Class<? extends Source<?>> getSourceClass() {
        return null;
    }

    default Class<? extends Sink<?>> getSinkClass() {
        return null;
    }

    default Class<? extends Transform<?, ?>> getTransformClass() {
        return null;
    }
}