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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * 包装类加载器，整合多个类加载器
 *
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

  /**
   * 默认类加载器，Resources中有设置的方法
   */
  ClassLoader defaultClassLoader;
  /**
   * 系统类加载器
   */
  private ClassLoader systemClassLoader;

  ClassLoaderWrapper() {
    try {
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
      // AccessControlException on Google App Engine
    }
  }

  /**
   * 使用当前类路径获取资源作为URL
   */
  URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /**
   * 从特定的类加载器开始，从类路径获取资源，将传入的类加载作为第一个类加载开始遍历
   */
  URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /**
   * 使用当前类路径获取资源作为输入流
   */
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /**
   * 从特定的类加载器开始，从类路径获取资源作为输入流，将传入的类加载作为第一个类加载开始遍历
   */
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /**
   * 加载一个类
   */
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /**
   * 加载一个类
   */
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /**
   * 使用一组类加载器加载资源作为输入流
   */
  private InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
    for (ClassLoader cl : classLoader) {
      if (null != cl) {
        InputStream returnValue = cl.getResourceAsStream(resource);
        if (null == returnValue) {
          returnValue = cl.getResourceAsStream("/" + resource);
        }
        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /**
   * 使用当前类路径获取资源作为URL
   */
  private URL getResourceAsURL(String resource, ClassLoader[] classLoader) {
    URL url;
    //遍历类加载器
    for (ClassLoader cl : classLoader) {
      if (null != cl) {
        url = cl.getResource(resource);
        if (null == url) {
          url = cl.getResource("/" + resource);
        }
        if (null != url) {
          return url;
        }
      }
    }
    return null;
  }

  /**
   * 加载一个类
   */
  private Class<?> classForName(String name, ClassLoader[] classLoader)
      throws ClassNotFoundException {
    for (ClassLoader cl : classLoader) {
      if (null != cl) {
        try {
          Class<?> c = Class.forName(name, true, cl);
          if (null != c) {
            return c;
          }
        } catch (ClassNotFoundException e) {
        }
      }
    }
    throw new ClassNotFoundException("Cannot find class: " + name);
  }

  /**
   * 获取类加载器数组
   */
  private ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    return new ClassLoader[]{
        classLoader,
        //默认类加载器
        defaultClassLoader,
        //当前线程类加载器
        Thread.currentThread().getContextClassLoader(),
        //当前类加载器
        getClass().getClassLoader(),
        //系统类加载器
        systemClassLoader};
  }

}
