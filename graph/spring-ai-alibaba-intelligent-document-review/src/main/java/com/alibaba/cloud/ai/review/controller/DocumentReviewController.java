package com.alibaba.cloud.ai.review.controller;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverConstant;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.review.controller.process.DocumentReviewProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 智能文档审核系统控制器
 * 演示人类返回节点特性：AI分析 -> 人类审核 -> 后续处理
 *
 * @author yingzi
 * @since 2025/6/26
 */
@RestController
@RequestMapping("/document/review")
public class DocumentReviewController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReviewController.class);

    private final CompiledGraph compiledGraph;

    @Value("classpath:/problematic-contract.md")
    private Resource contractResource;

    @Autowired
    public DocumentReviewController(@Qualifier("documentReviewGraph") StateGraph stateGraph) throws GraphStateException {
        SaverConfig saverConfig = SaverConfig.builder().register(SaverConstant.MEMORY, new MemorySaver()).build();
        this.compiledGraph = stateGraph
                .compile(CompileConfig.builder().saverConfig(saverConfig).interruptBefore("human_review").build());
    }

    /**
     * 开始文档审核流程
     * 示例请求：GET /document/review/start?document_content=这是一份合同，请审核其中的条款是否合规&document_type=contract&thread_id=review123
     */
    @GetMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> startReview(
            @RequestParam(value = "document_content", defaultValue = "这是一份技术文档，请审核其内容的准确性和完整性。", required = false) String documentContent,
            @RequestParam(value = "document_type", defaultValue = "technical", required = false) String documentType,
            @RequestParam(value = "urgency_level", defaultValue = "normal", required = false) String urgencyLevel,
            @RequestParam(value = "thread_id", defaultValue = "review_session", required = false) String threadId) throws GraphRunnerException {

        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("document_content", documentContent);
        objectMap.put("document_type", documentType);
        objectMap.put("urgency_level", urgencyLevel);

        DocumentReviewProcess reviewProcess = new DocumentReviewProcess(this.compiledGraph);
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AsyncGenerator<NodeOutput> resultFuture = compiledGraph.stream(objectMap, runnableConfig);
        reviewProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from document review stream"))
                .doOnError(e -> logger.error("Error occurred during document review streaming", e));
    }

    /**
     * 人类审核员提供反馈，继续审核流程
     * 示例请求：GET /document/review/continue?thread_id=review123&action=approve&comments=文档内容符合要求
     */
    @GetMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> continueReview(
            @RequestParam(value = "thread_id", defaultValue = "review_session", required = false) String threadId,
            @RequestParam(value = "action", defaultValue = "approve", required = false) String action,
            @RequestParam(value = "comments", defaultValue = "", required = false) String comments,
            @RequestParam(value = "suggested_changes", defaultValue = "", required = false) String suggestedChanges) throws GraphRunnerException {

        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        StateSnapshot stateSnapshot = this.compiledGraph.getState(runnableConfig);
        OverAllState state = stateSnapshot.state();
        state.withResume();

        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("review_action", action);
        objectMap.put("reviewer_comments", comments);
        objectMap.put("suggested_changes", suggestedChanges);

        state.withHumanFeedback(new OverAllState.HumanFeedback(objectMap, ""));

        // 创建 sink 并处理流式输出
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        DocumentReviewProcess reviewProcess = new DocumentReviewProcess(this.compiledGraph);
        AsyncGenerator<NodeOutput> resultFuture = compiledGraph.streamFromInitialNode(state, runnableConfig);
        reviewProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from document review continuation stream"))
                .doOnError(e -> logger.error("Error occurred during document review continuation streaming", e));
    }

    /**
     * 获取当前审核状态
     */
    @GetMapping("/status")
    public Map<String, Object> getReviewStatus(@RequestParam(value = "thread_id", defaultValue = "review_session") String threadId) {
        try {
            RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
            StateSnapshot stateSnapshot = this.compiledGraph.getState(runnableConfig);
            OverAllState state = stateSnapshot.state();

            Map<String, Object> status = new HashMap<>();
            status.put("thread_id", threadId);
            status.put("current_step", stateSnapshot.next().isEmpty() ? "completed" : stateSnapshot.next());
            status.put("document_content", state.value("document_content", ""));
            status.put("ai_analysis", state.value("ai_analysis_result", ""));
            status.put("risk_score", state.value("risk_score", 0));
            status.put("final_status", state.value("final_status", "pending"));

            return status;
        } catch (Exception e) {
            logger.error("Failed to get review status", e);
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("error", "Failed to get status: " + e.getMessage());
            return errorStatus;
        }
    }

    /**
     * 使用预设的问题合同进行审核
     * 示例请求：GET /document/review/contract?thread_id=contract123
     */
    @GetMapping(value = "/contract", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> reviewContract(@RequestParam(value = "thread_id", defaultValue = "contract_review", required = false) String threadId) throws Exception {

        // 读取合同文档内容
        String contractContent = contractResource.getContentAsString(StandardCharsets.UTF_8);

        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("document_content", contractContent);
        objectMap.put("document_type", "contract");
        objectMap.put("urgency_level", "high");

        logger.info("Starting contract review with thread_id: {}, content length: {}", threadId, contractContent.length());

        DocumentReviewProcess reviewProcess = new DocumentReviewProcess(this.compiledGraph);
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AsyncGenerator<NodeOutput> resultFuture = compiledGraph.stream(objectMap, runnableConfig);
        reviewProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from contract review stream"))
                .doOnError(e -> logger.error("Error occurred during contract review streaming", e));
    }

    /**
     * 获取合同文档内容预览
     */
    @GetMapping("/contract/preview")
    public ResponseEntity<Map<String, Object>> getContractPreview() {
        try {
            String contractContent = contractResource.getContentAsString(StandardCharsets.UTF_8);
            Map<String, Object> preview = new HashMap<>();
            preview.put("filename", "problematic-contract.md");
            preview.put("content", contractContent);
            preview.put("length", contractContent.length());
            preview.put("type", "contract");
            preview.put("description", "这是一份存在多项问题的软件开发合同，用于演示文档审核系统");

            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            logger.error("Failed to load contract preview", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to load contract: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
