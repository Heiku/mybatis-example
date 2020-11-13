## Mybatis



### 启动扫描

SpringBoot 启动的时候，在 `refresh()` 的时候会启动 BeanDefinitionRegistryPostProcessor， 其中包括了 MapperScannerConfigurer，然后会构建 ClassPathBeanDefinitionScanner 去扫描 @MapperScan 下或者配置文件下的 mapper，
在 `ClassPathMapperScanner#processBeanDefinitions()` 中，会将 BeanDefinition(..Mapper) 的 beanClass 替换成 
MapperFactoryBean<T>，这样在实例化该 factoryBean 的时候，`createBean()` 的时候会调用 `factoryBean#getObject()`。




```
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {
	@Override
  	protected void checkDaoConfig() {
    	super.checkDaoConfig();
    	
    	configuration.addMapper(this.mapperInterface);
    }
}

public abstract class SqlSessionDaoSupport extends DaoSupport {
  private SqlSessionTemplate sqlSessionTemplate;
}

public abstract class DaoSupport implements InitializingBean {
    @Override
    public final void afterPropertiesSet() throws IllegalArgumentException, BeanInitializationException {
    	checkDaoConfig();
    }
}
```



### 实例初始化

Configuration 类保存了 Mybatis 重要的配置信息，包括了 MapperRegistry<mapperInterfaceClass, mapperProxyFacoty>，

Map<mappedStatementId, mappedStatement>, Map<mappedStatementId, resultMap>, interceptorChain... 等重要信息，

用于存储 mybatis 执行过程中的面板数据。



每个 MapperFactoryBean 都持有 sqlSessionTemplate(线程安全) 这个 sqlSession 的实现，然后 DaoSupport 实现了 InitializingBean，
会将在初始化该 mapperFactoryBean 的时候，将该类 T(mapper) 注册到 MapperRegistry，具体存储是一个 map<class, MapperProxyFactory>，MapperProxyFactory 用于生成 MapperProxy。



除此之外，还通过 `MapperAnnotationBuilder#parse` 将 mapper 中的映射方法解析成对应的 MappedStatement，`MappedStatement` 是对应映射方法的一些配置信息，比如方法参数，执行语句等，并添加到 Configuration 中的 Map<String, MappedStatement> ，Configuration 成员 sqlSource, parameterMap 等比较重要，用于解析成对应的 sql 语句，交由 sqlSession 去执行。




```
public class MapperRegistry {
    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

    public <T> void addMapper(Class<T> type) {
        knownMappers.put(type, new MapperProxyFactory<>(type));    
        
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
    }
}
```



### 调用

我们在通过 `CityMapper.insert` 本质上是获取这个 mapperFactoryBean，然后通过 getObject()获取注册在 mapperRegistry 中的 `mapperProxy`。



MapperProxy 执行的时候，会根据执行的接口 mapper 和方法 method ，组合得到当前的 mappedStatementId，从直接注册的缓存里拿到对应的 MappedStatement, 构造对应的 MapperMehtodInvoker，并封装缓存在 mapperProxy 中的 Map<Method, MapperMehtodInvoker> 中。



如果已经缓存或者刚创建完，那么得到对应的 `MapperMethodInvoker` 调用实际上的 `mapperMethod`， mapperMethod 调用的过程中，会判断该 sql 的类型，根据具体的类型交由 sqlSession(SqlSessionTemplate) 进行处理，比如 insert、update、delete...



```
public class MapperProxy<T> implements InvocationHandler, Serializable {
	  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
	  		// 先找缓存，如果有该 mapperMethodInvoker 直接调用
	  		cachedInvoker(method).invoke(proxy, method, args, sqlSession);
	  		
	  		// 否则，构建一个。先根据缓存的 mappedStatement 构建 mapperMethod， 再构建 mapperMethodInvoker
	  		new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
	  }
}

private static class PlainMethodInvoker implements MapperMethodInvoker {
    private final MapperMethod mapperMethod;
 	
     @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }
}


public class MapperMethod {
	public Object execute(SqlSession sqlSession, Object[] args) {
        switch (command.getType()) {
          case INSERT: {
            Object param = method.convertArgsToSqlCommandParam(args);
            result = rowCountResult(sqlSession.insert(command.getName(), param));
            break;
          }
    }
}
```



### SqlSession



* tips

由于 sqlSession 是我们访问数据库的会话，所有数据库的操作都经由他实现，一个 sqlSession 应该只存在于一个业务请求中，这样才能保证事务的正确性。所以 sqlSession 肯定不能是单例，静态等，而是根据是否是同个事务去决定，也就是说在同一个事务中，应该始终使用同一个 sqlSession（联系 ThreadLocal）。



SqlSession 的创建我们都知道是通过 `SqlSessionFactory`, 在 Spring-Mybatis 将 SqlSessionFactory 封装成了 `SqlSessionFactoryBean` 利用 FactoryBean 的特性，可以通过 getObject() 获得 SqlSessionFactoey 实例。



* 创建 sqlSession 过程

  1. 从 Configuration 配置类中得到 Environment 数据源

  2. 从数据源中获取 TransactionFactory 和 DataSource，并构建 Transaction 连接管理对象

  3. 创建 Executor 对象（SqlSession 只是个数据库操作的门面，实际的数据库操作通过 Executor 实现，封装了 JDBC 所有的操作细节）

  4. 创建 SqlSession 会话

     

拿到 mapperMethod 的相关上下文之后，就可以通过 SqlSessionTemplate 进行执行处理。而因为 SqlSessionTemplate 内部持有的 sqlSession 本质上是一个代理对象，即 SqlSessionProxy， 所以最终调用还是按照 `SqlSessionInterceptor` 进行实际调用。



```
public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {
	... 
	// 构建 sqlSessionProxy
    this.sqlSessionProxy = (SqlSession) newProxyInstance(SqlSessionFactory.class.getClassLoader(),
        new Class[] { SqlSession.class }, new SqlSessionInterceptor());
  }
  
 private class SqlSessionInterceptor implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    
    
      	SqlSession sqlSession = getSqlSession(SqlSessionTemplate.this.sqlSessionFactory,
          	SqlSessionTemplate.this.executorType, SqlSessionTemplate.this.exceptionTranslator);
      		try {
        		Object result = method.invoke(sqlSession, args);
        		if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
          		// force commit even on non-dirty sessions because some databases require
          		// a commit/rollback before calling close()
          		sqlSession.commit(true);
        }
    }
}
```



首先会通过 `TransactionSynchronizationManager`获取当前事务的 sqlSession ，内部通过 ThreadLocal 进行了封装，如果取得到就直接返回，如果取不到，那么通过 SqlSessionFactory 构建一个 sqlSession，并封装为 sqlSessionHolder 注册到 TransactionSynchronizationManager。