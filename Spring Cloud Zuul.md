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
