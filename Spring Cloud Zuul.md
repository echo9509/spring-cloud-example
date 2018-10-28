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

## 路由前缀
Zuul提供了zuul.prefix参数来为全局路由规则增加前缀。**在使用该参数后，默认的前缀会失效，可以通过zuul.stripPrefix=false来关闭移除默认前缀的操作，**
**，也可以zuul.routes.<route>.strip-prefix=true来对指定路由关闭移除代理前缀的操作。**

具体解释如下图：
![i650VU.png](https://s1.ax1x.com/2018/10/27/i650VU.png)

## 本地跳转
Zuul支持forward形式的服务端跳转，如下所示：
```properties
zuul.routes.api-a.path=/api-a/**
zuul.routes.api-a.url=forward:/
```
注意：在网关应用中，需要有对应的接口。

## Cookie与头信息
Spring Cloud Zuul在请求路由时，会过滤掉HTTP请求头信息中的一些敏感信息，防止它们被传递到下游的服务器。

默认的敏感头信息通过zuul.sensitive-headers参数定义，包括Cookie、Set-Cookie、Authorization三个属性。

1. 全局默认覆盖：zuul.sensitive-headers=
2. 对指定路由开启自定义头：zuul.routes.<route>.custom-sensitive-headers=true
3. 将指定路由的敏感头设置唯恐：zuul.routes.<route>.sensitive-headers=

# Hystrix和Ribbon支持
Spring Cloud Zuul默认提供了Hystrix和Ribbon的支持，但是在使用path和url来设置路由时，对于路由转发的请求不会采用HystrixCommand来包装，所以
这类路由的请求没有线程隔离和断路器的保护，并且也不会具有负载均衡的能力。

1.hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds

该参数可以用来设置API网关中路由转发请求HystrixCommand执行的超时时间，当路由转发请求的命令执行时间超过该配置值之后，
Hystrix会讲该执行命令标记为TIMOUT并抛出异常

2.ribbon.ConnectTimeout

用来创建路由转发请求连接的超时时间。

当ribbon.ConnectTimeout的值小于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds值时，
若出现路由请求创建连接超时，会自动进行重试路由请求，如果重试失败，会出现如下错误：
![i6xEeH.png](https://s1.ax1x.com/2018/10/27/i6xEeH.png)

当ribbon.ConnectTimeout的值大于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds值时，
不会进行路由请求重试，而是直接按请求命令超时处理。
![i6xtkn.png](https://s1.ax1x.com/2018/10/27/i6xtkn.png)

3.ribbon.ReadTimeout

用来设置路由转发请求的超时时间。

当ribbon.ReadTimeout的值小于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds值时，
若路由请求的处理时间超过该配置值且依赖服务的请求还未响应的时候，会自动进行请求重试。如果重试后还没有获得响应，Zuul会
返回NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED错误。

当ribbon.ReadTimeout的值大于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds值时，
若路由请求的处理时间超过该配置值且依赖服务的请求还未响应的时候，不会自动重试，而是直接返回TIMEOUT的错误信息。

```properties
zuul.retryable=false
zuul.routes.<route>.retryable=false
```
上述两个参数配置分别是从全局和指定路由关闭请求重试。

# 过滤器详解
## 过滤器
过滤器主要负责对请求的处理过程进行干预，是实现请求校验、服务聚合等功能的基础。

路由映射主要由pre类型的过滤器完成，它将请求路径与配置的路由规则进行匹配，以找到需要转发的目标地址。

请求转发的部分则是由route类型的过滤器来完成，对pre类型过滤器获得路由地址进行转发。

Spring Cloud Zuul中实现的过滤器必须包含4个基本特征：过滤类型、执行顺序、执行条件、具体操作。其实就是ZuulFilter重写的四个方法。

下面主要看一下过滤器的类型：
1. pre：可以在请求被路由之前调用
2. routing：在路由请求时被调用
3. post：在routing和error过滤器之后被调用
4. error：处理请求时发生错误时被调用


## 请求生命周期
![icKPpT.png](https://s1.ax1x.com/2018/10/27/icKPpT.png)

HTTP请求到达API网关服务时，它会进入pre，在这里被pre过滤器处理，该类型过滤器的主要目的是在进行请求路由之前做一些前置加工，比如请求的校验等。

在完成了pre过滤器的处理之后，请求进入routing阶段，该阶段的主要内容是把外部请求转发到具体服务实例上去的过程，当服务实例将请求结果都返回之后，routing阶段完成。

routing阶段完成之后，会进入post的阶段，在post阶段可以对处理结果进行一些加工或转换等内容。

上述三个阶段发生异常时会进入error过滤器，但最终还是要流向post类型的过滤器。因为它需要通过post过滤器将最终结果返回给请求客户端。

## 核心过滤器

![ig9ZLT.png](https://s1.ax1x.com/2018/10/28/ig9ZLT.png)

### pre 过滤器
#### ServletDetectionFilter
它的执行顺序是-3，是最先被执行的过滤器。该过滤器总是会被执行，主要用来检测当前请求是通过DispatchServlet处理运行的，还是通过ZuulServlet来处理运行的。
检测结果会以boolean类型保存在当前请求上下文的isDispatcherServletRequest参数中。后续的过滤器中可以通过Request.isDispatcherServletRequest（）
和RequestUtils.isZuulServletRequest()来判断请求处理的源头。一般，发送到API网关的请求都会被DispatcherServlet处理，
除了/zuul/\*路径会被ZuulServlet处理，主要用来应对处理大文件上传的情况。另外，对于ZuulServlet的访问路径/zuul/\*，可以通过zuul.servletPath参数来进行修改。

#### Servlet30WrapperFilter
它的执行顺序是-2，是第二个执行的过滤器。目前的实现会对所有请求生效，主要为了将原始的HttpServletRequest包装成Servlet30WrapperFilter对象。

#### FormBodyWrapperFilter
它的执行顺序是-1，是第三个执行的过滤器。该过滤器只对两种类型的过滤器生效。第一类是Content-Type是application/x-www-form-urlencoded的请求，
第二类是Content-Type为multipart/form-data并且是由Spring的DispatcherServlet处理的请求(用到了ServletDetectionFilter的处理结果)。该过滤器
的主要目的是将符合要求的请求体包装成FormBodyRequestWrapper对象。

#### DebugFilter
执行顺序1，是第四个执行的过滤器。该过滤器会根据配置参数zuul.debug.request和请求中的debug参数来决定是否执行过滤器中的操作。它的具体操作内容是
将当前请求上下文中的debugRouting和debugRequest参数设置为true。另外对于请求参数中debug参数，可以通过zuul.debug.parameter来进行定义。

#### PreDecorationFilter
执行顺序是5，是pre阶段最后被执行的过滤器。该过滤器会判断当前请求上下文中是否存在forward.to和serviceId参数。如果都不存在，那么会执行具体过滤器的操作。
它的具体操作是为当前请求做一些预处理，比如：进行路由规则的匹配、在请求上下文中设置该请求的基本信息以及将路由匹配结果等一些设置信息等。在后续过滤器中，
可以通过RequestContext.getCurrentContext()来访问这些信息。另外，在该过滤器的实现中，还包括HTTP头请求处理的逻辑。对于头域的记录是通过
zuul.addProxyHeaders参数进行控制的，这个参数的默认值为true，zuul在请求跳转时默认会为请求增加x-Forwarded-*头域。

### routing过滤器
#### RibbonRoutingFilter
执行顺序是10，是route阶段第一个执行的过滤器。该过滤器只对请求上下文中存在serviceId参数的请求进行处理，即只对通过serviceId配置路由规则的请求生效。
该过滤器的执行逻辑就是面向服务路由的核心，通过使用Ribbon和Hystrix来向服务实例发起请求，并将服务实例的请求结果返回。

#### SimpleHostRoutingFilter
执行顺序100，是route阶段第二个执行的过滤器。该过滤器只对请求上下文中存在routeHost参数的请求进行处理，即只对通过url配置路由规则的请求生效。

#### SendForwardFilter
执行顺序是500，是route阶段第三个执行的过滤器。该过滤器只对请求上下文中存在forward.to参数的请求进行处理，即用来处理路由规则中的forward本地跳转配置。

### post过滤器
#### SendErrorFilter
执行顺序为0，是post阶段的第一个过滤器。该过滤器仅在请求上下文包含error.status_code参数并且还没有被该过滤器处理过的时候执行。
该过滤器的具体逻辑利用请求上下文中的错误信息组成一个forward到API网关/error错误端点的请求来产生错误响应

#### SendResponseFilter
执行顺序是1000，是post阶段最后执行的过滤器。该过滤器会检查请求上下文中是否包含请求响应相关的头信息、响应数据流或是响应体，
只有在包含它们其中一个的时候执行处理逻辑。该过滤器的处理逻辑就是利用请求上下文的响应信息来组织需要发送回客户端的响应内容。

## 异常处理(有待考证)
一般核心过滤器都会在具体的执行逻辑中进行try-catch，然后在异常处理时向请求上下文中添加一些error相关的参数，主要有下面几个：
1. error.status_code：错误编码
2. error.exception：Exception异常对象
3. error.message：错误信息

**上述有待考证**

### error过滤器
即使使用try-catch，我们的程序依然有可能抛出异常，因此会进入error阶段。

## 禁用过滤器
zuul.<SimpleClassName>.<filterType>.disable=true 可以禁用指定的过滤器