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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装接口
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 如果是普通对象，调用对象的get方法；如果是集合对象，获取对象的元素或value
   */
  Object get(PropertyTokenizer prop);

  /**
   * 如果是普通对象，调用对象的set方法；如果是集合对象，设置对象的元素或value
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 查找属性表达式中的属性，是否忽略下划线
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 可读属性的名称集合
   */
  String[] getGetterNames();

  /**
   * 可写属性的名称集合
   */
  String[] getSetterNames();

  /**
   * 解析属性表达式对应的set方法的参数类型
   */
  Class<?> getSetterType(String name);

  /**
   * 解析属性表达式对应的get方法的返回类型
   */
  Class<?> getGetterType(String name);

  /**
   * 判断属性表达式是否有set方法
   */
  boolean hasSetter(String name);

  /**
   * 判断属性表达式是否有get方法
   */
  boolean hasGetter(String name);

  /**
   * 为属性表达式指定的属性创建对应的MetaObject对象
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop,
      ObjectFactory objectFactory);

  /**
   * 是否为集合
   */
  boolean isCollection();

  /**
   * 调用collection的add方法
   */
  void add(Object element);

  /**
   * 调用collection的addAll方法
   */
  <E> void addAll(List<E> element);

}
