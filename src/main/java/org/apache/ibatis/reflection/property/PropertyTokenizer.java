/**
 * Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 表达式解析器
 *
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {

  /**
   * 当前表达式
   */
  private String name;
  /**
   * 表达式索引名称
   */
  private final String indexedName;
  /**
   * 表达式索引下标
   */
  private String index;
  /**
   * 表达式的字表达式
   */
  private final String children;

  public PropertyTokenizer(String fullname) {
    //查找.的下表
    int delim = fullname.indexOf('.');
    //有.
    if (delim > -1) {
      //表达式
      name = fullname.substring(0, delim);
      //子表达式
      children = fullname.substring(delim + 1);
    } else {
      //没有.
      name = fullname;
      children = null;
    }
    //索引名称
    indexedName = name;
    //存在数组，找到下标
    delim = name.indexOf('[');
    if (delim > -1) {
      //获取下标
      index = name.substring(delim + 1, name.length() - 1);
      //重新赋值name
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException(
        "Remove is not supported, as it has no meaning in the context of properties.");
  }
}
