/**
 * Copyright 2009-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * 每个实例对应一个类，缓存类的元数据,只解析一层，如果属性是另一个对象，不会递归解析
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 对应的class类型
   */
  private final Class<?> type;
  /**
   * 可读属性的名称数组，get开头的方法对应的属性
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性的名称数组，set,is开头的方法对应的属性
   */
  private final String[] writablePropertyNames;
  /**
   * set开头的方法或属性的invoiceker对象
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * get开头的方法或属性的invoiceker对象，有方法则是方法，没有对应的方法才存属性
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * set方法的参数类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * get方法的返回类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 无参构造函数
   */
  private Constructor<?> defaultConstructor;
  /**
   * 记录所有属性
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  /**
   * 构造函数
   *
   * @param clazz Class类型
   */
  public Reflector(Class<?> clazz) {
    type = clazz;
    // 设置默认构造函数
    addDefaultConstructor(clazz);
    // 设置get方法
    addGetMethods(clazz);
    // 设置set方法
    addSetMethods(clazz);
    //设置属性
    addFields(clazz);
    //可读属性数组
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    //可写属性数组
    writablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    //记录属性名称(key大写，value驼峰)
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    //记录属性名称(key大写，value驼峰)
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 检查是否可以控制成员访问，一般情况下都是返回true
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * 查找Clazz的无参构造函数
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 获取所有构造函数
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    for (Constructor<?> constructor : consts) {
      // 如果没有参数，就设置为reflector的默认构造函数
      if (constructor.getParameterTypes().length == 0) {
        defaultConstructor = constructor;
      }
    }
  }

  /**
   * 设置get方法
   */
  private void addGetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获取所有方法，包括自己的，父类的，接口的
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      // 过滤有参数的方法，有参数一定不是get方法
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      String name = method.getName();
      // 判断是否是get方法
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        // 将方法名转化为属性
        name = PropertyNamer.methodToProperty(name);
        // 如果方法不存在map中，就加入conflictingGetters中。conflictingGetters中key为属性名，value为方法
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    // 解决覆写的方法
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 解决覆写的方法，conflictingGetters中key为属性名，value为方法
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      // 属性名
      String propName = entry.getKey();
      // 遍历方法，可能一个属性对应多个方法（子类的返回值是父类返回值的子类，ArrayList->List）
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 如果当前返回类型和之前的类型一样
        if (candidateType.equals(winnerType)) {
          // 不是boolean类型，说明有错误
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property " + propName
                    + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // winner是candidate的父类，或者两个是同一个类（上面的判断已经排除了这种情况）
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property " + propName
                  + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      // 赋值getMethosd和getTypes
      addGetMethod(propName, winner);
    }
  }

  /**
   * 给getMethos和getTypes赋值
   */
  private void addGetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      // methodInvoker包含一个Class和method方法
      getMethods.put(name, new MethodInvoker(method));
      //解析返回值类型
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      //将属性名和对应的返回值对应的Class出入getTypes
      getTypes.put(name, typeToClass(returnType));
    }
  }

  /**
   * 设置set方法
   */
  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    //获取所有方法
    Method[] methods = getClassMethods(cls);
    //筛选出set方法
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          name = PropertyNamer.methodToProperty(name);
          // 如果方法不存在map中，就加入conflictingGetters中。conflictingGetters中key为属性名，value为方法
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    //解决子父类间的方法冲突
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 如果方法不存在map中，就加入conflictingGetters中。conflictingGetters中key为属性名，value为方法
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name,
      Method method) {
    // 只有在当前 Map 中 key 对应的值不存在或为 null 时
    // 才调用括号里的lambda表达式
    // name是key.即如果name不在map中，就讲那么作为可以put到map中，value为新建的list
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    // 把方法放入list中
    list.add(method);
  }

  /**
   * 解决覆写的方法，conflictingGetters中key为属性名，value为方法
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      //属性对应的所有方法
      List<Method> setters = conflictingSetters.get(propName);
      //属性的类型
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        //set方法的参数
        Class<?> paramType = setter.getParameterTypes()[0];
        //参数类型和属性类型相同
        if (paramType.equals(getterType)) {
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        //添加set方法和setTypes
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 选择最适合的set方法，最顶层的类型.
   *
   * @param setter1 目前为止最适合的方法
   * @param setter2 当前方法
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    //没有合适的方法(只有第一个元素会进入这个判断，应为所有的方法都是合适的)
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    //返回父类的类型
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property
        + "' in class '" + setter2.getDeclaringClass() + "' with types '" + paramType1.getName()
        + "' and '" + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  /**
   * 根据类型找到对应的Class
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    //获取所有属性
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      //如果set方法没有包含属性
      if (!setMethods.containsKey(field.getName())) {
        int modifiers = field.getModifiers();
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          //保存set属性
          addSetField(field);
        }
      }
      //如果get方法没有包含属性
      if (!getMethods.containsKey(field.getName())) {
        //保存get属性
        addGetField(field);
      }
    }
    //不是Object类型
    if (clazz.getSuperclass() != null) {
      //递归保存父类属性
      addFields(clazz.getSuperclass());
    }
  }

  /**
   * 添加单个set属性
   */
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      //保存set属性
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      //保存set参数类型
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 添加单个get属性
   */
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      //保存属性
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      //保存返回类型
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 检测属性名称是否合法
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 查找类中的全部方法，包括父类的方法和私有方法
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = cls;
    // 遍历查找，知道找到Object类
    while (currentClass != null && currentClass != Object.class) {
      // 获取所有本类方法的签名，并存入map中
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());
      // 获取类实现的接口
      Class<?>[] interfaces = currentClass.getInterfaces();
      // 遍历接口，获取所有接口里的方法签名，存入map中
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 获取父类，递归查找
      currentClass = currentClass.getSuperclass();
    }
    // 获取所有方法
    Collection<Method> methods = uniqueMethods.values();
    // 返回方法数组
    return methods.toArray(new Method[methods.size()]);
  }

  /**
   * 遍历方法，获取唯一签名，存入map中。签名格式： 返回值类型#方法名称：参数类型列表
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 如果不是桥接方法（一个类继承了一个范型类或者实现了一个范型接口, 那么编译器在编译这个类的时候就会生成一个叫做桥接方法的混合方法(混合方法简单的说就是由编译器生成的方法,
      // 方法上有synthetic修饰符)）
      if (!currentMethod.isBridge()) {
        // 获取方法的签名
        String signature = getSignature(currentMethod);
        // 如果不包含这个签名，把他存入map中
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获取方法的签名
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 返回值类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    // 获取参数类型
    Class<?>[] parameters = method.getParameterTypes();
    // 遍历参数类型
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * 获取类型
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  /**
   * 获取无参构造函数
   */
  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  /**
   * 是否有无参构造函数
   */
  boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  /**
   * 根据属性名获取对应的setInvoker
   */
  Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException(
          "There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * 根据属性名获取对应的Invoker
   */
  Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException(
          "There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * 根据属性名获取对应的set方法的参数类型
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException(
          "There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * 根据属性名获取对应的get方法的返回值的参数类型
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException(
          "There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * 获取可读属性数组
   */
  String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * 获取可写属性数组
   */
  String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * 根据属性名称检验是否包含对应的set方法
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * 根据属性名称检验是否包含对应的get方法
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  /**
   * 根据属性返回对应的属性名称，忽略大小写
   */
  String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
