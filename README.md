Anemones 
==
[![Travis Build Status](https://travis-ci.org/csdbianhua/anemones.svg?branch=master)](https://travis-ci.org/csdbianhua/anemones)
[![Licence](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/csdbianhua/anemones/blob/master/LICENSE)
[![codecov](https://codecov.io/gh/csdbianhua/anemones/branch/master/graph/badge.svg)](https://codecov.io/gh/csdbianhua/anemones)

Anemones是一个以redis为存储源的简单的后台任务处理库。

创造一些后台任务提交给Anemones，它会根据主题把这些任务放到不同的队列中，然后分发到不同的实例上处理。

## 快速上手

`git clone git@github.com:csdbianhua/anemones.git`

`mvn clean install -Dmaven.test.skip=true`

### Maven

``` xml
<dependency>
    <groupId>anemones</groupId>
    <artifactId>anemones-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Java

```java
class App {
    public static void main(String[] args){
        AnemonesConfig config = new AnemonesConfig();
        // namespace ①
        config.setNamespace("test");
        // concurrency ②
        config.setConcurrency(2);
        // workers ③
        config.setWorkers(Collections.singletonList(new AnemonesWorker() {
            @Override
            public String queue() {
                return "test_queue";
            }

            @Override
            public Object perform(String param) {

                int result = Integer.parseInt(param) * 2;
                System.out.printf("%s * 2 = %d \n", param, result);
                return result;
            }
        }));
        // redis url ④
        config.setRedisUrl(RedisURI.create("127.0.0.1", 6379));
        // param converter ⑤
        config.setConverter(new AnemonesParamConverter() {
            @Override
            public String serialize(AnemonesData object) {
                // you can use "fastjson" or other serializer and deserializer
                return JSON.toJSONString(object);
            }

            @Override
            public AnemonesData deserialize(String param) {
                return JSON.parseObject(param, AnemonesData.class);
            }
        });
        AnemonesRetryListener listener = new AnemonesRetryListener((data, throwable) -> errConsumer = true);
        // listeners ⑥
        config.setListeners(Collections.singletonList(listener));
        // wait seconds to terminate ⑦；
        config.setWaitSecondsToTerminate(1);
        AnemonesManager manager = new DefaultAnemonesManager(config);
        // init the manager ⑧
        manager.init();

        // will print "10 * 2 = 20" ⑨
        manager.submitTask("test_queue", Collections.singletonList("10"));

        // will print "20 * 2 = 40" at least 10 seconds later ⑩
        manager.submitIn("test_queue", Collections.singletonList("20"), 10, TimeUnit.SECONDS);

        try {
            // clean up resources
            manager.close();
        } catch (Exception e) {
            //
        }
    }
}
```

① 设定不同的命名空间以避免不同的业务重名导致的干扰

② 决定创建多少线程处理任务

③ 创建一个任务处理过程

④ 连接Redis的`RedisURI`对象. 具体构建方式查看 [lettuce redis client](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details).

⑤ 自定义 `AnemonesData` 的序列化与反序列化器

⑥ Anemones 可以使用自定义监听器来实现功能扩展，比如重试插件或者数据库日志插件。

⑦ 当`AnemonesManager`关闭时（close方法被调用），它会等待一段时间以等待正在处理的任务完成。然后会将还未完成的任务退回队列。

⑧ 初始化`AnemonesManager`

⑨ 此任务将会立马执行

⑩ 此任务将会在大概十秒后执行

## License
Anemones is released under the [MIT License](https://github.com/csdbianhua/anemones/blob/master/LICENSE).
