package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性名 → {@link SFunction} 的解析器（PivotOS 严格整改 S3）。
 *
 * <p><b>为什么需要它</b>：{@code LambdaQueryWrapper} 的条件列只接受类型化方法引用
 * （{@code BlFeeding::getAmountMl}），而同步存储的载荷/拉取列清单是<b>按实体推导</b>的
 * （{@link SyncEntityMeta}，改列只改实体）。两者需要一个接缝：给定
 * {@code (视图类, 属性名)}，产出对应的 {@code SFunction}。
 *
 * <p><b>为什么是 LambdaMetafactory 而不是字符串</b>：PivotOS 红线禁止字符串列名拼 SQL。
 * 这里用 {@link LambdaMetafactory} 从实体的 getter 方法句柄直接纺出<b>可序列化 lambda</b>
 * ——MyBatis-Plus 对 SFunction 的全部消费是 {@code writeReplace()} 提取
 * {@code SerializedLambda.implMethodName}（如 {@code getAmountMl}），再经
 * {@code PropertyNamer.methodToProperty} 还原属性名、由 {@code TableInfo} 元数据解析列名。
 * 整条链路只有类型化句柄与 MP 自有元数据：<b>零字符串列名、零反射拼 SQL、lambda 本体
 * 从不被调用</b>（MP 只读它的序列化描述）。
 *
 * <p>解析结果按（类, 属性）缓存：每表启动期解析一次（≈20 个属性 × 22 表）。
 */
final class SyncLambdas {

    private static final Map<String, SFunction<?, ?>> CACHE = new ConcurrentHashMap<>();

    static <E> SFunction<E, ?> resolve(Class<E> type, String property) {
        String key = type.getName() + '#' + property;
        // 不带泛型参数调用 computeIfAbsent，避免 JDK 嵌套映射死锁（get->create->get）
        return cast(type, property, CACHE.computeIfAbsent(key, k -> create(type, property)));
    }

    @SuppressWarnings("unchecked")
    private static <E> SFunction<E, ?> cast(Class<E> type, String property, SFunction<?, ?> fn) {
        return (SFunction<E, ?>) fn;
    }

    private static SFunction<?, ?> create(Class<?> type, String property) {
        try {
            Method getter = null;
            for (PropertyDescriptor pd : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
                if (pd.getName().equals(property)) {
                    getter = pd.getReadMethod();
                    break;
                }
            }
            if (getter == null) {
                throw new IllegalStateException(
                        "实体 " + type.getName() + " 没有属性 " + property + " 的可读 getter——列清单与实体脱节？");
            }
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle handle = lookup.unreflect(getter);
            // 实例化类型必须是视图类本身（而非 getter 的声明类）：
            // MP 按 lambda 的 instantiatedType 查 TableInfo 缓存，父类声明的 getter
            // （BlSyncDO.getUserId / BaseDO.getId）否则会被解析到父类头上而查无缓存
            MethodType instantiated = MethodType.methodType(getter.getReturnType(), type);
            CallSite site = LambdaMetafactory.altMetafactory(
                    lookup,
                    "apply",
                    MethodType.methodType(SFunction.class),
                    MethodType.methodType(Object.class, Object.class), // SAM Function.apply 的擦除形态
                    handle,
                    instantiated,
                    LambdaMetafactory.FLAG_SERIALIZABLE);
            return (SFunction<?, ?>) site.getTarget().invoke();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "为 " + type.getName() + '#' + property + " 生成类型化列句柄失败", t);
        }
    }

    private SyncLambdas() {
    }
}
