package com.dex.plugin.transform;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Transform;

public class FieldMapperDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "field-mapper";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Transform<?, ?>> getTransformClass() {
        return FieldMapperTransform.class;
    }
}