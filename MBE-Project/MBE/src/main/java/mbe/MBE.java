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
import mbe.common.CustomizedBipartiteGraph;
import mbe.common.Edge;
import mbe.common.Vertex;
import mbe.process.*;
import mbe.source.CustomizedTextInputFormat;
import mbe.utils.SerializableUtils;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.List;
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
		env.getConfig().setAutoWatermarkInterval(30000);

		// Step 1, create Graph and insert vertices.
		CustomizedBipartiteGraph customizedBipartiteGraph = new CustomizedBipartiteGraph();

		int temp = 3;

		String fileNameVL = null;
		String fileNameVR = null;
		String fileNameE = null;

		switch (temp){
			case 1:
				fileNameVL = "case1Vertices100L.csv";
				fileNameVR = "case1Vertices100R.csv";
				fileNameE = "case1Edges100.csv";
				break;
			case 2:
				fileNameVL = "case2Vertices1000L.csv";
				fileNameVR = "case2Vertices1000R.csv";
				fileNameE = "case2Edges1000.csv";
				break;
			case 3:
				fileNameVL = "case3Vertices10000L.csv";
				fileNameVR = "case3Vertices10000R.csv";
				fileNameE = "case3Edges10000.csv";
				break;
		}

		List<Vertex> verticesL = SerializableUtils.deserializePojos(fileNameVL, Vertex.class);
		List<Vertex> verticesR = SerializableUtils.deserializePojos(fileNameVR, Vertex.class);
		customizedBipartiteGraph.insertAllVertices(verticesL);
		customizedBipartiteGraph.insertAllVertices(verticesR);
		assert customizedBipartiteGraph.getVerticesL().size() == verticesL.size() : "Wrong vertices' size";
		assert customizedBipartiteGraph.getVerticesR().size() == verticesR.size() : "Wrong vertices' size";

		// Step2, create source node, import edge from deserialization
		DataStream<Edge> source = env
				.readFile(new CustomizedTextInputFormat(), SerializableUtils.directory + fileNameE)
				.setParallelism(5);

		// Step3, process DynamicBC

		// Sync Dynamic
//		DataStream<Long> costSyncDynamic = source
//				.map(new SyncDynamicProcessBase(customizedBipartiteGraph, MineLMBC.class))
//				.map(new CountRecordsNum());

		// Sync Static
//		DataStream<Long> costSyncStatic = source
//				.map(new SyncStaticProcessBase(customizedBipartiteGraph))
//				.map(new CountRecordsNum());

		// Async Dynamic
//		DataStream<Long> costAsyncDynamic = AsyncDataStream
//				.orderedWait(source, new AsyncDynamicProcessBase(customizedBipartiteGraph, MineLMBC.class),
//						100000, TimeUnit.MILLISECONDS, 5)
//				.disableChaining()
//				// erase will cause problems, so we must specify the type
//				.map(new CountRecordsNum());

		// Multi Threads
//		DataStream<Long> costMultiDynamic = source
//				.map(new MultiSubgraphAdapter(customizedBipartiteGraph))
//				.setParallelism(1)
//				.disableChaining()
//				.map(new MultiDynamicProcessBase())
//				.setParallelism(5)
//				.disableChaining()
//				.map(new MultiSubsumedBicliquesProcess())
//				.setParallelism(1)
//				.disableChaining()
//				.map(new CountRecordsNum())
//				.disableChaining();

		// Async Multi Threads
		DataStream<Long> costAsyncMultiThreads = AsyncDataStream
				.orderedWait(source
				.map(new MultiSubgraphAdapter(customizedBipartiteGraph))
				.setParallelism(1)
				.disableChaining(), new AsyncMultiDynamicProcessBase(customizedBipartiteGraph, MineLMBC.class),
						100000, TimeUnit.MILLISECONDS, 5)
				.setParallelism(5)
				.disableChaining()
				.map(new MultiSubsumedBicliquesProcess())
				.setParallelism(1)
				.disableChaining()
				.map(new CountRecordsNum())
				.disableChaining();

		// Step4, output bicliques or Size
//		source.print();
//		costSyncDynamic.print("Sync Dynamic");
//		costSyncStatic.print("Sync Static");
//		costAsyncDynamic.print("Async Dynamic");
//		costMultiDynamic.print("Multi Dynamic");
		costAsyncMultiThreads.print("Async Multi");

		env.execute("Dynamic BC");
	}
}
