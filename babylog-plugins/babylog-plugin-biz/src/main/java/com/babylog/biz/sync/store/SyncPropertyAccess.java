package com.babylog.biz.sync.store;

import org.springframework.dao.DataIntegrityViolationException;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体属性的对象层读写（getter/setter 调用 + 载荷值类型收敛）。
 *
 * <p><b>与 SQL 完全无关</b>：这里只做「载荷 Map ↔ 实体」「实体 → 下发 Map」的对象转换，
 * 所有 SQL 由 MyBatis-Plus 依据实体元数据生成。反射只触及实体自身的 JavaBean 方法。
 *
 * <p>类型收敛规则（对齐旧 NamedParameterJdbcTemplate 路径的驱动隐式转换）：
 * JSON 数字可能是 Integer/Long/BigDecimal，按目标属性类型收窄；字符串数字解析为目标
 * 数字类型；其余不兼容组合按「库约束拒绝」语义抛
 * {@link DataIntegrityViolationException}（上层 push 据此走 8342 单条拒收，与旧行为一致）。
 */
final class SyncPropertyAccess {

    private static final Map<String, Method> READERS = new ConcurrentHashMap<>();
    private static final Map<String, Method> WRITERS = new ConcurrentHashMap<>();

    static Object read(Object bean, String property) {
        try {
            return reader(bean.getClass(), property).invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 " + bean.getClass().getName() + '#' + property + " 失败", e);
        }
    }

    static void write(Object bean, String property, Object value) {
        Method writer = writer(bean.getClass(), property);
        Object coerced = coerce(property, value, writer.getParameterTypes()[0]);
        try {
            writer.invoke(bean, coerced);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 " + bean.getClass().getName() + '#' + property + " 失败", e);
        }
    }

    /** 读取方法的返回类型（payload 值按实体声明类型上行的依据） */
    static Class<?> propertyType(Class<?> type, String property) {
        return reader(type, property).getReturnType();
    }

    private static Method reader(Class<?> type, String property) {
        return READERS.computeIfAbsent(type.getName() + '#' + property, k -> find(type, property, true));
    }

    private static Method writer(Class<?> type, String property) {
        return WRITERS.computeIfAbsent(type.getName() + '#' + property, k -> find(type, property, false));
    }

    private static Method find(Class<?> type, String property, boolean read) {
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
                if (pd.getName().equals(property)) {
                    Method method = read ? pd.getReadMethod() : pd.getWriteMethod();
                    if (method != null) {
                        return method;
                    }
                }
            }
        } catch (java.beans.IntrospectionException e) {
            throw new IllegalStateException("内省 " + type.getName() + " 失败", e);
        }
        throw new IllegalStateException(
                "实体 " + type.getName() + " 没有属性 " + property + (read ? " 的 getter" : " 的 setter")
                        + "——列清单与实体脱节？");
    }

    private static Object coerce(String property, Object value, Class<?> target) {
        if (value == null) {
            // 实体字段全是包装类型，null 直写（等价旧路径显式绑定 NULL）
            return null;
        }
        if (target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (target == Long.class) {
                return number.longValue();
            }
            if (target == Integer.class) {
                return number.intValue();
            }
            if (target == Double.class) {
                return number.doubleValue();
            }
            // BlFeeding.createdBy 遮蔽 BaseDO.createBy（String 业务口径）：数字按字符串形式落列
            if (target == String.class) {
                return String.valueOf(value);
            }
        }
        if (value instanceof String text && Number.class.isAssignableFrom(target)) {
            try {
                if (target == Long.class) {
                    return Long.valueOf(text.trim());
                }
                if (target == Integer.class) {
                    return Integer.valueOf(text.trim());
                }
                if (target == Double.class) {
                    return Double.valueOf(text.trim());
                }
            } catch (NumberFormatException ignored) {
                // fallthrough：按库约束拒绝处理
            }
        }
        throw new DataIntegrityViolationException(
                "字段不满足库约束：" + property + " 的值类型 " + value.getClass().getSimpleName()
                        + " 与列类型 " + target.getSimpleName() + " 不兼容");
    }

    private SyncPropertyAccess() {
    }
}
