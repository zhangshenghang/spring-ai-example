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
 * 最终报告节点
 * 生成完整的文档审核报告，包含整个审核流程的总结
 * 
 * @author Jast
 */
public class FinalReportNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(FinalReportNode.class);

    private static final PromptTemplate FINAL_REPORT_PROMPT = new PromptTemplate(
        """
        请生成完整的文档审核最终报告：

        === 基本信息 ===
        文档类型：{document_type}
        紧急程度：{urgency_level}
        审核开始时间：{review_start_time}
        最终状态：{final_status}

        === AI分析结果 ===
        {ai_analysis_result}

        === 人工审核意见 ===
        审核动作：{review_action}
        审核员意见：{reviewer_comments}
        建议修改：{suggested_changes}

        === 处理结果 ===
        {processing_result}

        请生成一份专业的审核报告，包含：
        1. 执行摘要
        2. 审核过程概述
        3. 关键发现和风险点
        4. 决策依据和理由
        5. 后续行动计划
        6. 经验教训和改进建议
        7. 附件和参考文档

        请以结构化的markdown格式输出报告。

        文档审核最终报告：
        """
    );

    private final ChatClient chatClient;

    public FinalReportNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Final report node is running...");

        String documentType = state.value("document_type", "general");
        String urgencyLevel = state.value("urgency_level", "normal");
        String finalStatus = state.value("final_status", "completed");
        String aiAnalysisResult = state.value("ai_analysis_result", "");
        String reviewAction = state.value("review_action", "");
        String reviewerComments = state.value("reviewer_comments", "");
        String suggestedChanges = state.value("suggested_changes", "");
        
        // 获取处理结果
        String processingResult = getProcessingResult(state, finalStatus);
        
        String reviewStartTime = LocalDateTime.now().minusMinutes(30).toString(); // 估算审核开始时间

        logger.info("Generating final report for document type: {}, status: {}", documentType, finalStatus);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(FINAL_REPORT_PROMPT.getTemplate())
                        .param("document_type", documentType)
                        .param("urgency_level", urgencyLevel)
                        .param("review_start_time", reviewStartTime)
                        .param("final_status", finalStatus)
                        .param("ai_analysis_result", aiAnalysisResult)
                        .param("review_action", reviewAction)
                        .param("reviewer_comments", reviewerComments)
                        .param("suggested_changes", suggestedChanges)
                        .param("processing_result", processingResult))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("final_report_stream")
                .startingState(state)
                .mapResult(response -> {
                    String finalReport = response.getResult().getOutput().getText();
                    logger.info("Final report generated successfully, length: {}", finalReport.length());
                    return Map.of(
                        "final_report", finalReport,
                        "report_timestamp", System.currentTimeMillis(),
                        "workflow_completed", true
                    );
                })
                .build(chatResponseFlux);

        return Map.of("final_report", generator);
    }

    /**
     * 根据最终状态获取相应的处理结果
     */
    private String getProcessingResult(OverAllState state, String finalStatus) {
        switch (finalStatus) {
            case "approved":
                return state.value("approval_reason", "文档已通过审核");
            case "rejected":
                return state.value("rejection_reason", "文档未通过审核");
            case "needs_modification":
                return state.value("modification_guidance", "文档需要修改");
            default:
                return "审核流程已完成";
        }
    }
}
