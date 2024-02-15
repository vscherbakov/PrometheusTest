package prometheustest;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ThreadApp {
    public String getGreeting() {
        return "ThreadApp>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>:Hello World";
    }

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "60");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(App.PrometheusConfig1.class);
        var registry = context.getBean(CollectorRegistry.class);
        System.out.println(new App().getGreeting());

        var summary = new Summary.Builder()
                .name("mySummary")
                .help("mySummary desc")
                //.quantile(0.0, 0.0001)
                //.quantile(0.25, 0.0001)
                //.quantile(0.5, 0.0001)
                //.quantile(0.75, 0.0001)
                .create();

        var histo = new Histogram.Builder()
                .name("myHisto")
                .help("myHisto desc")
                .create();

        var counter = new Gauge.Builder()
                .name("myGauge")
                .help("myGauge desc")
                .create();


        registry.register(summary);
        registry.register(histo);
        registry.register(counter);
        var start = System.currentTimeMillis();
        var report = new App.SummaryReporter(summary); // n=45 34000-40000ms, 99 sec
        //var report = new SummaryThreadReporter(summary); // 728851ms
        //var report = new GaugeThreadReporter(counter); // 28s, n=47 - 60sec
        //var report = new HistoThreadReporter(histo);
        //var task = new FibonacciTask(50, report); // 45
        var pool = Executors.newFixedThreadPool(600);

        Consumer<Double> consumer = param -> summary.observe(param);
        //var SIZE = 2_000_000_000;
        var SIZE = 1000000;
        //var SIZE = 10_000_000;
        CountDownLatch latch = new CountDownLatch(SIZE);
        var atomic = new AtomicLong();
        for (int i = 0; i < SIZE; i++) {

            int finalI = i;
            pool.submit(() -> {
                // 30mil and 50 threads
                //summary.observe(System.currentTimeMillis() % 10000); 30mil + 22.5 sec
                //histo.observe(System.currentTimeMillis() % 10000); 30mil + 23.2 sec
                //counter.set(System.currentTimeMillis() % 10000); // 30mil + 20.9 sec
                // 50mil and 200 threads
                //summary.observe(System.currentTimeMillis() % 10000); // 50.4 sec
                //histo.observe(System.currentTimeMillis() % 10000); // 50.2 sec
                //counter.set(System.currentTimeMillis() % 10000); // 51.6 sec
                // empty operator is 38 sec

                // summary with quintiles
                summary.observe(atomic.addAndGet(1)); // 83 sec 1mil, 50 threads
                //histo.observe(System.currentTimeMillis() % 10000); // 0.6 sec

                latch.countDown();
                System.out.println(finalI);
            });
        }
        List<Double> quantiles = new ArrayList<>();
        quantiles.add(0.0);
        quantiles.add(0.25);
        quantiles.add(0.50);
        quantiles.add(0.75);

        ExecutorService executorService = Executors.newFixedThreadPool(quantiles.size());
        List<Future<Double>> futures = new ArrayList<>();


        latch.await();
        System.out.println("Duration>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + (System.currentTimeMillis() - start));
        Thread.sleep(100000);
    }

    /**
     * mySummary{quantile="0.0",} 1.0
     * mySummary{quantile="0.25",} 250033.0
     * mySummary{quantile="0.5",} 500017.0
     * mySummary{quantile="0.75",} 749999.0
     */

    static class FibonacciTask extends RecursiveTask<Integer> {
        private final int n;
        private Consumer<Integer> summary;

        public FibonacciTask(int n, Consumer<Integer> consumer) {
            this.n = n;
            this.summary = consumer;
        }

        @Override
        protected Integer compute() {
            if (n <= 1) {
                return n;
            }
            if (summary != null)
                summary.accept(n);

            App.FibonacciTask fib1 = new App.FibonacciTask(n - 1, summary);
            fib1.fork();

            App.FibonacciTask fib2 = new App.FibonacciTask(n - 2, summary);
            return fib2.compute() + fib1.join();
        }

    }


    /**
     * # HELP mySummary mySummary desc
     * # TYPE mySummary summary
     * mySummary_count 1.836311902E9
     * mySummary_sum 6.643838831E9
     * # HELP mySummary_created mySummary desc
     * # TYPE mySummary_created gauge
     * mySummary_created 1.69868041756E9
     */
    static class SummaryReporter implements Consumer<Integer> {

        private Summary summary;

        public SummaryReporter(Summary summary) {
            this.summary = summary;
        }

        @Override
        public void accept(Integer integer) {
            summary.observe(integer);
        }
    }

    static class HistoThreadReporter implements Consumer<Integer> {

        private Histogram hist;
        private ExecutorService service = Executors.newFixedThreadPool(1);

        public HistoThreadReporter(Histogram histogram) {
            this.hist = histogram;
        }

        @Override
        public void accept(Integer integer) {
            hist.observe(integer);
            //service.submit(() -> hist.observe(integer));
        }
    }

    static class GaugeThreadReporter implements Consumer<Integer> {

        private Gauge gauge;
        private ExecutorService service = Executors.newFixedThreadPool(1);

        public GaugeThreadReporter(Gauge gauge) {
            this.gauge = gauge;
        }

        @Override
        public void accept(Integer integer) {
            gauge.inc();
            //service.submit(() -> hist.observe(integer));
        }
    }


    @Configuration
    public static class PrometheusConfig1 {

        @Bean
        public CollectorRegistry prometheusMeterRegistry() {
            return new CollectorRegistry();
        }

        @Bean
        public HTTPServer httpServer() throws Exception {
            var inetAddress = new InetSocketAddress("localhost", 9191);
            var result = new HTTPServer(inetAddress, prometheusMeterRegistry());
            return result;
        }


    }
}