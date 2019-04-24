/**
 * Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * 属性解析
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * 是否开启默认值 默认不开启
   *
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * 特殊属性的key
   *
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  private static final String ENABLE_DEFAULT_VALUE = "false";
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
    //内部转化属性的类
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    //开始标志${,结束标志},使用转化VariableTokenHandler
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {

    private final Properties variables;
    //是否启用默认值
    private final boolean enableDefaultValue;
    //占位符和默认值之间的分隔符，一般用于解析配置文件
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      enableDefaultValue = Boolean
          .parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR,
          DEFAULT_VALUE_SEPARATOR);
    }

    /**
     * 为空返回默认值，不为空从variables中根据key获取对应的值，没有对应的key，返回默认值
     */
    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    /**
     * 处理占位符，${param}
     */
    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        //开始了默认值
        if (enableDefaultValue) {
          //分隔符
          int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          //存在分割符
          if (separatorIndex >= 0) {
            key = content.substring(0, separatorIndex);
            //分隔符下标之后的是默认值
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          //有默认值，查找对应的属性的值
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }
        }
        //没开启默认值，直接查找对应的属性的值
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}";
    }
  }

}
