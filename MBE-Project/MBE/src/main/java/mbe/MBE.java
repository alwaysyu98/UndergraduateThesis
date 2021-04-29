/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mbe;

import mbe.algorithm.MineLMBC;
import mbe.common.Biclique;
import mbe.common.CustomizedBipartiteGraph;
import mbe.common.Edge;
import mbe.common.Vertex;
import mbe.process.AsyncDynamicProcessBase;
import mbe.process.SyncDynamicProcessBase;
import mbe.process.SyncStaticProcessBase;
import mbe.source.CustomizedTextInputFormat;
import mbe.utils.SerializableUtils;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: DynamicBC
 * @author: Jiri Yu
 * @date: 2021/3/23 
 */
public class MBE {
	public static void main(String[] args) throws Exception {
		// localhost:8081
		// it needs local environment, that is why we include flink-dist.
		StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());
		// Because the source is bounded, we choose BATCH mode will get a better performance.
		env.setRuntimeMode(RuntimeExecutionMode.BATCH);
		env.setParallelism(1);

		long start = System.currentTimeMillis();

		// Step 1, create Graph and insert vertices.
		CustomizedBipartiteGraph customizedBipartiteGraph = new CustomizedBipartiteGraph();
		List<Vertex> verticesL = SerializableUtils.deserializePojos("case1Vertices100L.csv", Vertex.class);
		List<Vertex> verticesR = SerializableUtils.deserializePojos("case1Vertices100R.csv", Vertex.class);
		customizedBipartiteGraph.insertAllVertices(verticesL);
		customizedBipartiteGraph.insertAllVertices(verticesR);
		assert customizedBipartiteGraph.getVerticesL().size() == verticesL.size() : "Wrong vertices' size";
		assert customizedBipartiteGraph.getVerticesR().size() == verticesR.size() : "Wrong vertices' size";

		// Step2, create source node, import edge from deserialization
		DataStream<Edge> source = env.readFile(new CustomizedTextInputFormat(), SerializableUtils.directory + "case1Edges100.csv");

		// Step3, process DynmaicBC

		// Sync Dynamic
		DataStream<Long> costTimeSyncDynamic = source
				.map(new SyncDynamicProcessBase(customizedBipartiteGraph, MineLMBC.class));

		// Sync Static
		DataStream<Long> costTimeSyncStatic = source
				.map(new SyncStaticProcessBase(customizedBipartiteGraph));

		// Async Dynamic
		DataStream<Long> costTimeAsyncDynamic = AsyncDataStream.
				orderedWait(source, new AsyncDynamicProcessBase(customizedBipartiteGraph, MineLMBC.class),
						10000, TimeUnit.MILLISECONDS, 10)
				// erase will cause problems, so we must specify the type
				.flatMap((Set<Biclique> bicliques, Collector<Long> collector) -> collector.collect(1L))
				.returns(Types.LONG)
				.windowAll(TumblingProcessingTimeWindows.of(Time.seconds(1)))
				.sum(0);

		// Step4, output biclques or Size
//		costTimeSyncDynamic.print("Sync Dynamic");
//		costTimeSyncStatic.print("Sync Static");
		costTimeAsyncDynamic.print("Async Dynamic");

		env.execute("Dynamic BC");
	}
}
