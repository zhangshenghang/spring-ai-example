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
 * 拒绝处理节点
 * 当文档被人工审核拒绝后，生成拒绝通知和改进建议
 * 
 * @author yingzi
 * @since 2025/6/26
 */
public class RejectionProcessNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(RejectionProcessNode.class);

    private static final PromptTemplate REJECTION_PROCESS_PROMPT = new PromptTemplate(
        """
        文档未通过人工审核，已被拒绝。请生成正式的拒绝处理结果：

        文档类型：{document_type}
        风险评分：{risk_score}
        AI分析结果：{ai_analysis_result}
        审核员意见：{reviewer_comments}
        拒绝时间：{rejection_time}

        请生成以下内容：
        1. 正式的拒绝通知
        2. 详细的拒绝理由说明
        3. 发现的主要问题点
        4. 具体的改进建议
        5. 重新提交的要求和标准

        请以JSON格式输出，包含：
        - rejection_notice: 正式拒绝通知
        - rejection_reason: 拒绝理由
        - major_issues: 主要问题列表
        - improvement_suggestions: 改进建议列表
        - resubmission_requirements: 重新提交要求
        - deadline_for_resubmission: 重新提交截止时间
        - contact_for_clarification: 联系人信息

        拒绝处理结果：
        """
    );

    private final ChatClient chatClient;

    public RejectionProcessNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Rejection process node is running...");

        String documentType = state.value("document_type", "general");
        int riskScore = (Integer) state.value("risk_score", 5);
        String aiAnalysisResult = state.value("ai_analysis_result", "");
        String reviewerComments = state.value("reviewer_comments", "");
        String rejectionTime = LocalDateTime.now().toString();

        logger.info("Processing rejection for document type: {}, risk score: {}", documentType, riskScore);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(REJECTION_PROCESS_PROMPT.getTemplate())
                        .param("document_type", documentType)
                        .param("risk_score", String.valueOf(riskScore))
                        .param("ai_analysis_result", aiAnalysisResult)
                        .param("reviewer_comments", reviewerComments)
                        .param("rejection_time", rejectionTime))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("rejection_process_stream")
                .startingState(state)
                .mapResult(response -> {
                    String rejectionResult = response.getResult().getOutput().getText();
                    logger.info("Rejection process completed");
                    return Map.of(
                        "final_status", "rejected",
                        "rejection_reason", rejectionResult,
                        "rejection_timestamp", System.currentTimeMillis()
                    );
                })
                .build(chatResponseFlux);

        return Map.of(
            "final_status", "rejected",
            "rejection_reason", generator
        );
    }
}
