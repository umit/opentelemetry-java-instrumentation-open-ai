package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReflectionUtil {
  private ReflectionUtil() {}

  public static Object proxy(Object delegate, String preferredInterfaceName, InvocationHandler handler) {
    if (delegate == null || Proxy.isProxyClass(delegate.getClass())) {
      return delegate;
    }
    Class<?> proxyInterface = preferredInterfaceName == null
        ? firstInterface(delegate)
        : namedInterface(delegate, preferredInterfaceName);
    if (proxyInterface == null && preferredInterfaceName != null) {
      proxyInterface = firstInterface(delegate);
    }
    if (proxyInterface == null) {
      return delegate;
    }
    return Proxy.newProxyInstance(
        proxyInterface.getClassLoader(),
        new Class<?>[] {proxyInterface},
        handler);
  }

  public static Object invoke(Object delegate, Method method, Object[] args) throws Throwable {
    try {
      if (!method.canAccess(delegate)) {
        method.setAccessible(true);
      }
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  public static boolean isObjectMethod(Method method) {
    return method.getDeclaringClass() == Object.class;
  }

  public static Object call(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    try {
      Method method;
      try {
        method = target.getClass().getMethod(methodName);
      } catch (NoSuchMethodException e) {
        method = target.getClass().getDeclaredMethod(methodName);
      }
      if (!method.canAccess(target)) {
        method.setAccessible(true);
      }
      return unwrapOptional(method.invoke(target));
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  public static Object unwrapOptional(Object value) {
    if (value instanceof Optional<?> optional) {
      return optional.orElse(null);
    }
    return value;
  }

  public static String stringValue(Object value) {
    Object unwrapped = unwrapOptional(value);
    if (unwrapped == null) {
      return null;
    }
    Object valueObject = call(unwrapped, "asString");
    if (valueObject == null) {
      valueObject = call(unwrapped, "value");
    }
    return valueObject == null ? String.valueOf(unwrapped) : String.valueOf(valueObject);
  }

  public static Long longValue(Object value) {
    Object unwrapped = unwrapOptional(value);
    if (unwrapped instanceof Number number) {
      return number.longValue();
    }
    if (unwrapped == null) {
      return null;
    }
    try {
      return Long.parseLong(String.valueOf(unwrapped));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static Double doubleValue(Object value) {
    Object unwrapped = unwrapOptional(value);
    if (unwrapped instanceof Number number) {
      return number.doubleValue();
    }
    if (unwrapped == null) {
      return null;
    }
    try {
      return Double.parseDouble(String.valueOf(unwrapped));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static Boolean booleanValue(Object value) {
    Object unwrapped = unwrapOptional(value);
    if (unwrapped instanceof Boolean bool) {
      return bool;
    }
    if (unwrapped == null) {
      return null;
    }
    String text = String.valueOf(unwrapped);
    if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
      return Boolean.parseBoolean(text);
    }
    return null;
  }

  public static List<?> listValue(Object value) {
    Object unwrapped = unwrapOptional(value);
    return unwrapped instanceof List<?> list ? list : List.of();
  }

  public static List<String> stringListValue(Object value) {
    Object unwrapped = unwrapOptional(value);
    if (unwrapped == null) {
      return List.of();
    }
    if (unwrapped instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        String string = stringValue(item);
        if (string != null) {
          result.add(string);
        }
      }
      return result;
    }
    String string = stringValue(unwrapped);
    return string == null ? List.of() : List.of(string);
  }

  public static void setStringAttribute(Span span, AttributeKey<String> key, Object value) {
    String string = stringValue(value);
    if (string != null && !string.isBlank()) {
      span.setAttribute(key, string);
    }
  }

  public static void setLongAttribute(Span span, AttributeKey<Long> key, Object value) {
    Long number = longValue(value);
    if (number != null) {
      span.setAttribute(key, number);
    }
  }

  public static void setDoubleAttribute(Span span, AttributeKey<Double> key, Object value) {
    Double number = doubleValue(value);
    if (number != null) {
      span.setAttribute(key, number);
    }
  }

  public static void setBooleanAttribute(Span span, AttributeKey<Boolean> key, Object value) {
    Boolean bool = booleanValue(value);
    if (bool != null) {
      span.setAttribute(key, bool);
    }
  }

  private static Class<?> firstInterface(Object delegate) {
    Class<?> type = delegate.getClass();
    while (type != null) {
      Class<?>[] interfaces = type.getInterfaces();
      if (interfaces.length > 0) {
        return interfaces[0];
      }
      type = type.getSuperclass();
    }
    return null;
  }

  private static Class<?> namedInterface(Object delegate, String name) {
    try {
      Class<?> type = Class.forName(name, false, delegate.getClass().getClassLoader());
      return type.isInstance(delegate) ? type : null;
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
