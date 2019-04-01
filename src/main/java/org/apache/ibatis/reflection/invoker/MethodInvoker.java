/**
 * Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.ibatis.reflection.Reflector;

/**
 * get,set方法请求者
 *
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

  /**
   * 对应的Class类型
   */
  private final Class<?> type;

  /**
   * 对应的方法
   */
  private final Method method;

  public MethodInvoker(Method method) {
    this.method = method;
    //如果只有一个参数(即set方法)
    if (method.getParameterTypes().length == 1) {
      type = method.getParameterTypes()[0];
    } else {
      //其余设置返回类型
      type = method.getReturnType();
    }
  }

  @Override
  public Object invoke(Object target, Object[] args)
      throws IllegalAccessException, InvocationTargetException {
    try {
      //执行target的method方法，参数为args
      return method.invoke(target, args);
    } catch (IllegalAccessException e) {
      //没有访问权限则开启权限
      if (Reflector.canControlMemberAccessible()) {
        method.setAccessible(true);
        return method.invoke(target, args);
      } else {
        throw e;
      }
    }
  }

  @Override
  public Class<?> getType() {
    //返回对应的Class
    return type;
  }
}
