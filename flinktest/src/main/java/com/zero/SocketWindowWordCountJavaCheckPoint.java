package com.zero;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

/**
 * @author yinchen
 */
public class SocketWindowWordCountJavaCheckPoint {
    public static void main(String[] args) throws Exception {
        //获取需要的端口号
        int port;
        try {
            ParameterTool parameterTool = ParameterTool.fromArgs(args);
            port = parameterTool.getInt("port");
        } catch (Exception e) {
            System.err.println("No port set. use default port 9000--java");
            port = 9010;
        }

        //获取flink的运行环境
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        //设置statebackend
        env.setStateBackend(new MemoryStateBackend());

        CheckpointConfig config = env.getCheckpointConfig();

        // 任务流取消和故障时会保留Checkpoint数据，以便根据实际需要恢复到指定的Checkpoint
        config.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        // 设置checkpoint的周期, 每隔1000 ms进行启动一个检查点
        config.setCheckpointInterval(1000);

        // 设置模式为exactly-once
        config.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // 确保检查点之间有至少500 ms的间隔【checkpoint最小间隔】
        config.setMinPauseBetweenCheckpoints(500);
        // 检查点必须在一分钟内完成，或者被丢弃【checkpoint的超时时间】
        config.setCheckpointTimeout(60000);
        // 同一时间只允许进行一个检查点
        config.setMaxConcurrentCheckpoints(1);



        String hostname = "SparkMaster";
        String delimiter = "\n";
        //连接socket获取输入的数据
        DataStreamSource<String> text = env.socketTextStream(hostname, port, delimiter);


        // a a c

        // a 1
        // a 1
        // c 1
        DataStream<WordWithCount> windowCounts = text.flatMap(new FlatMapFunction<String, WordWithCount>() {
            public void flatMap(String value, Collector<WordWithCount> out) throws Exception {
                String[] splits = value.split("\\s");
                for (String word : splits) {
                    out.collect(new WordWithCount(word, 1L));
                }
            }
        }).keyBy("word")
                .timeWindow(Time.seconds(2), Time.seconds(1))//指定时间窗口大小为2秒，指定时间间隔为1秒
                .sum("count");//在这里使用sum或者reduce都可以
                /*.reduce(new ReduceFunction<WordWithCount>() {
                                    public WordWithCount reduce(WordWithCount a, WordWithCount b) throws Exception {

                                        return new WordWithCount(a.word,a.count+b.count);
                                    }
                                })*/
        //把数据打印到控制台并且设置并行度
        windowCounts.print().setParallelism(1);

        //这一行代码一定要实现，否则程序不执行
        env.execute("Socket window count");

    }

    public static class WordWithCount {
        public String word;
        public long   count;

        public WordWithCount() {
        }

        public WordWithCount(String word, long count) {
            this.word = word;
            this.count = count;
        }

        @Override
        public String toString() {
            return "作者 : 秦凯新 , 窗大小2秒，滑动1秒       {" +
                    " word='" + word + '\'' +
                    ", count=" + count +
                    '}';
        }
    }

}
