package com.alibaba.cloud.ai.review.controller.process;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文档审核流程处理器
 * 负责处理审核流程中的流式输出
 * 
 * @author yingzi
 * @since 2025/6/26
 */
public class DocumentReviewProcess {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReviewProcess.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CompiledGraph compiledGraph;

    public DocumentReviewProcess(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    public void processStream(AsyncGenerator<NodeOutput> generator, Sinks.Many<ServerSentEvent<String>> sink) {
        executor.submit(() -> {
            generator.forEachAsync(output -> {
                try {
                    logger.info("Document review output = {}", output);
                    String nodeName = output.node();
                    String content;
                    
                    if (output instanceof StreamingOutput streamingOutput) {
                        // 流式输出（如AI分析过程）
                        content = JSON.toJSONString(Map.of(nodeName, streamingOutput.chunk()));
                    } else {
                        // 节点完成输出
                        JSONObject nodeOutput = new JSONObject();
                        nodeOutput.put("data", output.state().data());
                        nodeOutput.put("node", nodeName);
                        nodeOutput.put("timestamp", System.currentTimeMillis());
                        
                        // 为不同节点添加特定信息
                        switch (nodeName) {
                            case "content_analysis":
                                nodeOutput.put("message", "正在分析文档内容...");
                                break;
                            case "compliance_check":
                                nodeOutput.put("message", "正在进行合规性检查...");
                                break;
                            case "risk_assessment":
                                nodeOutput.put("message", "正在评估风险等级...");
                                break;
                            case "human_review":
                                nodeOutput.put("message", "等待人工审核...");
                                nodeOutput.put("action_required", "人工审核员需要确认AI分析结果");
                                break;
                            case "approval_process":
                                nodeOutput.put("message", "正在处理审核结果...");
                                break;
                            case "final_report":
                                nodeOutput.put("message", "生成最终审核报告...");
                                break;
                            default:
                                nodeOutput.put("message", "处理中...");
                        }
                        
                        content = JSON.toJSONString(nodeOutput);
                    }
                    
                    sink.tryEmitNext(ServerSentEvent.builder(content).build());
                } catch (Exception e) {
                    logger.error("Error processing stream output", e);
                    throw new CompletionException(e);
                }
            }).thenAccept(v -> {
                // 流程正常完成
                logger.info("Document review stream completed successfully");
                sink.tryEmitComplete();
            }).exceptionally(e -> {
                logger.error("Document review stream failed", e);
                sink.tryEmitError(e);
                return null;
            });
        });
    }
}
