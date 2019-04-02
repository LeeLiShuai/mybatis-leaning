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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * @author Tim Fennell
 */
public class ResolverUtil<T> {

  /**
   * 日志
   */
  private static final Log log = LogFactory.getLog(ResolverUtil.class);

  /**
   * 接口
   */
  public interface Test {

    boolean matches(Class<?> type);
  }

  /**
   * 判断一个类是否继承某个类或实现某个方法
   */
  public static class IsA implements Test {

    private Class<?> parent;

    /**
     * 参数作为parent
     */
    public IsA(Class<?> parentType) {
      parent = parentType;
    }

    /**
     * 如果可以赋值给parent返回true，即type是parent的父类
     */
    @Override
    public boolean matches(Class<?> type) {
      return type != null && parent.isAssignableFrom(type);
    }

    @Override
    public String toString() {
      return "is assignable to " + parent.getSimpleName();
    }
  }

  /**
   * 是否使用了某个注解
   */
  public static class AnnotatedWith implements Test {

    private Class<? extends Annotation> annotation;

    /**
     * 构造函数
     */
    AnnotatedWith(Class<? extends Annotation> annotation) {
      this.annotation = annotation;
    }

    /**
     * 如果类上有type注解返回true
     */
    @Override
    public boolean matches(Class<?> type) {
      return type != null && type.isAnnotationPresent(annotation);
    }

    @Override
    public String toString() {
      return "annotated with @" + annotation.getSimpleName();
    }
  }

  /**
   * 保存符合的类
   */
  private Set<Class<? extends T>> matches = new HashSet<>();

  /**
   * 查找此类时使用的类加载器，如果没指定则使用当前线程的类加载器
   */
  private ClassLoader classloader;

  /**
   * 返回符合的类
   */
  public Set<Class<? extends T>> getClasses() {
    return matches;
  }

  /**
   * 返回用于扫描类的类加载器，如果没有指定，返回上下文类加载器
   */
  public ClassLoader getClassLoader() {
    return classloader == null ? Thread.currentThread().getContextClassLoader() : classloader;
  }

  /**
   * 设置扫描类的类加载器，如果没有指定，则使用上下文类加载器
   */
  private void setClassLoader(ClassLoader classloader) {
    this.classloader = classloader;
  }

  /**
   * 尝试发现可分配给所提供类型的类. 在提供接口的情况下，该方法将收集实现类. 在非接口类的情况下，将收集子类.
   */
  public ResolverUtil<T> findImplementations(Class<?> parent, String... packageNames) {
    if (packageNames == null) {
      return this;
    }
    Test test = new IsA(parent);
    for (String pkg : packageNames) {
      find(test, pkg);
    }
    return this;
  }

  /**
   * 扫描包中被注解的的类，加到set中
   */
  public ResolverUtil<T> findAnnotated(Class<? extends Annotation> annotation,
      String... packageNames) {
    if (packageNames == null) {
      return this;
    }
    Test test = new AnnotatedWith(annotation);
    for (String pkg : packageNames) {
      find(test, pkg);
    }
    return this;
  }

  /**
   * 从传入的包中递归查找所有类，加到set中
   */
  public ResolverUtil<T> find(Test test, String packageName) {
    //报名转化为路径
    String path = getPackagePath(packageName);
    try {
      //包下的所有资源
      List<String> children = VFS.getInstance().list(path);
      for (String child : children) {
        //是class文件
        if (child.endsWith(".class")) {
          addIfMatching(test, child);
        }
      }
    } catch (IOException ioe) {
      log.error("Could not read package: " + packageName, ioe);
    }

    return this;
  }

  /**
   * 将包名转化为路径
   */
  private String getPackagePath(String packageName) {
    return packageName == null ? null : packageName.replace('.', '/');
  }

  /**
   * 通过test的测试，则加入set中
   */
  private void addIfMatching(Test test, String fqn) {
    try {
      String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');
      ClassLoader loader = getClassLoader();
      if (log.isDebugEnabled()) {
        log.debug("Checking to see if class " + externalName + " matches criteria [" + test + "]");
      }
      Class<?> type = loader.loadClass(externalName);
      if (test.matches(type)) {
        matches.add((Class<T>) type);
      }
    } catch (Throwable t) {
      log.warn("Could not examine class '" + fqn + "'" + " due to a " +
          t.getClass().getName() + " with message: " + t.getMessage());
    }
  }
}
