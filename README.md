
### Mybatis

SpringBoot 启动的时候，在 `refresh()` 的时候会启动 BeanDefinitionRegistryPostProcessor， 其中包括了 MapperScannerConfigurer，
然后回构建 ClassPathBeanDefinitionScanner 去扫描 @MapperScan 下或者配置文件下的 mapper，
在 `ClassPathMapperScanner#processBeanDefinitions()` 中，会将 BeanDefinition(..Mapper) 的 beanClass 替换成 
MapperFactoryBean<T>，这样在实例化该 factoryBean 的时候，`createBean()` 的时候会调用 `factoryBean#getObject()`。

```
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {
}

public abstract class SqlSessionDaoSupport extends DaoSupport {

  private SqlSessionTemplate sqlSessionTemplate;
}

public abstract class DaoSupport implements InitializingBean {
    @Override
    	public final void afterPropertiesSet() throws IllegalArgumentException, BeanInitializationException {
    		// Let abstract subclasses check their configuration.
    		checkDaoConfig();
    }
}
```

每个 MapperFactoryBean 都持有 sqlSessionTemplate(线程安全) 这个 sqlSession 的实现，然后 DaoSupport 实现了 InitializingBean，
会将在实例化该 mapperFactoryBean 的时候，将该类 T(mapper) 注册到 MapperRegistry，具体存储是一个 map<class, MapperProxyFactory>，
MapperProxyFactory 用于生成 MapperProxy, 我们在通过 `CityMapper.insert` 本质上是获取这个 mapperFactoryBean，然后通过 getObject()
获取注册在 mapperRegistry 中的 mapperProxy，然后通过 proxy 最后会调用 sqlSessionTemplate 进行具体的 sqlSession 数据库连接操作。
```
public class MapperRegistry {
    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

    public <T> void addMapper(Class<T> type) {
        knownMappers.put(type, new MapperProxyFactory<>(type));    
    }
}
```