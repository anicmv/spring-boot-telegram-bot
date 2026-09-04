package com.github.anicmv.telegrambot.dispatcher.processor;

import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 处理器注册表，维护 UpdateType 到处理器的映射关系。
 */
public class UpdateProcessorRegistry {

    private final Map<UpdateType, UpdateProcessor> processorMap = new EnumMap<>(UpdateType.class);

    public UpdateProcessorRegistry(List<UpdateProcessor> processors) {
        for (UpdateProcessor processor : processors) {
            processorMap.put(processor.supportType(), processor);
        }
    }

    public UpdateProcessor get(UpdateType updateType) {
        return processorMap.get(updateType);
    }
}
