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

import java.util.Map;

/**
 * 风险评估节点
 * 评估文档可能带来的各种风险并给出风险等级
 * 
 * @author Jast
 */
public class RiskAssessmentNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentNode.class);

    private static final PromptTemplate RISK_ASSESSMENT_PROMPT = new PromptTemplate(
        """
        你是一个专业的风险评估专家。请对以下文档进行综合风险评估：

        文档类型：{document_type}
        紧急程度：{urgency_level}
        内容分析结果：{content_analysis_result}
        合规性检查结果：{compliance_result}

        请从以下维度评估风险：

        1. 法律风险：
           - 合规性违规风险
           - 法律责任风险
           - 监管处罚风险

        2. 财务风险：
           - 经济损失风险
           - 成本超支风险
           - 投资风险

        3. 声誉风险：
           - 品牌形象风险
           - 公众关系风险
           - 媒体负面报道风险

        4. 操作风险：
           - 执行困难风险
           - 技术实施风险
           - 人员配置风险

        5. 信息安全风险：
           - 数据泄露风险
           - 隐私保护风险
           - 网络安全风险

        请以JSON格式返回评估结果，包含：
        - overall_risk_score: 总体风险评分(1-10, 1最低，10最高)
        - risk_level: 风险等级(low/medium/high/critical)
        - legal_risk: 法律风险评分(1-10)
        - financial_risk: 财务风险评分(1-10)
        - reputation_risk: 声誉风险评分(1-10)
        - operational_risk: 操作风险评分(1-10)
        - security_risk: 安全风险评分(1-10)
        - identified_risks: 识别的具体风险列表
        - mitigation_measures: 风险缓解措施建议
        - escalation_required: 是否需要上级审批(true/false)

        风险评估结果：
        """
    );

    private final ChatClient chatClient;

    public RiskAssessmentNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Risk assessment node is running...");

        String documentType = state.value("document_type", "general");
        String urgencyLevel = state.value("urgency_level", "normal");
        String contentAnalysisResult = state.value("content_analysis_result", "");
        String complianceResult = state.value("compliance_result", "");

        logger.info("Performing risk assessment for document type: {}, urgency: {}", documentType, urgencyLevel);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(RISK_ASSESSMENT_PROMPT.getTemplate())
                        .param("document_type", documentType)
                        .param("urgency_level", urgencyLevel)
                        .param("content_analysis_result", contentAnalysisResult)
                        .param("compliance_result", complianceResult))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("risk_assessment_stream")
                .startingState(state)
                .mapResult(response -> {
                    String riskResult = response.getResult().getOutput().getText();
                    logger.info("Risk assessment completed, result length: {}", riskResult.length());
                    
                    // 尝试提取风险评分（简化处理）
                    int riskScore = extractRiskScore(riskResult);
                    
                    return Map.of(
                        "ai_analysis_result", riskResult,
                        "risk_score", riskScore
                    );
                })
                .build(chatResponseFlux);

        return Map.of("ai_analysis_result", generator, "risk_score", generator);
    }

    /**
     * 从AI分析结果中提取风险评分
     * 简化实现，实际应用中可能需要更复杂的JSON解析
     */
    private int extractRiskScore(String riskResult) {
        try {
            // 简单的正则匹配提取风险评分
            if (riskResult.contains("overall_risk_score")) {
                String[] parts = riskResult.split("overall_risk_score");
                if (parts.length > 1) {
                    String scorePart = parts[1].split(",")[0].replaceAll("[^0-9]", "");
                    if (!scorePart.isEmpty()) {
                        return Integer.parseInt(scorePart);
                    }
                }
            }
            // 默认中等风险
            return 5;
        } catch (Exception e) {
            logger.warn("Failed to extract risk score from result", e);
            return 5; // 默认中等风险
        }
    }
}
