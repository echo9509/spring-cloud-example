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

# 断路器原理
断路器在HystrixCommand和HystrixObservableCommand执行过程中起到至关重要的作用。查看一下核心组件HystrixCircuitBreaker
```java
package com.netflix.hystrix;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;
import rx.Subscriber;
import rx.Subscription;

public interface HystrixCircuitBreaker {

    boolean allowRequest();
    
    boolean isOpen();

    void markSuccess();

    void markNonSuccess();

    boolean attemptExecution();

    class Factory {
        // String is HystrixCommandKey.name() (we can't use HystrixCommandKey directly as we can't guarantee it implements hashcode/equals correctly)
        private static ConcurrentHashMap<String, HystrixCircuitBreaker> circuitBreakersByCommand = new ConcurrentHashMap<String, HystrixCircuitBreaker>();
    }


    class HystrixCircuitBreakerImpl implements HystrixCircuitBreaker {
    }

    static class NoOpCircuitBreaker implements HystrixCircuitBreaker {
    }

}
```
下面先看一下该接口的抽象方法：
1. allowRequest(): 每个Hystrix命令的请求都通过它判断是否被执行(已经不再使用,使用attemptExecution()方法进行判断)
2. attemptExecution(): 每个Hystrix命令的请求都通过它判断是否被执行
3. isOpen(): 返回当前断路器是否打开
4. markSuccess(): 用来关闭断路器
5. markNonSuccess: 用来打开断路器

下面看一下该接口中的类:
1. Factory: 维护了一个Hystrix命令和HystrixCircuitBreaker的关系的集合ConcurrentHashMap<String, HystrixCircuitBreaker> circuitBreakersByCommand。其中key通过HystrixCommandKey来定义，每一个Hystrix命令都需要有一个Key来标识，同时根据这个Key可以找到对应的断路器实例。
2. NoOpCircuitBreaker: 一个啥都不做的断路器，它允许所有请求通过，并且断路器始终处于闭合状态
3. HystrixCircuitBreakerImpl:断路器的另一个实现类。

## HystrixCircuitBreakerImpl介绍
在该类中定义了断路器的五个核心对象:
1. HystrixCommandProperties properties:断路器对应实例的属性集合对象
2. HystrixCommandMetrics metrics:用来让HystrixCommand记录各类度量指标的对象
3. AtomicReference<Status> status: 用来记录断路器的状态，默认是关闭状态
4. AtomicLong circuitOpened:断路器打开的时间戳，默认-1，表示断路器未打开
5. AtomicReference<Subscription> activeSubscription: 记录HystrixCommand


### isOpen方法介绍
```java
        @Override
        public boolean isOpen() {
            if (properties.circuitBreakerForceOpen().get()) {
                return true;
            }
            if (properties.circuitBreakerForceClosed().get()) {
                return false;
            }
            return circuitOpened.get() >= 0;
        }
```
用来判断断路器是否打开或关闭。主要步骤有：
1. 如果断路器强制打开，返回true
2. 如果断路器强制关闭，返回false
3. 判断circuitOpened的值，如果大于等于0，返回true, 否则返回false

### attemptExecution方法介绍
```java
        private boolean isAfterSleepWindow() {
            final long circuitOpenTime = circuitOpened.get();
            final long currentTime = System.currentTimeMillis();
            final long sleepWindowTime = properties.circuitBreakerSleepWindowInMilliseconds().get();
            return currentTime > circuitOpenTime + sleepWindowTime;
        }

        @Override
        public boolean attemptExecution() {
            if (properties.circuitBreakerForceOpen().get()) {
                return false;
            }
            if (properties.circuitBreakerForceClosed().get()) {
                return true;
            }
            if (circuitOpened.get() == -1) {
                return true;
            } else {
                if (isAfterSleepWindow()) {
                    if (status.compareAndSet(Status.OPEN, Status.HALF_OPEN)) {
                        //only the first request after sleep window should execute
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
```
该方法的主要逻辑有以下几步:
1. 如果断路器强制打开，返回false，不允许放过请求
2. 如果断路器强制关闭，返回true，允许放过请求
3. 如果断路器是关闭状态，返回true，允许放过请求
4. 判断当前时间是否超过断路器打开的时间加上滑动窗口的时间，如果没有超过，返回false，不允许放过请求
5. 如果没有超过，如果断路器是打开状态，并且设置断路器状态为半开状态成功时，返回true，允许放过请求
6. 如果失败，则返回false，不允许放过请求

### markSuccess方法
```java
        @Override
        public void markSuccess() {
            if (status.compareAndSet(Status.HALF_OPEN, Status.CLOSED)) {
                //This thread wins the race to close the circuit - it resets the stream to start it over from 0
                metrics.resetStream();
                Subscription previousSubscription = activeSubscription.get();
                if (previousSubscription != null) {
                    previousSubscription.unsubscribe();
                }
                Subscription newSubscription = subscribeToStream();
                activeSubscription.set(newSubscription);
                circuitOpened.set(-1L);
            }
        }
```
该方法主要用来关闭断路器，主要逻辑有以下几步：
1. 如果断路器状态是半开并且成功设置为关闭状态时，执行以下步骤。
2. 重置度量指标对象
3. 取消之前的订阅，发起新的订阅
4. 设置断路器的打开时间为-1

![断路器详细执行逻辑](https://s1.ax1x.com/2018/08/29/PX0QaD.png)

# 依赖隔离
Hystrix使用舱壁模式实现线程池的隔离，它会为每一个依赖服务创建一个独立的线程池，这样做的好处就是某个依赖服务出现延迟过高的情况，也只是对该服务的调用产生影响，不会拖慢其他的依赖服务。

通过对依赖服务的线程池进行隔离，主要带来以下好处：
1. 应用自身得到完全保护，不会受不可控的依赖服务影响。即便给依赖服务分配的线程池被填满，也不会影响应用自身的其余部分。
2. 有效降低接入新服务的危险。如果新服务接入后运行不稳定或存在问题，完全不会影响应用的其他请求。
3. 当依赖的服务从失效恢复正常后，它的线程池会被清理并且能够马上恢复健康的服务，相比之下，容器级别的清理恢复速度要慢的多。
4. 当依赖的服务出现配置错误的时候，线程池会快速反应出此问题(通过失败次数、延迟、超时、拒绝等指标的增加情况)。同时，我们可以在不影响应用功能的情况下通过实时的动态属性刷新（借助Spring Cloud Config和Spring Cloud Bus）来处理它。
5. 当依赖的服务因实现机制调整等原因造成其性能出现很大变化时，线程池的监控指标信息会反映出这样的变化。同时，我们可以通过实时动态刷新自身应用对依赖服务的阀值进行调整以适应依赖方的改变。
6. 除了上面通过线程池隔离服务发挥的优点以外，每个专有线程都提供了内置的并发实现，可以利用它为同步的依赖服务构建异步访问。

通过对依赖服务的线程池实现隔离，可以让我们的应用更加健壮，不会因为个别依赖服务出现问题而引起非相关服务的异常。同时它让我们的应用变的更加灵活，可以在不停止服务的情况下，配合动态配置刷新实现性能配置上的调整。

线程池的隔离方式可以为我们带来诸多好处，但是会增加系统的负载和开销。因此，Netflix针对线程池中的开销做了相关测试，用结果打消Hystrix实现对性能影响顾虑。

下面的图是官方提供的Hystrix命令性能监控图，该命令以每秒60个请求的速度(QPS)对一个单服务实例进行访问，该服务实例每秒运行的线程数峰值为350个。

![Hystrix命令性能监控图](https://s1.ax1x.com/2018/09/01/Pvh96f.png)

从图中大致可以得出使用线程池隔离与不使用线程池隔离的耗时差异表

比较情况|未使用线程池隔离|使用了线程池隔离|耗时差异
---|---|---|---
中位数|2ms|2ms|2ms
90百分位|5ms|8ms|3ms
99百分位|28ms|37ms|9ms

在99%情况下，使用线程池的延迟有9ms，大多数需求这样的消耗都可以忽略不计，但是对于延迟本就非常小的请求或者系统要求低延迟的应用来说就非常昂贵。因此Hystrix还提供了另外的解决方案：**信号量**

在Hysrtix中除了可以使用线程池之外，还可以使用信号量来控制单个依赖服务的并发度，信号量的开销远远小于线程池，但是它不能设置超时和实现异步访问。所以只有在依赖服务足够可靠的情况下才使用信号量。

HystrixCommand和HystrixObservableCommand中有两处支持信号量的使用：
1. 命令执行：如果将隔离策略参数execution.isolation.strategy设置为SEMAPHORE，Hystrix会使用信号量替代线程池来控制依赖服务的并发。
2. 降级逻辑：当Hysrtix尝试降级逻辑时，它会在调用线程中使用信号量。

信号量的默认值是10，我们可以通过动态刷新配置的方式来控制并发线程的数量。

# 使用详解
主要介绍Hystrix各接口和注解的使用方法。

## 创建请求命令
Hystrix命令就是我们之前所说的HystrixCommand，他用来封装具体的依赖服务调用逻辑。

## 继承方式实现HystrixCommand
首先通过代码实现HystrixCommand
```java
package cn.sh.ribbon.command;

import cn.sh.common.entity.User;
import com.netflix.hystrix.HystrixCommand;
import org.springframework.web.client.RestTemplate;

/**
 * @author sh
 */
public class UserCommand extends HystrixCommand<User> {

    private RestTemplate restTemplate;

    private Long id;

    public UserCommand(Setter setter, RestTemplate restTemplate, Long id) {
        super(setter);
        this.restTemplate = restTemplate;
        this.id = id;
    }
    
    @Override
    protected User run() throws Exception {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

}
```
通过上面实现的UserCommand，我们即可以实现请求的同步执行也可以实现异步执行，相关代码如下：
```java
package cn.sh.ribbon.service;

import cn.sh.common.entity.User;
import cn.sh.ribbon.command.UserCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 第一种使用命令的方式
     * @param id
     * @return
     */
    public User getUserById(Long id) {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("userKey");
        com.netflix.hystrix.HystrixCommand.Setter setter = com.netflix.hystrix.HystrixCommand.Setter.withGroupKey(groupKey);
        UserCommand userCommand = new UserCommand(setter, restTemplate, id);
        // 同步执行获取结果
//        return userCommand.execute();
        // 异步执行获取结果
        Future<User> future = userCommand.queue();
        try {
            return future.get();
        } catch (Exception e) {
            logger.info("获取结果发生异常", e);
        }
        return null;
    }

}
```

## 注解方式使用HystrixCommand
通过HystrixCommand注解可以更优雅的实现Hystrix命令的定义，如下：
```java
package cn.sh.ribbon.service;

import cn.sh.common.entity.User;
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
     * 通过注解方式获取User
     * @param id
     * @return
     */
    @HystrixCommand
    public User findUserById(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }
}
```
上述代码虽然可以优雅的实现Hystrix命令，但是上述获取User的方式只是同步执行的实现，如果需要实现异步执行则需要进行如下改造:
```java
package cn.sh.ribbon.service;

import cn.sh.common.entity.User;
import cn.sh.ribbon.command.UserCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

import java.util.concurrent.Future;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 通过注解方式异步执行获取User
     * @param id
     * @return
     */
    @HystrixCommand
    public Future<User> asyncFindUserFutureById(Long id) {
        return new AsyncResult<User>() {
            @Override
            public User invoke() {
                return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
            }
        };
    }
}
```


## 响应执行
除了传统的同步执行与异步执行之外，我们还可以将HystrixCommand通过Observable来实现响应式执行方式。通过调用observe()和toObservable()可以返回Observable对象， 如下:
```java
Observable<User> observe = userCommand.observe();
Observable<User> observe = userCommand.toObservable();
```
前者返回的是一个Hot Observable，该命令会在observe调用的时候立即执行，当Observable每次被订阅的时候都会重放它的行为。

后者返回的是一个Cold Observable，toObservable()执行之后，命令不会被立即执行，只有当所有订阅者都订阅他之后才会执行。

HystrixCommand具备了observe()和toObservable()的功能，但是它的实现有一定的局限性，它返回的Observable只能发射一次数据，所以Hystrix提供了另外的一个特殊命令封装HysrtixObservableCommand，通过命令可以发射多次的Observable

## 响应执行自定义命令
相关代码如下:
```java
package cn.sh.ribbon.command;

import cn.sh.common.entity.User;
import com.netflix.hystrix.HystrixObservableCommand;
import org.springframework.web.client.RestTemplate;
import rx.Observable;

/**
 * @author sh
 */
public class UserObservableCommand extends HystrixObservableCommand<User> {

    private RestTemplate restTemplate;

    private Long id;

    public UserObservableCommand (Setter setter, RestTemplate restTemplate, Long id) {
        super(setter);
        this.restTemplate = restTemplate;
        this.id = id;
    }

    @Override
    protected Observable<User> construct() {
        return Observable.create(subscriber -> {
            if (!subscriber.isUnsubscribed()) {
                User user = restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
                subscriber.onNext(user);
                subscriber.onCompleted();
            }
        });
    }
}
```
## 响应执行使用注解@HystrixCommand
相关代码如下：
```java
package cn.sh.ribbon.service;

import cn.sh.common.entity.User;
import cn.sh.ribbon.command.UserCommand;
import cn.sh.ribbon.command.UserObservableCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 使用注解实现响应式命令
     * @param id
     * @return
     */
    @HystrixCommand
    public Observable<User> observableGetUserId(Long id) {
        return Observable.create(subscriber -> {
            if (!subscriber.isUnsubscribed()) {
                User user = restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
                subscriber.onNext(user);
                subscriber.onCompleted();
            }
        });
    }

}
```
使用@HystrixCommand注解实现响应式命令，可以通过observableExecutionMode参数来控制是使用observe()还是toObservable()的执行方式。该参数有下面两种设置方式：
1. @HystrixCommand(observableExecutionMode = ObservableExecutionMode.EAGER): EAGER是该参数的模式值，表示使用observe()执行方式。
2. @HystrixCommand(observableExecutionMode = ObservableExecutionMode.LAZY): 表示使用toObservable()执行方式。

# 定义服务降级
fallback是Hystrix命令执行失败时使用的后备方法，用来实现服务的降级处理逻辑。在HystrixCommand中可以通过重载getFallback()方法来实现服务降级逻辑，Hystrix会在run()执行过程中出现错误、超时、线程池拒绝、断路器熔断等情况时，执行getFallback()方法内的逻辑。

## 继承HystrixCommand实现getFallback
```java
package cn.sh.ribbon.command;

import cn.sh.common.entity.User;
import com.netflix.hystrix.HystrixCommand;
import org.springframework.web.client.RestTemplate;

/**
 * @author sh
 */
public class UserCommand extends HystrixCommand<User> {

    private RestTemplate restTemplate;

    private Long id;

    public UserCommand(Setter setter, RestTemplate restTemplate, Long id) {
        super(setter);
        this.restTemplate = restTemplate;
        this.id = id;
    }

    @Override
    protected User run() throws Exception {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    @Override
    protected User getFallback() {
        User user = new User();
        user.setId(1L);
        user.setName("sh");
        return user;
    }
}
```
在HystrixObservableCommand实现的Hystrix命令中，我们可以通过重载resumeWithFallback来实现降级逻辑。该方法会返回一个Observable对象，当命令执行失败的时候，Hystrix会将Observable中的结果通知给所有的订阅者。

## 使用注解方式执行服务降级
```java
package cn.sh.ribbon.service;

import cn.sh.common.entity.User;
import cn.sh.ribbon.command.UserCommand;
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
     * 通过注解方式同步执行获取User
     * 注解方式服务降级
     * @param id
     * @return
     */
    @HystrixCommand(fallbackMethod = "getDefaultUser")
    public User findUserById(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    private User getDefaultUser() {
        User user = new User();
        user.setId(2L);
        user.setName("sh");
        return user;
    }

}
```
在使用注解来定义服务降级时，需要将具体的Hystrix命令与fallback实现函数定义在同一个类中，并且fallbacMethod的值必须与实现fallback方法的名字相同。

由于必须定义在一个类中，所以对于fallback的访问修饰符没有特定的要求，定义为private、protected、public均可。

上述代码中若getDefaultUser方法实现不是一个稳定逻辑，那我们也可以为它添加@HystrixCommand注解来生成Hystrix命令，同时使用fallbackMethod来指定服务降级逻辑。

在实际使用时，有一些情况可以不去实现降级逻辑，如：
1. 执行写操作的命令: 当Hystrix命令是用来执行写操作而不是返回一些信息的时候，实现服务降级逻辑的意义不是很大。当写入失败时，我们通常只需要通知调用者即可。
2. 执行批处理或离线计算命令: 当Hystrix命令是用来执行批处理程序生成一份报告或是进行任何类型的离线计算时，通常这些操作只需要将错误传播给调用者，然后让调用者稍后重试而不是发送给调用者一个静默的降级处理响应。

# 异常处理
## 异常传播
在HystrixCommand实现的run()方法中抛出异常时，除了HystrixBadRequestException之外，其他异常均会被Hystrix认为命令执行失败并触发服务降级的处理逻辑，所以当需要在命令执行中抛出不触发服务降级的异常时来选择它。

在使用注解配置实现Hystrix命令时，可以忽略指定的异常类型，只需要通过设置@HystrixCommand注解的ignoreExceptions参数，如下:
```java
    @HystrixCommand(fallbackMethod = "getDefaultUser", ignoreExceptions = NullPointerException.class)
    public User findUserById(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }
```
当上述方法抛出NullPointerException的异常时，不会触发后续的fallback逻辑。

## 异常获取
在传统的继承实现Hystrix命令时，可以在getFallback()方法中通过getExecutionException()方法来获取具体的异常，然后通过判断来进入不同的处理逻辑。

在注解配置方式中，只需要在fallback实现方法的参数中增加Throwable e对象的定义，这样在方法内部就可以获取触发服务降级的具体异常内容。

# 命令名称、分组和线程池划分
## 继承实现自定义命令
```java
    public UserCommand(RestTemplate restTemplate, Long id) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GroupName")).andCommandKey(HystrixCommandKey.Factory.asKey("CommandName")));
        this.restTemplate = restTemplate;
        this.id = id;
    }
```
从上面的代码中可以看出，我们并没有直接设置命令名称，而是先调用了withGroupKey来设置命令组名，然后才通过调用andCommandKey来设置命令名。

在Setter中只有withGroupKey静态函数可以创建Setter的实例，因此GroupKey是每个Setter必须的参数，而CommandKey则是一个可选参数。

通过设置命令组，Hystrix会根据组来组织和统计命令的告警、仪表盘等信息。除了上述可以统计信息之外，Hystrix命令默认的线程划分也是根据命令分组来实现的。默认情况下，Hystrix会让相同组名的命令使用同一个线程池，所以我们需要在创建Hystrix命令时为其指定命令组名来实现默认的线程池划分。

Hystrix还提供HystrixThreadPoolKey来对线程池进行设置，通过它可以实现更细粒度的线程池划分。
```java
    public UserCommand(RestTemplate restTemplate, Long id) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GroupName"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("ThreadPoolKey")));
        this.restTemplate = restTemplate;
        this.id = id;
    }
```
在没有指定HystrixThreadPoolKey的情况下，会使用命令组的方式来划分线程池。通常情况下，我们**尽量使用HystrixThreadPoolKey来指定线程池的划分**。因为多个不同的命令可能从业务逻辑上来看属于同一个组，但是往往从实现本身上需要跟其他命令来进行隔离。

## @HystrixCommand注解
使用注解时只需要设置注解的commandKey、groupKey以及threadPoolKey属性即可，他分别表示了命令名称、分组以及线程池划分。
```java
    @HystrixCommand(fallbackMethod = "getDefaultUser", ignoreExceptions = NullPointerException.class,
            commandKey = "findUserById", groupKey = "UserGroup", threadPoolKey = "findUserByIdThread")
    public User findUserById(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }
```

# 请求缓存
在高并发的场景之下，Hystrix中提供了请求缓存的功能，可以方便的开启和使用请求缓存来优化系统，达到减轻高并发时的请求线程消耗、降低请求响应时间的效果。

## 开启请求缓存功能
Hystrix请求缓存的使用非常简单，只需要在实现HystrixCommand或HystrixObservableCommand时，通过重载getCacheKey()方法来开启请求缓存。
```java
public class UserCommand extends HystrixCommand<User> {

    private RestTemplate restTemplate;

    private Long id;

    public UserCommand(RestTemplate restTemplate, Long id) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GroupName"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("ThreadPoolKey")));
        this.restTemplate = restTemplate;
        this.id = id;
    }

    @Override
    protected User run() throws Exception {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    @Override
    protected String getCacheKey() {
        return String.valueOf(id);
    }

    @Override
    protected User getFallback() {
        User user = new User();
        user.setId(1L);
        user.setName("sh");
        return user;
    }
}
```
在上面的示例中，通过getCacheKey方法中返回的请求缓存key值，就能让该请求命令具备缓存功能。当不同的外部请求处理逻辑调用了同一个依赖服务时，Hystrix会根据getCacheKey方法返回的值来区分是否是重复的请求，如果它们的cacheKey相同，那么依赖服务只会在第一个请求到达时被真实的调用一次，另外一个请求则是直接从请求缓存中返回结果。

开启缓存主要有以下好处:
1. 减少重复的请求数，降低依赖服务的并发度
2. 在同一用户请求的上下文中，相同依赖服务的返回数据始终保持一致
3. 请求缓存在run()和construct()执行之前生效，所以可以有效减少不必要的线程开销

## 清理失效缓存功能
使用请求缓存时，如果是只读操作，不需要考虑缓存内容是否正确的问题，但是如果请求命令中还有更新数据的写操作，那么缓存中的数据就需要我们在进行写操作时进行及时处理，以防止读操作的请求命令获取到了失效的数据。

在Hystrix中，可以通过HystrixRequestCache.clear()方法来进行缓存的清理
```java
    public static void flushCache(Long id) {
        //刷新缓存，根据id进行清理
        HystrixRequestCache.getInstance(GETTER_KEY,
                HystrixConcurrencyStrategyDefault.getInstance()).clear(String.valueOf(id));
    }
```
在上面的代码中，增加了一个静态方法flushCache，该方法通过HystrixRequestCache.getInstance(GETTER_KEY,HystrixConcurrencyStrategyDefault.getInstance())方法从默认的Hystrix并发策略中根据GETTER_KEY获取到该命令的请求缓存对象HystrixRequestCache的实例，然后再调用该请求缓存对象实例的clear方法，对Key为更新User的id值的缓存内容进行清理。

## 工作原理
由于getCacheKey方法在AbstractCommand抽象命令类中实现，所以我们从这个抽象类看起。

在AbstractCommand的源码片段中，可以看到，getCacheKey方法默认返回的是null，并且从isRequestCachingEnabled方法的逻辑中我们可以知道，如果不重写getCacheKey方法，让它返回一个非null值，那么缓存功能是不会开启的；同时请求命令的缓存开启属性也需要设置为true才能开启(该属性默认值为true,所以通常用该属性来控制请求缓存功能的强制关闭)。

从命令异步执行的核心方法toObservable()中可以看出与缓存的主要步骤：**尝试获取请求缓存**以及将**请求结果加入缓存**。

1. 尝试获取请求缓存：Hystrix命令在执行前会根据之前提到的isRequestCacheingEnabled方法来判断当前命令是否启用了缓存。如果开启了请求缓存并且重写了getCacheKey方法，并返回了一个非null的缓存Key值，那么就使用getCacheKey返回的Key值去调用HystrixRequestCache中的get(String cacheKey)来获取缓存的HystrixCacheObservable对象。

2. 将请求结果加入缓存：在执行命令缓存操作前，我们可以看到一个已经获得一个延迟执行命令结果HystrixObservable。接下来与尝试获取请求缓存操作一样，需要判断当前命令是否开启了请求缓存功能，如果开启了请求缓存并且getCacheKey返回了具体的Key值，就将hystrixObservable对象包装成请求缓存结果HystrixCachedObservable的实例对象toCache，然后将其放入当前命令的缓存对象中。从调用的putIfAbsent中，大致可以猜到在请求缓存对象HystrixRequestCache中维护了一个线程安全的Map来保护请求缓存的响应，所以在调用putIfAbsent将包装的请求缓存放入缓存对象后，对其返回结果fromCache进行了判断，如果其不为null，说明当前缓存Key的请求命令缓存命中，直接对toCache执行取消订阅操作(即不再发起真实请求)，同时调用缓存命令的处理方法handleRequestCacheHitAndEmitValues来执行缓存命中的结果获取。如果返回的fromCache为null，说明缓存没有命中，则将当前结果toCache缓存起来，并将其转换成Observable返回给调用者使用。

## 使用注解实现请求缓存
注解 | 描述 | 属性
--- | --- | ---
@CacheResult | 该注解用来标记请求命令返回的结果应该被缓存，他必须与@HystrixCommand注解结合使用 | cacheKeyMethod
@CacheRemove | 该注解让请求命令缓存失效，失效的缓存根据定义的Key决定 | commandKey,cacheKeyMethod
@CacheKey | 该注解用来在请求命令的参数上标记，使其作为缓存的Key值，如果没有标注则会使用所有参数。如果同时还使用了@CacheResult和@CacheRemove注解的cacheKeyMethod方法指定缓存Key的生成，那么该注解将不会起作用 | value

### 设置请求缓存
只需要在@HystrixCommand注解的方法上添加@CacheResult注解就可以为请求命令开启缓存功能，而它的缓存Key值会使用所有的参数。
```java
    @HystrixCommand(fallbackMethod = "getDefaultUser", ignoreExceptions = NullPointerException.class,
            commandKey = "findUserById", groupKey = "UserGroup", threadPoolKey = "findUserByIdThread")
    @CacheResult
    public User findUserById(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }
``` 
### 定义缓存Key
当使用注解来定义请求缓存时，若要为请求命令指定具体的缓存Key生成规则，可以使用@CacheResult和@CacheRemove注解的cacheKeyMethod属性指定具体的生成函数，也可以通过@CacheKey注解在方法参数中指定用于组装缓存Key的元素。
```java
    @HystrixCommand(fallbackMethod = "getDefaultUser", ignoreExceptions = NullPointerException.class,
            commandKey = "findUserById", groupKey = "UserGroup", threadPoolKey = "findUserByIdThread")
    @CacheResult(cacheKeyMethod = "findUserIdCacheKey")
    public User findUserById(@CacheKey("id") Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    private Long findUserIdCacheKey(Long id) {
        return id;
    }
```
**@CacheKey注解除了可以指定方法参数作为缓存Key以外，还允许访问参数对象的内部属性作为缓存Key。比如指定User对象的id属性作为缓存Key**

### 缓存清理
之前的示例中我们通过@CacheResult注解将请求结果置入Hystrix的请求缓存中。如果有update操作，我们需要让缓存失效，此时就需要通过@CacheRemove注解实现失效缓存的清理。

**@CacheRemove必须指定commandKey属性，用来指明需要使用请求缓存的请求命令。**
```java
    @HystrixCommand(fallbackMethod = "getDefaultUser", ignoreExceptions = NullPointerException.class,
            commandKey = "findUserById", groupKey = "UserGroup", threadPoolKey = "findUserByIdThread")
    @CacheResult
    public User findUserById(@CacheKey("id") Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    private Long findUserIdCacheKey(Long id) {
        return id;
    }


    @HystrixCommand
    @CacheRemove(commandKey = "findUserById")
    public void updateUser(@CacheKey("id") User user) {
        restTemplate.postForObject("http://USER-SERVICE/user", user, User.class);
    }
```

## 请求合并
微服务架构中的依赖通过远程调用实现，为了优化网络通信的耗时和依赖服务线程池的资源，避免出现排队等待与响应延迟的情况。Hystrix提供了**HystrixCollapser**来实现请求的合并，以减少通信消耗和线程数的占用。

HystrixCollapser实现了在HystrixCommand之前放置一个合并处理器，将处于一个很短的时间窗(默认10ms)内对同一依赖服务的多个请求进行整合并以批量方式发起请求的功能（服务提供方也需要提供响应的批量实现接口）。
通过HystrixCollapser的封装，开发者不需要关注线程合并的细节过程，只需关注批量化服务和处理。

从HystrixCollapser抽象类的定义中可以看到，它指定了三个不同的类型
1. BatchReturnType：合并后批量请求的返回类型
2. ResponseType：单个请求返回的类型
3. RequestArgumentType：请求参数类型

HystrixCollapser下的三个抽象方法：
1. RequestArgumentType getRequestArgument(): 该函数用来定义获取请求参数的方法。
2. HystrixCommand<BatchReturnType> createCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests): 合并请求产生批量命令的具体实现方法
3. mapResponseToRequests(BatchReturnType batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests): 批量命令结果返回后的处理，这里需要实现将批量结果拆分并传递给合并前的各个原子请求命令的逻辑。

### 继承方式理解实现请求合并
1、在USER-SERVICE中提供获取User的两个接口

1. /users/{id}: 根据id返回User对象的GET请求接口
2. /users?ids={ids}: 根据ids返回User对象列表的GET接口，其中ids为以,分隔的id集合

2、在服务消费端RIBBON-CONSUMER中，提供一个UserService，用来使用RestTemplate进行远程访问
```java
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public User find(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    @Override
    public List<User> findAll(List<Long> idList) {
        return restTemplate.getForObject("http://USER-SERVICE/users?ids={1}", List.class, StringUtils.join(idList,","));
    }
}
```

3、实现将短时间内多个获取单一User对象的请求命令进行合并

&emsp; **1、第一步，为请求合并的实现准备一个批量请求命令的实现**
```java
public class UserBatchCommand extends HystrixCommand<List<User>> {

    private UserService userService;

    private List<Long> idList;

    protected UserBatchCommand(UserService userService, List<Long> idList) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("userBatchGroup")));
        this.userService = userService;
        this.idList = idList;
    }

    @Override
    protected List<User> run() throws Exception {
        return userService.findAll(idList);
    }
}
```

&emsp;**2、通过继承HystrixCollapser实现请求合并器**
```java
public class UserCollapseCommand extends HystrixCollapser<List<User>, User, Long> {

    private UserService userService;

    private Long userId;

    public UserCollapseCommand(UserService userService, Long userId) {
        super(Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey("userCollapse"))
                .andCollapserPropertiesDefaults(HystrixCollapserProperties.Setter().withTimerDelayInMilliseconds(100)));
        this.userService = userService;
        this.userId = userId;
    }

    @Override
    public Long getRequestArgument() {
        return userId;
    }

    @Override
    protected HystrixCommand<List<User>> createCommand(Collection<CollapsedRequest<User, Long>> collapsedRequests) {
        List<Long> userIdList = new ArrayList<>(collapsedRequests.size());
        userIdList.addAll(collapsedRequests.stream().map(CollapsedRequest::getArgument).collect(Collectors.toList()));
        return new UserBatchCommand(userService, userIdList);
    }

    @Override
    protected void mapResponseToRequests(List<User> batchResponse, Collection<CollapsedRequest<User, Long>> collapsedRequests) {
        int count = 0;
        for (CollapsedRequest<User, Long> collapsedRequest : collapsedRequests) {
            User user = batchResponse.get(count++);
            collapsedRequest.setResponse(user);
        }
    }
    
}
```
在上面的构造函数中，我们为请求合并器设置了时间延迟属性，合并器会在该时间窗内收集获取单个User的请求并在时间窗结束时进行合并组装成单个批量要求。

getRequestArgument方法返回给定的单个请求参数userId，createCommand和mapResponseToRequests时请求合并器的两个核心

1. createCommand: 该方法的collapsedRequests参数中保存了延迟时间窗中收集到的所有获取单个User的请求。通过获取这些请求的参数来组织上面我们准备的批量请求命令的UserBatchCommand实例。
2. mapResponseToRequests: 在批量请求命令UserBatchCommand实例被触发执行完成之后，该方法开始执行，其中batchResponse参数保存了createCommand中组织的批量请求命令的返回结果，而collapsedRequests参数则代表了每个被合并的请求。在这里我们通过遍历批量结果batchResponse对象，为collasperRequests中每个合并前的单个请求设置返回结果，以此完成批量结果到单个请求结果的转换。

### 使用注解实现请求合并器
```java
    @HystrixCollapser(batchMethod = "findAll",
            collapserProperties = {@HystrixProperty(name ="timerDelayInMilliseconds", value = "100")})
    public User find(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    @HystrixCommand
    public List<User> findAll(List<Long> idList) {
        return restTemplate.getForObject("http://USER-SERVICE/users?ids={1}", List.class, StringUtils.join(idList,","));
    }
```
在查询单个User的请求命令上通过@HystrixCollapser注解创建了合并请求，并通过batchMethod属性指定了批量请求的实现方法为findAll，通过collapserProperties为请求合并器设置相关属性。
这里使用@HystrixProperty(name ="timerDelayInMilliseconds", value = "100")将合并时间窗设置为100ms。

### 请求合并缺点
请求合并虽然可以减少请求的数量以缓解依赖服务线程池的压力，但是用于请求合并的延迟时间窗会使得依赖服务的请求延迟增高。

在决定是否使用请求合并器主要参考以下两点：
1. 请求命令本身的延迟：如果依赖服务的请求命令本身是一个高延迟的命令，那么可以使用请求合并器。
2. 延迟时间窗内的并发量：如果一个时间窗内只有1-2个请求，那么这样额依赖服务不适合使用请求合并器。如果一个时间窗内具有很高的并发量，并且服务提供方也实现了批量处理接口，那么使用请求合并器可以有效减少网络连接数量并极大提升系统吞吐量。

# 属性详解
1. 通过继承的方式，可以使用Setter对象来对请求请求命令的属性进行设置
2. 当通过注解时，只需要使用@HystrixCommand中的commandProperties

属性的设置存在以下优先级区别（优先级由低到高）：
1. 全局设置默认值：如果没有设置下面三个级别的属性，那么这个属性就是默认值。
2. 全局配置属性：通过在配置文件中定义全局属性值，在应用启动时或在与Spring Cloud Config和Spring Cloud Bus实现的动态刷新配置功能配合下，可以实现对全局默认值的覆盖以及在运行期对全局默认值的动态调整。
3. 实例默认值：通过代码为实例定义默认值。通过代码的方式为实例设置属性值来覆盖默认的全局配置。
4. 实例配置属性：通过配置文件来为指定的实例进行属性配置，以覆盖前面的三个默认值。它也可用Spring Cloud Config和Spring Cloud Bus实现的动态刷新配置的功能实现对具体实例配置的动态调整。

## Command属性
Command属性主要用来控制HystrixCommand命令的行为。

### execution配置
execution配置控制的是HystrixCommand.run()的执行。

**execution.isolation.strategy**：该属性用来设置HystrixCommand.run()执行的隔离策略，有如下两个选项：
1. THREAD：通过线程池的隔离策略。它在独立的线程上执行，并且它的并发限制受线程池中线程数量的限制
2. SEMPAHORE：通过信号量隔离的策略。它在调用线程上执行，并且它的并发限制受信号量计数的限制。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | THREAD
全局配置属性 | hystrix.command.default.execution.isolation.strategy
实例默认值 | 通过HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)设置，也可以通过@HystrixProperty(name="execution.isolation.strategy",value="THREAD")注解设置
实例配置属性 | hystrix.command.HystrixCommandKey.execution.isolation.strategy

**execution.isolation.thread.timeoutInMilliseconds**：该属性用来配置HystrixCommand执行的超时时间，单位ms。当HystrixCommand执行时间超过该配置之后，Hystrix会将该执行命令标记为TIMEOUT并进入服务降级处理逻辑。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 1000ms
全局配置属性 | hystrix.command.default.execution.isolation.thread.timeoutInMillseconds
实例默认值 | 通过HystrixCommandProperties.Setter().withExecutionTimeoutInMillseconds(int value)设置，也可通过@HystrixProperty(name="execution.isolation.thread.timeoutInMilliseconds", value="2000")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.execution.isolation.thread.timeoutInMilliseconds

**execution.timeout.enabled**：该属性用来配置HystrixCommand.run()的执行是否启用超时时间。默认为true，如果设置为false，那么execution.isolation.thread.timeoutInMilliseconds属性的配置将不再起作用。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | true
全局配置属性 | hystrix.command.default.execution.timeout.enabled
实例默认值 | 通过HystrixCommandProperties.Setter().withExecutionTimeoutEnabled(boolean value)设置，也可以通过@HystrixProperty(name="execution.timeout.enabled", value="false")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.execution.timeout.enabled

**execution.isolation.thread.interruptOnTimeout**：该属性用来配置当HystrixCommand.run()执行超时的时候是否要将它中断。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | true
全局配置属性 | hystrix.command.default.execution.isolation.thread.interruptOnTimeout
实例默认值 | 通过HystrixCommandProperties.Setter().witheExecutionIsolationThreadInterruptOnTimeout(boolean value)设置，也可通过@HystrixProperty(name="execution.isolation.thread.interruptOnTimeout", value="false")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.execution.isolation.threa.interruptOnTimeout

**execution.isolation.thread.interruptOnCancel**：该属性用来配置当HystrixCommand.run()执行被取消的时候是否要将它中断

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | true
全局配置属性 | hystrix.command.default.execution.isolation.thread.interruptOnCancel
实例默认值 | 通过HystrixCommandProperties.Setter().withExecutionIsolationThreadInterruptOnCancel(boolean value)设置，也可通过@HystrixProperty(name="execution.isolation.thread.interruptOnCancel", value="false")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.execution.isolation.thread.interruotOnCancel

**execution.isolation.semaphore.maxConcurrentRequests**：当HystrixCommand的隔离策略使用信号量的时候，该属性用来配置信号量的大小(并发请求数)。当最大并发请求数达到该设置值时，后续的请求将会被拒绝。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 10
全局配置属性 | hystrix.command.default.execution.isolation.semaphore.maxConcurrentRequests
实例默认值 | 通过HystrixCommandProperties.Setter().withExecutionIsolationSemaphoreMaxConcurrentRequests(int value)设置，也可通过@HystrixProperty(name="execution.isolation.semaphore.maxConcurrentRequests", value="2")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.execution.isolation.semaphore.maxConcurrentRequests

### fallback配置
用来控制HystrixCommand.getFallback()的执行。这些属性同时适用于线程池的信号量的隔离策略。

**fallback.isolation.semaphore.maxConcurrentRequests**：该属性用来设置从调用线程中允许HystrixCommand.getFallback()执行的最大并发请求数。当达到最大并发请求数时，后续的请求将会被拒绝并抛出异常。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 10
全局配置属性 | hystrix.command.default.fallback.isolation.semaphore.maxConcurrentRequests
实例默认值 | 通过HystrixCommandProperties.Setter().withFallbackIsolationSemaphoreConcurrentRequests(int value)设置，也可以通过@HystrixProperty(name="fallback.isolation.semaphore.maxConcurrentRequests", value="20")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.fallback.isolation.semaphore.maxConcurrentReuqsts

**fallback.enabled**：该属性用来设置服务降级策略是否启用，如果设置为false，那么当请求失败或者拒绝发生时，将不会调用HystrixCommand.getFallBack()来执行服务降级逻辑

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | true
全局配置属性 | hystrix.command.default.fallback.enabled
实例默认值 | 通过HysrtixCommandProperties.Setter().withFallbackEnabled(boolean value)设置，也可通过@HystrixProperty(name="fallback.enabled", value="false")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.fallback.enabled

### circuitBreaker配置
主要用来控制HystrixCircuitBreaker的行为。

**circuitBreaker.enabled**：该属性用来确定当服务请求命令失败时，是否使用断路器来跟踪其健康指标和熔断请求。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | true
全局配置属性 | hystrix.command.default.circuitBreaker.enabled
实例默认值 | 通过HystrixCommandProperties.Setter().withCircuitBreakerEnabled(boolean value)设置，也可通过@HystrixProperty(name="circuitBreaker.enabled", value="false")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.circuitBreaker.enabled

**circuitBreaker.requestVolumeThreshold**：该属性用来设置在滚动时间窗(默认10s)中，断路器熔断的最小请求数(失败的)。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 20
全局配置属性 | hystrix.command.default.circuitBreaker.requestVolumeThreshold
实例默认值 | 通过HystrixCommandProperties.Setter().withCircuitBreakerRequestVolumeThreshold(int value)设置，也可通过@HystrixProperty(name="circuitBreaker.requestVolumeThreshold", value="30")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.circuitBreaker.requestVolumeThreshold

**circuitBreaker.sleepWindowInMilliseconds**：该属性用来设置当断路器打开之后的休眠时间窗。休眠时间窗结束后，会将断路器置为半开状态，尝试熔断的请求命令，如果依然失败就将断路器继续设置为打开状态，如果成功就设置为关闭状态。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 5000
全局配置属性 | hystrix.command.default.circuitBreaker.sleepWindowInMilliseconds
实例默认值 | 通过HystrixCommandProperties.Setter().withCircuitBreakerSleepWindowInMilliseconds(int value)设置，也可通过@HystrixProperty(name="circuitBreaker.sleepWindowInMilliseconds", value="3000")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.circuitBreaker.sleepWindowInMilliseconds

**circuitBreaker.errorThresholdPercentage**：该属性用来设置断路器打开的错误百分比条件。表示在滚动时间窗中，请求数量在未超过circuitBreaker.requestVolumeThreshold阀值的前提下，如果错误请求数的百分比超过50，就把断路器设置为打开状态，否则就设置为关闭状态。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 50
全局配置属性 | hystrix.command.default.circuitBreaker.errorThresholdPercentage
实例默认值 | 通过HystrixCommandProperties.Setter().withCircuitBreakerErrorThresholdPercentage(int value)设置，也可通过@HystrixProperty(name="circuitBreaker.errorThresholdPercentage", value="40")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.circuitBreaker.errorThresholdPercentage

**circuitBreaker.forceOpen**：如果将该属性设置为true，断路器将强制进入打开状态，他会拒绝所有请求。优先级高于circuitBreaker.forceClosed

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | false
全局配置属性 | hystrix.command.default.circuitBreaker.forceOpen
实例默认值 | 通过HystrixCommandProperties.Setter().withCircuitBreakerForceOpen(boolean value)设置，也可通过@HystrixProperty(name="circuitBreaker.forceOpen", value="true")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.circuitBreaker.forceOpen

**circuitBreaker.forceClosed**：如果将该属性设置为true，断路器将强制进入关闭状态，它会接受所有请求。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | false
全局配置属性 | hystrix.command.default.circuitBreaker.forceClosed
实例默认值 | 通过HystrixCommandProperties.Setter().withCircuitBreakerForceClosed(boolean value)设置，也可通过@HystrixProperty(name="circuitBreaker.forceClosed", value="true")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.circuitBreaker.forceClosed

### metrics配置
主要与HystrixCommand和HystrixObservableCommand执行中捕获的指标信息有关。

**metrics.rollingStats.timeInMilliseconds**：该属性用来设置滚动时间窗的长度，单位ms。该时间用于断路器判断健康度时需要收集信息的持续时间。断路器在收集指标信息的时候会根据设置的时间窗长度拆分成多个桶来累计度量值，每个桶记录了一段时间内的采集指标。例如，当采用默认值10000ms时，断路器默认将其拆分为10个桶，每个桶记录1000ms内的指标信息。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 10000
全局配置属性 | hystrix.command.default.metrics.rollingStats.timeInMilliseconds
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsRollingStatisticalWindowInMilliseconds(int value)设置，也可通过@HystrixProperty(name="metrics.rollingStats.timeInMilliseconds", value="20000")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.rollingStats.timeInMilliseconds

**注意该属性从Hystrix 1.4.12版本开始，只有在应用初始化的时候生效，通过动态刷新配置不会产生效果，这样做是为了避免出现运行期监测数据丢失的情况。并且该参数的配置必须能被metrics.rollingStats.numBuckets参数整除，不然抛出异常。**

**metrics.rollingStats.numBuckets**：该属性用来设置滚动时间窗统计指标信息时划分桶的数量

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 10
全局配置属性 | hystrix.command.default.metrics.rollingStats.numBuckets
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsRollingStatisticalWindowBuckets(int value)设置，也可通过@HystrixProperty(name="metrics.rollingStats.numBuckets", value="20")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.rollingStats.numBuckets

**metrics.rollingPercentile.enabled**：该属性用来设置对命令执行的延迟是否使用百分位数来跟踪和计算。如果设置为false，那么所有的概要统计都将返回-1。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | true
全局配置属性 | hystrix.command.default.metrics.rollingPercentile.enabled
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsRollingPercentileEnabled(boolean value)设置，也可通过@HystrixProperty(name="metrics.rollingPercentile.enabled", value="false")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.rollingPercentile.enabled

**metrics.rollingPercentile.timeInMilliseconds**：该属性用来设置百分位统计的滚动窗口的持续时间，单位ms。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 60000
全局配置属性 | hystrix.command.default.metrics.rollingPercentile.timeInMilliseconds
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsRollingPercentileWindowInMilliseconds(int value)设置，也可通过@HystrixProperty(name="metrics.rollingPercentile.timeInMilliseconds", value="50000")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.rollingPercentile.timeInMilliseconds

**注意该属性从Hystrix 1.4.12版本开始，只有在应用初始化的时候生效，通过动态刷新配置不会产生效果，这样做是为了避免出现运行期监测数据丢失的情况。并且该参数的配置必须能被metrics.rollingPercentile.numBuckets参数整除，不然抛出异常。**

**metrics.rollingPercentile.numBuckets**：该属性用来设置百分位统计滚动窗口中使用桶的数量。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 6
全局配置属性 | hystrix.command.default.metrics.rollingPercentile.numBuckets
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsRollingPercentileWindowBuckets(int value)设置，也可通过@HystrixProperty(name="metrics.rollingPercentile.numBuckets", value="6")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.rollingPercentile.numBuckets


**metrics.rollingPercentile.bucketSize**：该属性用来设置在执行过程中每个桶中保留的最大执行次数。如果在滚动时间内发生超过该设定值的执行次数，就从最初的位置开始重写。例如，
将该值设置为100，滚动窗口为10s，若在10s内一个桶中发生了500次执行，那么桶中只保留最后的100次执行的统计。另外，增加该值的大小将会增加内存量的消耗，并增加排序百分位数所需的计算时间。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 100
全局配置属性 | hystrix.command.default.metrics.rollingPercentile.bucketSize
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsRollingPercentileBucketSize(int value)设置，也可通过@HystrixProperty(name="metrics.rollingPercentile.bucketSize", value="120")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.rollingPercentile.bucketSize

**注意该属性从Hystrix 1.4.12版本开始，只有在应用初始化的时候生效，通过动态刷新配置不会产生效果，这样做是为了避免出现运行期监测数据丢失的情况。**

**metrics.healthSnapshot.intervalInMilliseconds**：该属性用来设置采集影响断路器状态的健康快照(请求的成功、错误百分比)的间隔等待时间。

属性级别 | 默认值、配置方式、配置属性
--- | ---
全局默认值 | 500
全局配置属性 | hystrix.command.default.metrics.healthSnapshot.intervalInMilliseconds
实例默认值 | 通过HystrixCommandProperties.Setter().withMetricsHealthSnapshotIntervalInMilliseconds(int value)设置，也可通过@HystrixProperty(name="metrics.healthSnapshot.intervalInMilliseconds", value="600")注解来设置
实例配置属性 | hystrix.command.HystrixCommandKey.metrics.healthSnapshot.intervalInMilliseconds