# 断路由器模式
在分布式架构中，当某个服务单元发生故障之后，通过断路由器的故障监控(类似熔断保险丝)，向调用方返回一个错误响应，而不是长时间的等待。这样就不会使得线程因调用故障服务被长时间占用不释放，避免了故障在分布式系统中的蔓延。

Spring Cloud Hystrix针对上述问题实现了断路由器、线程隔离等一系列服务保护功能。它是基于Netflix Hystrix实现，该框架的目标在于通过控制那些访问远程系统、服务和第三方库的节点，从而对延迟和故障提供更强大的容错能力。

Hystrix具备服务降级、服务熔断、线程和信号隔离、请求缓存、请求合并以及服务监控等强大功能。

# 快速入门
构建一个如下架构图的服务调用关系
![服务架构图](https://s1.ax1x.com/2018/08/20/P4kN9A.png)
分析上述架构图，主要有以下几项工作：
1. eureka-server工程: 服务注册中心，端口1111
2. hello-service工程: HELLO-SERVICE服务单元，启动两个实例，端口分别为8081和8082
3. ribbon-consumer工程: 使用Ribbon实现的服务消费者，端口9000

## 修改ribbon-consumer模块
### 修改pom.xml
首先在pom.xml文件中增加spring-cloud-starter-hystrix依赖
### 开启断路由器功能
在ribbon-consumer主类中使用**@EnableCircuitBreaker**注解开启断路由器功能，在这里还有一个小技巧，可以使用**@SpringCloudApplicationd**代替@EnableCircuitBreaker、@EnableEurekaClient、@SpringBootApplication这三个注解。
### 改造服务消费方式
改造ribbon-consumer中的HelloService，如下
```java
package cn.sh.ribbon.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 使用@HystrixCommand注解指定回调方法
     * @param name
     * @return
     */
    @HystrixCommand(fallbackMethod = "ribbonHelloFallback", commandKey = "helloKey")
    public String ribbonHello(String name) {
        long start = System.currentTimeMillis();
        String result = restTemplate.getForObject("http://HELLO-SERVICE/hello?name=" + name, String.class);
        long end = System.currentTimeMillis();
        logger.info("Spend Time:" + (end - start));
        return result;
    }

    public String ribbonHelloFallback() {
        return "Hello, this is fallback";
    }
}

```
### 改造服务提供者
改造hello-service模块中的HelloService.java，如下:
```java
package cn.sh.hello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    public String hello(String name) throws InterruptedException {
        int sleepTime = new Random().nextInt(3000);
        logger.info("sleepTime:" + sleepTime);
        Thread.sleep(sleepTime);
        return "Hello, " + name;
    }
}
```
在服务提供者的改造中，我们会让方法阻塞几秒中返回内容，由于Hystrix默认的超时时间为2000ms，在这里产生0-3000的随机数可以让处理过程有一定概率触发断路由器。

# 原理分析
![工作流程图](https://s1.ax1x.com/2018/08/21/PIzdl6.png)
下面根据工作流程图，我们来分析一下Hystrix是如何工作的。

# 创建HystrixCommand或HystrixObservableCommand对象
首先，构建一个HystrixCommand或HystrixObservableCommand对象，用来表示对依赖服务的操作请求，同时传递所有需要的参数。这两个对象都采用了命令模式来实现对服务调用操作的封装，但是这两个对象分别针对不同的应用场景。
1. HystrixCommand: 用在依赖的服务返回单个操作结果的时候
2. HystrixObservableCommand: 用在依赖的服务返回多个操作结果的时候

命令模式，将来自客户端的请求封装成一个对象，从而让你可以使用不同的请求对客户端进行参数化。它可以用于实现行为请求者和行为实现者的解耦，以便使两者可以适应变化
