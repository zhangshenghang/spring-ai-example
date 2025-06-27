package com.alibaba.cloud.ai.review.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.streaming.StreamingChatGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 修改处理节点
 * 当文档需要修改后重新审核时，生成修改指导和跟进计划
 * 
 * @author yingzi
 * @since 2025/6/26
 */
public class ModificationProcessNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ModificationProcessNode.class);

    private static final PromptTemplate MODIFICATION_PROCESS_PROMPT = new PromptTemplate(
        """
        文档需要修改后重新提交审核。请生成详细的修改指导：

        文档类型：{document_type}
        风险评分：{risk_score}
        AI分析结果：{ai_analysis_result}
        审核员意见：{reviewer_comments}
        建议修改内容：{suggested_changes}
        修改时间：{modification_time}

        请生成以下内容：
        1. 修改通知和总体指导
        2. 具体的修改要求和标准
        3. 优先级排序的修改清单
        4. 修改后的验收标准
        5. 时间节点和里程碑
        6. 技术支持和资源配置

        请以JSON格式输出，包含：
        - modification_notice: 修改通知
        - modification_requirements: 修改要求详情
        - priority_changes: 按优先级排序的修改清单
        - acceptance_criteria: 验收标准
        - timeline: 时间节点安排
        - support_resources: 支持资源
        - review_schedule: 重审时间安排

        修改指导结果：
        """
    );

    private final ChatClient chatClient;

    public ModificationProcessNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Modification process node is running...");

        String documentType = state.value("document_type", "general");
        int riskScore = (Integer) state.value("risk_score", 5);
        String aiAnalysisResult = state.value("ai_analysis_result", "");
        String reviewerComments = state.value("reviewer_comments", "");
        String suggestedChanges = state.value("suggested_changes", "");
        String modificationTime = LocalDateTime.now().toString();

        logger.info("Processing modification guidance for document type: {}", documentType);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(MODIFICATION_PROCESS_PROMPT.getTemplate())
                        .param("document_type", documentType)
                        .param("risk_score", String.valueOf(riskScore))
                        .param("ai_analysis_result", aiAnalysisResult)
                        .param("reviewer_comments", reviewerComments)
                        .param("suggested_changes", suggestedChanges)
                        .param("modification_time", modificationTime))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("modification_process_stream")
                .startingState(state)
                .mapResult(response -> {
                    String modificationResult = response.getResult().getOutput().getText();
                    logger.info("Modification process guidance generated");
                    return Map.of(
                        "final_status", "needs_modification",
                        "modification_guidance", modificationResult,
                        "modification_timestamp", System.currentTimeMillis()
                    );
                })
                .build(chatResponseFlux);

        return Map.of(
            "final_status", "needs_modification",
            "modification_guidance", generator
        );
    }
}
