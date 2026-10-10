package com.zjc.demo.common.dict;

import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

/**
 * 字典注册表：启动时扫描实现 {@link DictItem} 的枚举，按「枚举名转 kebab-case」作为字典类型。
 *
 * <p>
 * {@code UserStatus} 会注册成 {@code user-status}。新增字典只需写一个实现 {@link DictItem}
 * 的枚举，不用回来改这里。
 *
 * @author jiancai.zhong
 */
@Slf4j
@Component
public class DictRegistry {

    private static final String BASE_PACKAGE = "com.zjc.demo";

    private final Map<String, DictItem[]> registry = new LinkedHashMap<>();

    public DictRegistry() {
        scan();
    }

    /**
     * 判断字典类型是否存在。
     *
     * @param type 字典类型
     * @return 存在返回 {@code true}
     */
    public boolean exists(String type) {
        return registry.containsKey(type);
    }

    /**
     * 全部已注册的字典类型。
     *
     * @return 字典类型列表
     */
    public List<String> types() {
        return List.copyOf(registry.keySet());
    }

    /**
     * 取字典项列表。
     *
     * @param type 字典类型
     * @return 字典项列表；类型不存在时返回空列表
     */
    public List<DictItemVo> items(String type) {
        DictItem[] items = registry.get(type);
        if (items == null) {
            return List.of();
        }
        return Arrays.stream(items).map(item -> new DictItemVo(item.getValue(), item.getLabel())).toList();
    }

    /**
     * 扫描并注册实现 {@link DictItem} 的枚举。
     */
    private void scan() {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(DictItem.class));
        for (BeanDefinition definition : provider.findCandidateComponents(BASE_PACKAGE)) {
            register(definition.getBeanClassName());
        }
        log.info("已注册字典 {} 个: {}", registry.size(), registry.keySet());
    }

    /**
     * 注册单个类，非枚举或枚举常量为空的跳过。
     *
     * @param className 全限定类名
     */
    private void register(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isEnum() || !DictItem.class.isAssignableFrom(clazz)) {
                return;
            }
            Object[] constants = clazz.getEnumConstants();
            if (constants == null || constants.length == 0) {
                return;
            }
            DictItem[] items = Arrays.stream(constants).map(DictItem.class::cast).toArray(DictItem[]::new);
            registry.put(toKebabCase(clazz.getSimpleName()), items);
        } catch (ClassNotFoundException e) {
            log.warn("字典类加载失败: {}", className, e);
        }
    }

    /**
     * 类名转 kebab-case：{@code UserStatus} 到 {@code user-status}。
     *
     * @param name 类名
     * @return kebab-case 形式
     */
    private static String toKebabCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
