package com.zero.storm;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;

/**
 * 实现单词计数topology
 */
public class App {

    public static void main(String[] args){
        //实例化spout和bolt
        DocumentSpout spout = new DocumentSpout();
        SplitBolt splitBolt = new SplitBolt();
        WordCountBolt countBolt = new WordCountBolt();
        ReportBolt reportBolt = new ReportBolt();

        TopologyBuilder builder = new TopologyBuilder();//创建了一个TopologyBuilder实例

        //builder.setSpout(SENTENCE_SPOUT_ID, spout);//注册一个sentence spout
        //设置两个Executeor(线程)，默认一个
        builder.setSpout("document-spout", spout, 2);

        // DocumentSpout --> SplitBolt
        // 注册一个bolt并订阅sentence发射出的数据流，
        // shuffleGrouping方法告诉Storm要将SentenceSpout发射的tuple随机均匀的分发给SplitSentenceBolt的实例
        //SplitSentenceBolt单词分割器设置4个Task，2个Executeor(线程)
        builder.setBolt("split-bolt", splitBolt, 2).setNumTasks(4)
                .shuffleGrouping("document-spout");
        // 默认一个parallelism（一个executor）运行一个task，但是可以通过setNumTasks来指定task数，上面的例子就是2个executor，4个task，一个task对应一个实例

        // SplitBolt --> WordCountBolt

        //fieldsGrouping将含有特定数据的tuple路由到特殊的bolt实例中
        //这里fieldsGrouping()方法保证所有“word”字段相同的tuple会被路由到同一个WordCountBolt实例中
        //WordCountBolt单词计数器设置4个Executeor(线程)
        builder.setBolt("count-bolt", countBolt, 4)
                .fieldsGrouping("split-bolt", new Fields("word"));

        // WordCountBolt --> ReportBolt
        // globalGrouping是把WordCountBolt发射的所有tuple路由到唯一的ReportBolt
        builder.setBolt("report-bolt", reportBolt)
                .globalGrouping("count-bolt");

        Config config = new Config();//Config类是一个HashMap<String,Object>的子类，用来配置topology运行时的行为
        config.put("NAME", "zero"); // 会传给Spout、Bolt
        config.setNumAckers(0);// 默认acker会有一个executor

        //config.setNumWorkers(2); //设置worker数量
        LocalCluster cluster = new LocalCluster();

        //本地提交
        cluster.submitTopology("word-count-topology", config, builder.createTopology());

        Utils.sleep(100000);
        cluster.killTopology("word-count-topology");
        cluster.shutdown();
    }
}