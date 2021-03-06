package mbe.common;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.SimpleGraph;

import java.io.Serializable;
import java.util.*;

/**
 * @description: data structure for bipartite graph.
 * BipartiteGraph has been used by Flink Gelly, in case I include this library.
 * Because vertex num is fixed, so I use a trick to indicate left vertices set and right vertices set.
 * seq: [1, 2, 3, ..., verticesNum*], V means type of Vertex id
 * @className: CustomizedBipartiteGraph
 * @author: Jiri Yu
 * @date: 2021/4/10
 */
public class CustomizedBipartiteGraph implements Serializable {
    @JsonUnwrapped(prefix = "L")
    private final Set<Vertex> verticesL;
    @JsonUnwrapped(prefix = "R")
    private final Set<Vertex> verticesR;

    private Graph<Vertex, Edge> graph;

    public CustomizedBipartiteGraph() {
        this.verticesL = new HashSet<>();
        this.verticesR = new HashSet<>();
        this.graph = new SimpleGraph<>(Edge.class);
    }

    public CustomizedBipartiteGraph(Set<Vertex> verticesL,
                                    Set<Vertex> verticesR) {
        this.verticesL = new HashSet<>();
        this.verticesR = new HashSet<>();
        this.graph = new SimpleGraph<>(Edge.class);

        insertAllVertices(verticesL);
        insertAllVertices(verticesR);
    }

    // copy consturctor
    public CustomizedBipartiteGraph(CustomizedBipartiteGraph customizedBipartiteGraph) {
        this.verticesL = new HashSet<>(customizedBipartiteGraph.getVerticesL());
        this.verticesR = new HashSet<>(customizedBipartiteGraph.getVerticesR());
        this.graph = new SimpleGraph<>(Edge.class);

        // insert vertices
        insertAllVertices(this.verticesL);
        insertAllVertices(this.verticesR);

        // insert edges
        insertAllEdges(customizedBipartiteGraph.getEdges());
    }

    public Set<Vertex> getVerticesL() {
        return verticesL;
    }

    public Set<Vertex> getVerticesR() {
        return verticesR;
    }

    public Set<Edge> getEdges() {
        return graph.edgeSet();
    }

    public Set<Vertex> getVertices() {
        return graph.vertexSet();
    }

    public boolean containsEdge(Edge edge){
        return graph.containsEdge(edge);
    }

    public boolean containsEdge(Vertex v1, Vertex v2){
        return graph.containsEdge(v1, v2);
    }

    public boolean insertVertex(Vertex vertex) {
        if (vertex.getPartition().equals(Partition.LEFT)) {
            verticesL.add(vertex);
        } else {
            verticesR.add(vertex);
        }
        return graph.addVertex(vertex);
    }

    public void insertAllVertices(List<Vertex> vertices) {
        for (int i = 0; i < vertices.size(); i++) {
            insertVertex(vertices.get(i));
        }
    }

    public void insertAllVertices(Vertex[] vertices) {
        for (int i = 0; i < vertices.length; i++) {
            insertVertex(vertices[i]);
        }
    }

    public void insertAllVertices(Set<Vertex> vertices) {
        for (Vertex vertex : vertices){
            insertVertex(vertex);
        }
    }

    /*
     * @description: In this method, we assume all the vertex is legal.
     * That is, the edge's two end points are already in the graph.
     *
     * @param edge
     * @return boolean
     * @author Jiri Yu
     */
    public boolean insertEdge(Edge edge) {
        if (graph.containsEdge(edge)) {
            return false;
        }
        return graph.addEdge(edge.getLeft(), edge.getRight(), edge);
    }

    public void insertAllEdges(Edge[] edges) {
        for (int i = 0; i < edges.length; i++) {
            insertEdge(edges[i]);
        }
    }

    public void insertAllEdges(Set<Edge> edges) {
        for (Edge edge : edges){
            insertEdge(edge);
        }
    }

    /*
     * @description: utils function, for get a vertex's adjacent vertices. It is useful in MineLMBC algorithm.
     *
     * @param v
     * @return java.util.Set<mbe.common.Vertex<V>>
     * @author Jiri Yu
     */
    public Set<Vertex> getAdjacentVertices(Vertex v) {
        return Graphs.neighborSetOf(graph, v);
    }

    /*
     * @description: utils function, for get vertices' adjacent vertices. It is useful in MineLMBC algorithm.
     * Pay attention, gamma(X) returns vertices adjacent to all vertices in set X.
     * We can get gamma(X) == Intersect(gamma(x)), where x belongs to X.
     *
     * @param v
     * @return java.util.Set<mbe.common.Vertex>
     * @author Jiri Yu
     */
    public Set<Vertex> getAdjacentVertices(Set<Vertex> vertices) {
        Set<Vertex> adjacencies = new TreeSet<>();

        if (vertices.isEmpty()) {
            return adjacencies;
        }

        Iterator<Vertex> iterator = vertices.iterator();
        adjacencies.addAll(Graphs.neighborSetOf(graph, iterator.next()));
        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();
            adjacencies = getIntersectVerticesSet(adjacencies, Graphs.neighborSetOf(graph, vertex));
        }
        return adjacencies;
    }

    /*
     * @description: utils function, for get intersect set between set A and set B.
     *
     * @param A
     * @param B
     * @return java.util.Set<mbe.common.Vertex>
     * @author Jiri Yu
     */
    public Set<Vertex> getIntersectVerticesSet(Set<Vertex> A, Set<Vertex> B) {
        Set<Vertex> intersectSet = new HashSet<>();
        if (A.size() > B.size()) {
            for(Vertex vertex : A){
                if (B.contains(vertex)) {
                    intersectSet.add(vertex);
                }
            }
        } else {
            for(Vertex vertex : B){
                if (A.contains(vertex)) {
                    intersectSet.add(vertex);
                }
            }
        }
        return intersectSet;
    }

    /*
     * @description: this function for gammaX Union V, for quick use in MineLMBC.
     *
     * @param gammaX, adjacent vertices of vertices set X.
     * @param v, vertex
     * @return java.util.Set<mbe.common.Vertex>
     * @author Jiri Yu
     */
    public Set<Vertex> getAdjacentVerticesAndIntersect(Set<Vertex> gammaX, Vertex v) {
        Set<Vertex> vertices = Graphs.neighborSetOf(graph, v);
        return getIntersectVerticesSet(gammaX, vertices);
    }

    /*
     * @description: useful methods for flink
     *
     * @param edge
     * @return mbe.common.CustomizedBipartiteGraph
     * @author Jiri Yu
     */
    public CustomizedBipartiteGraph getSubGraph(Edge edge){
        Vertex u = edge.getLeft();
        Vertex v = edge.getRight();

        // Line 5, subgraph of G' induced by (gamma(G', u) U Gamma(G', v))
        // get vertices L & R
        Set<Vertex> verticesL = this.getAdjacentVertices(v);
        Set<Vertex> verticesR = this.getAdjacentVertices(u);
        CustomizedBipartiteGraph subGraph = new CustomizedBipartiteGraph(verticesL, verticesR);
        // get edges
        // Because it is just a specific method used in DynamicBC, so it isn't in CustomizedBG's methods.
        Set<Edge> edgeSet = new HashSet<>();
        for (Vertex vl : verticesL) {
            for (Vertex vr : verticesR) {
                if (this.containsEdge(vl, vr)) {
                    edgeSet.add(new Edge(vl, vr));
                }
            }
        }
        subGraph.insertAllEdges(edgeSet);
        return subGraph;
    }

    @Override
    public String toString() {
        // Most of the time graph.toString() is confusing.
        // I shall draw a graph that will be more clear.
        return graph.toString();
    }
}
