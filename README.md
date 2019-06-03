Anemones
==

Anemones is a Redis-backed library for Simple background processing.

Create background jobs, place those jobs on multiple queues, and process them later.

## Quick start

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

① Multiple `AnemonesManager` with same redis url and different namespaces will not affect each other.

② Decide how much threads will create to process jobs.

③ The background jobs of `AnemonesManager`

④ The `RedisURI` instance for connecting Redis. see [lettuce redis client](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details).

⑤ The custom converter for serializing and de-serializing `AnemonesData`

⑥ Anemones use listeners to implement extend features. Include the retry feature.

⑦ When `AnemonesManager` shutdown (method `close()` be called), it will wait some seconds to finish jobs in progressing.
Then the unfinished jobs will be pushed back to queue again.

⑧ Init the Redis connections and create thread pool and so on.

⑨ The task will be processed soon.

⑩ The task will be processed at least 10 seconds later.

## License
Anemones is released under the [MIT License](https://github.com/csdbianhua/anemones/blob/master/LICENSE).
