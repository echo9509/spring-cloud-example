# 快速入门
Spring Cloud Feign整合了Spring Cloud Ribbon和Spring Cloud Hystrix的使用，除了提供了上述两种框架的强大功能之外，它还提供了一种
声明式的Web服务客户端定义方式。（相关代码在feign-consumer模块下）

**1、创建feign-consumer工程，在pom.xml种引入spring-cloud-starter-eureka和spring-cloud-starter-openfeign依赖。**

**2、创建应用程序主类ConsumerApplication，并通过@EnableFeignClients注解开启Spring Cloud Feign的支持功能。**

**3、定义HelloService接口，通过@FeignClient注解指定服务名来绑定服务，然后再使用Spring MVC注解来绑定具体该服务提供的REST接口。**

**4、新增FeignConsumerController来实现对Feign客户端的调用。**

**5、在application.properties中指定服务注册中心，并定义自身的服务名为feign-consumer，端口使用9001**

# 参数绑定
在进行参数绑定时，@RequestParam、@RequestHeader等可以指定参数名称的注解，但是它们的value必不可少。Spring MVC中这两个注解会根据参数名作为默认值，
但是在Feign中绑定参数必须通过value属性来指明具体的参数名。

# Ribbon配置
Spring Cloud Feign的客户端负载均衡是通过Spring Cloud Ribbon实现，所以可以直接通过配置Ribbon客户端的方式来自定义各个客户端调用的参数。

## 全局配置
全局配置直接使用ribbon.<key>=<value>的方式来设置ribbon的各项默认参数

## 指定服务配置
指定服务配置使用<client>.ribbon.<key>=<value>的格式设置。

<client>参数来源：在定义客户端的时候，使用了@FeignClient注解。在初始化过程中，Spring Cloud Feign会根据注解的name属性或者value属性指定的服务名，
自动创建一个同名的Ribbon客户端。

# Hystrix配置
Spring Cloud Feign除了整合了Spring Cloud Ribbon之外，还引入了服务保护与容错的工具Hystrix。
默认情况下，Spring Cloud Feign会将所有Feign客户端的方法都封装到Hystrix命令中进行服务保护。

## 全局配置
对于Hystrix的全局配置同Spring Cloud Hystrix的全局配置一样，直接使用它的默认配置前缀hystrix.command.default就可以进行设置。

在对Hystrix进行配置前，需要将fegin.hystrix.enabled参数设置为true。

## 禁用Hystrix
feign.hystrix.enabled=false 全局关闭Hystrix

针对某个服务客户端关闭Hystrix支持时，需要通过使用@Scope("prototype")注解为指定的客户端配置Feign.Builder实例。

1. 构建一个关闭Hystrix的配置类
2. 在HelloService的@FeignClient注解中，通过configuration参数引入上面实现的配置。

```java
@Configuration
public class DisableHystrixConfiguration {

    @Bean
    @Scope("prototype")
    public Feign.Builder feignBuild() {
        return Feign.builder();
    }
}
@FeignClient(value = "hello-service", configuration = DisableHystrixConfiguration.class)
public interface HelloService {

    @GetMapping("/feignHello")
    String hello();

    @GetMapping("/hello1")
    String hello(@RequestParam("name") String name);

    @GetMapping("/hello2")
    User hello(@RequestHeader("name") String name, @RequestHeader("age") Integer age);

    @PostMapping("/hello3")
    String hello(@RequestBody User user);
}
```

## 指定命令配置
采用hystrix.command.<commandKey>作为前缀。<commandKey>默认情况下会采用Feign客户端中的方法名作为标识。

## 服务降级配置
1. 服务降级逻辑的实现只需要为Feign客户端的定义接口编写一个具体的接口实现类，其每个重写方法的实现逻辑都可以用来定义相应的服务降级逻辑。
2. 在服务绑定的HelloService中，通过@FeignClient注解的fallback属性来指定对应的服务降级实现类。

```java
@Component
public class HelloServiceFallback implements HelloService {

    @Override
    public String hello() {
        return "error";
    }

    @Override
    public String hello(String name) {
        return "error";
    }

    @Override
    public User hello(String name, Integer age) {
        return new User("UNKNOWN", 0);
    }

    @Override
    public String hello(User user) {
        return "error";
    }
}
```

## 其他配置
### 请求压缩
Spring Cloud Feign支持对请求与响应进行GZIP压缩，减少通信过程中的性能损耗。
```properties
# 开启请求与响应的压缩功能
feign.compression.request.enabled=true
feign.compression.response.enabled=true

# 指定压缩的请求数据类型
feign.compression.request.mime-types=text/xml,application/xml,application/json
# 请求压缩的大小下限
feign.compression.request.min-request-size=2048
```

## 日志配置
Spring Cloud Feign在构建被@FeignClient注解修饰的服务客户端时，会为每一个客户端创建一个feign.Logger实例。
可以在application.properties文件中使用**logger.level.<FeignClient>**的参数配置格式来开启指定Feign客户端的日志。

<FeignClient>是Feign客户端定义接口的完整路径。

在增加上述配置之后，并不能实现对响应模式日志的输出。这是因为Feign客户端默认的Logger.Level对象定义为NONE级别，该级别不会记录任何Feign调用过程中的信息，因此需要在主类中加入Logger.Level的Bean的创建，或者通过实现配置类。

对于Feign的Logger级别主要有下面4类：
1. NONE: 不记录任何信息
2. BASIC: 仅记录请求方法、URL以及响应状态码和执行时间
3. HEADERS: 除了记录BASIC级别的信息之外，还会记录请求和响应的头信息
4. FULL: 记录所有请求与响应的明细，包括头信息、请求体、元数据等。