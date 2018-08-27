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

## 第1步 创建HystrixCommand或HystrixObservableCommand对象
首先，构建一个HystrixCommand或HystrixObservableCommand对象，用来表示对依赖服务的操作请求，同时传递所有需要的参数。这两个对象都采用了命令模式来实现对服务调用操作的封装，但是这两个对象分别针对不同的应用场景。
1. HystrixCommand: 用在依赖的服务返回单个操作结果的时候
2. HystrixObservableCommand: 用在依赖的服务返回多个操作结果的时候

命令模式，将来自客户端的请求封装成一个对象，从而让你可以使用不同的请求对客户端进行参数化。它可以用于实现行为请求者和行为实现者的解耦，以便使两者可以适应变化

**命令模式的示例代码在command模块下**

通过命令模式的示例代码可以分析出命令模式的几个关键点：
1. Receiver: 接收者，处理具体的业务逻辑
2. Command: 抽象命令，定义了一个对象应具备的一系列命令操作，如execute()、undo()、redo()等。当命令操作被调用的时候就会触发接收者做具体命令对应的业务逻辑。
3. ConcreteCommand: 具体的命令实现，在这里要绑定命令操作和接收者之间的关系，execute()命令的实现转交给了Receiver的action()方法
4. Invoker: 调用者，它拥有一个命令对象，可以在需要时通过命令对象完成具体的业务逻辑

命令模式中Invoker和Receiver的关系非常类似于请求-响应模式，所以它比较适用于实现记录日志、撤销操作、队列请求等。

以下情况我们可以考虑使用命令模式:
1. 使用命令模式作为回调在面向对象系统中的替代。
2. 需要在不同的时间指定请求、将请求排队。一个命令对象和原先的请求发出者可以有不同的生命周期。换言之，原先的请求发出者可能已经不在了，但是命令本身仍然是活动的。这时命令的接收者可以是在本地，也可以在网络的另一个地址。命令对象可以在序列化之后传送到另一台机器上。
3. 系统需要支持命令的撤销。命令对象可以把状态存储起来，等到客户端需要撤销命令所产生的效果时，可以调用undo()方法，把命令所产生的效果撤销掉。命令对象还提供redo()方法，以供客户端在需要时再重新实施命令效果。
4. 如果要将系统中所有的数据更新到日志里，以便在系统崩溃时，可以根据日志读回所有的数据更新命令，重新调用execute()方法一条一条执行这些命令，从而恢复系统在崩溃前所做的数据更新。

## 第2步 命令执行
从图中我们可以看到一共存在4种命令的执行方式，Hystrix在执行时会根据创建的Command对象以及具体的情况来选择一个执行。

**HystrixCommand**

HystrixCommand实现了两个执行方式：
1. execute(): 同步执行，从依赖的服务返回一个单一的结果对象，或是在错误时抛出异常
2. queue(): 异步执行，直接返回一个Future对象，其中包含了服务执行结束时要返回的单一结果对象。

```java
R value = command.execute();
Future<R> fValue = command.queue();
```

**HystrixObservableCommand**

HystrixObservableCommand实现了另两种执行方式:
1. observer(): 返回Observable对象，它代表了操作的多个结果，是一个HotObservable
2. toObservable(): 同样返回Observable对象，也代表操作的多个结果，返回的是一个ColdObservable
```java
Observable<R> ohvalue = command.observe();
Observable<R> ocvalue = command.toObservable();
```

Hot Observable和Cold Observable，分别对应了上面command.observe()和command.toObservable的返回对象。

Hot Observable，不论事件源是否有订阅者，都会在创建后对事件进行发布，所以对Hot Observable的每一个订阅者都有可能是从事件源的中途开始的，并可能只是看到了整个操作的局部过程。

Cold Observable在没有订阅者的时候不会发布事件，而是进行等待，直到有订阅者后才会发布事件，所以对于Cold Observable的订阅者，它可以保证从一开始看到整个操作的全部过程。

HystrixCommand也使用RxJava实现：
1. execute():该方法是通过queue()返回的异步对象Future<R>的get()方法来实现同步执行的。该方法会等待任务执行结束，然后获得R类型的结果返回。
2. queue():通过toObservable()获得一个Cold Observable，并且通过通过toBlocking()将该Observable转换成BlockingObservable，它可以把数据以阻塞的方式发出来，toFuture方法则是把BlockingObservable转换为一个Future，该方法只是创建一个Future返回，并不会阻塞，这使得消费者可以自己决定如何处理异步操作。execute()则是直接使用了queue()返回的Future中的阻塞方法get()来实现同步操作的。
3. 通过这种方式转换的Future要求Observable只发射一个数据，所以这两个实现都只能返回单一结果。

### RxJava观察者-订阅者模式入门介绍
在Hystrix的底层实现中大量使用了RxJava。上面提到的Observable对象就是RxJava的核心内容之一，可以把Observable对象理解为**事件源**或是**被观察者**，与其对应的是Subscriber对象，可以理解为**订阅者**或是**观察者**。

1. Observable用来向订阅者Subscriber对象发布事件，Subscriber对象在接收到事件后对其进行处理，这里所指的事件通常就是对依赖服务的调用。
2. 一个Observable可以发出多个事件，直到结束或是发生异常。
3. Observable对象每发出一个事件，就会调用对应观察者Subscriber对象的onNext()方法。
4. 每一个Observable的执行，最后一定会通过调用Subscriber.onCompleted()或是Subscriber.onError()来结束该事件的操作流。

## 第3步 结果是否被缓存
若当前命令的请求缓存功能是被启用的，并且该命令缓存命中，那么缓存的结果会立即以Observable对象的形式返回。

## 第4步 断路器是否打开
在命令结果没有缓存命中的时候，Hystrix在执行命令前需要检查断路器是否为打开状态:
1. 如果断路器是打开的，Hystrix不会执行命令，而是直接赚到fallback处理逻辑(对应下面第8步)
2. 如果断路器是关闭的，那么Hystrix会跳到第5步，检查是否有可用资源来执行命令。

## 第5步 线程池/请求队列/信号量是否占满
如果与命令相关的线程池和请求队列或者信号量(不使用线程池的时候)已被占满，那么Hystrix不会执行命令，转接到fallback处理逻辑(对应下面第8步)

Hystrix所判断的线程池并非容器的线程池，而是每个依赖服务的专有线程池。Hystrix为了保证不会因为某个依赖服务的问题影响到其他依赖服务而采用了舱壁模式来隔离每个依赖的服务。

## 第6步 HystrixObservableCommand.construct()或HystrixCommand.run()
Hystrix会根据我们编写的方法来决定采取什么样的方式去请求依赖服务:
1. HystrixCommand.run(): 返回一个单一的结果，或者抛出异常
2. HystrixObservableCommand.construct(): 返回一个Observable对象来发射多个结果，或通过onError发送错误通知

如果run()或construct()方法的执行时间超过了命令设置的超时阀值，当前处理线程会抛出一个TimeoutException(如果该命令不在其自身的线程中执行，则会通过单独的计时线程抛出)。在这种情况下，Hystrix会转到fallback逻辑去处理(第8步)。同时，如果当前命令没有被取消或中断，那么它最终会忽略run()或construct()方法的返回。

如果命令没有抛出异常并返回了结果，那么Hystrix在记录一些日志并采集监控报告之后将该结果返回。在使用run()时，返回一个Observable，它会发射单个结果并产生onCompleted的结束通知，在使用construct()时，会直接返回该方法产生的Observable对象。

## 第7步 计算断路器的健康度
Hystrix会将成功、失败、拒绝、超时等信息报告给断路器，断路器会维护一组计数器来统计这些数据。

断路器会使用这些统计数据来决定是否要将断路器打开，来对某个依赖服务的请求进行熔断/短路，直到恢复期结束。若在恢复期结束后，根据统计数据判断如果还是未达到健康指标，就再次熔断/短路。

## 第8步 fallback处理
当命令执行失败时，Hystrix会进入fallback尝试回退处理，我们通常也称之为**服务降级**。能够引起服务降级处理的情况主要有以下几种:
1. 第4步，当前命令处于熔断/短路状态，断路器是打开的时候。
2. 第5步，当前命令的线程池、请求队列或者信号量被占满的时候。
3. 第6步，HystrixObservableCommand.construct()或HystrixCommand.run()抛出异常的时候。

在服务降级逻辑中，我们需要实现一个通用的响应结果，并且该结果的处理逻辑应当是从缓存或是根据一些静态逻辑来获取，而不是依赖网络请求获取。如果一定要在降级逻辑中包含网络请求，那么该请求也必须被包装在HystrixCommand或是HystrixObservableCommand中，从而形成级联的降级策略，而最终的降级逻辑一定不是一个依赖网络请求的处理，而是一个能够稳定返回结果的处理逻辑。

HystrixCommand和HystrixObservableCommand中实现降级逻辑时有以下不同:
1. 当使用HystrixCommand的时候，通过实现HystrixCommand.getFallback()来实现服务降级逻辑。
2. 当使用HystrixObservableCommand的时候，通过HystrixObservableCommand.resumeWithFallback()实现服务降级逻辑，该方法会返回一个Observable对象来发射一个或多个降级结果。

当命令的降级逻辑返回结果之后，Hystrix就将该结果返回给调用者。当使用HystrixCommand.getFallback()时候，它会返回一个Observable对象，该对象会发射getFallback()的处理结果。而使用HystrixObservableCommand.resumeWithFallback()实现的时候，它会将Observable对象直接返回。

如果我们没有为命令实现降级逻辑或在降级处理中抛出了异常，Hystrix依然会返回一个Observable对象，但是他不会发射任何结果数据，而是通过onError方法通知命令立即中断请求，并通过onError()方法将引起命令失败的异常发送给调用者。在降级策略的实现中我们应尽可能避免失败的情况。

如果在执行降级时发生失败，Hystrix会根据不同的执行方法作出不同的处理:
1. execute(): 抛出异常
2. queue(): 正常返回Future对象，但是调用get()来获取结果时会抛出异常
3. observe(): 正常返回Observable对象，当订阅它的时候，将立即通过订阅者的onError方法来通知中止请求
4. toObservable(): 正常返回Observable对象，当订阅它的时候，将通过调用订阅者的onError方法来通知中止请求

## 第9步 返回成功的响应
当Hystrix命令执行成功之后，它会将处理结果直接返回或是以Observable的形式返回。具体的返回形式取决于不同的命令执行方式。
![返回结果](https://s1.ax1x.com/2018/08/27/PLNImQ.png)
1. toObservable(): 返回原始的Observable，必须通过订阅它才会真正触发命令的执行流程
2. observe(): 在toObservable()产生原始Observable之后立即订阅它，让命令能够马上开始异步执行，并返回一个Observable对象，当调用它的subscribe时，将重新产生结果和通知给订阅者。
3. queue(): 将toObservable()产生的原始Observable通过toBlocking()方法转换成BlockingObservable对象，并调用它的toFuture()方法返回异步的Future对象
4. execute(): 在queue()产生异步结果Future对象之后，通过调用get()方法阻塞并等待结果的返回。