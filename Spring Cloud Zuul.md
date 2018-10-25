# 前言
API网关的定义类似于面向对象设计模式中的Facade模式，它的存在就像是整个微服务架构系统的门面一样，所有的外部客户端访问都需要经过它来调度和过滤。
它除了要实现请求路由、负载均衡、校验过滤功能之外，还需要更多的能力，比如与服务治理框架的融合、请求转发时的熔断机制、服务的聚合等一系列高级功能。

# spring-cloud-starter-zuul
该依赖模块中包含了以下几个模块：
1. zuul-core：核心依赖模块
2. spring-cloud-starter-hystrix：该依赖用来在网关服务中实现对微服务转发时候的保护机制，通过线程隔离和断路器，防止微服务的故障引发API网关资源无法释放，从而影响其他应用的对外服务。
3. spring-cloud-starter-ribbon：该依赖用来实现在网关服务进行路由转发时候的客户端负载均衡和请求重试
4. spring-boot-starter-actuator：该依赖用来提供常规的微服务管理端点。另外，在Spring Cloud Zuul中还特别提供了/routes端点来返回当前的路由规则

# 快速入门
## 请求路由
### 传统路由方式
增加如下配值
```properties
zuul.route.api-a-url.path=/api-a-ulr/**
zuul.route.api-a-url.url=http://localhost:8080/
```
在上述配置中，在所有发往API网关服务的请求中，所有符合/api-a-url/**规则的访问都将被路由转发到http://localhost:8080/地址上。

其中api-a-url为路由的名字，**但是一组path和url映射关系的路由名要相同。**

### 面向服务的路由
Spring Cloud Zuul可以和Spring Cloud Eureka无缝整合，路由的path为某个具体的服务名，url则交给Eureka的服务机制去自动维护。

增加如下配置
```properties
zuul.routes.api-a.path=/api-a/**
zuul.routes.api-a.service-id=hello-service

zuul.routes.api-b.path=/api-b/**
zuul.routes.api-b.service-id=feign-consumer

eureka.client.service-url.defaultZone=http://localhost:1111/eureka/
```
上述配置的作用主要是将网关服务注册到服务注册中心，然后配置了两个路由规则，以/api-a/开头的路由将会被转发到hell-service微服务，
以/api-b/开头的则会被路由到feign-consumer微服务

## 请求过滤
网关服务除了路由转发功能，还可以用作作验权等一些与具体业务无关的功能，可以使具体的业务微服务应用去除各种复杂的过滤器和拦截器，降低开发和测试复杂度。

继承ZuulFilter实现自定义的过滤器类，如下：
```java
package cn.sh.gateway.filter;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
public class AccessFilter extends ZuulFilter {

    private final Logger logger = LoggerFactory.getLogger(AccessFilter.class);

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() throws ZuulException {
        RequestContext context = RequestContext.getCurrentContext();
        HttpServletRequest request = context.getRequest();
        String accessToken = request.getParameter("accessToken");
        if (StringUtils.isBlank(accessToken)) {
            logger.warn("access token is empty");
            context.setSendZuulResponse(false);
            context.setResponseStatusCode(401);
            return null;
        }
        logger.info("access token ok");
        return null;
    }
}
```
上述代码简单实现了一个验证accessToken是否存在的功能。

重写ZuulFilter的方法具体的作用：
1. filterType：过滤器的类型，它决定过滤器在请求的哪个生命周期执行。代表会在请求被路由之前执行。
2. filterOrder：过滤器的执行顺序。当一个阶段存在多个过滤器时，需要根据该方法的返回值来一次执行，值越小，优先执行。
3. shouldFilter：判断该过滤器是否需要被执行。
4. run：过滤器的具体逻辑。

## 小结
使用API网关的主要原因如下：
1. 作为系统的统一入口，屏蔽系统内部各个微服务的细节。
2. 与服务治理框架结合，实现自动化的服务实例维护以及负载均衡的路由转发。
3. 实现接口权限校验与微服务业务逻辑的解耦。
4. 通过服务网关中的过滤器，在各生命周期中去校验请求的内容，将原本在对外服务层做的校验前移，保证了微服务的无状态性，同时降低微服务的测试难度，让服务本身更集中关注业务逻辑。

# 路由详解
## 传统路由
### 单实例配置
单实例配置可以见前一小结

### 多实例配置
增加如下配置:
```properties
zuul.routes.user-service.path=/user-service/**
zuul.routes.user-service.service-id=user-service
ribbon.eureka.enabled=false
user-service.ribbon.listOfServers=http://localhost:6000/,http://localhost:6001/
```
上述配置没有将网关服务整合进服务治理框架，因此需要实现负责均衡策略，因此需要与Spring Cloud Ribbon配合。

1. ribbon.eureka.enabled：在没有整合服务治理框架式，需要将该参数设置为false，在有服务治理框架时，zuul.routes.<route>.service-id指定的是服务名称。
2. user-service.ribbon.listOfServers：开头的user-service对应了zuul.routes.<route>.service-id中的service-id的值，上述两个参数的配置相当于在内部手工维护了服务与实例的对应关系

## 服务路由配置
面向服务的路由基本配置见上一节，除了上一节的配置，还有一种更为简洁的配置方式：**zuul.routes.<serviceId>=<path>，serviceId指定具体的服务名，path设置匹配的请求表达式。**

Spring Cloud Zuul在引入Spring Cloud Eureka之后，它会为Eureka中的每个服务都自动创建一个默认路由规则，这些规则的**path默认会以serviceId作为默认前缀。**

如果想禁用这个功能，可以通过zuul.ignored-services参数来设置，禁用多个服务之间用,分隔，禁用全部该参数值设置*即可。

## 自定义路由映射规则
```java
    @Bean
    public PatternServiceRouteMapper serviceRouteMapper() {
        return new PatternServiceRouteMapper(
                "(?<name>^.+)-(?<version>v.+$)", "${version}/${name}");
    }
```
PatternServiceRouteMapper对象可以让开发者通过正则表达式来自定义服务与路由映射的生成关系。

## 路径匹配
在Zuul中，路由匹配的路径表达式采用了Ant风格定义。

通配符 | 说明
--- | ---
？ | 匹配任意单个字符
* | 匹配任意数量的字符
** | 匹配任意数量的字符，支持多级目录

当一个URL路径匹配到不同的路由的表达式，匹配结果取决于路由规则的保存顺序。注意：.properties文件是无法保证顺序的，但是.yaml可以保证顺序。

## 忽略表达式
Zuul提供了一个忽略表达式参数zuul.ignored-patterns。该参数可以用来设置不希望被API网关进行路由的URL表达式。

```properties
zuul.ignored-patterns=/**/users/**
zuul.routes.user-service=/user-service/**
```
比如这时，你向网关微服务发起/user-service/users/1的请求，就会找不到该URL路径