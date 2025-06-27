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
 * 审批处理节点
 * 当文档通过人工审核后，生成正式的审批文件和后续处理指导
 * 
 * @author yingzi
 * @since 2025/6/26
 */
public class ApprovalProcessNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalProcessNode.class);

    private static final PromptTemplate APPROVAL_PROCESS_PROMPT = new PromptTemplate(
        """
        文档已通过人工审核，请生成正式的审批处理结果：

        文档类型：{document_type}
        风险评分：{risk_score}
        AI分析结果：{ai_analysis_result}
        审核员意见：{reviewer_comments}
        审批时间：{approval_time}

        请生成以下内容：
        1. 正式的审批通知
        2. 审批决定的理由说明
        3. 后续执行的指导建议
        4. 相关责任人和时间节点
        5. 监督和跟进机制

        请以JSON格式输出，包含：
        - approval_notice: 正式审批通知
        - approval_reason: 审批理由
        - execution_guide: 执行指导
        - responsible_parties: 责任人安排
        - follow_up_plan: 跟进计划
        - validity_period: 有效期
        - special_instructions: 特殊说明

        审批处理结果：
        """
    );

    private final ChatClient chatClient;

    public ApprovalProcessNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Approval process node is running...");

        String documentType = state.value("document_type", "general");
        int riskScore = (Integer) state.value("risk_score", 5);
        String aiAnalysisResult = state.value("ai_analysis_result", "");
        String reviewerComments = state.value("reviewer_comments", "");
        String approvalTime = LocalDateTime.now().toString();

        logger.info("Processing approval for document type: {}, risk score: {}", documentType, riskScore);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(APPROVAL_PROCESS_PROMPT.getTemplate())
                        .param("document_type", documentType)
                        .param("risk_score", String.valueOf(riskScore))
                        .param("ai_analysis_result", aiAnalysisResult)
                        .param("reviewer_comments", reviewerComments)
                        .param("approval_time", approvalTime))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("approval_process_stream")
                .startingState(state)
                .mapResult(response -> {
                    String approvalResult = response.getResult().getOutput().getText();
                    logger.info("Approval process completed successfully");
                    return Map.of(
                        "final_status", "approved",
                        "approval_reason", approvalResult,
                        "approval_timestamp", System.currentTimeMillis()
                    );
                })
                .build(chatResponseFlux);

        return Map.of(
            "final_status", "approved",
            "approval_reason", generator
        );
    }
}
