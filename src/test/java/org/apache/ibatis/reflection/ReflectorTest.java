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

import static com.googlecode.catchexception.apis.BDDCatchException.caughtException;
import static com.googlecode.catchexception.apis.BDDCatchException.when;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReflectorTest {

  /**
   * 测试getSetterType方法对普通类型参数的支持，返回set方法的参数的类型
   */
  @Test
  void testGetSetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    //Section类里有Long类型的id字段和对应的getset方法
    Reflector reflector = reflectorFactory.findForClass(Section.class);
    //所以通过reflector得到的id字段对应的set方法的参数的类型为Long类型
    Assertions.assertEquals(Long.class, reflector.getSetterType("id"));
  }

  /**
   * 测试getGetterType方法对普通类型参数的支持，返回get方法的返回值的类型
   */
  @Test
  void testGetGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    //同上个方法
    Reflector reflector = reflectorFactory.findForClass(Section.class);
    Assertions.assertEquals(Long.class, reflector.getGetterType("id"));
  }

  /**
   * 测试hasGet方法
   */
  @Test
  void shouldNotGetClass() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    //Section里没有class属性，所以返回false
    Reflector reflector = reflectorFactory.findForClass(Section.class);
    Assertions.assertFalse(reflector.hasGetter("class"));
  }

  static class Section {

    private Long id;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }

  /**
   * 测试getSetterType
   */
  @Test
  void shouldResolveSetterParam() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    //Child集成Parent，Parent里有子类访问权限(id，list，array)，私有属性(fld)，公有属性(pubFld)
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //获取setId的参数的类型为String类型
    assertEquals(String.class, reflector.getSetterType("id"));
  }

  /**
   * 测试setGetterType对泛型的支持
   */
  @Test
  void shouldResolveParameterizedSetterParam() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //无论list的具体类型是什么，
    assertEquals(List.class, reflector.getSetterType("list"));
  }

  /**
   * 测试getSetterType对泛型数组的支持
   */
  @Test
  void shouldResolveArraySetterParam() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    Class<?> clazz = reflector.getSetterType("array");
    //array的类型是数组
    assertTrue(clazz.isArray());
    //数组元素的类型为String
    assertEquals(String.class, clazz.getComponentType());
  }

  /**
   * getGetterType方法测试
   */
  @Test
  void shouldResolveGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    assertEquals(String.class, reflector.getGetterType("id"));
  }

  /**
   * 测试getSetterType对私有属性的支持
   */
  @Test
  void shouldResolveSetterTypeFromPrivateField() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //获取私有属性fld的set方法的参数的类型
    assertEquals(String.class, reflector.getSetterType("fld"));
  }

  /**
   * 测试getSetterType对公有属性的支持
   */
  @Test
  void shouldResolveGetterTypeFromPublicField() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //pubFld是公有属性
    assertEquals(String.class, reflector.getGetterType("pubFld"));
  }

  /**
   * 测试getGetterType对泛型的支持
   */
  @Test
  void shouldResolveParameterizedGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //list的get方法返回类型是List，不管T是什么类型返回的都是List
    assertEquals(List.class, reflector.getGetterType("list"));
  }

  /**
   * 测试getGetterType对泛型数组的支持
   */
  @Test
  void shouldResolveArrayGetterType() {
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Child.class);
    //同getSetterType对泛型数组的支持
    Class<?> clazz = reflector.getGetterType("array");
    assertTrue(clazz.isArray());
    assertEquals(String.class, clazz.getComponentType());
  }

  static abstract class Parent<T extends Serializable> {

    protected T id;
    protected List<T> list;
    protected T[] array;
    private T fld;
    public T pubFld;

    public T getId() {
      return id;
    }

    public void setId(T id) {
      this.id = id;
    }

    public List<T> getList() {
      return list;
    }

    public void setList(List<T> list) {
      this.list = list;
    }

    public T[] getArray() {
      return array;
    }

    public void setArray(T[] array) {
      this.array = array;
    }

    public T getFld() {
      return fld;
    }
  }

  static class Child extends Parent<String> {

  }

  /**
   * 测试只有set方法时的getSetterType
   */
  @Test
  void shouldResoleveReadonlySetterWithOverload() {
    class BeanClass implements BeanInterface<String> {

      @Override
      public void setId(String id) {
        // Do nothing
      }
    }
    //没有id字段，只有set方法
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(BeanClass.class);
    assertEquals(String.class, reflector.getSetterType("id"));
  }

  interface BeanInterface<T> {

    void setId(T id);
  }

  /**
   * 测试set方法重载，不符合规则
   */
  @Test
  void shouldSettersWithUnrelatedArgTypesThrowException() {
    class BeanClass {

      public void setTheProp(String arg) {
      }

      public void setTheProp(Integer arg) {
      }
    }

    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    //捕获异常
    when(reflectorFactory).findForClass(BeanClass.class);
    //异常信息显示累的某个属性有两个不同的set方法
    then(caughtException()).isInstanceOf(ReflectionException.class)
        .hasMessageContaining("theProp")
        .hasMessageContaining("BeanClass")
        .hasMessageContaining("java.lang.String")
        .hasMessageContaining("java.lang.Integer");
  }

  /**
   * 测试允许is和get同时存在
   */
  @Test
  void shouldAllowTwoBooleanGetters() throws Exception {
    class Bean {

      // JavaBean Spec allows this (see #906)
      public boolean isBool() {
        return true;
      }

      public boolean getBool() {
        return false;
      }

      public void setBool(boolean bool) {
      }
    }
    ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    Reflector reflector = reflectorFactory.findForClass(Bean.class);
    assertTrue((Boolean) reflector.getGetInvoker("bool").invoke(new Bean(), new Byte[0]));
  }
}
