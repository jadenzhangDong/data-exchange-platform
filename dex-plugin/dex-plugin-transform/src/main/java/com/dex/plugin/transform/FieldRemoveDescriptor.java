package com.dex.plugin.transform;

import com.dex.plugin.api.PluginDescriptor;
import com.dex.plugin.api.Transform;

public class FieldRemoveDescriptor implements PluginDescriptor {
    @Override
    public String getType() {
        return "field-remove";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Class<? extends Transform<?, ?>> getTransformClass() {
        return FieldRemoveTransform.class;
    }
}