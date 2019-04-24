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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * 解析xml文件，构建sqlsessionFactory
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 是否已经解析过xml文件
   */
  private boolean parsed;
  /**
   * 解析xml文件的工具类
   */
  private final XPathParser parser;
  /**
   * 标识<environment>标签名称
   */
  private String environment;
  /**
   * 反射工厂，创建和缓存Relfector对象
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment,
        props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    configuration.setVariables(props);
    parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析xml文件，
   */
  public Configuration parse() {
    //已经解析过了
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    //设置解析状态
    parsed = true;
    //解析
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 具体的解析方法
   */
  private void parseConfiguration(XNode root) {
    try {
      //解析properties
      propertiesElement(root.evalNode("properties"));
      //解析settings
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      //设置自定义文件加载
      loadCustomVfs(settings);
      //设置自定义log实现
      loadCustomLogImpl(settings);
      //解析typeAliases
      typeAliasesElement(root.evalNode("typeAliases"));
      //解析plugins
      pluginElement(root.evalNode("plugins"));
      //解析对象工厂
      objectFactoryElement(root.evalNode("objectFactory"));
      //解析对象包装工厂
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //解析reflectFactory
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      //将settings设置到configuration
      settingsElement(settings);
      //解析environments
      environmentsElement(root.evalNode("environments"));
      //解析数据库厂商标识
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //解析数据类型处理器
      typeHandlerElement(root.evalNode("typeHandlers"));
      //解析mappers
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 解析setting节点
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    //获取settings的子节点，即setting
    Properties props = context.getChildrenAsProperties();
    // 检查配置中是否是否已经有了set听
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key
            + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 设置自定义文件加载
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 设置自定义日志
   */
  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 解析typeAliases
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      //遍历所有子节点
      for (XNode child : parent.getChildren()) {
        //处理package
        // <typeAliases>
        //     <package name="demo.mybatis.entity"/>
        //</typeAliases>
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //处理其他
          // <typeAliases>
          //   <typeAlias type="demo.mybatis.entity.UserInfo" alias="UserInfo"/>
          //</typeAliases>
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            //注册别名
            if (alias == null) {
              //扫描注解
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException(
                "Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析plugin
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      //遍历子节点，即plugin
      for (XNode child : parent.getChildren()) {
        //获取interceptor属性的值
        String interceptor = child.getStringAttribute("interceptor");
        //获取自己节点的子节点，每一个plugin的子节点的具体属性
        Properties properties = child.getChildrenAsProperties();
        //解析别名后，实例化一个对象
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        //设置属性
        interceptorInstance.setProperties(properties);
        //添加到configuration中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 处理自定义的objectFactory
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //<objectFactory type = "org.mybatis.example.ExampleObjectFactory" >
      //   <property name = "someProperty" value = "100" / >
      //</objectFactory >
      //获取自定义的类型
      String type = context.getStringAttribute("type");
      //获取子节点作为properties
      Properties properties = context.getChildrenAsProperties();
      //新建一个对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      //设置属性
      factory.setProperties(properties);
      //设置到configuration
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 解析objectWrapperFactory
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //获取type对应的属性的值
      String type = context.getStringAttribute("type");
      //新建对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      //设置configuration使用的bjectWrapperFactory
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   * 解析reflectorFactory
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //获取type对应的属性的值
      String type = context.getStringAttribute("type");
      //获取对应的对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
      //设置到configuration中
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析properties节点
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //获取properties的子节点,即properties
      Properties defaults = context.getChildrenAsProperties();
      //properties引入一个resource文件，如<properties resource="src/main/resources/jdbc.properties"/>
      String resource = context.getStringAttribute("resource");
      //properties通过网络引入一个配置文件
      String url = context.getStringAttribute("url");
      //resource和URL不能同时存在
      if (resource != null && url != null) {
        throw new BuilderException(
            "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      //只有resource
      if (resource != null) {
        //解析属性放入defaults
        defaults.putAll(Resources.getResourceAsProperties(resource));
        //只有url
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //去除configuration里的配置
      Properties vars = configuration.getVariables();
      if (vars != null) {
        //放入defaults
        defaults.putAll(vars);
      }
      //defaults保存到对象中
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * 解析settings
   */
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(
        AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior
        .valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration
        .setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(
        booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(
        booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(
        ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(
        integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(
        booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration
        .setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(
        LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration
        .setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(
        stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"),
            "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(
        booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration
        .setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration
        .setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration
        .setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration
        .setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(
        booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析environments
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        //如果没有初始化builder时没有指定environment，设置默认的environment
        environment = context.getStringAttribute("default");
      }
      //遍历子节点，即environment
      for (XNode child : context.getChildren()) {
        //获取id
        String id = child.getStringAttribute("id");
        //与environment对比，是否相等，如果相等
        if (isSpecifiedEnvironment(id)) {
          //获取事务肝理气
          TransactionFactory txFactory = transactionManagerElement(
              child.evalNode("transactionManager"));
          //获取数据源工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          //数据源
          DataSource dataSource = dsFactory.getDataSource();
          //环境builder
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          //设置configuration里的环境
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      //获取type对应的属性
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      //获取对应属性
      Properties properties = context.getChildrenAsProperties();
      //新建对象
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      //设置属性
      databaseIdProvider.setProperties(properties);
    }
    //获取环境
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      //获取databaseid
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      //设置到configuration中
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析数据类型处理器
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      //遍历子节点，即typeHandler
      for (XNode child : parent.getChildren()) {
        //扫描一个包下的所有类型处理器
        // <typeHandlers>
        //  <package name="org.mybatis.example"/>
        //</typeHandlers>
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          //<typeHandlers>
          //  <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
          //</typeHandlers>
          //获取属性并注册
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析mapper文件
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      //遍历子节点，即mapper和package
      for (XNode child : parent.getChildren()) {
        //<mappers>
        //    <package name="org.mybatis.mappers"/>
        //</mappers>
        if ("package".equals(child.getName())) {
          //获取包名
          String mapperPackage = child.getStringAttribute("name");
          //添加到configuration中
          configuration.addMappers(mapperPackage);
        } else {
          //<mappers>
          //    <mapper class="org.mybatis.mappers.UserMapper"/>
          //</mappers>

          //<mappers>
          //    <mapper url="file:///var/mappers/UserMapper.xml"/>
          //</mappers>

          //<mappers>
          //    <mapper resource="org/mybatis/mappers/UserMapper.xml"/>
          //</mappers>
          //其余三种情况
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          //resource
          if (resource != null && url == null && mapperClass == null) {
            //用于打印异常
            ErrorContext.instance().resource(resource);
            //获取输入流
            InputStream inputStream = Resources.getResourceAsStream(resource);
            //新建mapperBuilder
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration,
                resource, configuration.getSqlFragments());
            //解析
            mapperParser.parse();
            //url
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                configuration.getSqlFragments());
            mapperParser.parse();
            //class
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException(
                "A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
