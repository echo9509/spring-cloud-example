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

## 重试机制

# Hystrix配置