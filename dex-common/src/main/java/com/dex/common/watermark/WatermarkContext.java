package com.dex.common.watermark;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class WatermarkContext implements ApplicationContextAware {
    private static WatermarkStore store;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        store = applicationContext.getBean(WatermarkStore.class);
    }

    public static WatermarkStore getStore() {
        if (store == null) {
            throw new IllegalStateException("WatermarkStore 未初始化");
        }
        return store;
    }
}