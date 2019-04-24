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
package org.apache.ibatis.parsing;

/**
 * 常用token解析类
 *
 * @author Clinton Begin
 */
public class GenericTokenParser {

  /**
   * 开始标记
   */
  private final String openToken;
  /**
   * 结束标记
   */
  private final String closeToken;
  /**
   * 解析器
   */
  private final TokenHandler handler;

  /**
   * 构造函数
   */
  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 解析，将所有被openToken和closeToken包围的字符，通过handler转化，重新组织text
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    //查找开始标记
    int start = text.indexOf(openToken);
    //没有开始标记
    if (start == -1) {
      return text;
    }
    //转化为字符数组
    char[] src = text.toCharArray();
    int offset = 0;
    StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    while (start > -1) {
      //如果text中在openToken前存在转义符就将转义符去掉。如果openToken前存在转义符，start的值必然大于0，最小也为1
      //因为此时openToken是不需要进行处理的，所以也不需要处理endToken。接着查找下一个openToken
      if (start > 0 && src[start - 1] == '\\') {
        //开始标记已转义。删除反斜杠并继续。
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        //找到openToken，继续查找closeToken
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();
        //对应的closeToken的下标
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            expression.append(src, offset, end - offset);
            offset = end + closeToken.length();
            break;
          }
        }
        if (end == -1) {
          // 没有结束标志，剩余所有字符添加到builder
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      start = text.indexOf(openToken, offset);
    }
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
