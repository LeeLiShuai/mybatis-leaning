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

import java.util.Arrays;

/**
 * 处理数组的hashCode,equals,toString方法
 */
public class ArrayUtil {

  /**
   * 数组的hashCode
   */
  public static int hashCode(Object obj) {
    if (obj == null) {
      return 0;
    }
    Class<?> clazz = obj.getClass();
    //不是数组
    if (!clazz.isArray()) {
      return obj.hashCode();
    }
    Class<?> componentType = clazz.getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.hashCode((long[]) obj);
    } else if (int.class.equals(componentType)) {
      return Arrays.hashCode((int[]) obj);
    } else if (short.class.equals(componentType)) {
      return Arrays.hashCode((short[]) obj);
    } else if (char.class.equals(componentType)) {
      return Arrays.hashCode((char[]) obj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.hashCode((byte[]) obj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.hashCode((boolean[]) obj);
    } else if (float.class.equals(componentType)) {
      return Arrays.hashCode((float[]) obj);
    } else if (double.class.equals(componentType)) {
      return Arrays.hashCode((double[]) obj);
    } else {
      return Arrays.hashCode((Object[]) obj);
    }
  }

  /**
   * 判断两个数组是否equals
   */
  public static boolean equals(Object thisObj, Object thatObj) {
    if (thisObj == null) {
      return thatObj == null;
    } else if (thatObj == null) {
      return false;
    }
    Class<?> clazz = thisObj.getClass();
    if (!clazz.equals(thatObj.getClass())) {
      return false;
    }
    if (!clazz.isArray()) {
      return thisObj.equals(thatObj);
    }
    Class<?> componentType = clazz.getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.equals((long[]) thisObj, (long[]) thatObj);
    } else if (int.class.equals(componentType)) {
      return Arrays.equals((int[]) thisObj, (int[]) thatObj);
    } else if (short.class.equals(componentType)) {
      return Arrays.equals((short[]) thisObj, (short[]) thatObj);
    } else if (char.class.equals(componentType)) {
      return Arrays.equals((char[]) thisObj, (char[]) thatObj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.equals((byte[]) thisObj, (byte[]) thatObj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.equals((boolean[]) thisObj, (boolean[]) thatObj);
    } else if (float.class.equals(componentType)) {
      return Arrays.equals((float[]) thisObj, (float[]) thatObj);
    } else if (double.class.equals(componentType)) {
      return Arrays.equals((double[]) thisObj, (double[]) thatObj);
    } else {
      return Arrays.equals((Object[]) thisObj, (Object[]) thatObj);
    }
  }

  /**
   * toString方法
   */
  public static String toString(Object obj) {
    if (obj == null) {
      return "null";
    }
    Class<?> clazz = obj.getClass();
    if (!clazz.isArray()) {
      return obj.toString();
    }
    Class<?> componentType = obj.getClass().getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.toString((long[]) obj);
    } else if (int.class.equals(componentType)) {
      return Arrays.toString((int[]) obj);
    } else if (short.class.equals(componentType)) {
      return Arrays.toString((short[]) obj);
    } else if (char.class.equals(componentType)) {
      return Arrays.toString((char[]) obj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.toString((byte[]) obj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.toString((boolean[]) obj);
    } else if (float.class.equals(componentType)) {
      return Arrays.toString((float[]) obj);
    } else if (double.class.equals(componentType)) {
      return Arrays.toString((double[]) obj);
    } else {
      return Arrays.toString((Object[]) obj);
    }
  }

}
